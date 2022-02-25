package org.exoplatform.processes.rest;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.rest.model.WorkEntity;
import org.exoplatform.processes.rest.model.WorkFlowEntity;
import org.exoplatform.processes.rest.util.EntityBuilder;
import org.exoplatform.processes.rest.util.RestUtils;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.rest.impl.RuntimeDelegateImpl;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.persistence.EntityNotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.RuntimeDelegate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ RestUtils.class, EntityBuilder.class, ConversationState.class, CommonsUtils.class})
public class ProcessesRestTest {

  @Mock
  private IdentityManager  identityManager;

  @Mock
  private ProcessesService processesService;

  @Mock
  private SpaceService spaceService;

  private ProcessesRest    processesRest;

  @Mock
  private Identity identity;

  @Before
  public void setUp() {
    RuntimeDelegate.setInstance(new RuntimeDelegateImpl());
    this.processesRest = new ProcessesRest(processesService, identityManager);
    PowerMockito.mockStatic(RestUtils.class);
    PowerMockito.mockStatic(EntityBuilder.class);
    PowerMockito.mockStatic(ConversationState.class);
    PowerMockito.mockStatic(CommonsUtils.class);

    ConversationState conversationState = mock(ConversationState.class);
    when(ConversationState.getCurrent()).thenReturn(conversationState);
    when(ConversationState.getCurrent().getIdentity()).thenReturn(identity);

  }

  @Test
  public void getWorkFlows() throws Exception {
    List<WorkFlow> workFlows = new ArrayList<>();
    List<WorkFlowEntity> workFlowEntities = new ArrayList<>();
    ProcessesFilter processesFilter = new ProcessesFilter();
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response1 = processesRest.getWorkFlows(1L, null, null, null, 0, 10);
    assertEquals(response1.getStatus(), Response.Status.UNAUTHORIZED.getStatusCode());
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(processesService.getWorkFlows(processesFilter, 0, 10, 1L)).thenReturn(workFlows);
    when(EntityBuilder.toRestEntities(workFlows, null)).thenReturn(workFlowEntities);
    Response response2 = processesRest.getWorkFlows(1L, true, null, null, 0, 10);
    assertEquals(response2.getStatus(), Response.Status.OK.getStatusCode());
    when(processesService.getWorkFlows(processesFilter, 0, 10, 1L)).thenThrow(RuntimeException.class);
    Response response3 = processesRest.getWorkFlows(1L, null, null, null, 0, 10);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response3.getStatus());

  }

  @Test
  public void isProcessesManager() {
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response1 = processesRest.isProcessesManager();
    assertEquals(response1.getStatus(), Response.Status.UNAUTHORIZED.getStatusCode());
    when(RestUtils.isProcessesGroupMember(identity)).thenReturn(true);
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    Response response2 = processesRest.isProcessesManager();
    assertEquals(response2.getStatus(), Response.Status.OK.getStatusCode());
    assertEquals("true", response2.getEntity());
    when(RestUtils.isProcessesGroupMember(identity)).thenThrow(RuntimeException.class);
    Response response3 = processesRest.isProcessesManager();
    assertEquals(response3.getStatus(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void deleteWorkflow() {
    Response response = processesRest.deleteWorkflow(null);
    assertEquals(response.getStatus(), Response.Status.BAD_REQUEST.getStatusCode());
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response1 = processesRest.deleteWorkflow(1l);
    assertEquals(response1.getStatus(), Response.Status.UNAUTHORIZED.getStatusCode());
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(RestUtils.isProcessesGroupMember(identity)).thenReturn(false);
    Response response2 = processesRest.deleteWorkflow(1l);
    assertEquals(response2.getStatus(), Response.Status.UNAUTHORIZED.getStatusCode());
    when(RestUtils.isProcessesGroupMember(identity)).thenReturn(true);
    doNothing().when(processesService).deleteWorkflowById(1l);
    Response response3 = processesRest.deleteWorkflow(1l);
    assertEquals(response3.getStatus(), Response.Status.OK.getStatusCode());
    doThrow(new EntityNotFoundException()).when(processesService).deleteWorkflowById(1l);
    Response response4 = processesRest.deleteWorkflow(1l);
    assertEquals(response4.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    doThrow(new RuntimeException()).when(processesService).deleteWorkflowById(1l);
    Response response5 = processesRest.deleteWorkflow(1l);
    assertEquals(response5.getStatus(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void updateWorkflow() throws ObjectNotFoundException, IllegalAccessException {
    WorkFlow workFlow = mock(WorkFlow.class);
    WorkFlowEntity workFlowEntity = mock(WorkFlowEntity.class);
    Response response1 = processesRest.updateWorkFlow(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response2 = processesRest.updateWorkFlow(workFlowEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response2.getStatus());
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(EntityBuilder.fromEntity(workFlowEntity)).thenReturn(workFlow);
    when(processesService.updateWorkFlow(workFlow, 1l)).thenReturn(workFlow);
    Response response3 = processesRest.updateWorkFlow(workFlowEntity);
    assertEquals(Response.Status.OK.getStatusCode(),response3.getStatus());

    when(processesService.updateWorkFlow(workFlow, 1L)).thenThrow(ObjectNotFoundException.class);
    Response response4 = processesRest.updateWorkFlow(workFlowEntity);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(),response4.getStatus());
  }

  @Test
  public void shouldReturnUnauthorizedErrorWhenUpdateWorkflow() throws ObjectNotFoundException, IllegalAccessException {
    WorkFlow workFlow = mock(WorkFlow.class);
    WorkFlowEntity workFlowEntity = mock(WorkFlowEntity.class);
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(EntityBuilder.fromEntity(workFlowEntity)).thenReturn(workFlow);
    when(processesService.updateWorkFlow(workFlow, 1L)).thenThrow(IllegalAccessException.class);
    Response response = processesRest.updateWorkFlow(workFlowEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(),response.getStatus());
  }

  @Test
  public void shouldReturnServerErrorWhenUpdateWorkflow() throws Exception {
    WorkFlow workFlow = mock(WorkFlow.class);
    WorkFlowEntity workFlowEntity = mock(WorkFlowEntity.class);
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(EntityBuilder.fromEntity(workFlowEntity)).thenReturn(workFlow);
    when(processesService.updateWorkFlow(workFlow, 1L)).thenThrow(RuntimeException.class);
    Response response = processesRest.updateWorkFlow(workFlowEntity);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),response.getStatus());
  }

  @Test
  public void getProcessesSpaceInfo() {
    Space processesSpace = new Space();
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response1 = processesRest.getProcessesSpaceInfo();
    assertEquals(response1.getStatus(), Response.Status.UNAUTHORIZED.getStatusCode());
    when(CommonsUtils.getService(SpaceService.class)).thenReturn(spaceService);
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(spaceService.getSpaceByGroupId("/spaces/processes_space")).thenReturn(null);
    Response response2 = processesRest.getProcessesSpaceInfo();
    assertEquals(response2.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    when(spaceService.getSpaceByGroupId("/spaces/processes_space")).thenReturn(processesSpace);
    Response response3 = processesRest.getProcessesSpaceInfo();
    assertEquals(response3.getStatus(), Response.Status.OK.getStatusCode());
    when(spaceService.getSpaceByGroupId("/spaces/processes_space")).thenThrow(RuntimeException.class);
    Response response4 = processesRest.getProcessesSpaceInfo();
    assertEquals(response4.getStatus(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void createWorkflow() throws IllegalAccessException {
    WorkFlow workFlow = mock(WorkFlow.class);
    WorkFlowEntity workFlowEntity = mock(WorkFlowEntity.class);
    Response response1 = processesRest.createWorkFlow(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response2 = processesRest.createWorkFlow(workFlowEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response2.getStatus());
    when(EntityBuilder.fromEntity(workFlowEntity)).thenReturn(workFlow);
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(processesService.createWorkFlow(workFlow,1L)).thenReturn(workFlow);
    Response response3 = processesRest.createWorkFlow(workFlowEntity);
    assertEquals(Response.Status.OK.getStatusCode(),response3.getStatus());
    when(processesService.createWorkFlow(workFlow,1L)).thenThrow(IllegalAccessException.class);
    Response response4 = processesRest.createWorkFlow(workFlowEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(),response4.getStatus());
  }

  @Test
  public void shouldReturnServerErrorWhenCreateWorkflow() throws Exception {
    WorkFlow workFlow = mock(WorkFlow.class);
    WorkFlowEntity workFlowEntity = mock(WorkFlowEntity.class);
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(EntityBuilder.fromEntity(workFlowEntity)).thenReturn(workFlow);
    when(processesService.createWorkFlow(workFlow, 1L)).thenThrow(RuntimeException.class);
    Response response = processesRest.createWorkFlow(workFlowEntity);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),response.getStatus());
  }

  @Test
  public void getWorks() throws Exception {
    List<Work> works = new ArrayList<>();
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response1 = processesRest.getWorks(0L, "", 0, 10);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response1.getStatus());
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(processesService.getWorks(1L, 0, 10)).thenReturn(works);
    Response response2 = processesRest.getWorks(null, "", 0, 10);
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());
    when(processesService.getWorks(1L, 0, 10)).thenThrow(RuntimeException.class);
    Response response3 = processesRest.getWorks(1L, "", 0, 10);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response3.getStatus());
  }

  @Test
  public void createWork() throws IllegalAccessException {
    WorkEntity workEntity = new WorkEntity();
    Work work = mock(Work.class);
    WorkFlowEntity workFlowEntity = new WorkFlowEntity();
    Response response1 = processesRest.createWork(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    workEntity.setProjectId(0L);
    workFlowEntity.setProjectId(0L);
    workEntity.setWorkFlow(workFlowEntity);
    Response response2 = processesRest.createWork(workEntity);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response2.getStatus());
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    workEntity.setProjectId(1L);
    workEntity.getWorkFlow().setProjectId(1L);
    Response response3 = processesRest.createWork(workEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response3.getStatus());
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(EntityBuilder.toWork(processesService, workEntity)).thenReturn(work);
    when(processesService.createWork(work, 1L)).thenReturn(work);
    when(EntityBuilder.toWorkEntity(processesService, work, "")).thenReturn(workEntity);
    Response response4 = processesRest.createWork(workEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response4.getStatus());
    when(processesService.createWork(work, 1L)).thenThrow(IllegalAccessException.class);
    Response response5 = processesRest.createWork(workEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response5.getStatus());
  }

  @Test
  public void shouldReturnServerErrorWhenCreateWork() throws Exception {
    WorkEntity workEntity = new WorkEntity();
    Work work = mock(Work.class);
    WorkFlowEntity workFlowEntity = new WorkFlowEntity();
    workEntity.setWorkFlow(workFlowEntity);
    workEntity.setProjectId(1L);
    workEntity.getWorkFlow().setProjectId(1L);
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(EntityBuilder.toWork(processesService, workEntity)).thenReturn(work);
    when(EntityBuilder.toWorkEntity(processesService, work, "")).thenReturn(workEntity);
    when(processesService.createWork(work, 1L)).thenThrow(RuntimeException.class);
    Response response5 = processesRest.createWork(workEntity);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response5.getStatus());
  }

  @Test
  public void updateWork() throws ObjectNotFoundException, IllegalAccessException {
    WorkEntity workEntity = new WorkEntity();
    Work work = mock(Work.class);
    Response response1 = processesRest.updateWork(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response3 = processesRest.updateWork(workEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response3.getStatus());
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(EntityBuilder.toWork(processesService, workEntity)).thenReturn(work);
    when(processesService.updateWork(work, 1L)).thenReturn(work);
    when(EntityBuilder.toWorkEntity(processesService, work, "")).thenReturn(workEntity);
    Response response4 = processesRest.updateWork(workEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response4.getStatus());
    when(processesService.updateWork(work, 1L)).thenThrow(ObjectNotFoundException.class);
    Response response5 = processesRest.updateWork(workEntity);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response5.getStatus());

  }
  @Test
  public void shouldReturnUnauthorizedErrorWWhenUpdateWork() throws Exception {
    WorkEntity workEntity = new WorkEntity();
    Work work = mock(Work.class);
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(EntityBuilder.toWork(processesService, workEntity)).thenReturn(work);
    when(EntityBuilder.toWorkEntity(processesService, work, "")).thenReturn(workEntity);
    when(processesService.updateWork(work, 1L)).thenThrow(IllegalAccessException.class);
    Response response6 = processesRest.updateWork(workEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response6.getStatus());
  }

  @Test
  public void shouldReturnServerErrorWhenUpdateWork() throws Exception {
    WorkEntity workEntity = new WorkEntity();
    Work work = mock(Work.class);
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(EntityBuilder.toWork(processesService, workEntity)).thenReturn(work);
    when(EntityBuilder.toWorkEntity(processesService, work, "")).thenReturn(workEntity);
    when(processesService.updateWork(work, 1L)).thenThrow(RuntimeException.class);
    Response response6 = processesRest.updateWork(workEntity);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response6.getStatus());
  }

  @Test
  public void countWorksByWorkflow() throws Exception {
    WorkFlow workFlow = mock(WorkFlow.class);
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response = processesRest.countWorksByWorkflow(null, null);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    Response response1 = processesRest.countWorksByWorkflow(null, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    when(processesService.getWorkFlowByProjectId(1L)).thenReturn(null);
    Response response2 = processesRest.countWorksByWorkflow(1L, null);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response2.getStatus());
    when(processesService.getWorkFlowByProjectId(1L)).thenReturn(workFlow);
    when(processesService.countWorksByWorkflow(1L, false)).thenReturn(2);
    Response response3 = processesRest.countWorksByWorkflow(1L, false);
    assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());
    when(processesService.countWorksByWorkflow(1L, false)).thenThrow(RuntimeException.class);
    Response response4 = processesRest.countWorksByWorkflow(1L, false);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response4.getStatus());
  }

  @Test
  public void deleteWorkById() {
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response = processesRest.deleteWork(1L);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    Response response1 = processesRest.deleteWork(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    Response response2 = processesRest.deleteWork(1L);
    verify(processesService, times(1)).deleteWorkById(1L);
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());
    doThrow(new RuntimeException()).when(processesService).deleteWorkById(1l);
    Response response3 = processesRest.deleteWork(1L);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response3.getStatus());
  }
}
