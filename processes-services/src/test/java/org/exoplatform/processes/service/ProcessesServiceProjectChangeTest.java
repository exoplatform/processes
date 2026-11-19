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
package org.exoplatform.processes.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.constant.ProcessesEvents;
import org.exoplatform.processes.constant.ProjectMode;
import org.exoplatform.processes.model.ProcessPermission;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.model.WorkFlowLabel;
import org.exoplatform.processes.plugin.RequestValuesPlugin;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.security.MembershipEntry;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dto.LabelDto;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.StatusDto;
import org.exoplatform.task.service.LabelService;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.StatusService;

/**
 * The change of a process's task project when the process is edited
 * (EXO-92333): which project an update may set, what it resets with the
 * project, and the move of the requests it flags in the same save.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessesServiceProjectChangeTest {

  private static final long          WORKFLOW_ID      = 7L;

  private static final String        SPACE_ID         = "3";

  private static final String        SPACE_GROUP      = "/spaces/hr";

  private static final String        OTHER_SPACE_ID   = "4";

  private static final String        OTHER_GROUP      = "/spaces/sales";

  private static final long          ADMIN_ID         = 1L;

  private static final long          EDITOR_ID        = 2L;

  private static final long          STORED_ID        = 11L;

  private static final long          TARGET_ID        = 10L;

  private static final long          OTHER_SPACE_PROJECT_ID = 20L;

  private static final long          TASKS_APP_ID     = 12L;

  private static final long          CONFLICT_ID      = 13L;

  @Mock
  private LabelService               labelService;

  @Mock
  private ListenerService            listenerService;

  @Mock
  private ProcessesStorage           processesStorage;

  @Mock
  private ProjectService             projectService;

  @Mock
  private IdentityManager            identityManager;

  @Mock
  private UserACL                    userACL;

  @Mock
  private SpaceService               spaceService;

  @Mock
  private ProcessesAttachmentService processesAttachmentService;

  @Mock
  private StatusService              statusService;

  @Mock
  private ProcessTranslationService  processTranslationService;

  @Mock
  private RequestValuesPlugin        plugin;

  private ProcessesServiceImpl       processesService;

  private WorkFlow                   storedWorkFlow;

  /**
   * Builds two spaces, a process stored in the first one's project
   * {@value #STORED_ID}, and the projects an update may name.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @BeforeEach
  void setUp() throws Exception {
    processesService = spy(new ProcessesServiceImpl(processesStorage,
                                                    projectService,
                                                    identityManager,
                                                    userACL,
                                                    spaceService,
                                                    processesAttachmentService,
                                                    statusService,
                                                    labelService,
                                                    listenerService,
                                                    processTranslationService));
    // The saves run in a transaction the platform aspect begins; the unit
    // tests call their bodies directly
    doAnswer(invocation -> processesService.writeWorkFlowRows(invocation.getArgument(0),
                                                              invocation.getArgument(1))).when(processesService)
                                                                                         .saveWorkFlowRows(any(), anyLong());
    doAnswer(invocation -> processesService.writeWorkFlowRowsAndRequestMove(invocation.getArgument(0),
                                                                            invocation.getArgument(1),
                                                                            invocation.getArgument(2))).when(processesService)
                                                                                                       .saveWorkFlowRowsAndRequestMove(any(),
                                                                                                                                       anyLong(),
                                                                                                                                       anyString());
    when(processesStorage.createProject(any())).thenAnswer(invocation -> {
      WorkFlow workFlow = invocation.getArgument(0);
      workFlow.setProjectId(30L);
      return workFlow;
    });
    when(processesStorage.saveWorkFlow(any(WorkFlow.class), anyLong())).thenAnswer(invocation -> invocation.getArgument(0));
    when(processesAttachmentService.selectProcessAttachments(anyLong(), anyLong(), anyLong(), any())).thenReturn(null);
    space(SPACE_ID, SPACE_GROUP);
    space(OTHER_SPACE_ID, OTHER_GROUP);
    mockUser(ADMIN_ID, "admin", new MembershipEntry("/platform/processes", "*"));
    mockUser(EDITOR_ID, "editor", new MembershipEntry(SPACE_GROUP, "manager"));

    project(STORED_ID, SPACE_GROUP);
    project(TARGET_ID, SPACE_GROUP);
    project(OTHER_SPACE_PROJECT_ID, OTHER_GROUP);
    ProjectDto tasksApp = project(TASKS_APP_ID, SPACE_GROUP);
    tasksApp.setManager(new HashSet<>(Set.of("john")));
    project(CONFLICT_ID, SPACE_GROUP);
    when(statusService.getStatuses(anyLong())).thenReturn(statuses(TARGET_ID, "To Do", "Request", "Done"));
    when(statusService.getStatuses(CONFLICT_ID)).thenReturn(statuses(CONFLICT_ID, "To Do", "request"));

    storedWorkFlow = new WorkFlow();
    storedWorkFlow.setId(WORKFLOW_ID);
    storedWorkFlow.setTitle("Leave");
    storedWorkFlow.setProjectId(STORED_ID);
    storedWorkFlow.setSpaceId(SPACE_ID);
    storedWorkFlow.setLabels(List.of(new WorkFlowLabel(5L, "Former")));
    storedWorkFlow.setRequesterFormIds(List.of(63L, 64L));
    storedWorkFlow.setAcl(new ProcessPermission(true, true, true, true));
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, ADMIN_ID)).thenReturn(storedWorkFlow);
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, EDITOR_ID)).thenReturn(storedWorkFlow);
  }

  /**
   * An update naming an eligible project of the managing space saves that
   * project, adds to it the process statuses it lacks, flags the move of the
   * requests in the same save as the editor, and hands the move over once the
   * save is done. Mutation: keep the stored project, as before, and the saved
   * project stays {@value #STORED_ID}.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  void anExistingProjectOfTheSpaceReplacesTheStoredOneAndFlagsTheMove() throws Exception {
    processesService.updateWorkFlow(update(ProjectMode.EXISTING, TARGET_ID), EDITOR_ID);

    ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);
    verify(processesStorage).saveWorkFlow(saved.capture(), eq(EDITOR_ID));
    assertEquals(TARGET_ID, saved.getValue().getProjectId());
    verify(processesStorage).requestProjectMove(eq(WORKFLOW_ID), eq("editor"), anyLong());
    verify(listenerService).broadcast(ProcessesEvents.PROCESS_PROJECT_CHANGED, WORKFLOW_ID, "editor");
    ArgumentCaptor<String> created = ArgumentCaptor.forClass(String.class);
    verify(statusService, org.mockito.Mockito.atLeastOnce()).createStatus(any(ProjectDto.class), created.capture());
    assertEquals(List.of("RequestInProgress", "Validated", "Refused", "Canceled"), created.getAllValues());
  }

  /**
   * An update that keeps the stored project, named again or not named, flags
   * no move, adds no status and broadcasts nothing. Mutation: flag the move
   * on every save and the stored project's update flags one.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  void anUpdateKeepingTheProjectFlagsNoMove() throws Exception {
    processesService.updateWorkFlow(update(ProjectMode.EXISTING, STORED_ID), EDITOR_ID);
    processesService.updateWorkFlow(update(null, TARGET_ID), EDITOR_ID);

    ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);
    verify(processesStorage, org.mockito.Mockito.times(2)).saveWorkFlow(saved.capture(), eq(EDITOR_ID));
    // A project named with no mode is the client's, never the saved one
    assertTrue(saved.getAllValues().stream().allMatch(workFlow -> workFlow.getProjectId() == STORED_ID));
    verify(processesStorage, never()).requestProjectMove(anyLong(), anyString(), anyLong());
    verify(statusService, never()).createStatus(any(ProjectDto.class), anyString());
    verify(listenerService, never()).broadcast(eq(ProcessesEvents.PROCESS_PROJECT_CHANGED), any(), any());
  }

  /**
   * A project the editor would not offer, or one holding a column named like a
   * process status in another case, is refused before any write. Mutation:
   * skip the eligibility check and the Tasks app project is saved.
   */
  @Test
  void anIneligibleProjectIsRefusedBeforeAnyWrite() {
    IllegalArgumentException notEligible = assertThrows(IllegalArgumentException.class,
                                                        () -> processesService.updateWorkFlow(update(ProjectMode.EXISTING,
                                                                                                     TASKS_APP_ID),
                                                                                              EDITOR_ID));
    assertEquals("processes.project.notEligible", notEligible.getMessage());
    IllegalArgumentException otherSpace = assertThrows(IllegalArgumentException.class,
                                                       () -> processesService.updateWorkFlow(update(ProjectMode.EXISTING,
                                                                                                    OTHER_SPACE_PROJECT_ID),
                                                                                             EDITOR_ID));
    assertEquals("processes.project.notEligible", otherSpace.getMessage());
    IllegalArgumentException conflict = assertThrows(IllegalArgumentException.class,
                                                     () -> processesService.updateWorkFlow(update(ProjectMode.EXISTING,
                                                                                                  CONFLICT_ID),
                                                                                           EDITOR_ID));
    assertEquals("processes.project.statusNameConflict", conflict.getMessage());
    verify(processesStorage, never()).saveWorkFlow(any(), anyLong());
    verify(processesStorage, never()).requestProjectMove(anyLong(), anyString(), anyLong());
    verify(statusService, never()).createStatus(any(ProjectDto.class), anyString());
  }

  /**
   * A new project is created in the managing space and named after the
   * process, and the move is flagged. Mutation: keep the stored project on
   * NEW and no project is created.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  void aNewProjectIsCreatedInTheManagingSpace() throws Exception {
    processesService.updateWorkFlow(update(ProjectMode.NEW, 0), EDITOR_ID);

    ArgumentCaptor<WorkFlow> creation = ArgumentCaptor.forClass(WorkFlow.class);
    verify(processesStorage).createProject(creation.capture());
    assertEquals(SPACE_ID, creation.getValue().getSpaceId());
    ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);
    verify(processesStorage).saveWorkFlow(saved.capture(), eq(EDITOR_ID));
    assertEquals(30L, saved.getValue().getProjectId());
    verify(processesStorage).requestProjectMove(eq(WORKFLOW_ID), eq("editor"), anyLong());
  }

  /**
   * A change of managing space that keeps the stored project is refused; with
   * a project of the new space, a processes administrator saves it. Mutation:
   * run the space-kept check on every update and the second one is refused.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  void aSpaceChangeComesWithAProjectOfTheNewSpace() throws Exception {
    WorkFlow keepingProject = update(null, 0);
    keepingProject.setSpaceId(OTHER_SPACE_ID);
    IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                                                    () -> processesService.updateWorkFlow(keepingProject, ADMIN_ID));
    assertEquals("processes.project.notEligible", refusal.getMessage());
    WorkFlow sameProject = update(ProjectMode.EXISTING, STORED_ID);
    sameProject.setSpaceId(OTHER_SPACE_ID);
    assertThrows(IllegalArgumentException.class, () -> processesService.updateWorkFlow(sameProject, ADMIN_ID));
    verify(processesStorage, never()).saveWorkFlow(any(), anyLong());

    WorkFlow moved = update(ProjectMode.EXISTING, OTHER_SPACE_PROJECT_ID);
    moved.setSpaceId(OTHER_SPACE_ID);
    processesService.updateWorkFlow(moved, ADMIN_ID);
    ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);
    verify(processesStorage).saveWorkFlow(saved.capture(), eq(ADMIN_ID));
    assertEquals(OTHER_SPACE_PROJECT_ID, saved.getValue().getProjectId());
    assertEquals(OTHER_SPACE_ID, saved.getValue().getSpaceId());
  }

  /**
   * The labels belong to the project: a project change that sends none resets
   * them, and the ones it sends are checked against the new project.
   * Mutation: keep the stored labels when none are sent and the former
   * project's label is saved.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  void aProjectChangeResetsTheLabelsToTheNewProjectsOnes() throws Exception {
    processesService.updateWorkFlow(update(ProjectMode.EXISTING, TARGET_ID), EDITOR_ID);
    ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);
    verify(processesStorage).saveWorkFlow(saved.capture(), eq(EDITOR_ID));
    assertEquals(List.of(), saved.getValue().getLabels());

    LabelDto targetLabel = new LabelDto();
    targetLabel.setId(8L);
    targetLabel.setName("Urgent");
    targetLabel.setProject(projectService.getProject(TARGET_ID));
    when(labelService.findLabelsByProject(eq(TARGET_ID), any(), anyInt(), anyInt())).thenReturn(List.of(targetLabel));
    when(labelService.getLabel(8L)).thenReturn(targetLabel);
    LabelDto formerProjectLabel = new LabelDto();
    formerProjectLabel.setId(5L);
    formerProjectLabel.setName("Former");
    formerProjectLabel.setProject(projectService.getProject(STORED_ID));
    when(labelService.getLabel(5L)).thenReturn(formerProjectLabel);
    WorkFlow withLabels = update(ProjectMode.EXISTING, TARGET_ID);
    withLabels.setLabels(List.of(new WorkFlowLabel(8L, "Urgent")));
    processesService.updateWorkFlow(withLabels, EDITOR_ID);
    verify(processesStorage, org.mockito.Mockito.times(2)).saveWorkFlow(saved.capture(), eq(EDITOR_ID));
    assertEquals(List.of(8L), saved.getValue().getLabels().stream().map(WorkFlowLabel::getId).toList());

    WorkFlow formerLabel = update(ProjectMode.EXISTING, TARGET_ID);
    formerLabel.setLabels(List.of(new WorkFlowLabel(5L, "Former")));
    IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                                                    () -> processesService.updateWorkFlow(formerLabel, EDITOR_ID));
    assertEquals("processes.label.notInProject", refusal.getMessage());
  }

  /**
   * A project change keeps, of the forms ticked for the requesters, those the
   * new project gives, and clears the rest; a new project gives none yet.
   * Mutation: check the forms against the former project and form 63 is
   * cleared too.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  void aProjectChangeKeepsOnlyTheRequesterFormsTheNewProjectGives() throws Exception {
    processesService.addRequestValuesPlugin(plugin);
    doThrow(new IllegalArgumentException("forms.form.notApplicable")).when(plugin)
                                                                     .checkRequesterForms(any(),
                                                                                          anyLong(),
                                                                                          eq(List.of(64L)),
                                                                                          any());
    doThrow(new IllegalArgumentException("forms.form.notApplicable")).when(plugin)
                                                                     .checkRequesterForms(any(),
                                                                                          eq(STORED_ID),
                                                                                          any(),
                                                                                          any());

    processesService.updateWorkFlow(update(ProjectMode.EXISTING, TARGET_ID), EDITOR_ID);
    ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);
    verify(processesStorage).saveWorkFlow(saved.capture(), eq(EDITOR_ID));
    assertEquals(List.of(63L), saved.getValue().getRequesterFormIds(), "the stored choice, re-checked against the new project");

    WorkFlow ticked = update(ProjectMode.EXISTING, TARGET_ID);
    ticked.setRequesterFormIds(Arrays.asList(64L, 63L, 63L));
    processesService.updateWorkFlow(ticked, EDITOR_ID);
    verify(processesStorage, org.mockito.Mockito.times(2)).saveWorkFlow(saved.capture(), eq(EDITOR_ID));
    assertEquals(List.of(63L), saved.getValue().getRequesterFormIds(), "the ticked forms, re-checked against the new project");

    processesService.updateWorkFlow(update(ProjectMode.NEW, 0), EDITOR_ID);
    verify(processesStorage, org.mockito.Mockito.times(3)).saveWorkFlow(saved.capture(), eq(EDITOR_ID));
    assertEquals(List.of(), saved.getValue().getRequesterFormIds(), "a new project gives no form yet");
  }

  /**
   * An editor who may not edit the process changes nothing, whatever project
   * the update names. Mutation: check the right after the project and the
   * project's statuses are created.
   */
  @Test
  void anEditorWhoMayNotEditTheProcessChangesNothing() {
    storedWorkFlow.setAcl(new ProcessPermission(true, false, false, true));
    assertThrows(IllegalAccessException.class,
                 () -> processesService.updateWorkFlow(update(ProjectMode.EXISTING, TARGET_ID), EDITOR_ID));
    verify(statusService, never()).createStatus(any(ProjectDto.class), anyString());
    verify(processesStorage, never()).saveWorkFlow(any(), anyLong());
    verify(processesStorage, never()).requestProjectMove(anyLong(), anyString(), anyLong());
  }

  /**
   * Builds an update of the stored process, renamed.
   *
   * @param mode the project mode sent, or null
   * @param projectId the project id sent
   * @return the update
   */
  private WorkFlow update(ProjectMode mode, long projectId) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(WORKFLOW_ID);
    workFlow.setTitle("Leave request");
    workFlow.setProjectMode(mode);
    workFlow.setProjectId(projectId);
    return workFlow;
  }

  /**
   * Mocks a space.
   *
   * @param id the space id
   * @param groupId its group id
   */
  private void space(String id, String groupId) {
    Space space = new Space();
    space.setId(id);
    space.setGroupId(groupId);
    when(spaceService.getSpaceById(id)).thenReturn(space);
    when(spaceService.getSpaceByGroupId(groupId)).thenReturn(space);
  }

  /**
   * Mocks a task project attached to a space.
   *
   * @param id the project id
   * @param spaceGroupId the space's group id
   * @return the project
   */
  private ProjectDto project(long id, String spaceGroupId) throws Exception {
    ProjectDto project = new ProjectDto();
    project.setId(id);
    project.setName("Project " + id);
    project.setManager(new HashSet<>(Set.of("manager:" + spaceGroupId)));
    project.setParticipator(new HashSet<>(Set.of("member:" + spaceGroupId)));
    when(projectService.getProject(id)).thenReturn(project);
    return project;
  }

  /**
   * Builds the columns of a project.
   *
   * @param projectId the project id
   * @param names the column names, in their order
   * @return the columns
   */
  private static List<StatusDto> statuses(long projectId, String... names) {
    List<StatusDto> statuses = new ArrayList<>();
    long id = projectId * 100;
    for (String name : names) {
      StatusDto status = new StatusDto();
      status.setId(id++);
      status.setName(name);
      statuses.add(status);
    }
    return statuses;
  }

  /**
   * Mocks an active user and their memberships.
   *
   * @param identityId the identity id
   * @param username the user name
   * @param memberships the memberships
   */
  private void mockUser(long identityId, String username, MembershipEntry... memberships) {
    Identity identity = new Identity(OrganizationIdentityProvider.NAME, username);
    identity.setId(String.valueOf(identityId));
    identity.setEnable(true);
    when(identityManager.getIdentity(identityId)).thenReturn(identity);
    when(userACL.getUserIdentity(username)).thenReturn(new org.exoplatform.services.security.Identity(username,
                                                                                                      Arrays.asList(memberships)));
  }
}
