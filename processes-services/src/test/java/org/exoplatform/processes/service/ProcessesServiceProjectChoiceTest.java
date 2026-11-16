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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.exception.ObjectNotFoundException;
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

  private static final long          CONFLICT_ID      = 13L;

  private static final long          OTHER_GROUP_ID   = 14L;

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
    processesService = new ProcessesServiceImpl(processesStorage,
                                                projectService,
                                                identityManager,
                                                userACL,
                                                spaceService,
                                                processesAttachmentService,
                                                statusService);
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
    ProjectDto conflict = project(CONFLICT_ID, "Conflict", Set.of(SPACE_MANAGER), Set.of(SPACE_MEMBER));
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
    when(statusService.getStatuses(CONFLICT_ID)).thenReturn(statuses(CONFLICT_ID, "To Do", "request"));
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
   * A space manager lists the projects attached to the space, less the one
   * with a column named like a process status in another case, in the order
   * Task returns them, each with the processes of that space feeding it.
   * Mutation: drop the attachment filter or the column check and the listing
   * holds an extra project.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aSpaceManagerListsTheEligibleProjectsWithTheirProcesses() throws Exception {
    when(processesStorage.findProcessesOfProjects(List.of(ATTACHED_2_ID, ATTACHED_ID), 3L))
                                                                                         .thenReturn(Map.of(ATTACHED_ID,
                                                                                                            List.of(new ProcessReference(7L,
                                                                                                                                         "Expense report"))));

    EligibleProjects projects = processesService.listEligibleProjects(SPACE_ID, 0, 0, SPACE_MANAGER_ID);

    assertEquals(2, projects.getSize());
    assertEquals(List.of(ATTACHED_2_ID, ATTACHED_ID), projects.getProjects().stream().map(EligibleProject::getId).toList());
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

    assertEquals(2, page.getSize());
    assertEquals(List.of(ATTACHED_ID), page.getProjects().stream().map(EligibleProject::getId).toList());
    assertEquals(List.of(), processesService.listEligibleProjects(SPACE_ID, 5, 1, ADMIN_ID).getProjects());
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
   * A process created in an existing eligible project keeps that project, and
   * only the process statuses the project lacks are added to it, before the
   * process is saved. Mutation: force the project id to 0, or create every
   * status, and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aProcessCreatedInAnExistingProjectKeepsItAndGetsTheMissingStatuses() throws Exception {
    when(statusService.getStatuses(ATTACHED_ID)).thenReturn(statuses(ATTACHED_ID, "To Do", "Request", "Validated"));

    processesService.createWorkFlow(newWorkFlow(ProjectMode.EXISTING, ATTACHED_ID), ADMIN_ID);

    ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);
    verify(processesStorage).saveWorkFlow(saved.capture(), eq(ADMIN_ID));
    assertEquals(ATTACHED_ID, saved.getValue().getProjectId());
    ArgumentCaptor<String> created = ArgumentCaptor.forClass(String.class);
    verify(statusService, org.mockito.Mockito.times(3)).createStatus(any(ProjectDto.class), created.capture());
    assertEquals(List.of("RequestInProgress", "Refused", "Canceled"), created.getAllValues());
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
   * An attached project with a column named like a process status in another
   * case is refused with its own code, and gets no status. Mutation: drop the
   * column check and the process is saved.
   *
   */
  @Test
  public void aProjectWithAColumnNamedLikeAStatusInAnotherCaseIsRefused() {
    IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                                                    () -> processesService.createWorkFlow(newWorkFlow(ProjectMode.EXISTING,
                                                                                                      CONFLICT_ID),
                                                                                          ADMIN_ID));

    assertEquals("processes.project.statusNameConflict", refusal.getMessage());
    verify(processesStorage, never()).saveWorkFlow(any(), anyLong());
    verify(statusService, never()).createStatus(any(), anyString());
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
