/*
 * Copyright (C) 2026 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.processes.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import org.picocontainer.Startable;

import io.meeds.common.ContainerTransactional;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.model.ProjectMovePass;
import org.exoplatform.processes.model.ProjectMoveState;
import org.exoplatform.processes.constant.TaskMoveResult;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;

/**
 * Moves the requests of a process to the task project a save set, in the
 * background, on a thread of its own. One mechanism serves the save, the
 * retries and the restart of a node: the process's move flag says that some
 * of its requests may be outside its project; a pass moves them, and only a
 * pass that leaves none behind clears it. A pass first claims the process
 * with a token and a lease, so one pass runs per process at a time across the
 * cluster. The pass's writes of the move state after the claim are
 * conditioned on its token, and those that end or stop the move also on the
 * move sequence it started with, so a save committed during the pass is never
 * overwritten; each turn reads the stored project again before it moves a
 * task. A sweep, on every node, hands over the processes whose next attempt
 * is due. No state is kept in memory per process.
 */
public class ProcessesProjectMoveService implements Startable {

  /** The request records a pass reads at a time */
  static final int                   PAGE_SIZE                 = 100;

  private static final Log           LOG                       = ExoLogger.getLogger(ProcessesProjectMoveService.class);

  private static final String        THREAD_NAME               = "processes-project-move";

  private final ProcessesStorage     processesStorage;

  private final ProjectService       projectService;

  private final UserACL              userACL;

  private final long                 leaseMillis;

  private final long                 initialDelaySeconds;

  private final long                 maxDelaySeconds;

  private final int                  errorAfterAttempts;

  private final long                 verifyDelaySeconds;

  private final long                 sweepPeriodSeconds;

  private LongSupplier               clock                     = System::currentTimeMillis;

  private LongSupplier               monotonicClock            = System::nanoTime;

  private ScheduledExecutorService   executor;

  private ScheduledFuture<?>         sweepFuture;

  /**
   * @param processesStorage the processes storage
   * @param projectService the Task add-on's project service
   * @param userACL the platform's ACL, to resolve the user a pass acts as
   * @param params leaseSeconds, retryInitialDelaySeconds,
   *          retryMaxDelaySeconds, retryErrorAfterAttempts,
   *          verifyDelaySeconds and sweepPeriodSeconds, each replaced by its
   *          default, with a WARN, when missing or out of its range
   */
  public ProcessesProjectMoveService(ProcessesStorage processesStorage,
                                     ProjectService projectService,
                                     UserACL userACL,
                                     InitParams params) {
    this.processesStorage = processesStorage;
    this.projectService = projectService;
    this.userACL = userACL;
    this.leaseMillis = readParam(params, "leaseSeconds", 600, 60, 86400) * 1000L;
    this.initialDelaySeconds = readParam(params, "retryInitialDelaySeconds", 60, 1, 86400);
    long maxDelay = readParam(params, "retryMaxDelaySeconds", 3600, 1, 604800);
    if (maxDelay < initialDelaySeconds) {
      LOG.warn("The processes move's retryMaxDelaySeconds {} is below its retryInitialDelaySeconds {}, {} is used",
               maxDelay,
               initialDelaySeconds,
               Math.max(3600, initialDelaySeconds));
      maxDelay = Math.max(3600, initialDelaySeconds);
    }
    this.maxDelaySeconds = maxDelay;
    this.errorAfterAttempts = (int) readParam(params, "retryErrorAfterAttempts", 10, 1, 1000);
    this.verifyDelaySeconds = readParam(params, "verifyDelaySeconds", 600, 60, 86400);
    this.sweepPeriodSeconds = readParam(params, "sweepPeriodSeconds", 300, 60, 86400);
  }

  /**
   * Starts the move thread and its sweep, whose first run resumes the moves a
   * stopped node left pending.
   */
  @Override
  public void start() {
    executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, THREAD_NAME);
      thread.setDaemon(true);
      return thread;
    });
    sweepFuture = executor.scheduleWithFixedDelay(this::sweep,
                                                  Math.min(60, sweepPeriodSeconds),
                                                  sweepPeriodSeconds,
                                                  TimeUnit.SECONDS);
  }

  /**
   * Stops the move thread: a pass under way stops at its next turn, its claim
   * released, and its process stays flagged for the next sweep of any node.
   */
  @Override
  public void stop() {
    if (sweepFuture != null) {
      sweepFuture.cancel(false);
    }
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  /**
   * Hands the move of a process's requests to the move thread, which runs a
   * pass when it can claim the process. Never throws: a move that cannot be
   * handed over now is left to the sweep.
   *
   * @param workFlowId the process id
   */
  public void submitMove(long workFlowId) {
    ScheduledExecutorService moveExecutor = executor;
    if (moveExecutor == null || moveExecutor.isShutdown()) {
      LOG.debug("The move of the requests of the process {} is left to the sweep", workFlowId);
      return;
    }
    try {
      moveExecutor.execute(() -> runMove(workFlowId));
    } catch (RejectedExecutionException e) {
      LOG.debug("The move of the requests of the process {} is left to the sweep", workFlowId, e);
    }
  }

  /**
   * Reads the processes whose move a pass may claim now.
   *
   * @return their ids, the most overdue first
   */
  @ContainerTransactional
  public List<Long> getDueMoves() {
    return processesStorage.getDueProjectMoves(clock.getAsLong());
  }

  /**
   * Ends a pass that read every page: clears the flag when the pass moved
   * nothing, failed nothing and left no request outside the project; schedules
   * the verification pass when it moved requests; schedules a retry, with a
   * doubling delay, otherwise. Each write is conditioned on the pass's token
   * and starting sequence.
   *
   * @param workFlowId the process id
   * @param token the pass's token
   * @param state the state the pass started from
   * @param result what the pass did
   * @return true when the move is done with for this pass, false when a save
   *         committed during the pass may need another one
   */
  @ContainerTransactional(atomic = true)
  public boolean endPass(long workFlowId, String token, ProjectMoveState state, ProjectMovePass result) {
    long remaining = processesStorage.countRequestsOutsideProject(workFlowId, state.projectId());
    long now = clock.getAsLong();
    if (result.getFailed() == 0 && remaining == 0) {
      LOG.info("The move of the process {} passed: moved {}, failed 0, remaining 0", workFlowId, result.getMoved());
      if (result.getMoved() == 0) {
        return processesStorage.clearProjectMove(workFlowId, state.seq(), token, now);
      }
      return processesStorage.rescheduleProjectMove(workFlowId,
                                                    state.seq(),
                                                    token,
                                                    now,
                                                    now + verifyDelaySeconds * 1000L,
                                                    false);
    }
    int attempts = state.attempts() + 1;
    long nextAttempt = now + retryDelaySeconds(attempts) * 1000L;
    processesStorage.rescheduleProjectMove(workFlowId, state.seq(), token, now, nextAttempt, true);
    List<Long> failedIds = result.getFailedIds();
    if (attempts >= errorAfterAttempts) {
      LOG.error("The move of the process {} failed {} times: moved {}, failed {}, remaining {}, failing requests {}",
                workFlowId,
                attempts,
                result.getMoved(),
                result.getFailed(),
                remaining,
                failedIds);
    } else {
      LOG.warn("The move of the process {} is retried: moved {}, failed {}, remaining {}, failing requests {}",
               workFlowId,
               result.getMoved(),
               result.getFailed(),
               remaining,
               failedIds);
    }
    return true;
  }

  /**
   * Claims a process's move for a pass.
   *
   * @param workFlowId the process id
   * @param token the pass's own random token
   * @return the state the pass starts from, or null when it holds no claim
   */
  @ContainerTransactional(atomic = true)
  public ProjectMoveState claimMove(long workFlowId, String token) {
    long now = clock.getAsLong();
    return processesStorage.claimProjectMove(workFlowId, token, now, now + leaseMillis);
  }

  /**
   * Extends the claim a pass holds.
   *
   * @param workFlowId the process id
   * @param token the pass's token
   * @return the move's state, or null when the pass no longer holds the claim
   */
  @ContainerTransactional(atomic = true)
  public ProjectMoveState renewMove(long workFlowId, String token) {
    long now = clock.getAsLong();
    return processesStorage.renewProjectMove(workFlowId, token, now, now + leaseMillis);
  }

  /**
   * Releases the claim a pass holds, if it still holds it.
   *
   * @param workFlowId the process id
   * @param token the pass's token
   */
  @ContainerTransactional(atomic = true)
  public void releaseMove(long workFlowId, String token) {
    processesStorage.releaseProjectMove(workFlowId, token);
  }

  /**
   * Stops the move of a process whose project no longer exists.
   *
   * @param workFlowId the process id
   * @param seq the move sequence the pass started with
   * @param token the pass's token
   */
  @ContainerTransactional(atomic = true)
  public void stopMove(long workFlowId, long seq, String token) {
    processesStorage.stopProjectMove(workFlowId, seq, token);
  }

  /**
   * Deletes the request record of a task that no longer exists.
   *
   * @param taskId the task id
   */
  @ContainerTransactional(atomic = true)
  public void deleteRequestRecord(long taskId) {
    processesStorage.deleteRequestRecord(taskId);
  }

  /**
   * Reads a page of a process's request records.
   *
   * @param workFlowId the process id
   * @param afterTaskId the last task id of the previous page, 0 for the first
   * @return the task ids after the given one, ascending
   */
  @ContainerTransactional
  public List<Long> readPage(long workFlowId, long afterTaskId) {
    return processesStorage.getRequestRecordTaskIds(workFlowId, afterTaskId, PAGE_SIZE);
  }

  /**
   * One turn of a pass: the stored project is read again from the database,
   * then the task is moved to it. A save that changes the project, or deletes
   * the process, committed between that read and the task's save still sends
   * this one task to the project read: the new project's pass moves it again,
   * and a deleted process's task stays a plain task.
   *
   * @param workFlowId the process id
   * @param projectId the project the pass moves the requests to
   * @param taskId the request's task id
   * @return what the move did, or null when the stored project changed
   */
  @ContainerTransactional
  public TaskMoveResult moveTask(long workFlowId, long projectId, long taskId) {
    Long storedProjectId = processesStorage.getStoredProjectId(workFlowId);
    if (storedProjectId == null || storedProjectId != projectId) {
      return null;
    }
    return processesStorage.moveTaskToProject(taskId, projectId);
  }

  /**
   * Tells whether a task project exists.
   *
   * @param projectId the project id
   * @return false when Task does not find it
   */
  @ContainerTransactional
  public boolean projectExists(long projectId) {
    if (projectId <= 0) {
      return false;
    }
    try {
      return projectService.getProject(projectId) != null;
    } catch (EntityNotFoundException e) {
      return false;
    }
  }

  /**
   * Hands over every process whose move a pass may claim now. A failure is
   * logged and never escapes, so the sweep keeps its schedule.
   */
  void sweep() {
    try {
      getDueMoves().forEach(this::submitMove);
    } catch (Throwable e) { // NOSONAR a throwable escaping cancels the sweep for good
      LOG.error("The sweep of the pending processes moves failed", e);
    }
  }

  /**
   * Runs one pass of a process's move under a fresh claim, as the editor who
   * changed the project. Whatever stops it, the claim is released and the
   * conversation state removed; a throwable is logged at ERROR and never
   * escapes.
   *
   * @param workFlowId the process id
   */
  void runMove(long workFlowId) {
    String token = UUID.randomUUID().toString();
    try {
      runClaimedMove(workFlowId, token);
    } catch (Throwable e) { // NOSONAR a throwable escaping an executor task is never logged
      LOG.error("The move of the requests of the process {} failed", workFlowId, e);
    } finally {
      try {
        releaseMove(workFlowId, token);
      } catch (Throwable e) { // NOSONAR the claim then expires with its lease
        LOG.warn("The claim of the move of the process {} could not be released, it expires with its lease", workFlowId, e);
      }
      ConversationState.setCurrent(null);
    }
  }

  /**
   * Computes the delay before the next attempt of a move: the initial delay,
   * doubled at each failed attempt, up to the maximum. The exponent is capped
   * before the product, so a move retried forever never overflows.
   *
   * @param attempts the failed attempts, the last one included
   * @return the delay, in seconds
   */
  long retryDelaySeconds(int attempts) {
    int exponent = Math.min(Math.max(attempts - 1, 0), 20);
    return Math.min(initialDelaySeconds * (1L << exponent), maxDelaySeconds);
  }

  /**
   * Tells whether the sweep is scheduled on the move thread.
   *
   * @return true between {@link #start()} and {@link #stop()}
   */
  boolean isSweepScheduled() {
    return sweepFuture != null && !sweepFuture.isDone();
  }

  /**
   * Replaces the clocks, for tests.
   *
   * @param clock the epoch-milliseconds clock
   * @param monotonicClock the nanoseconds monotonic clock
   */
  void setClocks(LongSupplier clock, LongSupplier monotonicClock) {
    this.clock = clock;
    this.monotonicClock = monotonicClock;
  }

  /**
   * Claims a process's move, then runs its passes while a save committed
   * during a clean pass asks for another one.
   *
   * @param workFlowId the process id
   * @param token the pass's token
   */
  private void runClaimedMove(long workFlowId, String token) {
    ProjectMoveState state = claimMove(workFlowId, token);
    if (state == null) {
      return;
    }
    long lastRenewal = monotonicClock.getAsLong();
    while (state != null) {
      setConversationState(state.requestedBy());
      if (!projectExists(state.projectId())) {
        LOG.error("The requests of the process {} cannot move: its project {} was not found, no pass runs until a save sets another project",
                  workFlowId,
                  state.projectId());
        stopMove(workFlowId, state.seq(), token);
        LOG.info("The move of the process {} stopped: moved 0, failed 0, its project was not found", workFlowId);
        return;
      }
      ProjectMovePass result = new ProjectMovePass();
      String stopReason = movePages(workFlowId, token, state, result, lastRenewal);
      if (stopReason != null) {
        LOG.info("The move of the process {} stopped: moved {}, failed {}, {}", workFlowId, result.getMoved(), result.getFailed(), stopReason);
        return;
      }
      lastRenewal = result.getLastRenewal();
      if (endPass(workFlowId, token, state, result)) {
        return;
      }
      // A save committed during a clean pass: start again from its state
      ProjectMoveState renewed = renewMove(workFlowId, token);
      lastRenewal = monotonicClock.getAsLong();
      if (renewed == null || renewed.seq() == state.seq()) {
        return;
      }
      state = renewed;
    }
  }

  /**
   * Moves the requests of a process page by page, renewing the claim when a
   * third of the lease has passed since the last renewal.
   *
   * @return null when the pages were all read, else why the pass stopped
   */
  private String movePages(long workFlowId, String token, ProjectMoveState state, ProjectMovePass result, long lastRenewal) {
    result.setLastRenewal(lastRenewal);
    long afterTaskId = 0;
    List<Long> page = readPage(workFlowId, afterTaskId);
    while (!page.isEmpty()) {
      for (Long taskId : page) {
        afterTaskId = taskId;
        if (Thread.currentThread().isInterrupted()) {
          return "its thread was stopped";
        }
        if (monotonicClock.getAsLong() - result.getLastRenewal() >= TimeUnit.MILLISECONDS.toNanos(leaseMillis / 3)) {
          if (renewMove(workFlowId, token) == null) {
            return "its claim was lost";
          }
          result.setLastRenewal(monotonicClock.getAsLong());
        }
        TaskMoveResult moveResult;
        try {
          moveResult = moveTask(workFlowId, state.projectId(), taskId);
        } catch (RuntimeException e) {
          LOG.warn("The request {} of the process {} could not move to the project {}", taskId, workFlowId, state.projectId(), e);
          result.countFailed(taskId);
          continue;
        }
        if (moveResult == null) {
          return "its project was changed";
        }
        if (moveResult == TaskMoveResult.MOVED) {
          result.countMoved();
        } else if (moveResult == TaskMoveResult.NOT_FOUND) {
          deleteRequestRecord(taskId);
        }
      }
      page = readPage(workFlowId, afterTaskId);
    }
    return null;
  }

  /**
   * Sets the conversation state of the user a pass acts as: Task's label
   * events read it, and the realizations of the moved tasks are credited to
   * that user. The system acts when the user is not known.
   *
   * @param userId the user id of the editor who changed the project
   */
  private void setConversationState(String userId) {
    Identity identity = userId == null ? null : userACL.getUserIdentity(userId);
    if (identity == null) {
      identity = new Identity(IdentityConstants.SYSTEM);
    }
    ConversationState.setCurrent(new ConversationState(identity));
  }

  /**
   * Reads an integer parameter, replaced by its default, with a WARN, when
   * missing, not a number or out of its range.
   */
  private static long readParam(InitParams params, String name, long defaultValue, long min, long max) {
    ValueParam param = params == null ? null : params.getValueParam(name);
    if (param == null || param.getValue() == null || param.getValue().isBlank()) {
      return defaultValue;
    }
    try {
      long value = Long.parseLong(param.getValue().trim());
      if (value >= min && value <= max) {
        return value;
      }
    } catch (NumberFormatException e) {
      // Logged below
    }
    LOG.warn("The processes move parameter {}={} is not an integer from {} to {}, {} is used",
             name,
             param.getValue(),
             min,
             max,
             defaultValue);
    return defaultValue;
  }
}
