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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.meeds.common.ContainerTransactional;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.constant.ProjectMode;
import org.exoplatform.processes.model.ProcessPermission;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.plugin.RequestValuesPlugin;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.MembershipEntry;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.service.LabelService;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.StatusService;

/**
 * The form a process's save chooses for its requests: checked by the request
 * values plugin before any write of the save, bound by it inside the atomic
 * save once the process's row is written, and counted as a change of its own.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ProcessesServiceProcessFormTest {

  private static final String        SPACE_ID    = "3";

  private static final String        SPACE_GROUP = "/spaces/hr";

  private static final long          ADMIN_ID    = 1L;

  private static final long          EDITOR_ID   = 2L;

  private static final long          PROJECT_ID  = 10L;

  private static final long          WORKFLOW_ID = 7L;

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
  private LabelService               labelService;

  @Mock
  private ListenerService            listenerService;

  @Mock
  private ProcessTranslationService processTranslationService;

  @Mock
  private RequestValuesPlugin        plugin;

  private ProcessesServiceImpl       processesService;

  /**
   * Builds the service with the plugin registered, an administrator and an
   * editor of the process, and a stored process of the space.
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
    // The platform aspect begins the transaction of the atomic save; the unit
    // tests run its un-advised body
    doAnswer(invocation -> processesService.writeWorkFlowRows(invocation.getArgument(0),
                                                              invocation.getArgument(1))).when(processesService)
                                                                                         .saveWorkFlowRows(any(), anyLong());
    processesService.addRequestValuesPlugin(plugin);
    when(processesStorage.createProject(any())).thenAnswer(invocation -> {
      WorkFlow workFlow = invocation.getArgument(0);
      workFlow.setProjectId(PROJECT_ID);
      return workFlow;
    });
    when(processesStorage.saveWorkFlow(any(WorkFlow.class), anyLong())).thenAnswer(invocation -> {
      WorkFlow workFlow = invocation.getArgument(0);
      if (workFlow.getId() == 0) {
        workFlow.setId(WORKFLOW_ID);
      }
      return workFlow;
    });
    Space space = new Space();
    space.setId(SPACE_ID);
    space.setGroupId(SPACE_GROUP);
    when(spaceService.getSpaceById(SPACE_ID)).thenReturn(space);
    mockUser(ADMIN_ID, "admin", new MembershipEntry("/platform/processes", "*"));
    mockUser(EDITOR_ID, "editor", new MembershipEntry(SPACE_GROUP, "manager"));
    ProjectDto project = new ProjectDto();
    project.setId(PROJECT_ID);
    project.setManager(new HashSet<>(Set.of("manager:" + SPACE_GROUP)));
    project.setParticipator(new HashSet<>(Set.of("member:" + SPACE_GROUP)));
    when(projectService.getProject(PROJECT_ID)).thenReturn(project);
    when(statusService.getStatuses(anyLong())).thenReturn(new ArrayList<>());
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, EDITOR_ID)).thenReturn(storedWorkFlow(true));
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, ADMIN_ID)).thenReturn(storedWorkFlow(true));
  }

  /**
   * A new process's form is checked with no process id and the space the save
   * sends, before Task creates the project; it is bound to the process once its
   * row is written. Mutation: check the form after the project's creation, or
   * bind it before the row, and the order fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aNewProcessFormIsCheckedBeforeAnyWriteAndBoundAfterTheRow() throws Exception {
    WorkFlow workFlow = newWorkFlow(ProjectMode.NEW);
    workFlow.setRequestFormId(5L);

    processesService.createWorkFlow(workFlow, ADMIN_ID);

    InOrder order = inOrder(plugin, processesStorage);
    order.verify(plugin).checkProcessForm(isNull(), eq(SPACE_ID), eq(5L), any(Identity.class));
    order.verify(processesStorage).createProject(any());
    order.verify(processesStorage).saveWorkFlow(any(), eq(ADMIN_ID));
    order.verify(plugin).setProcessForm(eq(WORKFLOW_ID), eq(5L), any(Identity.class));
  }

  /**
   * A form the plugin refuses fails the whole save before its first write: no
   * project, no status, no row. Mutation: check the form after the Task writes
   * and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aRefusedFormWritesNothing() throws Exception {
    doThrow(new IllegalArgumentException("forms.form.notApplicable")).when(plugin)
                                                                       .checkProcessForm(nullable(Long.class),
                                                                                         nullable(String.class),
                                                                                         any(),
                                                                                         any());
    WorkFlow newProcess = newWorkFlow(ProjectMode.NEW);
    newProcess.setRequestFormId(5L);
    WorkFlow existingProject = newWorkFlow(ProjectMode.EXISTING);
    existingProject.setProjectId(PROJECT_ID);
    existingProject.setRequestFormId(5L);

    assertEquals("forms.form.notApplicable",
                 assertThrows(IllegalArgumentException.class,
                              () -> processesService.createWorkFlow(newProcess, ADMIN_ID)).getMessage());
    assertEquals("forms.form.notApplicable",
                 assertThrows(IllegalArgumentException.class,
                              () -> processesService.createWorkFlow(existingProject, ADMIN_ID)).getMessage());

    verify(processesStorage, never()).createProject(any());
    verify(statusService, never()).createStatus(any(), any());
    verify(processesStorage, never()).saveWorkFlow(any(), anyLong());
    verify(plugin, never()).setProcessForm(anyLong(), nullable(Long.class), any());
  }

  /**
   * A new process saved without a form, or with no form, never asks the
   * plugin. Mutation: bind « no form » on a new process and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aNewProcessWithoutAFormDoesNotAskThePlugin() throws Exception {
    WorkFlow noChoice = newWorkFlow(ProjectMode.NEW);
    WorkFlow noForm = newWorkFlow(ProjectMode.NEW);
    noForm.setRequestFormId(0L);

    processesService.createWorkFlow(noChoice, ADMIN_ID);
    processesService.createWorkFlow(noForm, ADMIN_ID);

    verifyNoInteractions(plugin);
  }

  /**
   * With no plugin registered, a process has no form: a form id is refused
   * before any write, « no form » is accepted. Mutation: accept the form id
   * and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void withNoPluginAFormIsRefusedAndNoFormIsAccepted() throws Exception {
    processesService.removeRequestValuesPlugin(plugin);
    WorkFlow withForm = newWorkFlow(ProjectMode.NEW);
    withForm.setRequestFormId(5L);
    WorkFlow noForm = newWorkFlow(ProjectMode.NEW);
    noForm.setRequestFormId(0L);

    assertEquals("processes.workflow.formUnsupported",
                 assertThrows(IllegalArgumentException.class,
                              () -> processesService.createWorkFlow(withForm, ADMIN_ID)).getMessage());
    verify(processesStorage, never()).createProject(any());

    processesService.createWorkFlow(noForm, ADMIN_ID);
    verify(processesStorage).saveWorkFlow(any(), eq(ADMIN_ID));
  }

  /**
   * An update whose only change is the form saves it; one that sends the
   * process's own form and nothing else is refused as having no change, and
   * asks no right of the plugin. Mutation: count the same form as a change, or
   * drop the form from the no-change guard, and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void anUpdateWhoseOnlyChangeIsTheFormSavesIt() throws Exception {
    when(plugin.getProcessFormIds(WORKFLOW_ID)).thenReturn(List.of(5L));

    WorkFlow sameForm = storedWorkFlow(true);
    sameForm.setRequestFormId(5L);
    assertEquals("there are no changes to save",
                 assertThrows(IllegalArgumentException.class,
                              () -> processesService.updateWorkFlow(sameForm, EDITOR_ID)).getMessage());
    verify(plugin, never()).checkProcessForm(any(), any(), any(), any());

    WorkFlow otherForm = storedWorkFlow(true);
    otherForm.setRequestFormId(6L);
    processesService.updateWorkFlow(otherForm, EDITOR_ID);

    InOrder order = inOrder(plugin, processesStorage);
    order.verify(plugin).checkProcessForm(eq(WORKFLOW_ID), eq(SPACE_ID), eq(6L), any(Identity.class));
    order.verify(processesStorage).saveWorkFlow(any(), eq(EDITOR_ID));
    order.verify(plugin).setProcessForm(eq(WORKFLOW_ID), eq(6L), any(Identity.class));
  }

  /**
   * A process bound to a form, saved with 0, ends with no form; saved with its
   * own form unchanged and a title edit, keeps its form without any binding
   * write. Mutation: write the binding when the form is unchanged, or pass 0
   * as a form id, and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void zeroRemovesTheFormAndAnUnchangedFormIsKept() throws Exception {
    when(plugin.getProcessFormIds(WORKFLOW_ID)).thenReturn(List.of(5L));

    WorkFlow noForm = storedWorkFlow(true);
    noForm.setRequestFormId(0L);
    processesService.updateWorkFlow(noForm, EDITOR_ID);
    verify(plugin).checkProcessForm(eq(WORKFLOW_ID), eq(SPACE_ID), isNull(), any(Identity.class));
    verify(plugin).setProcessForm(eq(WORKFLOW_ID), isNull(), any(Identity.class));

    WorkFlow titleEdit = storedWorkFlow(true);
    titleEdit.setTitle("Renamed");
    titleEdit.setRequestFormId(5L);
    processesService.updateWorkFlow(titleEdit, EDITOR_ID);
    verify(plugin).setProcessForm(anyLong(), nullable(Long.class), any());
  }

  /**
   * A process with several own forms is never treated as unchanged: the plugin
   * is asked, and refuses an editor's choice there. Mutation: compare with the
   * first own form only and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aProcessWithSeveralFormsAsksThePlugin() throws Exception {
    when(plugin.getProcessFormIds(WORKFLOW_ID)).thenReturn(List.of(5L, 6L));
    doThrow(new IllegalArgumentException("forms.scope.severalForms")).when(plugin)
                                                                       .checkProcessForm(eq(WORKFLOW_ID), any(), eq(5L), any());
    WorkFlow workFlow = storedWorkFlow(true);
    workFlow.setRequestFormId(5L);

    assertEquals("forms.scope.severalForms",
                 assertThrows(IllegalArgumentException.class,
                              () -> processesService.updateWorkFlow(workFlow, EDITOR_ID)).getMessage());
    verify(processesStorage, never()).saveWorkFlow(any(), anyLong());
  }

  /**
   * A refusal of the binding inside the save propagates out of the atomic
   * method, after the row was written, so the platform aspect rolls the row
   * back with it; the method carries the atomic transaction. Mutation: catch
   * the refusal, or drop the annotation or its atomic flag, and this test
   * fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aRefusedBindingInsideTheSavePropagatesFromTheAtomicMethod() throws Exception {
    doThrow(new IllegalAccessException("raced")).when(plugin).setProcessForm(anyLong(), nullable(Long.class), any());
    WorkFlow workFlow = storedWorkFlow(true);
    workFlow.setRequestFormId(6L);

    assertThrows(IllegalAccessException.class, () -> processesService.writeWorkFlowRows(workFlow, EDITOR_ID));
    verify(processesStorage).saveWorkFlow(any(), eq(EDITOR_ID));

    ContainerTransactional transactional = ProcessesServiceImpl.class.getMethod("saveWorkFlowRows", WorkFlow.class, long.class)
                                                                     .getAnnotation(ContainerTransactional.class);
    assertTrue(transactional != null && transactional.atomic());
  }

  /**
   * The process's own forms are read for an editor only. Mutation: drop the
   * edit right check and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void theProcessFormsAreReadForAnEditorOnly() throws Exception {
    when(plugin.getProcessFormIds(WORKFLOW_ID)).thenReturn(List.of(5L));
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, 9L)).thenReturn(storedWorkFlow(false));
    when(processesStorage.getWorkFlowById(8L, EDITOR_ID)).thenReturn(null);

    assertEquals(List.of(5L), processesService.getProcessFormIds(WORKFLOW_ID, EDITOR_ID));
    assertThrows(IllegalAccessException.class, () -> processesService.getProcessFormIds(WORKFLOW_ID, 9L));
    assertThrows(ObjectNotFoundException.class, () -> processesService.getProcessFormIds(8L, EDITOR_ID));
  }

  /**
   * A new process in an existing project has the project forms its
   * requesters fill checked, distinct and in id order, before its first
   * write, and saved with its row; a new project stores none and asks
   * nothing. Mutation: check after the save, skip the check, or save the
   * client's list as sent, and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aNewProcessChecksItsRequesterFormsBeforeAnyWrite() throws Exception {
    WorkFlow existingProject = newWorkFlow(ProjectMode.EXISTING);
    existingProject.setProjectId(PROJECT_ID);
    existingProject.setRequesterFormIds(Arrays.asList(64L, 63L, 64L));

    processesService.createWorkFlow(existingProject, ADMIN_ID);

    InOrder order = inOrder(plugin, processesStorage);
    order.verify(plugin).checkRequesterForms(isNull(), eq(PROJECT_ID), eq(List.of(63L, 64L)), any(Identity.class));
    ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);
    order.verify(processesStorage).saveWorkFlow(saved.capture(), eq(ADMIN_ID));
    assertEquals(List.of(63L, 64L), saved.getValue().getRequesterFormIds());

    WorkFlow newProject = newWorkFlow(ProjectMode.NEW);
    newProject.setRequesterFormIds(List.of(63L));
    processesService.createWorkFlow(newProject, ADMIN_ID);

    verify(plugin).checkRequesterForms(any(), anyLong(), any(), any());
    verify(processesStorage, org.mockito.Mockito.times(2)).saveWorkFlow(saved.capture(), eq(ADMIN_ID));
    assertEquals("a new project has no form to choose yet", List.of(), saved.getValue().getRequesterFormIds());
  }

  /**
   * A requester form the plugin refuses, an id that names no form, or a
   * plugin that does not know the choice fails the whole save before its
   * first write. Mutation: catch the refusal, drop the id check, or make the
   * plugin's default accept the forms, and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aRefusedRequesterFormWritesNothing() throws Exception {
    doThrow(new IllegalArgumentException("forms.form.notApplicable")).when(plugin).checkRequesterForms(any(), anyLong(), any(), any());
    WorkFlow refused = newWorkFlow(ProjectMode.EXISTING);
    refused.setProjectId(PROJECT_ID);
    refused.setRequesterFormIds(List.of(63L));
    assertEquals("forms.form.notApplicable",
                 assertThrows(IllegalArgumentException.class, () -> processesService.createWorkFlow(refused, ADMIN_ID)).getMessage());

    WorkFlow noForm = newWorkFlow(ProjectMode.EXISTING);
    noForm.setProjectId(PROJECT_ID);
    noForm.setRequesterFormIds(Arrays.asList(63L, 0L));
    assertEquals("processes.workflow.requesterFormInvalid",
                 assertThrows(IllegalArgumentException.class, () -> processesService.createWorkFlow(noForm, ADMIN_ID)).getMessage());

    doCallRealMethod().when(plugin).checkRequesterForms(any(), anyLong(), any(), any());
    assertEquals("processes.workflow.formUnsupported",
                 assertThrows(IllegalArgumentException.class, () -> processesService.createWorkFlow(refused, ADMIN_ID)).getMessage());

    verify(statusService, never()).createStatus(any(), any());
    verify(processesStorage, never()).saveWorkFlow(any(), anyLong());
  }

  /**
   * An update that does not state the requester forms, or states the stored
   * ones, keeps them without asking the plugin, and states no change by
   * itself; one that changes them has them checked against the stored
   * project before its first write and saves them; an empty list removes
   * them unchecked. Mutation: save the client's null, count the stored forms
   * as a change, or check against the client's project, and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void anUpdateKeepsOrChecksTheRequesterForms() throws Exception {
    WorkFlow stored = storedWorkFlow(true);
    stored.setRequesterFormIds(List.of(63L));
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, EDITOR_ID)).thenReturn(stored);
    ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);

    WorkFlow unchanged = storedWorkFlow(true);
    unchanged.setRequesterFormIds(List.of(63L));
    assertEquals("there are no changes to save",
                 assertThrows(IllegalArgumentException.class, () -> processesService.updateWorkFlow(unchanged, EDITOR_ID)).getMessage());

    WorkFlow titleEdit = storedWorkFlow(true);
    titleEdit.setTitle("Renamed");
    processesService.updateWorkFlow(titleEdit, EDITOR_ID);
    verify(processesStorage).saveWorkFlow(saved.capture(), eq(EDITOR_ID));
    assertEquals("a save that does not state them keeps the stored ones", List.of(63L), saved.getValue().getRequesterFormIds());
    verify(plugin, never()).checkRequesterForms(any(), anyLong(), any(), any());

    WorkFlow changed = storedWorkFlow(true);
    changed.setProjectId(99L);
    changed.setRequesterFormIds(List.of(64L, 63L));
    processesService.updateWorkFlow(changed, EDITOR_ID);
    InOrder order = inOrder(plugin, processesStorage);
    order.verify(plugin).checkRequesterForms(eq(WORKFLOW_ID), eq(PROJECT_ID), eq(List.of(63L, 64L)), any(Identity.class));
    order.verify(processesStorage).saveWorkFlow(saved.capture(), eq(EDITOR_ID));
    assertEquals(List.of(63L, 64L), saved.getValue().getRequesterFormIds());

    WorkFlow cleared = storedWorkFlow(true);
    cleared.setRequesterFormIds(List.of());
    processesService.updateWorkFlow(cleared, EDITOR_ID);
    verify(processesStorage, org.mockito.Mockito.times(3)).saveWorkFlow(saved.capture(), eq(EDITOR_ID));
    assertEquals(List.of(), saved.getValue().getRequesterFormIds());
    verify(plugin).checkRequesterForms(any(), anyLong(), any(), any());
  }

  /**
   * The forms add-on reads a process's requester forms with no user: the
   * stored ones, or none for a process stored before them or gone. Mutation:
   * answer null for a process with none, and this test fails.
   */
  @Test
  public void theRequesterFormsAreReadForTheFormsAddOn() {
    WorkFlow stored = storedWorkFlow(true);
    stored.setRequesterFormIds(List.of(63L, 64L));
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, null)).thenReturn(stored);
    when(processesStorage.getWorkFlowById(8L, null)).thenReturn(storedWorkFlow(true));

    assertEquals(List.of(63L, 64L), processesService.getRequesterFormIds(WORKFLOW_ID));
    assertEquals(List.of(), processesService.getRequesterFormIds(8L));
    assertEquals(List.of(), processesService.getRequesterFormIds(9L));
  }

  private WorkFlow newWorkFlow(ProjectMode projectMode) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setTitle("Leave request");
    workFlow.setSpaceId(SPACE_ID);
    workFlow.setProjectMode(projectMode);
    workFlow.setRequestsCreators(new ArrayList<>());
    return workFlow;
  }

  /**
   * @param canEdit whether the reading user may edit it
   * @return the stored process of the space, as an editor sends it back
   */
  private static WorkFlow storedWorkFlow(boolean canEdit) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(WORKFLOW_ID);
    workFlow.setTitle("Leave request");
    workFlow.setSpaceId(SPACE_ID);
    workFlow.setProjectId(PROJECT_ID);
    workFlow.setRequestsCreators(new ArrayList<>());
    ProcessPermission acl = new ProcessPermission();
    acl.setCanEdit(canEdit);
    workFlow.setAcl(acl);
    return workFlow;
  }

  private void mockUser(long identityId, String username, MembershipEntry... memberships) {
    org.exoplatform.social.core.identity.model.Identity identity =
                                                               new org.exoplatform.social.core.identity.model.Identity(OrganizationIdentityProvider.NAME,
                                                                                                                       username);
    identity.setId(String.valueOf(identityId));
    identity.setEnable(true);
    when(identityManager.getIdentity(identityId)).thenReturn(identity);
    when(userACL.getUserIdentity(username)).thenReturn(new Identity(username, Arrays.asList(memberships)));
  }
}
