package org.exoplatform.processes.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.ws.rs.ext.RuntimeDelegate;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.constant.ProjectMode;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.rest.model.WorkEntity;
import org.exoplatform.processes.rest.model.WorkFlowEntity;
import org.exoplatform.processes.rest.util.EntityBuilder;
import org.exoplatform.processes.rest.util.RestUtils;
import org.exoplatform.processes.service.ProcessesAttachmentService;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.rest.impl.RuntimeDelegateImpl;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.task.service.StatusService;

@RunWith(MockitoJUnitRunner.Silent.class)
public class EntityBuilderTest {

  private static final MockedStatic<CommonsUtils>   COMMONS_UTILS   = mockStatic(CommonsUtils.class);

  private static final MockedStatic<RestUtils>      REST_UTILS      = mockStatic(RestUtils.class);

  private static final MockedStatic<ProcessesUtils> PROCESSES_UTILS = mockStatic(ProcessesUtils.class);

  @Mock
  private IdentityManager                           identityManager;

  @Mock
  private ProcessesService                          processesService;

  @Mock
  private ProcessesAttachmentService                processesAttachmentService;

  private ProcessesRest                             processesRest;

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    COMMONS_UTILS.close();
    REST_UTILS.close();
    PROCESSES_UTILS.close();
  }

  @Before
  public void setUp() {
    RuntimeDelegate.setInstance(new RuntimeDelegateImpl());
    this.processesRest = new ProcessesRest(processesService, identityManager, processesAttachmentService);
  }

  @Test
  public void toRestEntities() throws Exception {
    StatusService statusService = mock(StatusService.class);
    List<WorkFlow> workFlows = new ArrayList<>();
    Space space = new Space();
    space.setId("12");
    space.setPrettyName("test");
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
    Identity spaceIdentity = new Identity();
    spaceIdentity.setId("1");
    spaceIdentity.getProfile().getFullName();
    spaceIdentity.setRemoteId("test");
    spaceIdentity.setProviderId("space");
    Profile profile = new Profile();
    profile.setAvatarUrl("");
    profile.setProperty("fullName", "test");
    spaceIdentity.setProfile(profile);
    ProcessesFilter processesFilter = new ProcessesFilter();
    processesFilter.setQuery("test");
    processesFilter.setEnabled(true);
    COMMONS_UTILS.when(() -> CommonsUtils.getService(StatusService.class)).thenReturn(statusService);
    COMMONS_UTILS.when(() -> CommonsUtils.getService(IdentityManager.class)).thenReturn(identityManager);
    PROCESSES_UTILS.when(() -> ProcessesUtils.getProjectParentSpace(workFlow.getProjectId())).thenReturn(space);
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(processesService.getWorkFlows(processesFilter, 0, 10, 1L)).thenReturn(workFlows);
    when(identityManager.getOrCreateSpaceIdentity("test")).thenReturn(spaceIdentity);
    EntityBuilder.toEntity(workFlow);
    EntityBuilder.fromEntity(workFlowEntity);
    EntityBuilder.fromRestEntities(workFlowEntities);
    workFlowEntities.add(workFlowEntity);
    List<WorkFlow> fromRestEntities = EntityBuilder.fromRestEntities(workFlowEntities);
    assertNotNull(fromRestEntities);
    List<WorkFlowEntity> toRestEntities = EntityBuilder.toRestEntities(workFlows, "test");
    processesRest.getWorkFlows(1L, true, null, "test", "test", 0, 10);
    assertNotNull(toRestEntities);
  }

  @Test
  public void toWorkEntity() throws ObjectNotFoundException, IllegalAccessException {

    String username = "testuser";
    org.exoplatform.services.security.Identity root = new org.exoplatform.services.security.Identity(username);
    ConversationState.setCurrent(new ConversationState(root));
    long currentOwnerId = 2;
    org.exoplatform.social.core.identity.model.Identity currentIdentity =
                                                                        new org.exoplatform.social.core.identity.model.Identity(OrganizationIdentityProvider.NAME,
                                                                                                                                username);
    currentIdentity.setId(String.valueOf(currentOwnerId));
    Profile currentProfile = new Profile();
    currentProfile.setProperty(Profile.FULL_NAME, username);
    currentIdentity.setProfile(currentProfile);

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
    work.setStatus("todo");
    work.setCreatedBy("testuser");
    work.setStartDate(new Date());
    work.setEndDate(new Date());
    work.setDueDate(new Date());
    work.setCreatorId(1L);
    work.setDraftId(1L);
    work.setTaskId(1L);
    work.setIsDraft(false);
    Work work1 = EntityBuilder.toWork(processesService, workEntity);
    EntityBuilder.fromEntity(workEntity);
    EntityBuilder.toEntity(work);
    WorkEntity toWorkEntity = EntityBuilder.toWorkEntity(processesService, work, "test");
    when(processesService.updateWork(work1, 1L)).thenReturn(work);
    processesRest.updateWork(workEntity);
    assertNotNull(toWorkEntity);
  }

  /**
   * The project mode a REST body states is read as NEW or EXISTING, an unstated
   * one as none, and any other value is refused with its message code; the
   * process built from the body carries it. Mutation: accept any value as NEW,
   * or drop the mode from the process, and this test fails.
   */
  @Test
  public void theProjectModeIsReadFromTheBody() {
    assertNull(EntityBuilder.toProjectMode(null));
    assertNull(EntityBuilder.toProjectMode(" "));
    assertEquals(ProjectMode.NEW, EntityBuilder.toProjectMode("NEW"));
    assertEquals(ProjectMode.EXISTING, EntityBuilder.toProjectMode("EXISTING"));
    for (String invalid : new String[] { "existing", "OTHER" }) {
      IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, () -> EntityBuilder.toProjectMode(invalid));
      assertEquals("processes.project.invalidMode", refusal.getMessage());
    }
    WorkFlowEntity body = new WorkFlowEntity();
    body.setProjectMode("EXISTING");
    body.setProjectId(10L);
    WorkFlow workFlow = EntityBuilder.fromEntity(body);
    assertEquals(ProjectMode.EXISTING, workFlow.getProjectMode());
    assertEquals(10L, workFlow.getProjectId());
  }

  /**
   * A request built from a REST body names the process of the body's workFlow
   * and never keeps the project id the body sends, nor reads the process to
   * find one. Mutation: keep the body's project id, or drop the process id,
   * and this test fails.
   */
  @Test
  public void aRequestNamesItsProcessAndNeverTheProjectSent() {
    WorkEntity body = new WorkEntity();
    body.setProjectId(42L);
    WorkFlowEntity workFlow = new WorkFlowEntity();
    workFlow.setId(5L);
    workFlow.setProjectId(43L);
    body.setWorkFlow(workFlow);

    Work work = EntityBuilder.toWork(processesService, body);

    assertEquals(5L, work.getWorkFlowId());
    assertEquals(0L, work.getProjectId());
    org.mockito.Mockito.verifyNoInteractions(processesService);
  }

  /**
   * A created request is shown with the process it was created for, not with
   * the first process of its project, which several processes may share.
   * Mutation: read the process by the project again and this test fails.
   *
   * @throws Exception when the stubbed read fails
   */
  @Test
  @SuppressWarnings("removal")
  public void aCreatedRequestIsShownWithItsOwnProcess() throws Exception {
    COMMONS_UTILS.when(() -> CommonsUtils.getService(StatusService.class)).thenReturn(mock(StatusService.class));
    Work work = new Work();
    work.setProjectId(10L);
    work.setWorkFlowId(8L);
    WorkFlow ownProcess = new WorkFlow();
    ownProcess.setId(8L);
    WorkFlow firstProcess = new WorkFlow();
    firstProcess.setId(7L);
    when(processesService.getWorkFlow(8L, null)).thenReturn(ownProcess);
    when(processesService.getWorkFlowByProjectId(10L)).thenReturn(firstProcess);

    assertEquals(8L, EntityBuilder.toWorkEntity(processesService, work, "workFlow").getWorkFlow().getId());
    work.setWorkFlowId(0);
    assertEquals(7L, EntityBuilder.toWorkEntity(processesService, work, "workFlow").getWorkFlow().getId());
  }
}
