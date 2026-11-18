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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
import org.exoplatform.processes.model.ProcessPermission;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.plugin.RequestValuesPlugin;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * A request draft's save with its form values: one transaction for the row and
 * the values, no required-field check, the process's attachments copied once
 * the save is committed, and an update whose only change is its values.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ProcessesServiceDraftValuesTest {

  private static final long                                 WORKFLOW_ID = 7L;

  private static final long                                 USER_ID     = 1L;

  private static final long                                 DRAFT_ID    = 50L;

  private static final Map<Long, Map<String, List<Object>>> VALUES      = Map.of(12L, Map.of("reason", List.of("Holiday")));

  @Mock
  private ProcessesStorage                                  processesStorage;

  @Mock
  private IdentityManager                                   identityManager;

  @Mock
  private UserACL                                           userACL;

  @Mock
  private RequestValuesPlugin                               plugin;

  private ProcessesServiceImpl                              processesService;

  private WorkFlow                                          storedProcess;

  /**
   * Builds the service with the plugin registered, a process the user may
   * request on, and a stored draft of that user.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Before
  public void setUp() throws Exception {
    processesService = spy(new ProcessesServiceImpl(processesStorage, null, identityManager, userACL, null, null, null, null, null, null));
    doAnswer(invocation -> processesService.writeWorkDraftRows(invocation.getArgument(0),
                                                               invocation.getArgument(1),
                                                               invocation.getArgument(2),
                                                               invocation.getArgument(3))).when(processesService)
                                                                                         .saveWorkDraftRows(any(),
                                                                                                            anyLong(),
                                                                                                            any(),
                                                                                                            anyBoolean());
    processesService.addRequestValuesPlugin(plugin);
    storedProcess = new WorkFlow();
    storedProcess.setId(WORKFLOW_ID);
    ProcessPermission acl = new ProcessPermission();
    acl.setCanAddRequest(true);
    storedProcess.setAcl(acl);
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, USER_ID)).thenReturn(storedProcess);
    org.exoplatform.social.core.identity.model.Identity identity =
                                                               new org.exoplatform.social.core.identity.model.Identity(OrganizationIdentityProvider.NAME,
                                                                                                                       "mary");
    identity.setId(String.valueOf(USER_ID));
    identity.setEnable(true);
    when(identityManager.getIdentity(USER_ID)).thenReturn(identity);
    when(userACL.getUserIdentity("mary")).thenReturn(new Identity("mary"));
    when(processesStorage.saveWorkDraftRow(any(), eq(USER_ID))).thenAnswer(invocation -> {
      Work draft = invocation.getArgument(0);
      Work saved = new Work(draft.getId() == 0 ? DRAFT_ID : draft.getId(), draft.getTitle(), "", USER_ID, null, null, null, true, storedProcess);
      return saved;
    });
    when(processesStorage.getWorkDraftyId(DRAFT_ID)).thenReturn(new Work(DRAFT_ID, "d", "", USER_ID, null, null, null, true, storedProcess));
  }

  /**
   * A new draft's row is written first, its values next on its id, for the
   * stored process, and the process's attachments are copied after the save.
   * Mutation: copy the attachments inside the save, or write the values on the
   * client's process, and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aNewDraftWritesItsRowThenItsValuesThenCopiesTheAttachments() throws Exception {
    processesService.createWorkDraft(newDraft(), USER_ID);

    InOrder order = inOrder(processesStorage, plugin);
    order.verify(processesStorage).saveWorkDraftRow(any(), eq(USER_ID));
    order.verify(plugin).writeDraftValues(eq(WORKFLOW_ID), eq(DRAFT_ID), eq(VALUES), any(Identity.class));
    order.verify(processesStorage).copyProcessAttachmentsToDraft(eq(DRAFT_ID), eq(storedProcess), eq(USER_ID));
    verify(processesStorage, never()).saveWorkDraft(any(), anyLong());
  }

  /**
   * A new draft refused for its values copies no attachment. Mutation: copy
   * before the values' write and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aNewDraftRefusedForItsValuesCopiesNothing() throws Exception {
    doThrow(new IllegalArgumentException("forms.value.nullNotAllowed")).when(plugin).writeDraftValues(anyLong(), anyLong(), any(), any());

    assertThrows(IllegalArgumentException.class, () -> processesService.createWorkDraft(newDraft(), USER_ID));

    verify(processesStorage, never()).copyProcessAttachmentsToDraft(anyLong(), any(), anyLong());
  }

  /**
   * An update writes its values before its row; an update whose only change is
   * its values writes them and no row; an update with neither is refused as
   * today. Mutation: write the row first, write an unchanged row, or refuse a
   * values-only update, and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void anUpdateWritesItsValuesBeforeItsRowAndAValuesOnlyUpdateWritesNoRow() throws Exception {
    Work valuesOnly = storedDraft("d");
    valuesOnly.setFormValues(VALUES);
    processesService.updateWorkDraft(valuesOnly, USER_ID);
    verify(plugin).writeDraftValues(eq(WORKFLOW_ID), eq(DRAFT_ID), eq(VALUES), any(Identity.class));
    verify(processesStorage, never()).saveWorkDraftRow(any(), anyLong());

    Work renamed = storedDraft("renamed");
    renamed.setFormValues(VALUES);
    processesService.updateWorkDraft(renamed, USER_ID);
    InOrder order = inOrder(plugin, processesStorage);
    order.verify(plugin).writeDraftValues(eq(WORKFLOW_ID), eq(DRAFT_ID), eq(VALUES), any(Identity.class));
    order.verify(processesStorage).saveWorkDraftRow(any(), eq(USER_ID));

    assertEquals("there are no changes to save",
                 assertThrows(IllegalArgumentException.class,
                              () -> processesService.updateWorkDraft(storedDraft("d"), USER_ID)).getMessage());
  }

  /**
   * The server sets a request draft's state whatever the body states.
   * Mutation: keep the body's state and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void theServerSetsTheDraftState() throws Exception {
    Work draft = newDraft();
    draft.setIsDraft(false);

    processesService.createWorkDraft(draft, USER_ID);

    ArgumentCaptor<Work> saved = ArgumentCaptor.forClass(Work.class);
    verify(processesStorage).saveWorkDraftRow(saved.capture(), eq(USER_ID));
    assertTrue(saved.getValue().getIsDraft());
  }

  /**
   * With no plugin registered, a draft's values are refused before any write.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void withNoPluginADraftsValuesAreRefused() throws Exception {
    processesService.removeRequestValuesPlugin(plugin);

    assertEquals("processes.request.formValuesUnsupported",
                 assertThrows(IllegalArgumentException.class, () -> processesService.createWorkDraft(newDraft(), USER_ID)).getMessage());
    verify(processesStorage, never()).saveWorkDraftRow(any(), anyLong());
    verify(processesStorage, never()).saveWorkDraft(any(), anyLong());
  }

  /**
   * The draft save with values carries the atomic transaction, so a refusal
   * of its values rolls its row back. Mutation: drop the annotation or its
   * atomic flag and this test fails.
   *
   * @throws Exception on reflection
   */
  @Test
  public void theDraftSaveWithValuesIsAtomic() throws Exception {
    ContainerTransactional transactional = ProcessesServiceImpl.class.getMethod("saveWorkDraftRows",
                                                                                Work.class,
                                                                                long.class,
                                                                                Map.class,
                                                                                boolean.class)
                                                                     .getAnnotation(ContainerTransactional.class);
    assertTrue(transactional != null && transactional.atomic());
  }

  /**
   * An update keeps the stored draft's creator, creation and modification
   * dates and task, whether the body leaves them out or forges them, with or
   * without values, and the server sets its draft state. Mutation: drop any
   * one of the resets and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void anUpdateKeepsTheStoredCreatorDatesAndTask() throws Exception {
    Date created = new Date(1_000_000L);
    Date modified = new Date(2_000_000L);
    when(processesStorage.getWorkDraftyId(DRAFT_ID)).thenReturn(new Work(DRAFT_ID, "d", "", USER_ID, created, modified, null, true, storedProcess));
    when(processesStorage.saveWorkDraft(any(), eq(USER_ID))).thenAnswer(invocation -> invocation.getArgument(0));

    Work missing = new Work(DRAFT_ID, "renamed", "", 0, null, null, null, true, storedProcess);
    missing.setFormValues(VALUES);
    processesService.updateWorkDraft(missing, USER_ID);
    Work forged = new Work(DRAFT_ID, "renamed again", "", 99L, new Date(5L), new Date(6L), 4242L, false, storedProcess);
    processesService.updateWorkDraft(forged, USER_ID);

    ArgumentCaptor<Work> withValues = ArgumentCaptor.forClass(Work.class);
    verify(processesStorage).saveWorkDraftRow(withValues.capture(), eq(USER_ID));
    ArgumentCaptor<Work> withoutValues = ArgumentCaptor.forClass(Work.class);
    verify(processesStorage).saveWorkDraft(withoutValues.capture(), eq(USER_ID));
    for (Work saved : List.of(withValues.getValue(), withoutValues.getValue())) {
      assertEquals(USER_ID, saved.getCreatorId());
      assertEquals(created, saved.getCreatedDate());
      assertEquals(modified, saved.getModifiedDate());
      assertNull(saved.getTaskId());
      assertTrue(saved.getIsDraft());
    }
  }

  /**
   * A values-only update that echoes the draft as the read returns it (task id
   * 0, its dates, another state, an empty attachment list, a status) writes its
   * values and no row. Mutation:
   * compare the whole draft instead of its title and description and this test
   * fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aValuesOnlyUpdateEchoingTheReadWritesNoRow() throws Exception {
    Work echoed = new Work(DRAFT_ID, "d", "", USER_ID, new Date(7L), new Date(8L), 0L, false, storedProcess);
    echoed.setAttachments(new ArrayList<>());
    echoed.setStatus("Request");
    echoed.setFormValues(VALUES);

    processesService.updateWorkDraft(echoed, USER_ID);

    verify(plugin).writeDraftValues(eq(WORKFLOW_ID), eq(DRAFT_ID), eq(VALUES), any(Identity.class));
    verify(processesStorage, never()).saveWorkDraftRow(any(), anyLong());
  }

  /**
   * A stored row that is no draft is not found by any path that reads a
   * draft by id: the owner's read, update and delete, and a request made from
   * it, and none of them writes. Mutation: drop the draft-state condition of
   * the owner's draft read and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aRowThatIsNoDraftIsFoundByNoDraftPath() throws Exception {
    when(processesStorage.getWorkDraftyId(DRAFT_ID)).thenReturn(new Work(DRAFT_ID, "d", "", USER_ID, null, null, null, false, storedProcess));
    storedProcess.setEnabled(true);
    Work update = storedDraft("renamed");
    update.setFormValues(VALUES);
    Work request = new Work();
    request.setWorkFlowId(WORKFLOW_ID);
    request.setDraftId(DRAFT_ID);

    assertThrows(ObjectNotFoundException.class, () -> processesService.getWorkDraft(DRAFT_ID, USER_ID));
    assertThrows(ObjectNotFoundException.class, () -> processesService.updateWorkDraft(update, USER_ID));
    assertThrows(ObjectNotFoundException.class, () -> processesService.deleteWorkDraftById(DRAFT_ID, USER_ID));
    assertThrows(ObjectNotFoundException.class, () -> processesService.createWork(request, USER_ID, null));

    verify(plugin, never()).writeDraftValues(anyLong(), anyLong(), any(), any());
    verify(processesStorage, never()).saveWorkDraftRow(any(), anyLong());
    verify(processesStorage, never()).saveWorkDraft(any(), anyLong());
    verify(processesStorage, never()).deleteWorkDraftById(anyLong());
  }

  /**
   * With the plugin gone between a draft save's check and its write, the write
   * refuses the values as unsupported rather than failing on a missing plugin.
   * Mutation: drop the write's own plugin check and this test fails on a
   * NullPointerException.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aDraftWriteWithThePluginGoneRefusesTheValues() throws Exception {
    processesService.removeRequestValuesPlugin(plugin);

    assertEquals("processes.request.formValuesUnsupported",
                 assertThrows(IllegalArgumentException.class,
                              () -> processesService.writeWorkDraftRows(storedDraft("renamed"), USER_ID, VALUES, true)).getMessage());
    verify(processesStorage, never()).saveWorkDraftRow(any(), anyLong());
  }

  private Work newDraft() {
    WorkFlow sentProcess = new WorkFlow();
    sentProcess.setId(WORKFLOW_ID);
    Work draft = new Work(0, "d", "", USER_ID, null, null, null, true, sentProcess);
    draft.setFormValues(VALUES);
    return draft;
  }

  private Work storedDraft(String title) {
    return new Work(DRAFT_ID, title, "", USER_ID, null, null, null, true, storedProcess);
  }
}
