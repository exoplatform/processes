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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.exoplatform.processes.model.WorkFlowLabel;
import org.exoplatform.task.dto.LabelDto;
import org.mockito.InOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.task.service.LabelService;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.constant.ProjectMode;
import org.exoplatform.processes.model.EligibleProject;
import org.exoplatform.processes.model.EligibleProjects;
import org.exoplatform.processes.model.ProcessReference;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.services.security.MembershipEntry;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dao.OrderBy;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.StatusDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.StatusService;

/**
 * The choice of a process's task project at creation (EXO-91987): which
 * projects a space offers, who may list them, and how a process is created in
 * an existing project.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ProcessesServiceProjectChoiceTest {

  private static final String        SPACE_ID         = "3";

  private static final String        SPACE_GROUP      = "/spaces/hr";

  private static final String        SPACE_MANAGER    = "manager:" + SPACE_GROUP;

  private static final String        SPACE_MEMBER     = "member:" + SPACE_GROUP;

  private static final long          ADMIN_ID         = 1L;

  private static final long          SPACE_MANAGER_ID = 2L;

  private static final long          MEMBER_ID        = 3L;

  private static final long          ATTACHED_ID      = 10L;

  private static final long          ATTACHED_2_ID    = 11L;

  private static final long          TASKS_APP_ID     = 12L;

  private static final long          OWN_COLUMNS_ID      = 13L;

  private static final long          OTHER_GROUP_ID   = 14L;

  @Mock
  private LabelService labelService;

  @Mock
  private ListenerService listenerService;

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
  private ProcessTranslationService processTranslationService;

  private ProcessesServiceImpl       processesService;

  private Space                      space;

  /**
   * Builds a space, its users and five projects: two attached to the space, one
   * created from the Tasks app with the space among its participants, one
   * attached but with a column named « request », one whose participants hold
   * another group.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Before
  public void setUp() throws Exception {
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
    // The save of a process's rows runs in a transaction the platform aspect
    // begins; the unit tests call its body directly
    doAnswer(invocation -> processesService.writeWorkFlowRows(invocation.getArgument(0),
                                                              invocation.getArgument(1))).when(processesService)
                                                                                         .saveWorkFlowRows(any(), anyLong());
    when(processesStorage.createProject(any())).thenAnswer(invocation -> invocation.getArgument(0));
    space = new Space();
    space.setId(SPACE_ID);
    space.setGroupId(SPACE_GROUP);
    when(spaceService.getSpaceById(SPACE_ID)).thenReturn(space);
    mockUser(ADMIN_ID, "admin", new MembershipEntry("/platform/processes", "*"));
    mockUser(SPACE_MANAGER_ID, "spacemanager", new MembershipEntry(SPACE_GROUP, "manager"));
    mockUser(MEMBER_ID, "member", new MembershipEntry(SPACE_GROUP, "member"));
    when(spaceService.isManager(space, "spacemanager")).thenReturn(true);

    ProjectDto attached = project(ATTACHED_ID, "B project", Set.of(SPACE_MANAGER, "john"), Set.of(SPACE_MEMBER, "mary"));
    ProjectDto attached2 = project(ATTACHED_2_ID, "A project", Set.of(SPACE_MANAGER), Set.of(SPACE_MEMBER));
    ProjectDto tasksApp = project(TASKS_APP_ID, "Tasks app project", Set.of("john"), Set.of(SPACE_MEMBER));
    ProjectDto conflict = project(OWN_COLUMNS_ID, "Own columns", Set.of(SPACE_MANAGER), Set.of(SPACE_MEMBER));
    ProjectDto otherGroup = project(OTHER_GROUP_ID, "Other group", Set.of(SPACE_MANAGER), Set.of(SPACE_MEMBER, "member:/spaces/it"));
    when(projectService.findProjects(eq(Arrays.asList(SPACE_MANAGER, SPACE_MEMBER)), isNull(), any(OrderBy.class), eq(0), eq(0)))
                                                                                                                         .thenReturn(List.of(attached2,
                                                                                                                                             attached,
                                                                                                                                             tasksApp,
                                                                                                                                             conflict,
                                                                                                                                             otherGroup));
    for (ProjectDto project : List.of(attached, attached2, tasksApp, conflict, otherGroup)) {
      when(projectService.getProject(project.getId())).thenReturn(project);
    }
    when(statusService.getStatuses(anyLong())).thenReturn(statuses(ATTACHED_ID, "To Do", "Done"));
    when(statusService.getStatuses(OWN_COLUMNS_ID)).thenReturn(statuses(OWN_COLUMNS_ID, "To Do", "request"));
    when(processesStorage.saveWorkFlow(any(WorkFlow.class), anyLong())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  /**
   * A project is attached to a space when its managers hold the space's
   * manager membership and no group membership but the space's two is among
   * its managers and participants; users are allowed. Mutation: drop either
   * condition and an assertion fails.
   */
  @Test
  public void aProjectIsAttachedToTheSpaceOfItsManagerMembershipOnly() {
    assertTrue(ProcessesServiceImpl.isAttachedToSpace(project(1, "p", Set.of(SPACE_MANAGER), Set.of(SPACE_MEMBER)), SPACE_GROUP));
    assertTrue("users added one by one are allowed",
               ProcessesServiceImpl.isAttachedToSpace(project(1, "p", Set.of(SPACE_MANAGER, "john"), Set.of(SPACE_MEMBER, "mary")),
                                                      SPACE_GROUP));
    assertFalse("a Tasks app project with the space among its participants",
                ProcessesServiceImpl.isAttachedToSpace(project(1, "p", Set.of("john"), Set.of(SPACE_MEMBER)), SPACE_GROUP));
    assertFalse("another space's manager membership",
                ProcessesServiceImpl.isAttachedToSpace(project(1, "p", Set.of(SPACE_MANAGER, "manager:/spaces/it"), Set.of(SPACE_MEMBER)),
                                                       SPACE_GROUP));
    assertFalse("another group among the participants",
                ProcessesServiceImpl.isAttachedToSpace(project(1, "p", Set.of(SPACE_MANAGER), Set.of(SPACE_MEMBER, "/platform/users")),
                                                       SPACE_GROUP));
    assertFalse("a space whose group id merely starts like this one",
                ProcessesServiceImpl.isAttachedToSpace(project(1, "p", Set.of("manager:/spaces/hr_team"), Set.of()), SPACE_GROUP));
  }

  /**
   * A space manager lists the projects attached to the space, whatever their
   * columns, in the order Task returns them, each with the processes of that
   * space feeding it. Mutation: drop the attachment filter and the listing
   * holds an extra project; filter on the columns again and the project with
   * its own columns is left out.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aSpaceManagerListsTheEligibleProjectsWithTheirProcesses() throws Exception {
    when(processesStorage.findProcessesOfProjects(List.of(ATTACHED_2_ID, ATTACHED_ID, OWN_COLUMNS_ID), 3L))
                                                                                         .thenReturn(Map.of(ATTACHED_ID,
                                                                                                            List.of(new ProcessReference(7L,
                                                                                                                                         "Expense report"))));

    EligibleProjects projects = processesService.listEligibleProjects(SPACE_ID, 0, 0, SPACE_MANAGER_ID);

    assertEquals(3, projects.getSize());
    assertEquals(List.of(ATTACHED_2_ID, ATTACHED_ID, OWN_COLUMNS_ID),
                 projects.getProjects().stream().map(EligibleProject::getId).toList());
    assertEquals(List.of(), projects.getProjects().get(0).getWorkflows());
    assertEquals(List.of(new ProcessReference(7L, "Expense report")), projects.getProjects().get(1).getWorkflows());
  }

  /**
   * A page is cut after the eligibility filter, so it is never short while
   * eligible projects remain, and the size counts every eligible project.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aPageIsCutAfterTheFilter() throws Exception {
    EligibleProjects page = processesService.listEligibleProjects(SPACE_ID, 1, 1, ADMIN_ID);

    assertEquals(3, page.getSize());
    assertEquals(List.of(ATTACHED_ID), page.getProjects().stream().map(EligibleProject::getId).toList());
    assertEquals(List.of(), processesService.listEligibleProjects(SPACE_ID, 5, 1, ADMIN_ID).getProjects());
    assertEquals(List.of(OWN_COLUMNS_ID),
                 processesService.listEligibleProjects(SPACE_ID, 2, 1, ADMIN_ID).getProjects().stream().map(EligibleProject::getId).toList());
  }

  /**
   * Only a processes administrator or a manager of the space lists its
   * projects, and an unknown space answers not found. Mutation: drop the gate
   * and a space member lists them.
   */
  @Test
  public void onlyAnAdministratorOrASpaceManagerListsTheProjects() {
    assertThrows(IllegalAccessException.class, () -> processesService.listEligibleProjects(SPACE_ID, 0, 0, MEMBER_ID));
    assertThrows(ObjectNotFoundException.class, () -> processesService.listEligibleProjects("99", 0, 0, ADMIN_ID));
    assertThrows(ObjectNotFoundException.class, () -> processesService.listEligibleProjects(null, 0, 0, ADMIN_ID));
    verify(processesStorage, never()).findProcessesOfProjects(any(), anyLong());
  }

  /**
   * A process created in an existing eligible project keeps that project and
   * leaves its columns as they are, two of them here, before the process is
   * saved (EXO-94923). Mutation: force the project id to 0 and the saved
   * project changes; add the former process columns again and the never()
   * fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aProcessCreatedInAnExistingProjectKeepsItAndItsColumns() throws Exception {
    when(statusService.getStatuses(ATTACHED_ID)).thenReturn(statuses(ATTACHED_ID, "To Do", "Done"));

    processesService.createWorkFlow(newWorkFlow(ProjectMode.EXISTING, ATTACHED_ID), ADMIN_ID);

    ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);
    verify(processesStorage).saveWorkFlow(saved.capture(), eq(ADMIN_ID));
    assertEquals(ATTACHED_ID, saved.getValue().getProjectId());
    verify(statusService, never()).createStatus(nullable(ProjectDto.class), nullable(String.class));
    verify(statusService, never()).createInitialStatuses(nullable(ProjectDto.class));
  }

  /**
   * A project that is not attached to the managing space, a project that does
   * not exist, a missing project id and an unknown space are refused as not
   * eligible, before anything is written. Mutation: drop the eligibility check
   * and the process is saved.
   *
   * @throws Exception when the project lookup stub fails
   */
  @Test
  public void aProjectTheEditorWouldNotOfferIsRefused() throws Exception {
    when(projectService.getProject(404L)).thenThrow(new EntityNotFoundException(404L, ProjectDto.class));
    for (long projectId : new long[] { TASKS_APP_ID, OTHER_GROUP_ID, 0L, 404L }) {
      IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                                                      () -> processesService.createWorkFlow(newWorkFlow(ProjectMode.EXISTING,
                                                                                                        projectId),
                                                                                            ADMIN_ID));
      assertEquals("processes.project.notEligible", refusal.getMessage());
    }
    WorkFlow inAnUnknownSpace = newWorkFlow(ProjectMode.EXISTING, ATTACHED_ID);
    inAnUnknownSpace.setSpaceId("99");
    IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                                                    () -> processesService.createWorkFlow(inAnUnknownSpace, ADMIN_ID));
    assertEquals("processes.project.notEligible", refusal.getMessage());
    verify(processesStorage, never()).saveWorkFlow(any(), anyLong());
    verify(statusService, never()).createStatus(any(), anyString());
  }

  /**
   * An attached project whose columns are named like the former process
   * columns in another case is a project like any other: the process is saved
   * on it and the project gets no column (EXO-94923). Mutation: refuse such a
   * project again and the process is not saved.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aProjectWithItsOwnColumnNamesIsKeptAsItIs() throws Exception {
    processesService.createWorkFlow(newWorkFlow(ProjectMode.EXISTING, OWN_COLUMNS_ID), ADMIN_ID);

    ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);
    verify(processesStorage).saveWorkFlow(saved.capture(), eq(ADMIN_ID));
    assertEquals(OWN_COLUMNS_ID, saved.getValue().getProjectId());
    verify(statusService, never()).createStatus(nullable(ProjectDto.class), nullable(String.class));
  }

  /**
   * A process created with a new project, or with no mode stated as API
   * clients send it today, never keeps a project id the client sent and reads
   * no existing project. Mutation: keep the project id for a missing mode and
   * this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aNewProjectNeverKeepsTheProjectIdSent() throws Exception {
    processesService.createWorkFlow(newWorkFlow(ProjectMode.NEW, ATTACHED_ID), ADMIN_ID);
    processesService.createWorkFlow(newWorkFlow(null, ATTACHED_ID), ADMIN_ID);

    ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);
    verify(processesStorage, org.mockito.Mockito.times(2)).saveWorkFlow(saved.capture(), eq(ADMIN_ID));
    saved.getAllValues().forEach(workFlow -> assertEquals(0L, workFlow.getProjectId()));
    verify(projectService, never()).getProject(anyLong());
    verify(statusService, never()).createStatus(any(), anyString());
  }

  /**
   * Only a processes administrator creates a process, in an existing project
   * too, and a refused user writes no status. Mutation: check the project
   * before the user and statuses get created for a space manager.
   */
  @Test
  public void aSpaceManagerCannotCreateAProcessInAnExistingProject() {
    assertThrows(IllegalAccessException.class,
                 () -> processesService.createWorkFlow(newWorkFlow(ProjectMode.EXISTING, ATTACHED_ID), SPACE_MANAGER_ID));
    verify(statusService, never()).createStatus(any(), anyString());
    verify(processesStorage, never()).saveWorkFlow(any(), anyLong());
  }

  /**
   * An existing project gives the process its labels: a label sent with an id
   * is the project's stored label, whatever name the client sent; a name the
   * project has already, case and spaces aside, reuses that label; a name it
   * lacks is created there once, by the saving user, with no color, after the
   * project's columns, left as they are, and before the process's rows are saved, and
   * the creation is broadcast after the save. Mutation: trust the client's
   * name, compare names with their case, create the label twice, or
   * broadcast before the save or after the links, and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void anExistingProjectGivesItsLabelsAndGetsTheMissingOnes() throws Exception {
    LabelDto holiday = label(101L, "Holiday", ATTACHED_ID);
    LabelDto urgent = label(102L, "Urgent", ATTACHED_ID);
    when(labelService.getLabel(101L)).thenReturn(holiday);
    when(labelService.findLabelsByProject(eq(ATTACHED_ID), any(), eq(0), eq(0))).thenReturn(List.of(urgent, holiday));
    when(labelService.createLabel(any(LabelDto.class))).thenAnswer(invocation -> {
      LabelDto created = invocation.getArgument(0);
      created.setId(103L);
      return created;
    });
    WorkFlow workFlow = newWorkFlow(ProjectMode.EXISTING, ATTACHED_ID);
    workFlow.setLabels(List.of(new WorkFlowLabel(101L, "renamed by the client"),
                               new WorkFlowLabel(0L, " urgent "),
                               new WorkFlowLabel(0L, "New one"),
                               new WorkFlowLabel(0L, "new ONE")));

    processesService.createWorkFlow(workFlow, ADMIN_ID);

    ArgumentCaptor<LabelDto> created = ArgumentCaptor.forClass(LabelDto.class);
    verify(labelService, times(1)).createLabel(created.capture());
    assertEquals("New one", created.getValue().getName());
    assertEquals("admin", created.getValue().getUsername());
    assertEquals(ATTACHED_ID, created.getValue().getProject().getId());
    assertNull(created.getValue().getColor());
    ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);
    verify(processesStorage).saveWorkFlow(saved.capture(), eq(ADMIN_ID));
    assertEquals(List.of(new WorkFlowLabel(101L, "Holiday"), new WorkFlowLabel(102L, "Urgent"), new WorkFlowLabel(103L, "New one")),
                 saved.getValue().getLabels());
    verify(statusService, never()).createStatus(nullable(ProjectDto.class), nullable(String.class));
    InOrder order = inOrder(labelService, processesService, listenerService, processesStorage);
    order.verify(labelService).createLabel(any());
    order.verify(processesService).saveWorkFlowRows(any(), eq(ADMIN_ID));
    // Broadcast once the rows are committed, before the attachments' links,
    // which a failure must not keep from being announced
    order.verify(listenerService).broadcast(eq("exo.process.created"), any(), any());
    order.verify(processesStorage).linkWorkFlowAttachments(any(), any(), eq(ADMIN_ID));
  }

  /**
   * Every label is checked before the first write: a label of another
   * project, a label deleted in Tasks, a blank name and a name longer than a
   * Task label are refused with their code, and neither a status, nor a label,
   * nor the process is written. Mutation: check the labels after the
   * project's statuses, drop the project check or the length check, and this
   * test fails.
   */
  @Test
  public void aLabelIsCheckedBeforeAnyWrite() throws Exception {
    when(labelService.getLabel(201L)).thenReturn(label(201L, "Elsewhere", ATTACHED_2_ID));
    assertRefused(new WorkFlowLabel(201L, "Elsewhere"), "processes.label.notInProject");
    assertRefused(new WorkFlowLabel(202L, "Deleted"), "processes.label.notInProject");
    assertRefused(new WorkFlowLabel(0L, "   "), "processes.label.invalidName");
    assertRefused(new WorkFlowLabel(0L, "x".repeat(51)), "processes.label.invalidName");

    verify(statusService, never()).createStatus(any(), any());
    verify(labelService, never()).createLabel(any());
    verify(processesStorage, never()).saveWorkFlow(any(), anyLong());
    verify(listenerService, never()).broadcast(any(String.class), any(), any());
  }

  /**
   * A new project has no label to choose: an id is refused before the project
   * is created, and the names are created in the new project once it exists.
   * A name of 50 characters is accepted. Mutation: accept an id in NEW mode,
   * or create the labels before the project, and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aNewProjectGetsTheNamedLabelsOnceCreated() throws Exception {
    when(labelService.getLabel(101L)).thenReturn(label(101L, "Holiday", ATTACHED_ID));
    WorkFlow withId = newWorkFlow(ProjectMode.NEW, 0);
    withId.setLabels(List.of(new WorkFlowLabel(101L, "Holiday")));
    IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                                                    () -> processesService.createWorkFlow(withId, ADMIN_ID));
    assertEquals("processes.label.notInProject", refusal.getMessage());
    verify(processesStorage, never()).createProject(any());

    ProjectDto newProject = project(30L, "Leave request", Set.of(SPACE_MANAGER), Set.of(SPACE_MEMBER));
    when(projectService.getProject(30L)).thenReturn(newProject);
    when(processesStorage.createProject(any())).thenAnswer(invocation -> {
      WorkFlow created = invocation.getArgument(0);
      created.setProjectId(30L);
      return created;
    });
    when(labelService.createLabel(any(LabelDto.class))).thenAnswer(invocation -> {
      LabelDto created = invocation.getArgument(0);
      created.setId(301L);
      return created;
    });
    String name = "y".repeat(50);
    WorkFlow named = newWorkFlow(ProjectMode.NEW, 0);
    named.setLabels(List.of(new WorkFlowLabel(0L, name)));

    processesService.createWorkFlow(named, ADMIN_ID);

    ArgumentCaptor<LabelDto> created = ArgumentCaptor.forClass(LabelDto.class);
    InOrder order = inOrder(processesStorage, labelService);
    order.verify(processesStorage).createProject(any());
    order.verify(labelService).createLabel(created.capture());
    assertEquals(30L, created.getValue().getProject().getId());
    ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);
    verify(processesStorage).saveWorkFlow(saved.capture(), eq(ADMIN_ID));
    assertEquals(List.of(new WorkFlowLabel(301L, name)), saved.getValue().getLabels());
  }

  /**
   * A save that fails once Task created a new process's project propagates
   * its failure, after logging the project it leaves, and neither links
   * attachments nor broadcasts the creation. Mutation: swallow the failure or
   * broadcast before the save and this test fails.
   *
   * @throws Exception never
   */
  @Test
  public void aFailingSaveAfterANewProjectPropagatesAndBroadcastsNothing() throws Exception {
    doThrow(new IllegalStateException("the rows fail")).when(processesService).saveWorkFlowRows(any(), anyLong());

    assertThrows(IllegalStateException.class, () -> processesService.createWorkFlow(newWorkFlow(ProjectMode.NEW, 0), ADMIN_ID));

    verify(processesStorage).createProject(any());
    verify(processesStorage, never()).linkWorkFlowAttachments(any(), any(), anyLong());
    verify(listenerService, never()).broadcast(any(String.class), any(), any());
  }

  /**
   * Lists the labels of an eligible project of the space, by id, to a
   * processes administrator or a manager of the space only; a project the
   * space's processes may not feed is refused. Mutation: drop the gate or the
   * eligibility check and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void theEditorListsTheLabelsOfAnEligibleProjectOnly() throws Exception {
    when(labelService.findLabelsByProject(eq(ATTACHED_ID), any(), eq(0), eq(0))).thenReturn(List.of(label(102L, "Urgent", ATTACHED_ID),
                                                                                                    label(101L, "Holiday", ATTACHED_ID)));

    assertEquals(List.of(new WorkFlowLabel(101L, "Holiday"), new WorkFlowLabel(102L, "Urgent")),
                 processesService.listProjectLabels(SPACE_ID, ATTACHED_ID, SPACE_MANAGER_ID));
    assertThrows(IllegalAccessException.class, () -> processesService.listProjectLabels(SPACE_ID, ATTACHED_ID, MEMBER_ID));
    assertThrows(ObjectNotFoundException.class, () -> processesService.listProjectLabels("99", ATTACHED_ID, ADMIN_ID));
    IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                                                    () -> processesService.listProjectLabels(SPACE_ID, TASKS_APP_ID, ADMIN_ID));
    assertEquals("processes.project.notEligible", refusal.getMessage());
  }

  /**
   * The save of a process's rows is the one atomic method of a process save,
   * which the edit's move state and the Forms binding join later. Mutation:
   * drop atomic = true and this test fails.
   *
   * @throws Exception never
   */
  @Test
  public void theSaveOfAProcessRowsIsAtomic() throws Exception {
    io.meeds.common.ContainerTransactional transactional =
                                                         ProcessesServiceImpl.class.getMethod("saveWorkFlowRows", WorkFlow.class, long.class)
                                                                                   .getAnnotation(io.meeds.common.ContainerTransactional.class);
    assertTrue(transactional != null && transactional.atomic());
  }

  /**
   * Asserts a process with one label is refused with a code.
   *
   * @param label the label
   * @param code the expected code
   */
  private void assertRefused(WorkFlowLabel label, String code) {
    WorkFlow workFlow = newWorkFlow(ProjectMode.EXISTING, ATTACHED_ID);
    workFlow.setLabels(List.of(label));
    IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                                                    () -> processesService.createWorkFlow(workFlow, ADMIN_ID));
    assertEquals(code, refusal.getMessage());
  }

  /**
   * Builds a Task label of a project.
   *
   * @param id its id
   * @param name its name
   * @param projectId its project id
   * @return the label
   */
  private static LabelDto label(long id, String name, long projectId) {
    ProjectDto project = new ProjectDto();
    project.setId(projectId);
    LabelDto label = new LabelDto();
    label.setId(id);
    label.setName(name);
    label.setProject(project);
    return label;
  }

  /**
   * Builds a new process of the test space.
   *
   * @param projectMode the project mode
   * @param projectId the project id the client sends
   * @return the process
   */
  private WorkFlow newWorkFlow(ProjectMode projectMode, long projectId) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setTitle("Leave request");
    workFlow.setSpaceId(SPACE_ID);
    workFlow.setProjectId(projectId);
    workFlow.setProjectMode(projectMode);
    workFlow.setRequestsCreators(new ArrayList<>());
    return workFlow;
  }

  /**
   * Builds a task project.
   *
   * @param id its id
   * @param name its name
   * @param managers its manager entries
   * @param participators its participant entries
   * @return the project
   */
  private static ProjectDto project(long id, String name, Set<String> managers, Set<String> participators) {
    ProjectDto project = new ProjectDto();
    project.setId(id);
    project.setName(name);
    project.setManager(new HashSet<>(managers));
    project.setParticipator(new HashSet<>(participators));
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
