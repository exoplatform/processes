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
package org.exoplatform.processes.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.notification.utils.NotificationArguments;
import org.exoplatform.processes.notification.utils.NotificationUtils;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.idgenerator.IDGeneratorService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.social.core.utils.MentionUtils;
import org.exoplatform.task.dto.CommentDto;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.StatusDto;
import org.exoplatform.task.dto.TaskDto;

/**
 * The request notifications name the request's own process, by its id, from
 * which each recipient reads its title in their own language, and by its
 * stored title, whatever the task project that several processes may share.
 */
class RequestNotificationListenersTest {

  private static MockedStatic<CommonsUtils>        commonsUtils;

  private static MockedStatic<ExoContainerContext> exoContainerContext;

  private static MockedStatic<NotificationUtils>   notificationUtils;

  private static MockedStatic<MentionUtils>        mentionUtils;

  private ProcessesService                         processesService;

  private ProjectDto                               sharedProject;

  @BeforeAll
  static void mockPlatform() {
    commonsUtils = mockStatic(CommonsUtils.class);
    exoContainerContext = mockStatic(ExoContainerContext.class);
    notificationUtils = mockStatic(NotificationUtils.class, CALLS_REAL_METHODS);
    mentionUtils = mockStatic(MentionUtils.class);
    exoContainerContext.when(() -> ExoContainerContext.getService(IDGeneratorService.class)).thenReturn(null);
    notificationUtils.when(() -> NotificationUtils.getProcessLink(anyLong())).thenReturn("process-link");
    notificationUtils.when(() -> NotificationUtils.getRequestLink(anyLong())).thenReturn("request-link");
    notificationUtils.when(() -> NotificationUtils.getRequestCommentsLink(anyLong())).thenReturn("comments-link");
    UserPortalConfigService portalConfigService = mock(UserPortalConfigService.class);
    commonsUtils.when(() -> CommonsUtils.getService(UserPortalConfigService.class)).thenReturn(portalConfigService);
    mentionUtils.when(() -> MentionUtils.substituteUsernames(nullable(String.class), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
  }

  @AfterAll
  static void closePlatform() {
    commonsUtils.close();
    exoContainerContext.close();
    notificationUtils.close();
    mentionUtils.close();
  }

  @BeforeEach
  void setUp() throws Exception {
    processesService = mock(ProcessesService.class);
    sharedProject = new ProjectDto();
    sharedProject.setId(50L);
    sharedProject.setName("Shared project");
    when(processesService.getWorkFlow(7L, null)).thenReturn(workFlow(7L, "Leave"));
    when(processesService.getWorkFlow(8L, null)).thenReturn(workFlow(8L, "Expenses"));
  }

  /**
   * A created request's notification names its process, not its project.
   * Mutants: the project's name stored, or the process id left out.
   */
  @Test
  void aCreatedRequestNamesItsProcess() {
    Work work = new Work();
    work.setId(3L);
    work.setWorkFlowId(7L);
    work.setProjectId(50L);

    NotificationContext ctx = new CreateRequestNotificationListener(processesService).buildContext(new Event<>("created",
                                                                                                                work,
                                                                                                                sharedProject));

    assertEquals("Leave", ctx.value(NotificationArguments.REQUEST_PROCESS));
    assertEquals("7", ctx.value(NotificationArguments.WORKFLOW_ID));
  }

  /**
   * A canceled request's notification names its own process from its request
   * record; a task with no record keeps the project's name and no id.
   * Mutants: the project's name stored for a recorded request, or an id
   * appended for an unrecorded one.
   */
  @Test
  void aCanceledRequestNamesItsOwnProcess() {
    when(processesService.getRequestWorkFlowId(3L)).thenReturn(8L);

    NotificationContext ctx = new CancelRequestNotificationListener(processesService).buildContext(new Event<>("canceled",
                                                                                                               task(3L),
                                                                                                               sharedProject));

    assertEquals("Expenses", ctx.value(NotificationArguments.REQUEST_PROCESS));
    assertEquals("8", ctx.value(NotificationArguments.WORKFLOW_ID));

    NotificationContext unrecorded = new CancelRequestNotificationListener(processesService).buildContext(new Event<>("canceled",
                                                                                                                      task(4L),
                                                                                                                      sharedProject));
    assertEquals("Shared project", unrecorded.value(NotificationArguments.REQUEST_PROCESS));
    assertNull(unrecorded.value(NotificationArguments.WORKFLOW_ID));
  }

  /**
   * A comment's notification names the request's own process, not the first
   * process of a shared project; a request with no record names that first
   * process.
   * Mutants: the project's first process named for a recorded request, or no
   * id appended.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  void aCommentNamesTheRequestsOwnProcess() throws Exception {
    when(processesService.getWorkFlowByProjectId(50L)).thenReturn(workFlow(7L, "Leave"));
    when(processesService.getRequestWorkFlowId(3L)).thenReturn(8L);
    CommentDto comment = new CommentDto();
    comment.setAuthor("mary");
    comment.setComment("Approved");

    NotificationContext ctx = new RequestCommentNotificationListener(mock(org.exoplatform.portal.config.UserACL.class), processesService).buildContext(new Event<>("comment",
                                                                                                                      task(3L),
                                                                                                                      comment));

    assertEquals("Expenses", ctx.value(NotificationArguments.REQUEST_PROCESS));
    assertEquals("8", ctx.value(NotificationArguments.WORKFLOW_ID));

    NotificationContext unrecorded = new RequestCommentNotificationListener(mock(org.exoplatform.portal.config.UserACL.class), processesService).buildContext(new Event<>("comment",
                                                                                                                             task(4L),
                                                                                                                             comment));
    assertEquals("Leave", unrecorded.value(NotificationArguments.REQUEST_PROCESS));
    assertEquals("7", unrecorded.value(NotificationArguments.WORKFLOW_ID));
  }

  /**
   * @param taskId the task id
   * @return a request task of the shared project
   */
  private TaskDto task(long taskId) {
    StatusDto status = new StatusDto();
    status.setProject(sharedProject);
    TaskDto task = new TaskDto();
    task.setId(taskId);
    task.setCreatedBy("john");
    task.setTitle("A request");
    task.setStatus(status);
    return task;
  }

  /**
   * @param id the process id
   * @param title its stored title
   * @return the process
   */
  private static WorkFlow workFlow(long id, String title) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(id);
    workFlow.setTitle(title);
    return workFlow;
  }
}
