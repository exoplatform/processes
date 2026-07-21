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
package org.exoplatform.processes.mcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.mcp.model.PendingApprovalModel;
import org.exoplatform.processes.mcp.model.RequestModel;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFilter;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.task.dao.TaskQuery;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.StatusDto;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.service.TaskService;

/**
 * Guards the Processes MCP tools against the live NPE where a ProcessesFilter is
 * passed to {@code getWorkFlows} with a null {@code isProcessManager}, which the
 * service / WorkFlowDAO unboxes into a primitive {@code boolean}. These tests
 * exercise the real {@code ProcessesMcpTool} (only its collaborators are mocked)
 * and assert the filter it builds is fully populated, so the null-unbox trap
 * cannot regress.
 */
public class ProcessesMcpToolTest {

  private ProcessesService processesService;

  private TaskService      taskService;

  private IdentityManager  identityManager;

  private ProcessesMcpTool tool;

  @Before
  public void setUp() {
    processesService = mock(ProcessesService.class);
    taskService = mock(TaskService.class);
    identityManager = mock(IdentityManager.class);
    tool = new ProcessesMcpTool(processesService, taskService, identityManager);

    Identity socialIdentity = mock(Identity.class);
    when(socialIdentity.getId()).thenReturn("1");
    when(identityManager.getOrCreateUserIdentity("testuser")).thenReturn(socialIdentity);

    ConversationState.setCurrent(new ConversationState(new org.exoplatform.services.security.Identity("testuser")));
  }

  @After
  public void tearDown() {
    ConversationState.setCurrent(null);
  }

  @Test
  public void listProcessesSetsIsProcessManagerToAvoidNpe() throws Exception {
    when(processesService.getWorkFlows(any(ProcessesFilter.class),
                                       anyInt(),
                                       anyInt(),
                                       anyLong())).thenReturn(Collections.emptyList());

    tool.listProcesses();

    ArgumentCaptor<ProcessesFilter> captor = ArgumentCaptor.forClass(ProcessesFilter.class);
    verify(processesService).getWorkFlows(captor.capture(), anyInt(), anyInt(), anyLong());
    ProcessesFilter filter = captor.getValue();
    assertNotNull("isProcessManager must be non-null (the service unboxes it into a boolean)",
                  filter.getIsProcessManager());
    assertFalse(filter.getIsProcessManager());
    assertEquals(Boolean.TRUE, filter.getEnabled());
  }

  @Test
  public void getPendingApprovalsSetsIsProcessManagerToAvoidNpe() throws Exception {
    when(processesService.getWorkFlows(any(ProcessesFilter.class),
                                       anyInt(),
                                       anyInt(),
                                       anyLong())).thenReturn(Collections.emptyList());

    tool.getPendingApprovals();

    ArgumentCaptor<ProcessesFilter> captor = ArgumentCaptor.forClass(ProcessesFilter.class);
    verify(processesService).getWorkFlows(captor.capture(), anyInt(), anyInt(), anyLong());
    assertNotNull("isProcessManager must be non-null (the service unboxes it into a boolean)",
                  captor.getValue().getIsProcessManager());
  }

  @Test
  public void getMyRequestsReturnsMappedRequestsForCurrentUser() throws Exception {
    Work work = new Work();
    work.setId(10L);
    work.setTitle("My request");
    work.setStatus("Request");
    when(processesService.getWorks(eq(1L), any(WorkFilter.class), anyInt(), anyInt())).thenReturn(Collections.singletonList(work));

    List<RequestModel> requests = tool.getMyRequests("Request");

    assertEquals(1, requests.size());
    assertEquals(10L, requests.get(0).id());
    assertEquals("My request", requests.get(0).title());

    ArgumentCaptor<WorkFilter> captor = ArgumentCaptor.forClass(WorkFilter.class);
    verify(processesService).getWorks(eq(1L), captor.capture(), anyInt(), anyInt());
    assertEquals("Request", captor.getValue().getStatus());
  }

  @Test
  public void getMyRequestsLeavesStatusUnsetWhenBlank() throws Exception {
    when(processesService.getWorks(anyLong(), any(WorkFilter.class), anyInt(), anyInt())).thenReturn(Collections.emptyList());

    tool.getMyRequests("  ");

    ArgumentCaptor<WorkFilter> captor = ArgumentCaptor.forClass(WorkFilter.class);
    verify(processesService).getWorks(anyLong(), captor.capture(), anyInt(), anyInt());
    assertNull(captor.getValue().getStatus());
  }

  @Test(expected = IllegalArgumentException.class)
  public void getRequestDetailsRejectsNonPositiveId() throws Exception {
    tool.getRequestDetails(0L);
  }

  @Test(expected = ObjectNotFoundException.class)
  public void getRequestDetailsThrowsWhenRequestNotFound() throws Exception {
    when(processesService.getWorkById(1L, 99L)).thenReturn(null);

    tool.getRequestDetails(99L);
  }

  @Test
  public void getRequestDetailsReturnsMappedRequest() throws Exception {
    Work work = new Work();
    work.setId(7L);
    work.setTitle("Details request");
    when(processesService.getWorkById(1L, 7L)).thenReturn(work);

    RequestModel request = tool.getRequestDetails(7L);

    assertEquals(7L, request.id());
    assertEquals("Details request", request.title());
  }

  @Test(expected = IllegalArgumentException.class)
  public void submitWorkRequestRejectsNonPositiveProcessId() throws Exception {
    tool.submitWorkRequest(0L, "title", "description");
  }

  @Test(expected = IllegalArgumentException.class)
  public void submitWorkRequestRejectsBlankTitle() throws Exception {
    tool.submitWorkRequest(1L, "  ", "description");
  }

  @Test(expected = ObjectNotFoundException.class)
  public void submitWorkRequestThrowsWhenProcessNotFound() throws Exception {
    when(processesService.getWorkFlow(5L, 1L)).thenReturn(null);

    tool.submitWorkRequest(5L, "title", "description");
  }

  @Test(expected = IllegalArgumentException.class)
  public void submitWorkRequestRejectsDisabledProcess() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(5L);
    workFlow.setTitle("Disabled process");
    workFlow.setEnabled(false);
    when(processesService.getWorkFlow(5L, 1L)).thenReturn(workFlow);

    tool.submitWorkRequest(5L, "title", "description");
  }

  @Test
  public void submitWorkRequestCreatesWorkUnderProcessProject() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(5L);
    workFlow.setEnabled(true);
    workFlow.setProjectId(42L);
    when(processesService.getWorkFlow(5L, 1L)).thenReturn(workFlow);

    Work created = new Work();
    created.setId(11L);
    created.setTitle("New request");
    when(processesService.createWork(any(Work.class), eq(1L))).thenReturn(created);

    RequestModel request = tool.submitWorkRequest(5L, "New request", null);

    assertEquals(11L, request.id());
    ArgumentCaptor<Work> captor = ArgumentCaptor.forClass(Work.class);
    verify(processesService).createWork(captor.capture(), eq(1L));
    assertEquals("New request", captor.getValue().getTitle());
    assertEquals(42L, captor.getValue().getProjectId());
    assertEquals("", captor.getValue().getDescription());
  }

  @Test(expected = IllegalArgumentException.class)
  public void cancelWorkRequestRejectsNonPositiveId() throws Exception {
    tool.cancelWorkRequest(0L);
  }

  @Test(expected = ObjectNotFoundException.class)
  public void cancelWorkRequestThrowsWhenRequestNotFound() throws Exception {
    when(processesService.getWorkById(1L, 99L)).thenReturn(null);

    tool.cancelWorkRequest(99L);
  }

  @Test
  public void cancelWorkRequestReturnsAsIsWhenAlreadyCanceled() throws Exception {
    Work work = new Work();
    work.setId(3L);
    work.setStatus("Canceled");
    work.setCompleted(true);
    when(processesService.getWorkById(1L, 3L)).thenReturn(work);

    tool.cancelWorkRequest(3L);

    verify(processesService, never()).updateWork(any(Work.class), anyLong());
  }

  @Test
  public void cancelWorkRequestCancelsPendingRequest() throws Exception {
    Work work = new Work();
    work.setId(3L);
    work.setStatus("Request");
    work.setCompleted(false);
    when(processesService.getWorkById(1L, 3L)).thenReturn(work);
    when(processesService.updateWork(any(Work.class), eq(1L))).thenAnswer(invocation -> invocation.getArgument(0));

    RequestModel result = tool.cancelWorkRequest(3L);

    assertEquals("Canceled", result.status());
    assertTrue(result.completed());
    ArgumentCaptor<Work> captor = ArgumentCaptor.forClass(Work.class);
    verify(processesService).updateWork(captor.capture(), eq(1L));
    assertEquals("Canceled", captor.getValue().getStatus());
    assertTrue(captor.getValue().isCompleted());
  }

  @Test
  public void getPendingApprovalsReturnsEmptyWhenNoManagedProcess() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setProjectId(5L);
    workFlow.setCanShowPending(false);
    when(processesService.getWorkFlows(any(ProcessesFilter.class), anyInt(), anyInt(), anyLong())).thenReturn(Collections.singletonList(workFlow));

    List<PendingApprovalModel> pending = tool.getPendingApprovals();

    assertTrue(pending.isEmpty());
    verify(taskService, never()).findTasks(any(TaskQuery.class), anyInt(), anyInt());
  }

  @Test
  public void getPendingApprovalsMapsTasksFromManagedProjects() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setProjectId(5L);
    workFlow.setTitle("Onboarding");
    workFlow.setCanShowPending(true);
    when(processesService.getWorkFlows(any(ProcessesFilter.class), anyInt(), anyInt(), anyLong())).thenReturn(Collections.singletonList(workFlow));

    ProjectDto project = new ProjectDto();
    project.setId(5L);
    StatusDto status = new StatusDto();
    status.setName("Request");
    status.setProject(project);
    TaskDto task = new TaskDto();
    task.setId(21L);
    task.setTitle("Pending task");
    task.setStatus(status);
    task.setCreatedBy("john");

    when(taskService.findTasks(any(TaskQuery.class), anyInt(), anyInt())).thenReturn(Collections.singletonList(task))
                                                                          .thenReturn(Collections.emptyList());

    List<PendingApprovalModel> pending = tool.getPendingApprovals();

    assertEquals(1, pending.size());
    assertEquals(21L, pending.get(0).taskId());
    assertEquals("Onboarding", pending.get(0).processTitle());
    assertEquals(5L, pending.get(0).projectId());
  }

}
