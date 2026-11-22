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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.model.ProcessPermission;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.services.attachments.model.Attachment;
import org.exoplatform.services.listener.ListenerService;

/**
 * A process's documents to fill in are given to a user who may make a request
 * on it or see it, whatever their right on the process's own files, and to no
 * one else (EXO-94918).
 */
@RunWith(MockitoJUnitRunner.class)
public class ProcessesServiceDocumentsToFillTest {

  private static final long          WORKFLOW_ID = 9L;

  private static final long          USER_ID     = 3L;

  @Mock
  private ProcessesStorage           processesStorage;

  @Mock
  private ProcessesAttachmentService processesAttachmentService;

  @Mock
  private ListenerService            listenerService;

  @Mock
  private ProcessTranslationService  processTranslationService;

  /**
   * A requester who may make a request on the process, but not see it, gets
   * its documents, read for them. Mutation: require the access right as well,
   * or read the documents for another user, and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aRequesterGetsTheDocumentsToFill() throws Exception {
    List<Attachment> documents = List.of(new Attachment());
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, USER_ID)).thenReturn(workFlow(true, false));
    when(processesAttachmentService.getProcessDocuments(USER_ID, WORKFLOW_ID)).thenReturn(documents);

    assertEquals(documents, service().getDocumentsToFill(WORKFLOW_ID, USER_ID));
  }

  /**
   * A user who sees the process without making requests on it, such as a
   * member of its managing space viewing a sent request, gets its documents.
   * Mutation: require the right to make a request alone, and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void aViewerOfTheProcessGetsTheDocumentsToFill() throws Exception {
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, USER_ID)).thenReturn(workFlow(false, true));
    when(processesAttachmentService.getProcessDocuments(USER_ID, WORKFLOW_ID)).thenReturn(List.of());

    assertEquals(List.of(), service().getDocumentsToFill(WORKFLOW_ID, USER_ID));
  }

  /**
   * A user who may neither make a request on the process nor see it gets a
   * not-found, and the process's documents are not read. Mutation: drop the
   * check, and this test fails.
   */
  @Test
  public void aUserWithNoRightOnTheProcessIsRefused() {
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, USER_ID)).thenReturn(workFlow(false, false));

    assertThrows(ObjectNotFoundException.class, () -> service().getDocumentsToFill(WORKFLOW_ID, USER_ID));
    verify(processesAttachmentService, never()).getProcessDocuments(anyLong(), anyLong());
  }

  /**
   * An unknown process, or one read with no permission, gets a not-found.
   */
  @Test
  public void anUnknownProcessIsNotFound() {
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, USER_ID)).thenReturn(null);
    assertThrows(ObjectNotFoundException.class, () -> service().getDocumentsToFill(WORKFLOW_ID, USER_ID));

    WorkFlow noAcl = new WorkFlow();
    noAcl.setId(WORKFLOW_ID);
    when(processesStorage.getWorkFlowById(WORKFLOW_ID, USER_ID)).thenReturn(noAcl);
    assertThrows(ObjectNotFoundException.class, () -> service().getDocumentsToFill(WORKFLOW_ID, USER_ID));
  }

  /**
   * Builds the service with the collaborators these checks use.
   *
   * @return the service
   */
  private ProcessesServiceImpl service() {
    return new ProcessesServiceImpl(processesStorage,
                                    null,
                                    null,
                                    null,
                                    null,
                                    processesAttachmentService,
                                    null,
                                    null,
                                    listenerService,
                                    processTranslationService);
  }

  /**
   * Builds a process with the given rights of the user.
   *
   * @param canAddRequest whether the user may make a request on it
   * @param canAccess whether the user may see it
   * @return the process
   */
  private static WorkFlow workFlow(boolean canAddRequest, boolean canAccess) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(WORKFLOW_ID);
    ProcessPermission acl = new ProcessPermission();
    acl.setCanAddRequest(canAddRequest);
    acl.setCanAccess(canAccess);
    workFlow.setAcl(acl);
    return workFlow;
  }
}
