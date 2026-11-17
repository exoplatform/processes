package org.exoplatform.processes.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.ws.rs.core.Response;
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
import org.exoplatform.processes.model.WorkFlowLabel;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.rest.model.WorkEntity;
import org.exoplatform.processes.rest.model.WorkFlowEntity;
import org.exoplatform.processes.rest.util.EntityBuilder;
import org.exoplatform.processes.rest.util.RestUtils;
import org.exoplatform.processes.service.ProcessesAttachmentService;
import org.exoplatform.processes.service.ProcessTranslationService;
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

  /**
   * A process is shown with its stored space, which the update checks against,
   * and a process stored before it had one with its project's space, so the
   * editor sends back the space the update expects. Mutation: show the
   * project's space again and the first assertion fails.
   */
  @Test
  public void aProcessIsShownWithItsStoredSpace() {
    COMMONS_UTILS.when(() -> CommonsUtils.getService(StatusService.class)).thenReturn(mock(StatusService.class));
    org.exoplatform.social.core.space.spi.SpaceService spaceService = mock(org.exoplatform.social.core.space.spi.SpaceService.class);
    COMMONS_UTILS.when(() -> CommonsUtils.getService(org.exoplatform.social.core.space.spi.SpaceService.class)).thenReturn(spaceService);
    Space storedSpace = new Space();
    storedSpace.setId("5");
    Space projectSpace = new Space();
    projectSpace.setId("1");
    when(spaceService.getSpaceById("5")).thenReturn(storedSpace);
    PROCESSES_UTILS.when(() -> ProcessesUtils.getProjectParentSpace(10L)).thenReturn(projectSpace);
    WorkFlow workFlow = new WorkFlow();
    workFlow.setProjectId(10L);
    workFlow.setSpaceId("5");

    assertEquals("5", EntityBuilder.toEntity(workFlow, "").getParentSpace().getId());
    workFlow.setSpaceId("");
    assertEquals("1", EntityBuilder.toEntity(workFlow, "").getParentSpace().getId());
  }

  /**
   * The labels of a REST body reach the process for the Service to check, and
   * a process is shown with its labels; a body without labels gives none, so
   * an update keeps the stored ones. Mutation: drop either mapping and this
   * test fails.
   */
  @Test
  public void theLabelsGoBothWays() {
    COMMONS_UTILS.when(() -> CommonsUtils.getService(StatusService.class)).thenReturn(mock(StatusService.class));
    WorkFlowEntity body = new WorkFlowEntity();
    assertNull(EntityBuilder.fromEntity(body).getLabels());
    List<WorkFlowLabel> labels = List.of(new WorkFlowLabel(101L, "Holiday"), new WorkFlowLabel(0L, "New one"));
    body.setLabels(labels);
    assertEquals(labels, EntityBuilder.fromEntity(body).getLabels());

    WorkFlow workFlow = new WorkFlow();
    workFlow.setSpaceId("");
    workFlow.setLabels(List.of(new WorkFlowLabel(101L, "Holiday")));
    assertEquals(List.of(new WorkFlowLabel(101L, "Holiday")), EntityBuilder.toEntity(workFlow, "").getLabels());
  }

  /**
   * A reader's language puts the process's translated texts on its REST view,
   * the stored process untouched, and on the process a request expands; no
   * language keeps the stored texts and reads no translation.
   * Mutants: the locale ignored by the view or by the request's expansion, the
   * stored process overwritten, or the translations read without a locale.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  public void toEntityPutsTheProcessTextsInTheReadersLanguage() throws Exception {
    ProcessTranslationService processTranslationService = mockTranslations();
    WorkFlow workFlow = translatedWorkFlow();

    WorkFlowEntity french = EntityBuilder.toEntity(workFlow, "", Locale.FRENCH);
    assertEquals("Congés", french.getTitle());
    assertEquals("Demander un congé", french.getDescription());
    assertEquals("<p>Règles</p>", french.getSummary());
    assertEquals("Leave", workFlow.getTitle());
    assertEquals("Congés", EntityBuilder.toRestEntities(List.of(workFlow), "", Locale.FRENCH).get(0).getTitle());
    assertEquals("Congés", EntityBuilder.toEntity(workFlow, Locale.FRENCH).getTitle());

    Work work = new Work();
    work.setId(4L);
    work.setWorkFlowId(9L);
    when(processesService.getWorkFlow(9L, null)).thenReturn(workFlow);
    assertEquals("Congés", EntityBuilder.toWorkEntity(processesService, work, "workFlow", Locale.FRENCH).getWorkFlow().getTitle());

    WorkFlowEntity stored = EntityBuilder.toEntity(workFlow, "");
    assertEquals("Leave", stored.getTitle());
    assertEquals("Ask for leave", stored.getDescription());
    assertEquals("Leave rules", stored.getSummary());
    assertEquals("Leave", EntityBuilder.toWorkEntity(processesService, work, "workFlow").getWorkFlow().getTitle());
    verify(processTranslationService, never()).getTitle(any(), org.mockito.ArgumentMatchers.isNull());
  }

  /**
   * The process read by id is in the user's language, unless the editor asks
   * for its stored texts; the process list is in the user's language.
   * Mutants: the translate flag ignored, or the list built without the
   * user's locale.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  public void restReadsAnswerInTheUsersLanguageButTheEditorsRead() throws Exception {
    mockTranslations();
    WorkFlow workFlow = translatedWorkFlow();
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(processesService.getWorkFlow(9L, 1L)).thenReturn(workFlow);
    when(processesService.getWorkFlows(any(ProcessesFilter.class), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong()))
        .thenReturn(List.of(workFlow));
    ProcessesRest frenchRest = spy(processesRest);
    doReturn(Locale.FRENCH).when(frenchRest).getLocale();

    assertEquals("Congés", ((WorkFlowEntity) frenchRest.getWorkFlowById(9L, "", true).getEntity()).getTitle());
    assertEquals("Leave", ((WorkFlowEntity) frenchRest.getWorkFlowById(9L, "", false).getEntity()).getTitle());
    Response list = frenchRest.getWorkFlows(null, true, null, null, "", 0, 10);
    assertEquals("Congés", ((List<?>) list.getEntity()).stream().map(WorkFlowEntity.class::cast).findFirst().orElseThrow().getTitle());
  }

  /**
   * @return the translation reader the builder looks up, answering French
   *         texts for the process 9
   */
  private ProcessTranslationService mockTranslations() {
    StatusService statusService = mock(StatusService.class);
    ProcessTranslationService processTranslationService = mock(ProcessTranslationService.class);
    COMMONS_UTILS.when(() -> CommonsUtils.getService(StatusService.class)).thenReturn(statusService);
    COMMONS_UTILS.when(() -> CommonsUtils.getService(ProcessTranslationService.class)).thenReturn(processTranslationService);
    when(processTranslationService.getTitle(any(WorkFlow.class), any(Locale.class)))
        .thenAnswer(invocation -> Locale.FRENCH.equals(invocation.getArgument(1)) ? "Congés" : ((WorkFlow) invocation.getArgument(0)).getTitle());
    when(processTranslationService.getDescription(any(WorkFlow.class), any(Locale.class))).thenReturn("Demander un congé");
    when(processTranslationService.getSummary(any(WorkFlow.class), any(Locale.class))).thenReturn("<p>Règles</p>");
    return processTranslationService;
  }

  /**
   * @return a process with its stored texts in the default language
   */
  private WorkFlow translatedWorkFlow() {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(9L);
    workFlow.setTitle("Leave");
    workFlow.setDescription("Ask for leave");
    workFlow.setSummary("Leave rules");
    workFlow.setSpaceId("");
    return workFlow;
  }
}
