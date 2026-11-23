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
package org.exoplatform.processes.upgrade;

import java.util.List;

import jakarta.persistence.EntityManager;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.processes.dao.RequestDAO;
import org.exoplatform.processes.entity.RequestEntity;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.task.dto.StatusDto;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.TaskService;

/**
 * Carries the meaning of the requests created before EXO-94923 over to their
 * own state, once: a request's progress was then read from the names of the
 * columns processes created, which it now never reads. Those names are read
 * here, once, as data; the columns stay on the boards as ordinary columns.
 * A task the upgrade completes is first moved to its process's stored project
 * when it sits elsewhere, as every completion of a request is; the others stay
 * where they are. Per request, on its record still without state:
 * <ul>
 * <li>in « Canceled »: cancelled, its task completed, archived when its task
 * was completed (cancelled requests were listed with the archived ones);</li>
 * <li>in « Validated » or « Refused », not completed: its task is completed,
 * so it reads decided and not pending, not archived;</li>
 * <li>in « Validated » or « Refused », completed: archived;</li>
 * <li>in another column, completed (archived while open): archived, its task
 * left completed, so it reads closed;</li>
 * <li>in another column, open: neither, it stays pending.</li>
 * </ul>
 * Each request's flags and its task's completion are written together by
 * {@link ProcessesStorage#saveRequestState}, which writes the flags back when
 * the completion fails; only the records whose flags are both null are read,
 * and a request created since names its state, so a rerun after a failure
 * handles only what is left of the older requests. One case is not handled
 * again: a run stopped between a request's flags and its task's completion
 * leaves the flags written and the task open, which the next run skips; that
 * request then reads pending, and a manager who completes a former
 * « Canceled » one sees it read cancelled. A request the upgrade completes
 * whose process's stored project has no column cannot be moved there: its
 * failure stops the run at that request, before the requests after it,
 * until a column is added to that project. Each task it completes fires Task's update listeners once. It runs
 * after {@link ProcessesRequestUpgradePlugin}, which records the older
 * requests first. A failure is thrown, so the upgrade is not recorded as done.
 */
public class ProcessesRequestStateUpgradePlugin extends UpgradeProductPlugin {

  private static final Log           LOG                = ExoLogger.getLogger(ProcessesRequestStateUpgradePlugin.class);

  /** How many records one page reads. */
  static final int                   PAGE_SIZE          = 200;

  /** The column of a cancelled request, as processes created it. */
  static final String                LEGACY_CANCELED    = "Canceled";

  /** The columns of a decided request, as processes created them. */
  static final List<String>          LEGACY_DECIDED     = List.of("Validated", "Refused");

  private final RequestDAO           requestDAO;

  private final TaskService          taskService;

  private final ProcessesStorage     processesStorage;

  private final EntityManagerService entityManagerService;

  /**
   * Builds the plugin.
   *
   * @param initParams the plugin's parameters
   * @param requestDAO the request records' storage
   * @param taskService the Task add-on's tasks
   * @param processesStorage writes a request's state, then its task's
   *          completion, writing the state back when the completion fails
   * @param entityManagerService the entity manager of the startup, cleared
   *          after each page
   */
  public ProcessesRequestStateUpgradePlugin(InitParams initParams,
                                            RequestDAO requestDAO,
                                            TaskService taskService,
                                            ProcessesStorage processesStorage,
                                            EntityManagerService entityManagerService) {
    super(initParams);
    this.requestDAO = requestDAO;
    this.taskService = taskService;
    this.processesStorage = processesStorage;
    this.entityManagerService = entityManagerService;
  }

  /**
   * Fills the state of every request record that has none, page by page in
   * task id order.
   *
   * @param oldVersion the version upgraded from
   * @param newVersion the version upgraded to
   */
  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long start = System.currentTimeMillis();
    StateCount count = new StateCount();
    long afterTaskId = 0;
    List<RequestEntity> page;
    do {
      page = requestDAO.findWithoutStateAfter(afterTaskId, PAGE_SIZE);
      for (RequestEntity request : page) {
        upgradeRequest(request.getTaskId(), count);
        afterTaskId = request.getTaskId();
      }
      EntityManager entityManager = entityManagerService.getEntityManager();
      if (entityManager != null) {
        entityManager.clear();
      }
    } while (page.size() == PAGE_SIZE);
    LOG.info("Processes request state upgrade: {} cancelled, {} decided requests closed, {} archived, {} pending, {} with no task, in {} ms",
             count.canceled,
             count.closed,
             count.archived,
             count.pending,
             count.noTask,
             System.currentTimeMillis() - start);
  }

  /**
   * Fills the state of one request from its task's column and completion.
   *
   * @param taskId the request's task id
   * @param count the counts of the upgrade, incremented
   */
  void upgradeRequest(long taskId, StateCount count) {
    TaskDto task;
    try {
      task = taskService.getTask(taskId);
    } catch (EntityNotFoundException e) {
      task = null;
    }
    if (task == null) {
      // Its task is gone: the record gets a state, so no rerun reads it again
      processesStorage.saveRequestState(taskId, Boolean.FALSE, Boolean.FALSE, null);
      count.noTask++;
      return;
    }
    String column = columnName(task.getStatus());
    boolean completed = task.isCompleted();
    if (LEGACY_CANCELED.equalsIgnoreCase(column)) {
      processesStorage.saveRequestState(taskId, Boolean.TRUE, completed, completed ? null : Boolean.TRUE);
      count.canceled++;
    } else if (LEGACY_DECIDED.stream().anyMatch(name -> name.equalsIgnoreCase(column))) {
      processesStorage.saveRequestState(taskId, Boolean.FALSE, completed, completed ? null : Boolean.TRUE);
      if (completed) {
        count.archived++;
      } else {
        count.closed++;
      }
    } else {
      processesStorage.saveRequestState(taskId, Boolean.FALSE, completed, null);
      if (completed) {
        count.archived++;
      } else {
        count.pending++;
      }
    }
  }

  /**
   * Reads the trimmed name of a task's column.
   *
   * @param status the task's column, or null
   * @return its trimmed name, or an empty text when it has none
   */
  private static String columnName(StatusDto status) {
    return status == null || status.getName() == null ? "" : status.getName().trim();
  }

  /**
   * The counts an upgrade logs.
   */
  static class StateCount {

    /** The requests read as cancelled. */
    int canceled;

    /** The decided requests whose task the upgrade completed. */
    int closed;

    /** The requests read as archived. */
    int archived;

    /** The requests left pending. */
    int pending;

    /** The records whose task is gone. */
    int noTask;
  }
}
