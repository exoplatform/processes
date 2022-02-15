package org.exoplatform.processes.rest;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.rest.model.WorkFlowEntity;
import org.exoplatform.processes.rest.util.EntityBuilder;
import org.exoplatform.processes.rest.util.RestUtils;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.rest.impl.RuntimeDelegateImpl;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
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
import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ RestUtils.class, EntityBuilder.class, ConversationState.class })
public class ProcessesRestTest {

  @Mock
  private IdentityManager  identityManager;

  @Mock
  private ProcessesService processesService;

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

    ConversationState conversationState = mock(ConversationState.class);
    when(ConversationState.getCurrent()).thenReturn(conversationState);
    when(ConversationState.getCurrent().getIdentity()).thenReturn(identity);

  }

  @Test
  public void getWorkFlows() throws IllegalAccessException {
    List<WorkFlow> workFlows = new ArrayList<>();
    List<WorkFlowEntity> workFlowEntities = new ArrayList<>();
    ProcessesFilter processesFilter = new ProcessesFilter();
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response1 = processesRest.getWorkFlows(1L, null, null, null, 0, 10);
    assertEquals(response1.getStatus(), Response.Status.UNAUTHORIZED.getStatusCode());
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(processesService.getWorkFlows(processesFilter, 0, 10, 1L)).thenReturn(workFlows);
    when(EntityBuilder.toRestEntities(workFlows, null)).thenReturn(workFlowEntities);
    Response response2 = processesRest.getWorkFlows(1L, null, null, null, 0, 10);
    assertEquals(response2.getStatus(), Response.Status.OK.getStatusCode());
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
  }

  @Test
  public void deleteWorkflow() {
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
}
