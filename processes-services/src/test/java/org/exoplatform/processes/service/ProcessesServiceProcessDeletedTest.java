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

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.processes.constant.ProcessesEvents;
import org.exoplatform.processes.model.ProcessDeletion;
import org.exoplatform.processes.model.ProcessPermission;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.services.listener.ListenerService;

/**
 * A process's deletion is announced once it is committed, with the tasks its
 * requests had, so another add-on can drop what it bound to the process.
 */
@RunWith(MockitoJUnitRunner.class)
public class ProcessesServiceProcessDeletedTest {

  private static final long WORKFLOW_ID = 7L;

  private static final long USER_ID     = 1L;

  @Mock
  private ProcessesStorage  processesStorage;

  @Mock
  private ListenerService   listenerService;

  @Mock
  private ProcessTranslationService processTranslationService;

  /**
   * The deletion is broadcast after the storage's deletion returned, with the
   * process id and its request task ids. Mutation: broadcast before the
   * deletion, or without the task ids, and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aDeletionIsBroadcastAfterItCommitsWithTheRequestTasks() throws Exception {
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, USER_ID)).thenReturn(workFlow(true));
    when(processesStorage.deleteWorkflowById(WORKFLOW_ID)).thenReturn(new ProcessDeletion(List.of(100L, 101L), List.of(50L, 51L)));

    service().deleteWorkflowById(WORKFLOW_ID, USER_ID);

    InOrder order = inOrder(processesStorage, listenerService);
    order.verify(processesStorage).deleteWorkflowById(WORKFLOW_ID);
    order.verify(listenerService).broadcast(ProcessesEvents.PROCESS_DELETED, Long.valueOf(WORKFLOW_ID), List.of(100L, 101L));
    // Each draft of the process was deleted with it, announced after the process
    order.verify(listenerService).broadcast(ProcessesEvents.REQUEST_DRAFT_DELETED, Long.valueOf(50L), Long.valueOf(WORKFLOW_ID));
    order.verify(listenerService).broadcast(ProcessesEvents.REQUEST_DRAFT_DELETED, Long.valueOf(51L), Long.valueOf(WORKFLOW_ID));
  }

  /**
   * A draft's deletion by its owner, and the platform's own deletion of one,
   * is announced after it with the draft's process; a refused one is not.
   * Mutation: drop either broadcast, or broadcast before the deletion, and
   * this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  @SuppressWarnings("deprecation")
  public void aDraftDeletionIsBroadcastWithItsProcess() throws Exception {
    when(processesStorage.getWorkDraftyId(50L)).thenReturn(draft(50L, USER_ID));
    when(processesStorage.getWorkDraftyId(51L)).thenReturn(draft(51L, 2L));

    service().deleteWorkDraftById(50L, USER_ID);
    assertThrows(IllegalAccessException.class, () -> service().deleteWorkDraftById(51L, USER_ID));
    service().deleteWorkDraftById(51L);

    InOrder order = inOrder(processesStorage, listenerService);
    order.verify(processesStorage).deleteWorkDraftById(50L);
    order.verify(listenerService).broadcast(ProcessesEvents.REQUEST_DRAFT_DELETED, Long.valueOf(50L), Long.valueOf(WORKFLOW_ID));
    order.verify(processesStorage).deleteWorkDraftById(51L);
    order.verify(listenerService).broadcast(ProcessesEvents.REQUEST_DRAFT_DELETED, Long.valueOf(51L), Long.valueOf(WORKFLOW_ID));
    verify(listenerService, org.mockito.Mockito.times(2)).broadcast(eq(ProcessesEvents.REQUEST_DRAFT_DELETED), any(), any());
  }

  private static org.exoplatform.processes.model.Work draft(long id, long ownerId) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(WORKFLOW_ID);
    return new org.exoplatform.processes.model.Work(id, "draft", "description", ownerId, null, null, null, true, workFlow);
  }

  /**
   * A refused or failed deletion announces nothing. Mutation: broadcast before
   * the storage's deletion and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aRefusedOrFailedDeletionIsNotBroadcast() throws Exception {
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, USER_ID)).thenReturn(workFlow(false));
    assertThrows(IllegalAccessException.class, () -> service().deleteWorkflowById(WORKFLOW_ID, USER_ID));

    when(processesStorage.getWorkFlowById(WORKFLOW_ID, 2L)).thenReturn(workFlow(true));
    doThrow(new IllegalStateException("rolled back")).when(processesStorage).deleteWorkflowById(WORKFLOW_ID);
    assertThrows(IllegalStateException.class, () -> service().deleteWorkflowById(WORKFLOW_ID, 2L));

    verify(listenerService, never()).broadcast(anyString(), any(), any());
  }

  /**
   * The platform's own deletion is announced the same way.
   *
   * @throws Exception never
   */
  @Test
  @SuppressWarnings("deprecation")
  public void thePlatformDeletionIsBroadcastToo() throws Exception {
    when(processesStorage.deleteWorkflowById(WORKFLOW_ID)).thenReturn(new ProcessDeletion(List.of(), List.of()));

    service().deleteWorkflowById(WORKFLOW_ID);

    verify(listenerService).broadcast(eq(ProcessesEvents.PROCESS_DELETED), eq(Long.valueOf(WORKFLOW_ID)), eq(List.of()));
  }

  private ProcessesServiceImpl service() {
    return new ProcessesServiceImpl(processesStorage, null, null, null, null, null, null, null, listenerService, processTranslationService);
  }

  private static WorkFlow workFlow(boolean canDelete) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(WORKFLOW_ID);
    ProcessPermission acl = new ProcessPermission();
    acl.setCanDelete(canDelete);
    workFlow.setAcl(acl);
    return workFlow;
  }
}
