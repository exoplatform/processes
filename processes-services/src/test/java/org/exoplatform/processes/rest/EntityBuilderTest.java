package org.exoplatform.processes.rest;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.model.*;
import org.exoplatform.processes.rest.model.WorkEntity;
import org.exoplatform.processes.rest.model.WorkFlowEntity;
import org.exoplatform.processes.rest.util.EntityBuilder;
import org.exoplatform.processes.rest.util.RestUtils;
import org.exoplatform.processes.service.ProcessesAttachmentService;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.rest.impl.RuntimeDelegateImpl;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.task.service.StatusService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.RuntimeDelegate;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
public class EntityBuilderTest {

  @Mock
  private IdentityManager  identityManager;

  @Mock
  private ProcessesService processesService;

  @Mock
  private ProcessesAttachmentService processesAttachmentService;

  private ProcessesRest    processesRest;
  private IdentityRegistry identityRegistry;

  @Before
  public void setUp() {
    RuntimeDelegate.setInstance(new RuntimeDelegateImpl());
    identityRegistry = mock(IdentityRegistry.class);
    this.processesRest = new ProcessesRest(processesService, identityManager, processesAttachmentService);
  }

  @Test
  @PrepareForTest({ RestUtils.class , CommonsUtils.class , ProcessesUtils.class})
  public void toRestEntities() throws Exception {
    StatusService statusService = mock(StatusService.class);
    List<WorkFlow> workFlows = new ArrayList<>();
    PowerMockito.mockStatic(RestUtils.class);
    PowerMockito.mockStatic(CommonsUtils.class);
    PowerMockito.mockStatic(ProcessesUtils.class);
    Space space =new Space();
    space.setId("test");
    space.setDisplayName("test");
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(1L);
    workFlow.setProjectId(1L);
    workFlows.add(workFlow);
    List<WorkFlowEntity> workFlowEntities = new ArrayList<>();
    WorkFlowEntity workFlowEntity = new WorkFlowEntity();
    workFlowEntity.setId(11);
    workFlowEntity.setSummary("test");
    workFlowEntity.setDescription("description");
    workFlowEntity.setTitle("title");
    workFlowEntity.setProjectId(1L);
    ProcessesFilter processesFilter = new ProcessesFilter();
    processesFilter.setQuery("test");
    processesFilter.setEnabled(true);
    when(CommonsUtils.getService(StatusService.class)).thenReturn(statusService);
    when(ProcessesUtils.getProjectParentSpace(workFlow.getProjectId())).thenReturn(space);
    when(RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(processesService.getWorkFlows(processesFilter, 0, 10, 1L)).thenReturn(workFlows);
    EntityBuilder.toEntity(workFlow);
    EntityBuilder.fromEntity(workFlowEntity);
    EntityBuilder.fromRestEntities(workFlowEntities);
    workFlowEntities.add(workFlowEntity);
    List<WorkFlow> fromRestEntities = EntityBuilder.fromRestEntities(workFlowEntities);
    assertNotNull(fromRestEntities);
    List<WorkFlowEntity> toRestEntities =EntityBuilder.toRestEntities(workFlows,"test");
    processesRest.getWorkFlows(1L, true, "test", "test", 0, 10);
    assertNotNull(toRestEntities);
  }

  @Test
  @PrepareForTest({ CommonsUtils.class , ProcessesUtils.class})
  public void toWorkEntity() throws ObjectNotFoundException, IllegalAccessException {

    String username = "testuser";
    org.exoplatform.services.security.Identity root = new org.exoplatform.services.security.Identity(username);
    ConversationState.setCurrent(new ConversationState(root));
    long currentOwnerId = 2;
    org.exoplatform.social.core.identity.model.Identity currentIdentity = new org.exoplatform.social.core.identity.model.Identity(OrganizationIdentityProvider.NAME, username);
    currentIdentity.setId(String.valueOf(currentOwnerId));
    Profile currentProfile = new Profile();
    currentProfile.setProperty(Profile.FULL_NAME, username);
    currentIdentity.setProfile(currentProfile);
    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity(username);

    when(identityRegistry.getIdentity(username)).thenReturn(userID);
    when(identityManager.getIdentity((String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);
    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(currentIdentity);

    WorkEntity workEntity = new WorkEntity();
    Work work = new Work();
    workEntity.setId(12L);
    workEntity.setDescription("description");
    workEntity.setCompleted(true);
    workEntity.setTitle("title");
    work.setId(12L);
    work.setDescription("description");
    work.setCompleted(true);
    work.setTitle("title");
    Work work1 = EntityBuilder.toWork(processesService, workEntity);
    EntityBuilder.fromEntity(workEntity);
    EntityBuilder.toEntity(work);
    WorkEntity toWorkEntity = EntityBuilder.toWorkEntity(processesService,work,"test");
    when(processesService.updateWork(work1, 1L)).thenReturn(work);
    processesRest.updateWork(workEntity);
    assertNotNull(toWorkEntity);
  }
}
