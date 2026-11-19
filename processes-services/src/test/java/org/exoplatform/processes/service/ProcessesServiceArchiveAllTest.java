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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFilter;
import org.exoplatform.processes.storage.ProcessesStorage;

/**
 * « Archive all »: the user's own finished requests, each through the checked
 * archive, as that user.
 */
@RunWith(MockitoJUnitRunner.class)
public class ProcessesServiceArchiveAllTest {

  private static final long    USER_ID = 7L;

  @Mock
  private ProcessesStorage     processesStorage;

  private ProcessesServiceImpl processesService;

  /**
   * Builds the service over a mocked storage, spied so the checked archive of
   * one request can be observed.
   */
  @Before
  public void setUp() {
    processesService = spy(new ProcessesServiceImpl(processesStorage, null, null, null, null, null, null, null, null, null));
  }

  /**
   * The user's open requests are read once, theirs only, and each Validated,
   * Refused or Canceled one is archived through the checked archive as that
   * user; a pending one is left. Mutation: drop the status check, archive
   * through the unchecked overload or the storage, or read another user's
   * requests, and this test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  public void theFinishedRequestsOfTheUserAreArchivedThroughTheCheckedArchive() throws Exception {
    select(work(1L, "Validated"), work(2L, "Refused"), work(3L, "Canceled"), work(4L, "Request"), work(5L, "RequestInProgress"));
    doReturn(null).when(processesService).updateWorkCompleted(anyLong(), eq(true), eq(USER_ID));

    assertEquals(3, processesService.archiveFinishedWorks(USER_ID));

    verify(processesService).updateWorkCompleted(1L, true, USER_ID);
    verify(processesService).updateWorkCompleted(2L, true, USER_ID);
    verify(processesService).updateWorkCompleted(3L, true, USER_ID);
    verify(processesService, never()).updateWorkCompleted(eq(4L), anyBoolean(), anyLong());
    verify(processesService, never()).updateWorkCompleted(eq(5L), anyBoolean(), anyLong());
    verify(processesService, never()).updateWorkCompleted(nullable(Long.class), anyBoolean());
    verify(processesStorage, never()).updateWorkCompleted(anyLong(), anyBoolean());
    ArgumentCaptor<WorkFilter> filter = ArgumentCaptor.forClass(WorkFilter.class);
    verify(processesStorage).getWorks(eq(USER_ID), filter.capture(), eq(0), eq(0));
    assertEquals(Boolean.FALSE, filter.getValue().getCompleted());
  }

  /**
   * A request selected as finished but reopened by a manager before its turn,
   * or archived meanwhile, is left: the status and the archive are read again
   * at each turn. Mutation: archive from the selection's copy and this test
   * fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  public void aRequestThatChangedBeforeItsTurnIsLeft() throws Exception {
    select(work(1L, "Validated"), work(2L, "Refused"), work(3L, "Canceled"));
    when(processesStorage.getWorkById(1L)).thenReturn(work(1L, "RequestInProgress"));
    Work archivedMeanwhile = work(2L, "Refused");
    archivedMeanwhile.setCompleted(true);
    when(processesStorage.getWorkById(2L)).thenReturn(archivedMeanwhile);
    doReturn(null).when(processesService).updateWorkCompleted(3L, true, USER_ID);

    assertEquals(1, processesService.archiveFinishedWorks(USER_ID));

    verify(processesService, never()).updateWorkCompleted(eq(1L), anyBoolean(), anyLong());
    verify(processesService, never()).updateWorkCompleted(eq(2L), anyBoolean(), anyLong());
    verify(processesService).updateWorkCompleted(3L, true, USER_ID);
  }

  /**
   * An archive that changes the next candidate (the same request listed twice
   * here) is seen at the next turn: the request now archived is not archived
   * again nor counted twice. Mutation: skip the re-read and this test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  public void anArchiveThatChangesTheNextCandidateIsSeenAtItsTurn() throws Exception {
    select(work(1L, "Validated"), work(1L, "Validated"));
    Work archived = work(1L, "Validated");
    archived.setCompleted(true);
    when(processesStorage.getWorkById(1L)).thenReturn(work(1L, "Validated"), archived);
    doReturn(null).when(processesService).updateWorkCompleted(1L, true, USER_ID);

    assertEquals(1, processesService.archiveFinishedWorks(USER_ID));

    verify(processesService).updateWorkCompleted(1L, true, USER_ID);
  }

  /**
   * A request gone before its turn (the re-read finds none), refused to the
   * user, or deleted while archived, is skipped and not counted, and the others
   * are still archived. Mutation: let a refusal stop the loop, count it, or
   * archive a request the re-read did not find, and this test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  public void aGoneOrRefusedRequestIsSkippedAndNotCounted() throws Exception {
    select(work(1L, "Validated"), work(2L, "Refused"), work(3L, "Canceled"), work(4L, "Validated"));
    when(processesStorage.getWorkById(4L)).thenReturn(null);
    doThrow(new IllegalAccessException("not allowed")).when(processesService).updateWorkCompleted(1L, true, USER_ID);
    doThrow(new ObjectNotFoundException("gone")).when(processesService).updateWorkCompleted(2L, true, USER_ID);
    doReturn(null).when(processesService).updateWorkCompleted(3L, true, USER_ID);

    assertEquals(1, processesService.archiveFinishedWorks(USER_ID));

    verify(processesService).updateWorkCompleted(3L, true, USER_ID);
    verify(processesService, never()).updateWorkCompleted(eq(4L), anyBoolean(), anyLong());
  }

  /**
   * With no finished request, nothing is archived and the count is 0.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  public void nothingIsArchivedWithoutAFinishedRequest() throws Exception {
    select(work(4L, "Request"));

    assertEquals(0, processesService.archiveFinishedWorks(USER_ID));

    verify(processesService, never()).updateWorkCompleted(anyLong(), anyBoolean(), anyLong());
  }

  /**
   * Makes the storage list these requests as the user's open ones, and read
   * each again, unchanged, at its turn, unless a test stubs that read.
   *
   * @param works the listed requests
   * @throws Exception never, the mock throws nothing
   */
  private void select(Work... works) throws Exception {
    when(processesStorage.getWorks(eq(USER_ID), any(WorkFilter.class), eq(0), eq(0))).thenReturn(new ArrayList<>(List.of(works)));
    for (Work listed : works) {
      org.mockito.Mockito.lenient().when(processesStorage.getWorkById(listed.getId())).thenReturn(work(listed.getId(), listed.getStatus()));
    }
  }

  private static Work work(long id, String status) {
    Work work = new Work();
    work.setId(id);
    work.setStatus(status);
    return work;
  }
}
