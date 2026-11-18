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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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
 * A new request's form values: checked before its task exists, written once
 * its task and record exist, the request removed when the write refuses them,
 * and refused with no plugin to take them.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ProcessesServiceRequestValuesTest {

  private static final long                                 WORKFLOW_ID = 7L;

  private static final long                                 USER_ID     = 1L;

  private static final long                                 TASK_ID     = 100L;

  private static final long                                 DRAFT_ID    = 50L;

  private static final Map<Long, Map<String, List<Object>>> VALUES      = Map.of(12L, Map.of("reason", List.of("Holiday")));

  @Mock
  private ProcessesStorage                                  processesStorage;

  @Mock
  private IdentityManager                                   identityManager;

  @Mock
  private UserACL                                           userACL;

  @Mock
  private ProcessesAttachmentService                        processesAttachmentService;

  @Mock
  private RequestValuesPlugin                               plugin;

  private ProcessesServiceImpl                              processesService;

  private Work                                              created;

  /**
   * Builds the service with the plugin registered, an enabled process the user
   * may request on, and a storage that creates request 100.
   */
  @Before
  public void setUp() {
    processesService = new ProcessesServiceImpl(processesStorage,
                                                null,
                                                identityManager,
                                                userACL,
                                                null,
                                                processesAttachmentService,
                                                null,
                                                null,
                                                null,
                                                null);
    processesService.addRequestValuesPlugin(plugin);
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(WORKFLOW_ID);
    workFlow.setTitle("Leave");
    workFlow.setEnabled(true);
    ProcessPermission acl = new ProcessPermission();
    acl.setCanAddRequest(true);
    workFlow.setAcl(acl);
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, USER_ID)).thenReturn(workFlow);
    org.exoplatform.social.core.identity.model.Identity identity =
                                                               new org.exoplatform.social.core.identity.model.Identity(OrganizationIdentityProvider.NAME,
                                                                                                                       "mary");
    identity.setId(String.valueOf(USER_ID));
    identity.setEnable(true);
    when(identityManager.getIdentity(USER_ID)).thenReturn(identity);
    when(userACL.getUserIdentity("mary")).thenReturn(new Identity("mary"));
    created = new Work();
    created.setId(TASK_ID);
    when(processesStorage.createRequest(any(), eq(USER_ID))).thenReturn(created);
    when(processesStorage.completeRequest(eq(created), any(), eq(USER_ID))).thenReturn(created);
  }

  /**
   * The values are checked before the task exists, written once it and its
   * record exist, and the request ends after the write. Mutation: write before
   * the task, or end the request before the write, and the order fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void theValuesAreCheckedBeforeTheTaskAndWrittenAfterIt() throws Exception {
    assertSame(created, processesService.createWork(request(null), USER_ID));

    InOrder order = inOrder(plugin, processesStorage);
    order.verify(plugin).checkRequestValues(eq(WORKFLOW_ID), isNull(), eq(VALUES), any(Identity.class));
    order.verify(processesStorage).createRequest(any(), eq(USER_ID));
    order.verify(plugin).writeRequestValues(eq(WORKFLOW_ID), isNull(), eq(TASK_ID), eq(VALUES), any(Identity.class));
    order.verify(processesStorage).completeRequest(eq(created), any(), eq(USER_ID));
  }

  /**
   * A request made from the requester's own draft of the same process hands
   * the draft id to the plugin, which reads the draft's values with the sent
   * ones.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aRequestFromADraftHandsTheDraftToThePlugin() throws Exception {
    WorkFlow draftProcess = new WorkFlow();
    draftProcess.setId(WORKFLOW_ID);
    when(processesStorage.getWorkDraftyId(DRAFT_ID)).thenReturn(new Work(DRAFT_ID, "d", "", USER_ID, null, null, null, true, draftProcess));

    processesService.createWork(request(DRAFT_ID), USER_ID);

    verify(plugin).checkRequestValues(eq(WORKFLOW_ID), eq(DRAFT_ID), eq(VALUES), any(Identity.class));
    verify(plugin).writeRequestValues(eq(WORKFLOW_ID), eq(DRAFT_ID), eq(TASK_ID), eq(VALUES), any(Identity.class));
  }

  /**
   * A refused check creates nothing. Mutation: create the task before the
   * check and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aRefusedCheckCreatesNothing() throws Exception {
    doThrow(new IllegalArgumentException("forms.value.required:reason")).when(plugin)
                                                                       .checkRequestValues(anyLong(), nullable(Long.class), any(), any());

    assertEquals("forms.value.required:reason",
                 assertThrows(IllegalArgumentException.class, () -> processesService.createWork(request(null), USER_ID)).getMessage());

    verify(processesStorage, never()).createRequest(any(), anyLong());
  }

  /**
   * A write refused once the task exists removes the request, ends nothing,
   * and answers the refusal. Mutation: keep the request, or end it after the
   * refusal, and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aRefusedWriteRemovesTheRequest() throws Exception {
    doThrow(new IllegalArgumentException("forms.form.notApplicable")).when(plugin)
                                                                       .writeRequestValues(anyLong(),
                                                                                           nullable(Long.class),
                                                                                           anyLong(),
                                                                                           any(),
                                                                                           any());

    assertThrows(IllegalArgumentException.class, () -> processesService.createWork(request(null), USER_ID));

    verify(processesStorage).removeRequest(TASK_ID);
    verify(processesStorage, never()).completeRequest(any(), any(), anyLong());
  }

  /**
   * With no plugin registered, values are refused before anything is
   * created; a request without values is saved as today. Mutation: ignore the
   * values and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void withNoPluginValuesAreRefusedAndARequestWithoutIsSaved() throws Exception {
    processesService.removeRequestValuesPlugin(plugin);

    assertEquals("processes.request.formValuesUnsupported",
                 assertThrows(IllegalArgumentException.class, () -> processesService.createWork(request(null), USER_ID)).getMessage());
    verify(processesStorage, never()).saveWork(any(), anyLong());

    Work noValues = request(null);
    noValues.setFormValues(null);
    processesService.createWork(noValues, USER_ID);
    verify(processesStorage).saveWork(noValues, USER_ID);
  }

  private static Work request(Long draftId) {
    Work work = new Work();
    work.setWorkFlowId(WORKFLOW_ID);
    work.setDraftId(draftId);
    work.setFormValues(VALUES);
    return work;
  }
}
