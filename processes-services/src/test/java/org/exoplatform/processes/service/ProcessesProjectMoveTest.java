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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.commons.persistence.impl.EntityManagerHolder;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.dao.RequestDAO;
import org.exoplatform.processes.dao.WorkDraftDAO;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.RequestEntity;
import org.exoplatform.processes.entity.WorkEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.storage.ProcessesStorageImpl;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dao.TaskQuery;
import org.exoplatform.task.dto.LabelDto;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.StatusDto;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.CommentService;
import org.exoplatform.task.service.LabelService;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.StatusService;
import org.exoplatform.task.service.TaskService;

/**
 * The move of a process's requests to the project a save set (EXO-92333),
 * through the real storage and DAOs on the schema the processes changelog
 * creates on HSQLDB: the claim, the pass, its turns and its end are the woven
 * transactional methods of the move service, run as on the platform; Task's
 * services are mocked.
 */
class ProcessesProjectMoveTest {

  private static final String               CHANGELOG  = "db/changelog/processes-rdbms.db.changelog-master.xml";

  private static final long                 NOW        = 1_000_000_000L;

  private static final long                 FORMER_ID  = 10L;

  private static final long                 TARGET_ID  = 20L;

  private Connection                        connection;

  private EntityManagerFactory              entityManagerFactory;

  private EntityManager                     entityManager;

  private MockedStatic<EntityManagerHolder> entityManagerHolder;

  private MockedStatic<RequestLifeCycle>    requestLifeCycle;

  private TaskService                       taskService;

  private ProjectService                    projectService;

  private StatusService                     statusService;

  private LabelService                      labelService;

  private UserACL                           userACL;

  private ProcessesProjectMoveService       moveService;

  private ProcessesStorageImpl              storage;

  private IdentityManager                   identityManager;

  private ListenerService                   listenerService;

  private long                              now        = NOW;

  private final Map<Long, TaskDto>          tasks      = new HashMap<>();

  private final List<StatusDto>             targetColumns = new ArrayList<>();

  /**
   * Builds the schema with the changelog and process 1, whose project is now
   * {@value #TARGET_ID}, with its move pending since {@value #NOW} as john;
   * then the move service on the real storage with Task's services mocked.
   *
   * @throws Exception when Liquibase or the engine fails
   */
  @BeforeEach
  void createSchema() throws Exception {
    String url = "jdbc:hsqldb:mem:processes-move-" + System.nanoTime();
    connection = DriverManager.getConnection(url, "SA", "");
    new Liquibase(CHANGELOG,
                  new ClassLoaderResourceAccessor(),
                  DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)))
                                                                                                                  .update(new Contexts(),
                                                                                                                          new LabelExpression());
    sql("INSERT INTO PROCESSES_WORK_FLOW (WORK_FLOW_ID, TITLE, DESCRIPTION, SUMMARY, ENABLED, CREATOR_ID, MODIFIER_ID, CREATED_DATE, MODIFIED_DATE, PROJECT_ID) VALUES (1, 'Leave', 'd', 's', TRUE, 5, 5, CURRENT_DATE, CURRENT_DATE, "
        + TARGET_ID + ")");
    sql("UPDATE PROCESSES_WORK_FLOW SET PROJECT_MOVE_PENDING = TRUE, MOVE_SEQ = 1, MOVE_NEXT_ATTEMPT = " + NOW
        + ", MOVE_REQUESTED_BY = 'john' WHERE WORK_FLOW_ID = 1");
    entityManagerFactory = new HibernatePersistenceConfiguration("processes-move-test").managedClass(WorkFlowEntity.class)
                                                                                       .managedClass(WorkEntity.class)
                                                                                       .managedClass(RequestEntity.class)
                                                                                       .property("jakarta.persistence.jdbc.url", url)
                                                                                       .property("jakarta.persistence.jdbc.user", "SA")
                                                                                       .property("jakarta.persistence.jdbc.password", "")
                                                                                       .property("hibernate.hbm2ddl.auto", "none")
                                                                                       .createEntityManagerFactory();
    entityManager = entityManagerFactory.createEntityManager();
    entityManagerHolder = mockStatic(EntityManagerHolder.class);
    entityManagerHolder.when(EntityManagerHolder::get).thenReturn(entityManager);
    taskService = mock(TaskService.class);
    projectService = mock(ProjectService.class);
    statusService = mock(StatusService.class);
    labelService = mock(LabelService.class);
    userACL = mock(UserACL.class);
    when(userACL.getUserIdentity("john")).thenReturn(new Identity("john"));
    identityManager = mock(IdentityManager.class);
    listenerService = mock(ListenerService.class);
    storage = new ProcessesStorageImpl(new WorkFlowDAO(),
                                       new WorkDraftDAO(),
                                       new RequestDAO(),
                                       taskService,
                                       projectService,
                                       statusService,
                                       identityManager,
                                       mock(SpaceService.class),
                                       listenerService,
                                       mock(ProcessesAttachmentService.class),
                                       mock(FileService.class),
                                       userACL,
                                       labelService,
                                       mock(CommentService.class));
    moveService = new ProcessesProjectMoveService(storage, projectService, userACL, params());
    moveService.setClocks(() -> now, () -> 0L);
    // The woven transaction aspect finds the entity manager through the
    // current container, as on the platform
    EntityManagerService entityManagerService = mock(EntityManagerService.class);
    when(entityManagerService.getEntityManager()).thenReturn(entityManager);
    when(entityManagerService.getEntityManagerFactory()).thenReturn(entityManagerFactory);
    ExoContainer container = mock(ExoContainer.class);
    when(container.getComponentInstanceOfType(EntityManagerService.class)).thenReturn(entityManagerService);
    ExoContainerContext.setCurrentContainer(container);
    requestLifeCycle = mockStatic(RequestLifeCycle.class);

    when(projectService.getProject(TARGET_ID)).thenReturn(project(TARGET_ID));
    when(projectService.getProject(FORMER_ID)).thenReturn(project(FORMER_ID));
    targetColumns.add(status(2001L, "Request", null));
    targetColumns.add(status(2002L, "Validated", null));
    when(statusService.getStatuses(TARGET_ID)).thenAnswer(invocation -> new ArrayList<>(targetColumns));
    when(statusService.createStatus(any(ProjectDto.class), anyString())).thenAnswer(invocation -> {
      StatusDto created = status(2100L + targetColumns.size(), invocation.getArgument(1), null);
      targetColumns.add(created);
      return created;
    });
    when(taskService.getTask(anyLong())).thenAnswer(invocation -> {
      TaskDto task = tasks.get((Long) invocation.getArgument(0));
      if (task == null) {
        throw new EntityNotFoundException(invocation.getArgument(0), TaskDto.class);
      }
      return task;
    });
    when(taskService.updateTask(any(TaskDto.class))).thenAnswer(invocation -> invocation.getArgument(0));
    // The end of a pass counts the requests left outside the project
    when(taskService.countTasks(any(TaskQuery.class))).thenAnswer(invocation -> (int) tasks.values()
                                                                                     .stream()
                                                                                     .filter(task -> task.getStatus() == null
                                                                                         || task.getStatus().getProject() == null
                                                                                         || task.getStatus().getProject().getId() != TARGET_ID)
                                                                                     .count());
  }

  /**
   * Drops the database.
   *
   * @throws SQLException when it cannot be shut down
   */
  @AfterEach
  void dropSchema() throws SQLException {
    ConversationState.setCurrent(null);
    requestLifeCycle.close();
    ExoContainerContext.setCurrentContainer(null);
    entityManagerHolder.close();
    entityManager.close();
    entityManagerFactory.close();
    try (Statement statement = connection.createStatement()) {
      statement.execute("SHUTDOWN");
    }
    connection.close();
  }

  /**
   * A pass moves every request of the process, completed ones included, each
   * to the target's column of its name, with a status that carries no project,
   * as the editor; it then schedules the verification pass rather than
   * clearing the flag, and releases its claim. Mutation: clear the flag after
   * a pass that moved requests and it is cleared at once.
   *
   * @throws Exception on a read failure
   */
  @Test
  void aPassMovesEveryRequestThenSchedulesTheVerification() throws Exception {
    request(101L, "Request", false);
    request(102L, "Validated", true);
    request(103L, "Request", false);
    List<String> actors = new ArrayList<>();
    doAnswer(invocation -> {
      actors.add(ConversationState.getCurrent().getIdentity().getUserId());
      return saveInTarget(invocation.getArgument(0));
    }).when(taskService).updateTask(any(TaskDto.class));

    moveService.runMove(1L);

    ArgumentCaptor<TaskDto> updated = ArgumentCaptor.forClass(TaskDto.class);
    verify(taskService, times(3)).updateTask(updated.capture());
    assertEquals(List.of(2001L, 2002L, 2001L), updated.getAllValues().stream().map(task -> task.getStatus().getId()).toList());
    assertTrue(updated.getAllValues().stream().allMatch(task -> task.getStatus().getProject() == null),
               "the status handed to Task names only its id and name, which Task maps the task's status by");
    assertTrue(updated.getAllValues().get(1).isCompleted());
    assertEquals(List.of("john", "john", "john"), actors);
    assertTrue(pending());
    assertEquals(NOW + 600_000L, longValue("MOVE_NEXT_ATTEMPT"));
    assertEquals(0, longValue("MOVE_ATTEMPTS"));
    assertNull(stringValue("MOVE_OWNER"), "the claim is released");
    assertNull(ConversationState.getCurrent(), "the conversation state is removed");
  }

  /**
   * The verification pass, finding every request in the project, clears the
   * flag; one that finds a task put back meanwhile moves it and schedules
   * another verification. Mutation: skip the clear's condition on the
   * moved count and the put-back task is left behind with the flag cleared.
   *
   * @throws Exception on a read failure
   */
  @Test
  void theVerificationPassClearsTheFlagOnceNothingIsLeftToMove() throws Exception {
    request(101L, "Request", false);
    moveService.runMove(1L);
    assertTrue(pending());
    // A stale copy saved in Tasks put the task back in the former project
    request(101L, "Request", false);
    now = NOW + 600_000L;
    moveService.runMove(1L);
    verify(taskService, times(2)).updateTask(any(TaskDto.class));
    assertTrue(pending());

    now = NOW + 1_200_000L;
    moveService.runMove(1L);
    verify(taskService, times(2)).updateTask(any(TaskDto.class));
    assertFalse(pending());
  }

  /**
   * A request that fails to move leaves the others moved, the flag set, and a
   * retry whose delay doubles at each failed attempt, up to its cap.
   * Mutation: stop the pass at the first failure and the next request is not
   * moved.
   *
   * @throws Exception on a read failure
   */
  @Test
  void aFailedRequestIsRetriedWithADoublingDelay() throws Exception {
    request(101L, "Request", false);
    request(102L, "Request", false);
    doThrow(new IllegalStateException("Task refused")).when(taskService).updateTask(eq(tasks.get(101L)));

    moveService.runMove(1L);
    verify(taskService, times(2)).updateTask(any(TaskDto.class));
    assertTrue(pending());
    assertEquals(1, longValue("MOVE_ATTEMPTS"));
    assertEquals(NOW + 60_000L, longValue("MOVE_NEXT_ATTEMPT"));

    now = NOW + 60_000L;
    moveService.runMove(1L);
    assertEquals(2, longValue("MOVE_ATTEMPTS"));
    assertEquals(now + 120_000L, longValue("MOVE_NEXT_ATTEMPT"));
    assertEquals(3600L, moveService.retryDelaySeconds(100));
    assertEquals(3600L, moveService.retryDelaySeconds(63), "the capped exponent keeps the product from overflowing to 0");
    assertEquals(3600L, moveService.retryDelaySeconds(64), "the capped exponent keeps the product from overflowing to 0");
    assertEquals(60L, moveService.retryDelaySeconds(1));
  }

  /**
   * A pass queued twice after a failed attempt runs once: the claim reads the
   * next attempt, which the failure set ahead. Mutation: drop the next
   * attempt from the claim and the second pass retries at once.
   *
   * @throws Exception on a read failure
   */
  @Test
  void aPassQueuedTwiceAfterAFailureRunsOnce() throws Exception {
    request(101L, "Request", false);
    doThrow(new IllegalStateException("Task refused")).when(taskService).updateTask(any(TaskDto.class));
    moveService.runMove(1L);
    moveService.runMove(1L);
    verify(taskService, times(1)).updateTask(any(TaskDto.class));
    assertEquals(1, longValue("MOVE_ATTEMPTS"));
  }

  /**
   * A process whose target project was deleted in Tasks stops its move with no
   * retry: no request moves, and the sweep skips it until a save sets another
   * project. Mutation: retry it like a failure and the sweep picks it up
   * again.
   *
   * @throws Exception on a read failure
   */
  @Test
  void aDeletedTargetStopsTheMoveWithNoRetry() throws Exception {
    request(101L, "Request", false);
    when(projectService.getProject(TARGET_ID)).thenThrow(new EntityNotFoundException(TARGET_ID, ProjectDto.class));

    moveService.runMove(1L);
    verify(taskService, never()).updateTask(any(TaskDto.class));
    assertTrue(pending());
    assertNull(stringValue("MOVE_NEXT_ATTEMPT"));
    now = NOW + 86_400_000L;
    assertEquals(List.of(), moveService.getDueMoves());
  }

  /**
   * A claim held by another pass, its lease alive, stops this one before any
   * move; once the lease expired, the claim is taken over. Mutation: drop the
   * lease condition from the claim and both passes run.
   *
   * @throws Exception on a read failure
   */
  @Test
  void aLiveClaimStopsAnotherPassUntilItsLeaseExpires() throws Exception {
    request(101L, "Request", false);
    sql("UPDATE PROCESSES_WORK_FLOW SET MOVE_OWNER = 'other', MOVE_LEASE_UNTIL = " + (NOW + 1000) + " WHERE WORK_FLOW_ID = 1");
    assertEquals(List.of(), moveService.getDueMoves());
    moveService.runMove(1L);
    verify(taskService, never()).getTask(anyLong());

    now = NOW + 1001;
    assertEquals(List.of(1L), moveService.getDueMoves());
    moveService.runMove(1L);
    verify(taskService, times(1)).updateTask(any(TaskDto.class));
  }

  /**
   * A save that changes the project again during a pass stops it at its next
   * turn, leaving the flag set for the save's own pass, and the pass writes
   * none of its conditional updates. Mutation: drop the turn's re-read of the
   * stored project and the second request goes to the former target.
   *
   * @throws Exception on a read failure
   */
  @Test
  void aSaveChangingTheProjectDuringAPassStopsIt() throws Exception {
    request(101L, "Request", false);
    request(102L, "Request", false);
    doAnswer(invocation -> {
      sql("UPDATE PROCESSES_WORK_FLOW SET PROJECT_ID = 30, MOVE_SEQ = MOVE_SEQ + 1, MOVE_NEXT_ATTEMPT = " + NOW
          + " WHERE WORK_FLOW_ID = 1");
      return invocation.getArgument(0);
    }).when(taskService).updateTask(eq(tasks.get(101L)));

    moveService.runMove(1L);
    verify(taskService, times(1)).updateTask(any(TaskDto.class));
    assertTrue(pending());
    assertEquals(0, longValue("MOVE_ATTEMPTS"));
    assertEquals(NOW, longValue("MOVE_NEXT_ATTEMPT"));
  }

  /**
   * A save that keeps the project but commits during a clean pass makes the
   * pass start again with the save's sequence, rather than leave its
   * conditional clear unwritten. Mutation: return after the unwritten clear
   * and the flag stays with no pass due before the sweep.
   *
   * @throws Exception on a read failure
   */
  @Test
  void aSaveCommittedDuringACleanPassMakesItRunAgain() throws Exception {
    task(101L, TARGET_ID, "Request", false);
    when(taskService.getTask(101L)).thenAnswer(invocation -> {
      sql("UPDATE PROCESSES_WORK_FLOW SET MOVE_SEQ = MOVE_SEQ + 1 WHERE WORK_FLOW_ID = 1 AND MOVE_SEQ = 1");
      return tasks.get(101L);
    });

    moveService.runMove(1L);
    verify(taskService, times(2)).getTask(101L);
    assertFalse(pending());
  }

  /**
   * A request whose task was deleted in Tasks loses its record, counting as
   * neither moved nor failed. Mutation: count it as failed and the flag stays.
   *
   * @throws Exception on a read failure
   */
  @Test
  void aDeletedTaskLosesItsRecord() throws Exception {
    sql("INSERT INTO PROCESSES_REQUEST (TASK_ID, WORK_FLOW_ID, CREATOR_ID) VALUES (101, 1, 5)");
    moveService.runMove(1L);
    assertEquals(0, count("SELECT COUNT(*) FROM PROCESSES_REQUEST"));
    assertFalse(pending());
  }

  /**
   * The former project's labels are removed before the task moves; a removal
   * that fails leaves the task in place for the next attempt. Mutation: move
   * the task before removing its labels and the failed removal leaves it
   * moved with its former label.
   *
   * @throws Exception on a read failure
   */
  @Test
  void theFormerLabelsGoFirstAndAFailedRemovalLeavesTheTask() throws Exception {
    request(101L, "Request", false);
    LabelDto label = new LabelDto();
    label.setId(7L);
    when(labelService.findLabelsByTask(any(TaskDto.class), eq(FORMER_ID), any(Identity.class), anyInt(), anyInt())).thenReturn(List.of(label));

    moveService.runMove(1L);
    InOrder order = inOrder(labelService, taskService);
    order.verify(labelService).removeTaskFromLabel(any(TaskDto.class), eq(7L));
    order.verify(taskService).updateTask(any(TaskDto.class));

    request(102L, "Request", false);
    doThrow(new IllegalStateException("label")).when(labelService).removeTaskFromLabel(eq(tasks.get(102L)), eq(7L));
    now = NOW + 600_000L;
    moveService.runMove(1L);
    verify(taskService, never()).updateTask(eq(tasks.get(102L)));
    assertEquals(1, longValue("MOVE_ATTEMPTS"));
  }

  /**
   * A column missing in the target is created once for the whole pass, and a
   * column named in another case is found by the lowest id, never created.
   * Mutation: create the column at each turn and it is created twice.
   *
   * @throws Exception on a read failure
   */
  @Test
  void aMissingColumnIsCreatedOnceAndAnotherCaseIsFoundByLowestId() throws Exception {
    request(101L, "Refused", true);
    request(102L, "Refused", true);
    targetColumns.add(status(2050L, "FOO", null));
    targetColumns.add(status(2040L, "Foo", null));
    request(103L, " foo ", false);

    moveService.runMove(1L);
    verify(statusService, times(1)).createStatus(any(ProjectDto.class), eq("Refused"));
    verify(statusService, never()).createStatus(any(ProjectDto.class), eq(" foo "));
    ArgumentCaptor<TaskDto> updated = ArgumentCaptor.forClass(TaskDto.class);
    verify(taskService, times(3)).updateTask(updated.capture());
    assertEquals(2040L, updated.getAllValues().get(2).getStatus().getId());
  }

  /**
   * A request update racing the move, its task still in the former project,
   * sends the task to the process's stored project, its former labels removed
   * first, then applies the update there; the request status is the stored
   * project's column of that name. Mutation: drop the move from the request
   * update, or move the task to the project it is in, and the task stays in
   * the former project.
   *
   * @throws Exception on a read failure
   */
  @Test
  void aRequestUpdateRacingTheMoveSendsItsTaskToTheStoredProject() throws Exception {
    request(101L, "Request", false);
    LabelDto label = new LabelDto();
    label.setId(7L);
    when(labelService.findLabelsByTask(any(TaskDto.class), eq(FORMER_ID), any(Identity.class), anyInt(), anyInt())).thenReturn(List.of(label));
    ConversationState.setCurrent(new ConversationState(new Identity("mary")));

    storage.updateWorkCompleted(101L, true);

    InOrder order = inOrder(labelService, taskService);
    order.verify(labelService).removeTaskFromLabel(any(TaskDto.class), eq(7L));
    order.verify(taskService, times(2)).updateTask(any(TaskDto.class));
    assertEquals(TARGET_ID, tasks.get(101L).getStatus().getProject().getId());
    assertEquals(2001L, tasks.get(101L).getStatus().getId());

    request(102L, "Request", false);
    org.exoplatform.social.core.identity.model.Identity mary =
                                                             new org.exoplatform.social.core.identity.model.Identity("organization",
                                                                                                                     "mary");
    mary.setId("5");
    when(identityManager.getIdentity(5L)).thenReturn(mary);
    org.exoplatform.processes.model.Work update = new org.exoplatform.processes.model.Work();
    update.setId(102L);
    update.setTitle("Leave");
    update.setDescription("d");
    update.setStatus("Validated");
    update.setProjectId(FORMER_ID);
    storage.saveWork(update, 5L);
    ArgumentCaptor<TaskDto> updated = ArgumentCaptor.forClass(TaskDto.class);
    verify(taskService, times(4)).updateTask(updated.capture());
    assertEquals(2002L, updated.getValue().getStatus().getId());
  }

  /**
   * A request update whose process's stored project was deleted in Tasks
   * leaves the task in its project and applies the update there. Mutation:
   * let the move's failure escape and the update fails.
   *
   * @throws Exception on a read failure
   */
  @Test
  void aRequestUpdateWithTheStoredProjectDeletedLeavesTheTaskInPlace() throws Exception {
    request(101L, "Request", false);
    when(projectService.getProject(TARGET_ID)).thenThrow(new EntityNotFoundException(TARGET_ID, ProjectDto.class));

    storage.updateWorkCompleted(101L, true);

    ArgumentCaptor<TaskDto> updated = ArgumentCaptor.forClass(TaskDto.class);
    verify(taskService).updateTask(updated.capture());
    assertTrue(updated.getValue().isCompleted());
    assertEquals(FORMER_ID, updated.getValue().getStatus().getProject().getId());
    verify(labelService, never()).removeTaskFromLabel(any(TaskDto.class), anyLong());
  }

  /**
   * A pass whose end fails releases its claim and removes its conversation
   * state, and leaves the flag for the next sweep. Mutation: release the claim
   * only on a normal end and the claim stays held until its lease expires.
   *
   * @throws Exception on a read failure
   */
  @Test
  void aPassWhoseEndFailsReleasesItsClaimAndKeepsTheFlag() throws Exception {
    request(101L, "Request", false);
    when(taskService.countTasks(any(TaskQuery.class))).thenThrow(new IllegalStateException("count"));

    moveService.runMove(1L);

    assertNull(stringValue("MOVE_OWNER"));
    assertNull(ConversationState.getCurrent());
    assertTrue(pending());
    assertEquals(List.of(1L), moveService.getDueMoves());
  }

  /**
   * The move thread starts with its sweep scheduled, hands a submitted move to
   * a pass, survives a sweep whose read fails, and once stopped hands nothing
   * over. Mutation: schedule no sweep in start() and it is not scheduled; let a
   * sweep failure escape and the sweep throws.
   */
  @Test
  void theMoveThreadSchedulesItsSweepAndStopsHandingMovesOver() {
    ProcessesProjectMoveService thread = org.mockito.Mockito.spy(moveService);
    doAnswer(invocation -> null).when(thread).runMove(anyLong());
    doReturn(List.of(3L)).when(thread).getDueMoves();
    thread.start();
    try {
      assertTrue(thread.isSweepScheduled());
      thread.submitMove(1L);
      verify(thread, timeout(5000)).runMove(1L);
      thread.sweep();
      verify(thread, timeout(5000)).runMove(3L);
      doThrow(new IllegalStateException("sweep")).when(thread).getDueMoves();
      thread.sweep();
    } finally {
      thread.stop();
    }
    assertFalse(thread.isSweepScheduled());
    thread.submitMove(2L);
    verify(thread, after(300).never()).runMove(2L);
  }

  /**
   * Adds a request of process 1 whose task is in the former project.
   */
  private void request(long taskId, String statusName, boolean completed) throws SQLException {
    task(taskId, FORMER_ID, statusName, completed);
    if (count("SELECT COUNT(*) FROM PROCESSES_REQUEST WHERE TASK_ID = " + taskId) == 0) {
      sql("INSERT INTO PROCESSES_REQUEST (TASK_ID, WORK_FLOW_ID, CREATOR_ID) VALUES (" + taskId + ", 1, 5)");
    }
  }

  /**
   * Puts a task in a project's column, and makes its update move it there.
   */
  private void task(long taskId, long projectId, String statusName, boolean completed) throws SQLException {
    TaskDto task = new TaskDto();
    task.setId(taskId);
    task.setCompleted(completed);
    task.setStatus(status(projectId * 100 + taskId, statusName, project(projectId)));
    tasks.put(taskId, task);
    if (count("SELECT COUNT(*) FROM PROCESSES_REQUEST WHERE TASK_ID = " + taskId) == 0) {
      sql("INSERT INTO PROCESSES_REQUEST (TASK_ID, WORK_FLOW_ID, CREATOR_ID) VALUES (" + taskId + ", 1, 5)");
    }
    doAnswer(invocation -> saveInTarget(invocation.getArgument(0))).when(taskService).updateTask(eq(task));
  }

  /**
   * Saves a task as Task does after a move: it is then in the target project,
   * in the column of the status it was given.
   */
  private TaskDto saveInTarget(TaskDto saved) {
    StatusDto moved = status(saved.getStatus().getId(), saved.getStatus().getName(), project(TARGET_ID));
    TaskDto copy = new TaskDto();
    copy.setId(saved.getId());
    copy.setCompleted(saved.isCompleted());
    copy.setStatus(moved);
    tasks.put(saved.getId(), copy);
    return saved;
  }

  private static ProjectDto project(long id) {
    ProjectDto project = new ProjectDto();
    project.setId(id);
    return project;
  }

  private static StatusDto status(long id, String name, ProjectDto project) {
    StatusDto status = new StatusDto();
    status.setId(id);
    status.setName(name);
    status.setProject(project);
    return status;
  }

  private static InitParams params() {
    InitParams params = new InitParams();
    for (String[] entry : new String[][] { { "leaseSeconds", "600" }, { "retryInitialDelaySeconds", "60" },
        { "retryMaxDelaySeconds", "3600" }, { "verifyDelaySeconds", "600" } }) {
      ValueParam param = new ValueParam();
      param.setName(entry[0]);
      param.setValue(entry[1]);
      params.addParameter(param);
    }
    return params;
  }

  private boolean pending() throws SQLException {
    return count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW WHERE WORK_FLOW_ID = 1 AND PROJECT_MOVE_PENDING = TRUE") == 1;
  }

  private long longValue(String column) throws SQLException {
    connection.commit();
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT " + column + " FROM PROCESSES_WORK_FLOW WHERE WORK_FLOW_ID = 1")) {
      rows.next();
      return rows.getLong(1);
    }
  }

  private String stringValue(String column) throws SQLException {
    connection.commit();
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT " + column + " FROM PROCESSES_WORK_FLOW WHERE WORK_FLOW_ID = 1")) {
      rows.next();
      return rows.getString(1);
    }
  }

  private void sql(String statementText) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(statementText);
    }
    connection.commit();
  }

  private int count(String query) throws SQLException {
    connection.commit();
    try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(query)) {
      rows.next();
      return rows.getInt(1);
    }
  }
}
