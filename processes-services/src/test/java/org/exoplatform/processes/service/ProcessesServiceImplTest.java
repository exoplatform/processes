package org.exoplatform.processes.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.model.IllustrativeAttachment;
import org.exoplatform.processes.model.ProcessPermission;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFilter;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.services.security.MembershipEntry;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.service.ProjectService;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ProcessesServiceImplTest {

  private static final String    PROCESS_SPACE_GROUP = "/spaces/hr";

  private static final long      PROCESS_PROJECT_ID  = 10L;

  private static final long      OTHER_PROJECT_ID    = 20L;

  private static final long      WORKFLOW_ID         = 7L;

  private static final long      WORK_ID             = 100L;

  private static final long      NON_PROCESS_TASK_ID = 200L;

  private static final long      DECIDED_WORK_ID     = 101L;

  private static final long      UNKNOWN_ID          = 999L;

  private static final long      DRAFT_ID            = 50L;

  private static final long      CREATOR_ID          = 1L;

  private static final long      PROJECT_MANAGER_ID  = 2L;

  private static final long      NON_MEMBER_ID       = 3L;

  private static final long      OTHER_SPACE_ID      = 4L;

  private static final long      PARTICIPANT_ID      = 5L;

  private static final long      PROCESSES_ADMIN_ID  = 6L;

  private static final long      PLATFORM_ADMIN_ID   = 7L;

  private static final long      DISABLED_USER_ID    = 8L;

  @Mock
  private ProcessesStorage       processesStorage;

  @Mock
  private ProjectService         projectService;

  @Mock
  private IdentityManager        identityManager;

  @Mock
  private UserACL                userACL;

  @Mock
  private SpaceService           spaceService;

  private ProcessesService       processesService;

  private WorkFlow               disabledWorkFlow, enabledWorkFlow;

  private Work                   work1, work2;

  private final List<WorkFlow>   enabledWorkFlowList  = new ArrayList<>();

  private final List<WorkFlow>   disabledWorkFlowList = new ArrayList<>();

  private final List<WorkFlow>   allWorkFlowList      = new ArrayList<>();

  private final List<Work>       allWorkList          = new ArrayList<>();

  @FunctionalInterface
  private interface WorkWrite {
    void run(long workId, long userIdentityId) throws Exception;
  }

  @Before
  public void setUp() throws Exception {
    this.processesService = new ProcessesServiceImpl(processesStorage, projectService, identityManager, userACL, spaceService);
    disabledWorkFlow = new WorkFlow();
    disabledWorkFlow.setEnabled(false);
    enabledWorkFlow = new WorkFlow();
    enabledWorkFlow.setId(1L);
    enabledWorkFlow.setEnabled(true);

    allWorkFlowList.add(disabledWorkFlow);
    allWorkFlowList.add(disabledWorkFlow);

    enabledWorkFlowList.add(enabledWorkFlow);
    disabledWorkFlowList.add(disabledWorkFlow);

    allWorkList.add(work1);
    allWorkList.add(work2);

    mockUser(CREATOR_ID, "creator", new MembershipEntry(PROCESS_SPACE_GROUP, "member"));
    mockUser(PROJECT_MANAGER_ID, "manager", new MembershipEntry(PROCESS_SPACE_GROUP, "manager"));
    mockUser(NON_MEMBER_ID, "nonmember");
    mockUser(OTHER_SPACE_ID, "otherspace", new MembershipEntry("/spaces/sales", "manager"));
    mockUser(PARTICIPANT_ID, "participant", new MembershipEntry(PROCESS_SPACE_GROUP, "member"));
    mockUser(PROCESSES_ADMIN_ID, "processesadmin", new MembershipEntry("/platform/processes", "*"));
    org.exoplatform.services.security.Identity platformAdmin = mockUser(PLATFORM_ADMIN_ID, "admin");
    when(userACL.isAdministrator(platformAdmin)).thenReturn(true);
    mockUser(DISABLED_USER_ID, "disabled", new MembershipEntry(PROCESS_SPACE_GROUP, "manager"));
    when(identityManager.getIdentity(DISABLED_USER_ID)).thenReturn(socialIdentity(DISABLED_USER_ID, "disabled", false));

    ProjectDto project = new ProjectDto();
    project.setId(PROCESS_PROJECT_ID);
    project.setManager(new HashSet<>(Collections.singletonList(new MembershipEntry(PROCESS_SPACE_GROUP,
                                                                                   "manager").toString())));
    project.setParticipator(new HashSet<>(Collections.singletonList(new MembershipEntry(PROCESS_SPACE_GROUP,
                                                                                        "member").toString())));
    when(projectService.getProject(PROCESS_PROJECT_ID)).thenReturn(project);

    WorkFlow processWorkFlow = new WorkFlow();
    processWorkFlow.setId(WORKFLOW_ID);
    processWorkFlow.setProjectId(PROCESS_PROJECT_ID);
    processWorkFlow.setEnabled(true);
    when(processesStorage.getWorkFlowByProjectId(PROCESS_PROJECT_ID)).thenReturn(processWorkFlow);
    when(processesStorage.getWorkFlowByProjectId(OTHER_PROJECT_ID)).thenReturn(null);

    when(processesStorage.getWorkById(WORK_ID)).thenReturn(storedWork(WORK_ID, PROCESS_PROJECT_ID));
    when(processesStorage.getWorkById(NON_PROCESS_TASK_ID)).thenReturn(storedWork(NON_PROCESS_TASK_ID, OTHER_PROJECT_ID));
    Work decidedWork = storedWork(DECIDED_WORK_ID, PROCESS_PROJECT_ID);
    decidedWork.setStatus("Validated");
    decidedWork.setCompleted(true);
    when(processesStorage.getWorkById(DECIDED_WORK_ID)).thenReturn(decidedWork);
    when(processesStorage.getWorkById(UNKNOWN_ID)).thenReturn(null);
  }

  @Test
  public void getWorkFlows() throws IllegalAccessException {

    ProcessesFilter processesFilter = new ProcessesFilter();
    processesFilter.setEnabled(true);
    processesFilter.setQuery("test");
    when(processesStorage.findWorkFlows(processesFilter, 0, 0, 10)).thenReturn(enabledWorkFlowList);

    List<WorkFlow> enabledResult = processesService.getWorkFlows(processesFilter, 0, 10, 0L);
    assertEquals(enabledWorkFlowList, enabledResult);
    assertEquals(1, enabledResult.size());
    assertTrue(enabledResult.get(0).isEnabled());
  }

  @Test
  public void getWorks() throws Exception {

    WorkFilter workFilter = new WorkFilter();
    workFilter.setQuery("test");
    when(processesStorage.getWorks(0L, workFilter, 0, 10)).thenReturn(allWorkList);
    assertEquals(processesService.getWorks(0L, workFilter, 0, 10), allWorkList);
  }

  @Test
  public void getWorkFlowByProjectId() throws Exception {

    when(processesStorage.getWorkFlowByProjectId(0L)).thenReturn(enabledWorkFlow);
    assertEquals(processesService.getWorkFlowByProjectId(0L).getId(), 1L);
  }

  @Test
  public void getWorkFlow() throws IllegalAccessException {

    when(processesStorage.getWorkFlowById(1L, null)).thenReturn(enabledWorkFlow);
    assertEquals(processesService.getWorkFlow(1L, null).getId(), 1L);
  }

  @Test
  public void countWorkFlows() throws IllegalAccessException {

    ProcessesFilter processesFilter = new ProcessesFilter();
    processesFilter.setEnabled(true);
    processesFilter.setQuery("test");
    when(processesStorage.countWorkFlows(processesFilter)).thenReturn(enabledWorkFlowList.size());
    assertEquals(processesService.countWorkFlows(processesFilter, 0L), enabledWorkFlowList.size());
  }

  @Test
  public void updateWorkflow() throws ObjectNotFoundException, IllegalAccessException {
    WorkFlow workFlow = new WorkFlow();
    WorkFlow updatedWorkflow = new WorkFlow();
    updatedWorkflow.setId(1L);
    updatedWorkflow.setDescription("anything");
    updatedWorkflow.setAcl(new ProcessPermission(true, true, false, false));
    workFlow.setId(0L);
    Throwable exception1 = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWorkFlow(null, 1l));
    assertEquals("Workflow Type is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).getWorkById(1L);

    Throwable exception2 = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWorkFlow(workFlow, 1l));
    assertEquals("workflow type id must not be equal to 0", exception2.getMessage());
    verify(processesStorage, times(0)).getWorkById(1L);

    workFlow.setId(1L);
    when(processesStorage.getWorkFlowById(workFlow.getId(), 1L)).thenReturn(null);
    Throwable exception3 = assertThrows(ObjectNotFoundException.class, () -> this.processesService.updateWorkFlow(workFlow, 1l));
    assertEquals("oldWorkFlow is not exist", exception3.getMessage());

    workFlow.setAcl(new ProcessPermission(true, true, false, false));
    when(processesStorage.getWorkFlowById(workFlow.getId(), 1L)).thenReturn(workFlow);
    Throwable exception4 = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWorkFlow(workFlow, 1l));
    assertEquals("there are no changes to save", exception4.getMessage());

    when(processesStorage.getWorkFlowById(workFlow.getId(), 1L)).thenReturn(updatedWorkflow);
    this.processesService.updateWorkFlow(workFlow, 1l);
    verify(processesStorage, times(1)).saveWorkFlow(workFlow, 1L);
  }

  /**
   * A user who can't edit the stored workflow, resolved with the user's own
   * memberships, is refused before anything is saved. Mutation: drop the
   * canEdit guard, or resolve the workflow with a null user id (which the
   * storage answers with no permission at all), and this test fails.
   */
  @Test
  public void updateWorkflowIsRefusedWhenUserCantEditIt() {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(WORKFLOW_ID);
    workFlow.setDescription("new description");
    WorkFlow storedWorkFlow = new WorkFlow();
    storedWorkFlow.setId(WORKFLOW_ID);
    storedWorkFlow.setAcl(new ProcessPermission(false, false, false, true));
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, NON_MEMBER_ID)).thenReturn(storedWorkFlow);

    assertThrows(IllegalAccessException.class, () -> processesService.updateWorkFlow(workFlow, NON_MEMBER_ID));
    verify(processesStorage, never()).saveWorkFlow(any(), anyLong());
  }

  /**
   * The stored project and creation data of a process are kept on update: a
   * project id sent by the client would re-point the process, and re-assign the
   * managers of that other project. Mutation: drop any of the three resets and
   * this test fails.
   */
  @Test
  public void updateWorkflowKeepsTheStoredProjectAndCreation() throws Exception {
    java.util.Date createdDate = new java.util.Date(1000L);
    WorkFlow storedWorkFlow = new WorkFlow();
    storedWorkFlow.setId(WORKFLOW_ID);
    storedWorkFlow.setProjectId(PROCESS_PROJECT_ID);
    storedWorkFlow.setCreatorId(CREATOR_ID);
    storedWorkFlow.setCreatedDate(createdDate);
    storedWorkFlow.setAcl(new ProcessPermission(true, true, false, false));
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, PARTICIPANT_ID)).thenReturn(storedWorkFlow);
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(WORKFLOW_ID);
    workFlow.setDescription("new description");
    workFlow.setProjectId(OTHER_PROJECT_ID);
    workFlow.setCreatorId(PARTICIPANT_ID);
    workFlow.setCreatedDate(new java.util.Date(2000L));

    processesService.updateWorkFlow(workFlow, PARTICIPANT_ID);

    ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);
    verify(processesStorage).saveWorkFlow(saved.capture(), anyLong());
    assertEquals(PROCESS_PROJECT_ID, saved.getValue().getProjectId());
    assertEquals(CREATOR_ID, saved.getValue().getCreatorId());
    assertEquals(createdDate, saved.getValue().getCreatedDate());
  }

  /**
   * A new process always creates its own project: a project id sent by the
   * client would bind the process to an existing project, which deleting the
   * process then removes. Mutation: drop the reset and this test fails.
   */
  @Test
  public void createWorkflowNeverBindsAnExistingProject() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setProjectId(OTHER_PROJECT_ID);

    processesService.createWorkFlow(workFlow, PROCESSES_ADMIN_ID);

    ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);
    verify(processesStorage).saveWorkFlow(saved.capture(), anyLong());
    assertEquals(0L, saved.getValue().getProjectId());
  }

  /**
   * An illustration sent on update is always saved as the stored illustration
   * file, so that the storage never keeps, overwrites or serves a file the
   * client names; a process with no stored illustration gets a new file.
   * Mutation: drop the illustration id reset and this test fails.
   */
  @Test
  public void updateWorkflowKeepsTheStoredIllustrationFile() throws Exception {
    WorkFlow storedWorkFlow = new WorkFlow();
    storedWorkFlow.setId(WORKFLOW_ID);
    storedWorkFlow.setIllustrativeAttachment(new IllustrativeAttachment(55L));
    storedWorkFlow.setAcl(new ProcessPermission(true, true, false, false));
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, PARTICIPANT_ID)).thenReturn(storedWorkFlow);
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(WORKFLOW_ID);
    workFlow.setIllustrativeAttachment(new IllustrativeAttachment(99L, "image.png", "image/png", 10L, null));

    processesService.updateWorkFlow(workFlow, PARTICIPANT_ID);

    ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);
    verify(processesStorage).saveWorkFlow(saved.capture(), anyLong());
    assertEquals(Long.valueOf(55L), saved.getValue().getIllustrativeAttachment().getId());

    storedWorkFlow.setIllustrativeAttachment(new IllustrativeAttachment((Long) null));
    workFlow.setIllustrativeAttachment(new IllustrativeAttachment(99L, "image.png", "image/png", 10L, null));
    processesService.updateWorkFlow(workFlow, PARTICIPANT_ID);
    verify(processesStorage, times(2)).saveWorkFlow(saved.capture(), anyLong());
    assertNull(saved.getValue().getIllustrativeAttachment().getId());
  }

  /**
   * A new process never reuses an existing illustration file. Mutation: drop
   * the illustration id reset and this test fails.
   */
  @Test
  public void createWorkflowNeverReusesAnIllustrationFile() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setIllustrativeAttachment(new IllustrativeAttachment(99L, "image.png", "image/png", 10L, null));

    processesService.createWorkFlow(workFlow, PROCESSES_ADMIN_ID);

    ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);
    verify(processesStorage).saveWorkFlow(saved.capture(), anyLong());
    assertNull(saved.getValue().getIllustrativeAttachment().getId());
  }

  /**
   * A process illustration is served to the users who see the process in their
   * lists (participators and request creators), never read with a null user.
   * Mutation: drop the canAccess/canAddRequest guard, or either of its two
   * branches, and this test fails.
   */
  @Test
  public void getWorkFlowIllustrationIsGuarded() throws Exception {
    WorkFlow hidden = new WorkFlow();
    hidden.setIllustrativeAttachment(new IllustrativeAttachment(55L));
    hidden.setAcl(new ProcessPermission(false, false, false, false));
    WorkFlow participated = new WorkFlow();
    participated.setIllustrativeAttachment(new IllustrativeAttachment(55L));
    participated.setAcl(new ProcessPermission(true, true, false, false));
    WorkFlow requestable = new WorkFlow();
    requestable.setIllustrativeAttachment(new IllustrativeAttachment(55L));
    requestable.setAcl(new ProcessPermission(false, false, false, true));
    WorkFlow withoutIllustration = new WorkFlow();
    withoutIllustration.setIllustrativeAttachment(new IllustrativeAttachment((Long) null));
    withoutIllustration.setAcl(new ProcessPermission(true, true, false, false));
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, NON_MEMBER_ID)).thenReturn(hidden);
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, PARTICIPANT_ID)).thenReturn(participated);
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, CREATOR_ID)).thenReturn(requestable);
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, PROJECT_MANAGER_ID)).thenReturn(withoutIllustration);

    assertThrows(ObjectNotFoundException.class, () -> processesService.getWorkFlowIllustration(UNKNOWN_ID, CREATOR_ID));
    assertThrows(IllegalAccessException.class, () -> processesService.getWorkFlowIllustration(WORKFLOW_ID, NON_MEMBER_ID));
    assertThrows(ObjectNotFoundException.class, () -> processesService.getWorkFlowIllustration(WORKFLOW_ID, PROJECT_MANAGER_ID));
    verify(processesStorage, never()).getIllustrationImageById(any());

    processesService.getWorkFlowIllustration(WORKFLOW_ID, PARTICIPANT_ID);
    processesService.getWorkFlowIllustration(WORKFLOW_ID, CREATOR_ID);
    verify(processesStorage, times(2)).getIllustrationImageById(55L);
  }

  /**
   * Moving a process to another space hands its project, and the management of
   * its requests, to the target space's managers: an editor of the process who
   * isn't a processes manager may move it only as a manager of both spaces.
   * Mutation: drop the move guard, its current-space or target-space check, or
   * the processes manager branch, and this test fails.
   */
  @Test
  public void updateWorkflowSpaceMoveIsGuarded() throws Exception {
    Space currentSpace = new Space();
    currentSpace.setId("1");
    currentSpace.setGroupId(PROCESS_SPACE_GROUP);
    Space targetSpace = new Space();
    targetSpace.setId("2");
    targetSpace.setGroupId("/spaces/sales");
    when(spaceService.getSpaceByGroupId(PROCESS_SPACE_GROUP)).thenReturn(currentSpace);
    when(spaceService.getSpaceById("2")).thenReturn(targetSpace);
    for (long userId : Arrays.asList(PARTICIPANT_ID, PROJECT_MANAGER_ID, OTHER_SPACE_ID, PROCESSES_ADMIN_ID)) {
      WorkFlow storedWorkFlow = new WorkFlow();
      storedWorkFlow.setId(WORKFLOW_ID);
      storedWorkFlow.setProjectId(PROCESS_PROJECT_ID);
      storedWorkFlow.setAcl(new ProcessPermission(true, true, false, false));
      when(processesStorage.getWorkFlowById(WORKFLOW_ID, userId)).thenReturn(storedWorkFlow);
    }
    when(spaceService.isManager(currentSpace, "manager")).thenReturn(true);
    when(spaceService.isManager(targetSpace, "otherspace")).thenReturn(true);

    assertThrows(IllegalAccessException.class, () -> processesService.updateWorkFlow(movedWorkFlow("2"), PARTICIPANT_ID));
    assertThrows(IllegalAccessException.class, () -> processesService.updateWorkFlow(movedWorkFlow("2"), PROJECT_MANAGER_ID));
    assertThrows(IllegalAccessException.class, () -> processesService.updateWorkFlow(movedWorkFlow("2"), OTHER_SPACE_ID));
    verify(processesStorage, never()).saveWorkFlow(any(), anyLong());

    processesService.updateWorkFlow(movedWorkFlow("1"), PARTICIPANT_ID);
    processesService.updateWorkFlow(movedWorkFlow("2"), PROCESSES_ADMIN_ID);
    when(spaceService.isManager(targetSpace, "manager")).thenReturn(true);
    processesService.updateWorkFlow(movedWorkFlow("2"), PROJECT_MANAGER_ID);
    verify(processesStorage, times(3)).saveWorkFlow(any(), anyLong());
  }

  @Test
  public void createWorkflow() throws IllegalAccessException {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(1L);
    Throwable exception1 = assertThrows(IllegalArgumentException.class, () -> this.processesService.createWorkFlow(null, 1L));
    assertEquals("workFlow is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).saveWorkFlow(workFlow, 1L);

    Throwable exception2 = assertThrows(IllegalArgumentException.class, () -> this.processesService.createWorkFlow(workFlow, 1L));
    assertEquals("workFlow id must be equal to 0", exception2.getMessage());
    verify(processesStorage, times(0)).saveWorkFlow(workFlow, 1L);

    workFlow.setId(0L);
    processesService.createWorkFlow(workFlow, PROCESSES_ADMIN_ID);
    verify(processesStorage, times(1)).saveWorkFlow(workFlow, PROCESSES_ADMIN_ID);
  }

  /**
   * Only a member of /platform/processes creates a workflow, as the UI's
   * isProcessesManager gate. Mutation: drop the guard and this test fails.
   */
  @Test
  public void createWorkflowIsRefusedWhenUserIsNotProcessesManager() {
    WorkFlow workFlow = new WorkFlow();
    assertThrows(IllegalAccessException.class, () -> processesService.createWorkFlow(workFlow, PROJECT_MANAGER_ID));
    assertThrows(IllegalAccessException.class, () -> processesService.createWorkFlow(workFlow, UNKNOWN_ID));
    verify(processesStorage, never()).saveWorkFlow(any(), anyLong());
  }

  @Test
  public void deleteWorkflowByIdWithUser() throws Exception {
    WorkFlow deletable = new WorkFlow();
    deletable.setId(WORKFLOW_ID);
    deletable.setAcl(new ProcessPermission(true, true, true, true));
    WorkFlow notDeletable = new WorkFlow();
    notDeletable.setId(WORKFLOW_ID);
    notDeletable.setAcl(new ProcessPermission(true, true, false, true));
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, PROCESSES_ADMIN_ID)).thenReturn(deletable);
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, PROJECT_MANAGER_ID)).thenReturn(notDeletable);

    assertThrows(IllegalArgumentException.class, () -> processesService.deleteWorkflowById(null, PROCESSES_ADMIN_ID));
    assertThrows(ObjectNotFoundException.class, () -> processesService.deleteWorkflowById(UNKNOWN_ID, PROCESSES_ADMIN_ID));
    assertThrows(IllegalAccessException.class, () -> processesService.deleteWorkflowById(WORKFLOW_ID, PROJECT_MANAGER_ID));
    verify(processesStorage, never()).deleteWorkflowById(anyLong());

    processesService.deleteWorkflowById(WORKFLOW_ID, PROCESSES_ADMIN_ID);
    verify(processesStorage, times(1)).deleteWorkflowById(WORKFLOW_ID);
  }

  @Test
  public void createWork() throws Exception {
    Work work = new Work();
    work.setId(1L);
    Throwable exception1 = assertThrows(IllegalArgumentException.class, () -> this.processesService.createWork(null, 1L));
    assertEquals("work is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).saveWork(work, 1L);

    Throwable exception2 = assertThrows(IllegalArgumentException.class, () -> this.processesService.createWork(work, 1L));
    assertEquals("work id must be equal to 0", exception2.getMessage());
    verify(processesStorage, times(0)).saveWork(work, 1L);

    work.setId(0L);
    work.setProjectId(PROCESS_PROJECT_ID);
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, CREATOR_ID)).thenReturn(requestableWorkFlow(true, true));
    processesService.createWork(work, CREATOR_ID);
    verify(processesStorage, times(1)).saveWork(work, CREATOR_ID);
  }

  /**
   * The request's process is resolved from the stored project with the user's
   * memberships: a user without canAddRequest is refused, whatever the client
   * sent. Mutation: drop the canAddRequest guard and this test fails.
   */
  @Test
  public void createWorkIsRefusedWhenUserCantAddRequest() {
    Work work = new Work();
    work.setProjectId(PROCESS_PROJECT_ID);
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, NON_MEMBER_ID)).thenReturn(requestableWorkFlow(true, false));

    assertThrows(IllegalAccessException.class, () -> processesService.createWork(work, NON_MEMBER_ID));
    verify(processesStorage, never()).saveWork(any(), anyLong());
  }

  /**
   * A disabled process accepts no request, whatever the client says about it.
   * Mutation: drop the enabled check and this test fails.
   */
  @Test
  public void createWorkIsRefusedWhenStoredWorkflowIsDisabled() {
    Work work = new Work();
    work.setProjectId(PROCESS_PROJECT_ID);
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, CREATOR_ID)).thenReturn(requestableWorkFlow(false, true));

    assertThrows(IllegalArgumentException.class, () -> processesService.createWork(work, CREATOR_ID));
    verify(processesStorage, never()).saveWork(any(), anyLong());
  }

  @Test
  public void createWorkIsRefusedOnNonProcessProject() {
    Work work = new Work();
    work.setProjectId(OTHER_PROJECT_ID);

    assertThrows(ObjectNotFoundException.class, () -> processesService.createWork(work, CREATOR_ID));
    verify(processesStorage, never()).saveWork(any(), anyLong());
  }

  /**
   * Creating a request from a draft moves the draft's attachments and deletes
   * it: the draft must be the user's own. Mutation: drop the draft check and
   * this test fails.
   */
  @Test
  public void createWorkIsRefusedFromAnotherUserDraft() {
    Work work = new Work();
    work.setProjectId(PROCESS_PROJECT_ID);
    work.setDraftId(DRAFT_ID);
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, NON_MEMBER_ID)).thenReturn(requestableWorkFlow(true, true));
    when(processesStorage.getWorkDraftyId(DRAFT_ID)).thenReturn(storedDraft(CREATOR_ID));

    assertThrows(IllegalAccessException.class, () -> processesService.createWork(work, NON_MEMBER_ID));
    verify(processesStorage, never()).saveWork(any(), anyLong());
  }

  @Test
  public void updateWork() throws ObjectNotFoundException, IllegalAccessException {
    Work work = new Work();
    work.setId(0L);
    Throwable exception1 = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWork(null, 1L));
    assertEquals("Work is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).saveWork(work, 1L);

    Throwable exception2 = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWork(work, 1L));
    assertEquals("work id must not be equal to 0", exception2.getMessage());
    verify(processesStorage, times(0)).saveWork(work, 1L);

    work.setId(UNKNOWN_ID);
    assertThrows(ObjectNotFoundException.class, () -> this.processesService.updateWork(work, CREATOR_ID));
    verify(processesStorage, times(0)).saveWork(work, CREATOR_ID);

    Work sameWork = storedWork(WORK_ID, PROCESS_PROJECT_ID);
    Throwable exception4 = assertThrows(IllegalArgumentException.class,
                                        () -> this.processesService.updateWork(sameWork, CREATOR_ID));
    assertEquals("there are no changes to save", exception4.getMessage());
    verify(processesStorage, times(0)).saveWork(sameWork, CREATOR_ID);

    Work newWork = storedWork(WORK_ID, PROCESS_PROJECT_ID);
    newWork.setDescription("anything");
    processesService.updateWork(newWork, PROJECT_MANAGER_ID);
    verify(processesStorage, times(1)).saveWork(newWork, PROJECT_MANAGER_ID);
  }

  /**
   * The request's statuses are read from its project: a project id sent by the
   * client is replaced by the stored one. Mutation: drop the projectId reset
   * and this test fails.
   */
  @Test
  public void updateWorkKeepsTheStoredProject() throws Exception {
    Work work = storedWork(WORK_ID, OTHER_PROJECT_ID);
    work.setStatus("Validated");

    processesService.updateWork(work, PROJECT_MANAGER_ID);

    ArgumentCaptor<Work> saved = ArgumentCaptor.forClass(Work.class);
    verify(processesStorage).saveWork(saved.capture(), anyLong());
    assertEquals(PROCESS_PROJECT_ID, saved.getValue().getProjectId());
  }

  @Test
  public void updateWorkIsGuarded() {
    assertWorkWriteIsGuarded((workId, userId) -> {
      Work work = storedWork(workId, PROCESS_PROJECT_ID);
      work.setStatus("Canceled");
      work.setCompleted(true);
      processesService.updateWork(work, userId);
    },
                             () -> verify(processesStorage, never()).saveWork(any(), anyLong()),
                             () -> verify(processesStorage, times(4)).saveWork(any(), anyLong()));
  }

  /**
   * A requester who doesn't manage the process only cancels a pending request:
   * setting any other status or cancelling a decided request is refused, and a
   * content change is ignored, while a manager of the process may set any
   * status. Mutation: drop the status guard, the content reset or the manager
   * branch, and this test fails.
   */
  @Test
  public void updateWorkLetsTheRequesterOnlyCancel() throws Exception {
    Work validation = storedWork(WORK_ID, PROCESS_PROJECT_ID);
    validation.setStatus("Validated");
    validation.setCompleted(true);
    assertThrows(IllegalAccessException.class, () -> processesService.updateWork(validation, CREATOR_ID));
    Work decidedCancel = storedWork(DECIDED_WORK_ID, PROCESS_PROJECT_ID);
    decidedCancel.setStatus("Canceled");
    decidedCancel.setCompleted(true);
    assertThrows(IllegalAccessException.class, () -> processesService.updateWork(decidedCancel, CREATOR_ID));
    verify(processesStorage, never()).saveWork(any(), anyLong());

    Work cancel = storedWork(WORK_ID, PROCESS_PROJECT_ID);
    cancel.setStatus("Canceled");
    cancel.setCompleted(true);
    cancel.setDescription("rewritten");
    processesService.updateWork(cancel, CREATOR_ID);
    ArgumentCaptor<Work> saved = ArgumentCaptor.forClass(Work.class);
    verify(processesStorage).saveWork(saved.capture(), eq(CREATOR_ID));
    assertEquals("Canceled", saved.getValue().getStatus());
    assertEquals("description", saved.getValue().getDescription());

    processesService.updateWork(validation, PROJECT_MANAGER_ID);
    verify(processesStorage).saveWork(validation, PROJECT_MANAGER_ID);
  }

  /**
   * A requester who doesn't manage the process can't reopen a decided request,
   * while a manager can, and the requester can still reopen a pending one.
   * Mutation: drop the reopen guard, or the manager branch, and this test
   * fails.
   */
  @Test
  public void updateWorkCompletedNeverLetsTheRequesterReopenADecision() throws Exception {
    assertThrows(IllegalAccessException.class, () -> processesService.updateWorkCompleted(DECIDED_WORK_ID, false, CREATOR_ID));
    verify(processesStorage, never()).updateWorkCompleted(anyLong(), anyBoolean());

    processesService.updateWorkCompleted(WORK_ID, false, CREATOR_ID);
    processesService.updateWorkCompleted(DECIDED_WORK_ID, false, PROJECT_MANAGER_ID);
    verify(processesStorage).updateWorkCompleted(WORK_ID, false);
    verify(processesStorage).updateWorkCompleted(DECIDED_WORK_ID, false);
  }

  @Test
  public void countWorksByWorkflow() throws Exception {
    Throwable exception1 = assertThrows(IllegalArgumentException.class,
                                        () -> this.processesService.countWorksByWorkflow(null, false));
    assertEquals("Project Id is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).countWorksByWorkflow(1L, false);

    Throwable exception2 =
                         assertThrows(IllegalArgumentException.class, () -> this.processesService.countWorksByWorkflow(1L, null));
    assertEquals("isCompleted should not be null", exception2.getMessage());
    verify(processesStorage, times(0)).countWorksByWorkflow(1L, false);

    processesService.countWorksByWorkflow(1L, false);
    verify(processesStorage, times(1)).countWorksByWorkflow(1L, false);
  }

  @Test
  public void deleteWorkById() {
    Throwable exception1 = assertThrows(IllegalArgumentException.class, () -> this.processesService.deleteWorkById(null));
    assertEquals("Work Id is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).deleteWorkById(anyLong());
    processesService.deleteWorkById(1L);
    verify(processesStorage, times(1)).deleteWorkById(1L);
  }

  /**
   * Only a request of a process is deleted, and only by its creator or a
   * manager of its process. Mutation: drop getManageableWork from
   * deleteWorkById(Long, long) and every refusal of this test fails, each on
   * the never-deleted verification.
   */
  @Test
  public void deleteWorkByIdWithUserIsGuarded() {
    assertThrows(IllegalArgumentException.class, () -> processesService.deleteWorkById(null, CREATOR_ID));
    assertWorkWriteIsGuarded((workId, userId) -> processesService.deleteWorkById(workId, userId),
                             () -> verify(processesStorage, never()).deleteWorkById(anyLong()),
                             () -> verify(processesStorage, times(4)).deleteWorkById(WORK_ID));
  }

  @Test
  public void updateWorkCompletedWithUserIsGuarded() {
    assertThrows(IllegalArgumentException.class, () -> processesService.updateWorkCompleted(null, true, CREATOR_ID));
    assertWorkWriteIsGuarded((workId, userId) -> processesService.updateWorkCompleted(workId, true, userId),
                             () -> verify(processesStorage, never()).updateWorkCompleted(anyLong(), anyBoolean()),
                             () -> verify(processesStorage, times(4)).updateWorkCompleted(WORK_ID, true));
  }

  @Test
  public void createWorkDraft() throws Exception {
    Work work = new Work();
    work.setId(1L);
    Throwable exception1 = assertThrows(IllegalArgumentException.class, () -> this.processesService.createWorkDraft(null, 1L));
    assertEquals("WorkDraft is mandatory", exception1.getMessage());
    Throwable exception2 = assertThrows(IllegalArgumentException.class, () -> this.processesService.createWorkDraft(work, 1L));
    assertEquals("WorkDraft id must be equal to 0", exception2.getMessage());
    work.setId(0L);
    Throwable exception3 = assertThrows(IllegalArgumentException.class, () -> this.processesService.createWorkDraft(work, 1L));
    assertEquals("WorkDraft workflow is mandatory", exception3.getMessage());

    WorkFlow sentWorkFlow = new WorkFlow();
    sentWorkFlow.setId(WORKFLOW_ID);
    sentWorkFlow.setProjectId(OTHER_PROJECT_ID);
    work.setWorkFlow(sentWorkFlow);
    WorkFlow storedWorkFlow = requestableWorkFlow(true, true);
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, CREATOR_ID)).thenReturn(storedWorkFlow);
    processesService.createWorkDraft(work, CREATOR_ID);
    verify(processesStorage, times(1)).saveWorkDraft(work, CREATOR_ID);
    assertEquals(PROCESS_PROJECT_ID, work.getWorkFlow().getProjectId());
  }

  /**
   * A draft is the first step of a request: a user who can't add a request to
   * the process can't create one. Mutation: drop the canAddRequest guard and
   * this test fails.
   */
  @Test
  public void createWorkDraftIsRefusedWhenUserCantAddRequest() {
    Work work = new Work();
    WorkFlow sentWorkFlow = new WorkFlow();
    sentWorkFlow.setId(WORKFLOW_ID);
    work.setWorkFlow(sentWorkFlow);
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, NON_MEMBER_ID)).thenReturn(requestableWorkFlow(true, false));

    assertThrows(IllegalAccessException.class, () -> processesService.createWorkDraft(work, NON_MEMBER_ID));
    sentWorkFlow.setId(UNKNOWN_ID);
    assertThrows(ObjectNotFoundException.class, () -> processesService.createWorkDraft(work, NON_MEMBER_ID));
    verify(processesStorage, never()).saveWorkDraft(any(), anyLong());
  }

  @Test
  public void updateWorkDraft() throws Exception {
    Work work = new Work();
    work.setId(0L);
    Throwable exception1 = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWorkDraft(null, 1L));
    assertEquals("WorkDraft Type is mandatory", exception1.getMessage());
    Throwable exception2 = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWorkDraft(work, 1L));
    assertEquals("WorkDraft type id must not be equal to 0", exception2.getMessage());
    work.setId(DRAFT_ID);
    when(processesStorage.getWorkDraftyId(DRAFT_ID)).thenReturn(null);
    Throwable exception3 = assertThrows(ObjectNotFoundException.class,
                                        () -> this.processesService.updateWorkDraft(work, CREATOR_ID));
    assertEquals("oldWorkDraft is not exist", exception3.getMessage());

    when(processesStorage.getWorkDraftyId(DRAFT_ID)).thenReturn(storedDraft(CREATOR_ID));
    Work unchangedDraft = storedDraft(CREATOR_ID);
    Throwable exception4 = assertThrows(IllegalArgumentException.class,
                                        () -> this.processesService.updateWorkDraft(unchangedDraft, CREATOR_ID));
    assertEquals("there are no changes to save", exception4.getMessage());

    Work newWork = storedDraft(NON_MEMBER_ID);
    newWork.setDescription("test");
    newWork.setWorkFlow(new WorkFlow());
    processesService.updateWorkDraft(newWork, CREATOR_ID);
    verify(processesStorage, times(1)).saveWorkDraft(newWork, CREATOR_ID);
    assertEquals(CREATOR_ID, newWork.getCreatorId());
    assertEquals(WORKFLOW_ID, newWork.getWorkFlow().getId());
  }

  /**
   * Only the draft's creator can overwrite it. Mutation: drop the creator check
   * and this test fails.
   */
  @Test
  public void updateWorkDraftIsRefusedToAnotherUser() {
    when(processesStorage.getWorkDraftyId(DRAFT_ID)).thenReturn(storedDraft(CREATOR_ID));
    Work work = storedDraft(PARTICIPANT_ID);
    work.setDescription("overwritten");

    assertThrows(IllegalAccessException.class, () -> processesService.updateWorkDraft(work, PARTICIPANT_ID));
    verify(processesStorage, never()).saveWorkDraft(any(), anyLong());
  }

  @Test
  public void getWorkDrafts() {
    List<Work> workList = new ArrayList<>();
    WorkFilter workFilter = new WorkFilter();
    workFilter.setIsDraft(true);
    workFilter.setQuery("test");
    workList.add(new Work());
    when(processesStorage.findAllWorkDraftsByUser(workFilter, 0, 10, 1L)).thenReturn(workList);
    List<Work> list = processesService.getWorkDrafts(1L, workFilter, 0, 10);
    verify(processesStorage, times(1)).findAllWorkDraftsByUser(workFilter, 0, 10, 1L);
    assertEquals(list, workList);
  }

  @Test
  public void deleteWorkDraftById() {
    Throwable exception1 = assertThrows(IllegalArgumentException.class, () -> this.processesService.deleteWorkDraftById(null));
    assertEquals("WorkDraft id is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).deleteWorkDraftById(1L);
    processesService.deleteWorkDraftById(1L);
    verify(processesStorage, times(1)).deleteWorkDraftById(1L);
  }

  /**
   * Only the draft's creator can delete it. Mutation: drop the creator check
   * and the refusal fails on the never-deleted verification.
   */
  @Test
  public void deleteWorkDraftByIdWithUserIsGuarded() throws Exception {
    when(processesStorage.getWorkDraftyId(DRAFT_ID)).thenReturn(storedDraft(CREATOR_ID));

    assertThrows(IllegalArgumentException.class, () -> processesService.deleteWorkDraftById(null, CREATOR_ID));
    assertThrows(ObjectNotFoundException.class, () -> processesService.deleteWorkDraftById(UNKNOWN_ID, CREATOR_ID));
    assertThrows(IllegalAccessException.class, () -> processesService.deleteWorkDraftById(DRAFT_ID, PARTICIPANT_ID));
    assertThrows(IllegalAccessException.class, () -> processesService.deleteWorkDraftById(DRAFT_ID, PROCESSES_ADMIN_ID));
    verify(processesStorage, never()).deleteWorkDraftById(anyLong());

    processesService.deleteWorkDraftById(DRAFT_ID, CREATOR_ID);
    verify(processesStorage, times(1)).deleteWorkDraftById(DRAFT_ID);
  }

  @Test
  public void getWorkById() {
    Throwable exception1 = assertThrows(IllegalArgumentException.class, () -> this.processesService.getWorkById(1L, null));
    assertEquals("Work id is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).getWorkById(1L, 1L);
    processesService.getWorkById(1L, 1L);
    verify(processesStorage, times(1)).getWorkById(1L, 1L);
  }

  @Test
  public void updateWorkCompleted() {
    Throwable exception1 = assertThrows(IllegalArgumentException.class,
                                        () -> this.processesService.updateWorkCompleted(null, false));
    assertEquals("Work id is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).updateWorkCompleted(1L, false);
    processesService.updateWorkCompleted(1L, true);
    verify(processesStorage, times(1)).updateWorkCompleted(1L, true);
  }

  @Test
  public void getIllustrationImageById() throws ObjectNotFoundException, IOException, FileStorageException {
    Throwable exception1 =
                         assertThrows(IllegalArgumentException.class, () -> this.processesService.getIllustrationImageById(null));
    assertEquals("IllustrationId id is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).getIllustrationImageById(1L);
    processesService.getIllustrationImageById(1L);
    verify(processesStorage, times(1)).getIllustrationImageById(1L);
  }

  /**
   * Runs one work write as every kind of user, refusals first: an unknown
   * work is not found; a task of a non-process project, a non-member, a
   * member of another space, a participant of the process space who isn't
   * the creator, a disabled user and an unknown identity are refused; then
   * the creator, the process project's manager, a /platform/processes member
   * and a platform administrator succeed (four writes).
   */
  private void assertWorkWriteIsGuarded(WorkWrite write, Runnable verifyNothingWritten, Runnable verifyFourWrites) {
    assertThrows(ObjectNotFoundException.class, () -> write.run(UNKNOWN_ID, CREATOR_ID));
    assertThrows(IllegalAccessException.class, () -> write.run(NON_PROCESS_TASK_ID, PROCESSES_ADMIN_ID));
    for (long refusedUserId : Arrays.asList(NON_MEMBER_ID, OTHER_SPACE_ID, PARTICIPANT_ID, DISABLED_USER_ID, UNKNOWN_ID)) {
      assertThrows("user " + refusedUserId + " must be refused",
                   IllegalAccessException.class,
                   () -> write.run(WORK_ID, refusedUserId));
    }
    verifyNothingWritten.run();

    for (long allowedUserId : Arrays.asList(CREATOR_ID, PROJECT_MANAGER_ID, PROCESSES_ADMIN_ID, PLATFORM_ADMIN_ID)) {
      try {
        write.run(WORK_ID, allowedUserId);
      } catch (Exception e) {
        throw new AssertionError("user " + allowedUserId + " must be allowed", e);
      }
    }
    verifyFourWrites.run();
  }

  private org.exoplatform.services.security.Identity mockUser(long identityId, String username, MembershipEntry... memberships) {
    when(identityManager.getIdentity(identityId)).thenReturn(socialIdentity(identityId, username, true));
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity(username,
                                                                                                             Arrays.asList(memberships));
    when(userACL.getUserIdentity(username)).thenReturn(aclIdentity);
    return aclIdentity;
  }

  private Identity socialIdentity(long identityId, String username, boolean enabled) {
    Identity identity = new Identity(OrganizationIdentityProvider.NAME, username);
    identity.setId(String.valueOf(identityId));
    identity.setEnable(enabled);
    return identity;
  }

  private Work storedWork(long workId, long projectId) {
    return new Work(workId, "title", "description", "Request", false, "creator", null, null, null, null, false, null, projectId);
  }

  private Work storedDraft(long creatorId) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(WORKFLOW_ID);
    return new Work(DRAFT_ID, "draft", "description", creatorId, null, null, null, true, workFlow);
  }

  private WorkFlow movedWorkFlow(String spaceId) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(WORKFLOW_ID);
    workFlow.setDescription("moved");
    workFlow.setSpaceId(spaceId);
    return workFlow;
  }

  private WorkFlow requestableWorkFlow(boolean enabled, boolean canAddRequest) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(WORKFLOW_ID);
    workFlow.setProjectId(PROCESS_PROJECT_ID);
    workFlow.setEnabled(enabled);
    workFlow.setAcl(new ProcessPermission(false, false, false, canAddRequest));
    return workFlow;
  }
}
