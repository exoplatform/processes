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
package org.exoplatform.processes.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.processes.constant.ProcessesEvents;
import org.exoplatform.processes.dao.RequestDAO;
import org.exoplatform.processes.dao.WorkDraftDAO;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.RequestEntity;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.StatusDto;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.StatusService;
import org.exoplatform.task.service.TaskService;

/**
 * The request's own state in the processes storage (EXO-94923): its write
 * with the task's completion, the cancel's broadcast, and the columns a new
 * process project starts with.
 */
@ExtendWith(MockitoExtension.class)
class ProcessesStorageRequestStateTest {

  @Mock
  private WorkFlowDAO          workFlowDAO;

  @Mock
  private WorkDraftDAO         workDraftDAO;

  @Mock
  private RequestDAO           requestDAO;

  @Mock
  private TaskService          taskService;

  @Mock
  private ProjectService       projectService;

  @Mock
  private StatusService        statusService;

  @Mock
  private SpaceService         spaceService;

  @Mock
  private ListenerService      listenerService;

  private ProcessesStorageImpl processesStorage;

  /**
   * Builds the storage over mocked DAOs and Task services.
   */
  @BeforeEach
  void setUp() {
    processesStorage = new ProcessesStorageImpl(workFlowDAO,
                                                workDraftDAO,
                                                requestDAO,
                                                taskService,
                                                projectService,
                                                statusService,
                                                null,
                                                spaceService,
                                                listenerService,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null);
  }

  /**
   * The state's write sets the flags it is given on the record, keeps the
   * others, then writes the task's completion when given; with no completion
   * the task is not written. Mutation: overwrite a flag given as null, write
   * the task before the record, or complete the task when not asked, and this
   * test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  void theStateWritesTheGivenFlagsThenTheCompletion() throws Exception {
    RequestEntity record = new RequestEntity(40L, 3L, 1L, null, Boolean.TRUE);
    when(requestDAO.find(40L)).thenReturn(record);
    TaskDto task = new TaskDto();
    task.setId(40L);
    when(taskService.getTask(40L)).thenReturn(task);
    when(taskService.updateTask(task)).thenReturn(task);

    processesStorage.writeRequestState(40L, Boolean.TRUE, null, Boolean.TRUE);

    ArgumentCaptor<RequestEntity> written = ArgumentCaptor.forClass(RequestEntity.class);
    InOrder order = inOrder(requestDAO, taskService);
    order.verify(requestDAO).update(written.capture());
    order.verify(taskService).updateTask(task);
    assertEquals(Boolean.TRUE, written.getValue().getCanceled());
    assertEquals(Boolean.TRUE, written.getValue().getArchived());
    assertTrue(task.isCompleted());

    processesStorage.writeRequestState(40L, null, Boolean.FALSE, null);
    assertEquals(Boolean.FALSE, record.getArchived());
    assertEquals(Boolean.TRUE, record.getCanceled());
    verify(taskService).updateTask(task);
  }

  /**
   * A task with no request record gets no state, and nothing is written.
   * Mutation: create a record for it and the DAO is written.
   */
  @Test
  void aTaskWithoutRecordGetsNoState() {
    assertThrows(jakarta.persistence.EntityNotFoundException.class,
                 () -> processesStorage.writeRequestState(41L, Boolean.TRUE, null, Boolean.TRUE));
    verify(requestDAO, never()).update(any());
    verify(requestDAO, never()).create(any());
  }

  /**
   * The state and the completion commit together: the write is an atomic
   * transaction. Mutation: drop the annotation or its atomic flag and this
   * test fails.
   *
   * @throws NoSuchMethodException never
   */
  @Test
  void theStateWriteIsAtomic() throws NoSuchMethodException {
    io.meeds.common.ContainerTransactional transactional =
                                                       ProcessesStorageImpl.class.getMethod("saveRequestState",
                                                                                            long.class,
                                                                                            Boolean.class,
                                                                                            Boolean.class,
                                                                                            Boolean.class)
                                                                                 .getAnnotation(io.meeds.common.ContainerTransactional.class);
    assertNotNull(transactional);
    assertTrue(transactional.atomic());
  }

  /**
   * The cancel's broadcast carries the request's task and its project, as
   * the cancel's notification reads them; a task gone or in no project
   * broadcasts nothing. Mutation: broadcast another payload, or broadcast a
   * task with no project, and this test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  void theCancelBroadcastsTheTaskAndItsProject() throws Exception {
    ProjectDto project = new ProjectDto();
    project.setId(9L);
    StatusDto status = new StatusDto();
    status.setProject(project);
    TaskDto task = new TaskDto();
    task.setId(40L);
    task.setStatus(status);
    when(taskService.getTask(40L)).thenReturn(task);
    when(taskService.getTask(41L)).thenReturn(new TaskDto());
    when(taskService.getTask(42L)).thenThrow(new EntityNotFoundException(42L, TaskDto.class));

    processesStorage.broadcastRequestCanceled(40L);
    processesStorage.broadcastRequestCanceled(41L);
    processesStorage.broadcastRequestCanceled(42L);

    verify(listenerService).broadcast(ProcessesEvents.REQUEST_CANCELED, task, project);
    verify(listenerService, org.mockito.Mockito.times(1)).broadcast(anyString(), nullable(Object.class), nullable(Object.class));
  }

  /**
   * A process's own new project starts with Task's initial columns, those
   * every new board of Tasks gets, never the former process columns: no
   * column name is chosen by processes (EXO-94923). Mutation: create the
   * former five columns again and the never() fails.
   */
  @Test
  void aNewProcessProjectGetsTasksInitialColumns() {
    Space space = new Space();
    space.setId("4");
    space.setGroupId("/spaces/hr");
    when(spaceService.getSpaceById("4")).thenReturn(space);
    when(projectService.createProject(any(ProjectDto.class))).thenAnswer(invocation -> {
      ProjectDto created = invocation.getArgument(0);
      created.setId(9L);
      return created;
    });
    WorkFlow workFlow = new WorkFlow();
    workFlow.setTitle("Leave");
    workFlow.setSpaceId("4");

    WorkFlow created = processesStorage.createProject(workFlow);

    assertEquals(9L, created.getProjectId());
    ArgumentCaptor<ProjectDto> project = ArgumentCaptor.forClass(ProjectDto.class);
    verify(statusService).createInitialStatuses(project.capture());
    assertEquals(9L, project.getValue().getId());
    verify(statusService, never()).createStatus(nullable(ProjectDto.class), nullable(String.class));
    assertFalse(created.getManager().isEmpty());
    assertNull(created.getIllustrativeAttachment());
  }
}
