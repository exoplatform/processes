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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.mcp.model.PendingApprovalModel;
import org.exoplatform.processes.mcp.model.ProcessModel;
import org.exoplatform.processes.mcp.model.RequestModel;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFilter;
import org.exoplatform.processes.model.ProcessPermission;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dao.TaskQuery;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.StatusDto;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.service.TaskService;

/**
 * Exercises the real {@code ProcessesMcpTool} (only its collaborators are
 * mocked): the live NPE where a {@code ProcessesFilter} passed to
 * {@code getWorkFlows} with a null {@code isProcessManager} is unboxed into a
 * primitive {@code boolean} by the service / {@code WorkFlowDAO}; the ACL gate
 * on {@code submitWorkRequest} ({@code WorkFlow.acl.canAddRequest}); the
 * refusal to cancel an already-decided request; and the read/cancel/pending
 * paths' mapping and self-scoping.
 */
public class ProcessesMcpToolTest {

  private ProcessesService processesService;

  private TaskService      taskService;

  private IdentityManager  identityManager;

  private SpaceService     spaceService;

  private ProcessSpaceScopeResolver processSpaceScopeResolver;

  private ProcessesMcpTool tool;

  @Before
  public void setUp() {
    processesService = mock(ProcessesService.class);
    taskService = mock(TaskService.class);
    identityManager = mock(IdentityManager.class);
    spaceService = mock(SpaceService.class);
    processSpaceScopeResolver = mock(ProcessSpaceScopeResolver.class);
    tool = new ProcessesMcpTool(processesService, taskService, identityManager, spaceService, processSpaceScopeResolver);

    Identity socialIdentity = mock(Identity.class);
    when(socialIdentity.getId()).thenReturn("1");
    when(identityManager.getOrCreateUserIdentity("testuser")).thenReturn(socialIdentity);

    ConversationState.setCurrent(new ConversationState(new org.exoplatform.services.security.Identity("testuser")));
  }

  @After
  public void tearDown() {
    ConversationState.setCurrent(null);
  }

  /**
   * With a space, the listing names only the processes whose project the
   * process resolver puts in that space, across pages of the user's
   * processes; a space the user isn't a member of, or an unknown one, lists
   * none, and no space keeps every process the user sees.
   * Mutants: the space filter, the membership or the unknown-space check
   * removed, the paging stopped after the first page, or the unscoped path
   * filtered too.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  public void listProcessesOfASpaceNamesOnlyThatSpacesProcesses() throws Exception {
    List<WorkFlow> firstPage = new ArrayList<>();
    for (int i = 1; i <= 200; i++) {
      firstPage.add(workFlow(i, 100 + i));
    }
    List<WorkFlow> secondPage = List.of(workFlow(201, 7));
    when(processesService.getWorkFlows(any(ProcessesFilter.class), eq(0), eq(200), anyLong())).thenReturn(firstPage);
    when(processesService.getWorkFlows(any(ProcessesFilter.class), eq(200), eq(200), anyLong())).thenReturn(secondPage);
    when(processSpaceScopeResolver.spaceOfProject(101L)).thenReturn(12L);
    when(processSpaceScopeResolver.spaceOfProject(102L)).thenReturn(13L);
    when(processSpaceScopeResolver.spaceOfProject(7L)).thenReturn(12L);
    when(processSpaceScopeResolver.spaceOfProject(103L)).thenReturn(14L);
    // An unknown space is refused by its own check, whatever a membership
    // lookup on a null space would answer.
    when(spaceService.isMember(isNull(Space.class), eq("testuser"))).thenReturn(true);
    Space space = mock(Space.class);
    when(spaceService.getSpaceById(12L)).thenReturn(space);
    when(spaceService.isMember(space, "testuser")).thenReturn(true);
    Space other = mock(Space.class);
    when(spaceService.getSpaceById(13L)).thenReturn(other);
    when(spaceService.isMember(other, "testuser")).thenReturn(false);

    assertEquals(List.of(1L, 201L), tool.listProcesses(12L).stream().map(ProcessModel::id).toList());
    assertTrue(tool.listProcesses(13L).isEmpty());
    assertTrue(tool.listProcesses(14L).isEmpty());
    assertEquals(200, tool.listProcesses(null).size());
    assertEquals(200, tool.listProcesses(0L).size());
  }

  /**
   * A space's listing stops at 200 processes: it keeps the first 200 of the
   * space across pages, even when a page holds more than the cap leaves room
   * for, and never reads the page after the one that fills the cap. A process
   * met again on a later page, as an unordered query may return it, is
   * listed once.
   * Mutants: either cap clause removed (the page loop's or the add's), or the
   * repeat check removed.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  public void listProcessesOfASpaceStopsAtTheCapAndListsAProcessOnce() throws Exception {
    List<WorkFlow> firstPage = new ArrayList<>();
    for (int i = 1; i <= 200; i++) {
      firstPage.add(workFlow(i, i <= 150 ? i : 10000 + i));
    }
    List<WorkFlow> secondPage = new ArrayList<>();
    secondPage.add(workFlow(1, 1));
    for (int i = 201; i <= 399; i++) {
      secondPage.add(workFlow(i, i));
    }
    when(processesService.getWorkFlows(any(ProcessesFilter.class), eq(0), eq(200), anyLong())).thenReturn(firstPage);
    when(processesService.getWorkFlows(any(ProcessesFilter.class), eq(200), eq(200), anyLong())).thenReturn(secondPage);
    lenient().when(processesService.getWorkFlows(any(ProcessesFilter.class), eq(400), eq(200), anyLong()))
             .thenReturn(List.of(workFlow(401, 401)));
    when(processSpaceScopeResolver.spaceOfProject(anyLong())).thenAnswer(invocation -> {
      long projectId = invocation.getArgument(0);
      return projectId < 10000 ? 12L : 13L;
    });
    Space space = mock(Space.class);
    when(spaceService.getSpaceById(12L)).thenReturn(space);
    when(spaceService.isMember(space, "testuser")).thenReturn(true);

    List<Long> ids = tool.listProcesses(12L).stream().map(ProcessModel::id).toList();

    assertEquals(200, ids.size());
    assertEquals(200, new HashSet<>(ids).size());
    assertEquals(Long.valueOf(150L), ids.get(149));
    assertEquals(Long.valueOf(250L), ids.get(199));
    verify(processesService, never()).getWorkFlows(any(ProcessesFilter.class), eq(400), eq(200), anyLong());
  }

  /**
   * @param id a process id
   * @param projectId its project id
   * @return an enabled process of that project
   */
  private static WorkFlow workFlow(long id, long projectId) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(id);
    workFlow.setProjectId(projectId);
    workFlow.setEnabled(true);
    return workFlow;
  }

  @Test
  public void listProcessesSetsIsProcessManagerToAvoidNpe() throws Exception {
    when(processesService.getWorkFlows(any(ProcessesFilter.class),
                                       anyInt(),
                                       anyInt(),
                                       anyLong())).thenReturn(Collections.emptyList());

    tool.listProcesses(null);

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
    workFlow.setAcl(new ProcessPermission(false, false, false, true));
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

  @Test
  public void submitWorkRequestRejectsProcessWithoutAddRequestAcl() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(5L);
    workFlow.setTitle("Restricted process");
    workFlow.setEnabled(true);
    workFlow.setProjectId(42L);
    workFlow.setAcl(new ProcessPermission(true, false, false, false));
    when(processesService.getWorkFlow(5L, 1L)).thenReturn(workFlow);

    assertThrows(IllegalAccessException.class, () -> tool.submitWorkRequest(5L, "title", "description"));

    verify(processesService, never()).createWork(any(Work.class), anyLong());
  }

  @Test
  public void submitWorkRequestRejectsProcessWithNoAcl() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(5L);
    workFlow.setTitle("No-acl process");
    workFlow.setEnabled(true);
    workFlow.setProjectId(42L);
    when(processesService.getWorkFlow(5L, 1L)).thenReturn(workFlow);

    assertThrows(IllegalAccessException.class, () -> tool.submitWorkRequest(5L, "title", "description"));

    verify(processesService, never()).createWork(any(Work.class), anyLong());
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
  public void cancelWorkRequestRejectsAnAlreadyValidatedRequest() throws Exception {
    Work work = new Work();
    work.setId(3L);
    work.setStatus("Validated");
    work.setCompleted(true);
    when(processesService.getWorkById(1L, 3L)).thenReturn(work);

    assertThrows(IllegalArgumentException.class, () -> tool.cancelWorkRequest(3L));

    verify(processesService, never()).updateWork(any(Work.class), anyLong());
  }

  @Test
  public void cancelWorkRequestRejectsAnAlreadyRefusedRequest() throws Exception {
    Work work = new Work();
    work.setId(3L);
    work.setStatus("Refused");
    work.setCompleted(true);
    when(processesService.getWorkById(1L, 3L)).thenReturn(work);

    assertThrows(IllegalArgumentException.class, () -> tool.cancelWorkRequest(3L));

    verify(processesService, never()).updateWork(any(Work.class), anyLong());
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
