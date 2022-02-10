package org.exoplatform.processes.rest;

import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.rest.model.WorkFlowEntity;
import org.exoplatform.processes.rest.util.EntityBuilder;
import org.exoplatform.processes.rest.util.RestUtils;
import org.exoplatform.processes.service.ProcessesService;
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

import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ RestUtils.class, EntityBuilder.class, ConversationState.class })
public class ProcessesRestTest {

  @Mock
  private IdentityManager  identityManager;

  @Mock
  private ProcessesService processesService;

  private ProcessesRest    processesRest;

  @Before
  public void setUp() {
    this.processesRest = new ProcessesRest(processesService, identityManager);
    PowerMockito.mockStatic(RestUtils.class);
    PowerMockito.mockStatic(EntityBuilder.class);
    PowerMockito.mockStatic(ConversationState.class);

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
    ConversationState conversationState = mock(ConversationState.class);
    when(ConversationState.getCurrent()).thenReturn(conversationState);
    Identity identity = mock(Identity.class);
    when(ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response1 = processesRest.isProcessesManager();
    assertEquals(response1.getStatus(), Response.Status.UNAUTHORIZED.getStatusCode());
    when(RestUtils.isProcessesGroupMember(identity)).thenReturn(true);
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    Response response2 = processesRest.isProcessesManager();
    assertEquals(response2.getStatus(), Response.Status.OK.getStatusCode());
    assertEquals("true", response2.getEntity());
  }
}
