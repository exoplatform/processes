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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFilter;
import org.exoplatform.processes.storage.ProcessesStorage;

/**
 * « Archive all »: the user's own finished requests, each through the checked
 * archive, as that user.
 */
@ExtendWith(MockitoExtension.class)
class ProcessesServiceArchiveAllTest {

  private static final long    USER_ID = 7L;

  @Mock
  private ProcessesStorage     processesStorage;

  /** A request whose task is not completed. */
  private static final int     PENDING  = 0;

  /** A request a manager decided: its task completed. */
  private static final int     CLOSED   = 1;

  /** A request its requester cancelled: its task completed. */
  private static final int     CANCELED = 2;

  private ProcessesServiceImpl processesService;

  /**
   * Builds the service over a mocked storage, spied so the checked archive of
   * one request can be observed.
   */
  @BeforeEach
  void setUp() {
    processesService = spy(new ProcessesServiceImpl(processesStorage, null, null, null, null, null, null, null, null, null));
  }

  /**
   * The user's requests not archived yet are read once, theirs only, and each
   * finished one, decided or cancelled (its task completed), is archived
   * through the requester's archive as that user, its task untouched; a
   * pending one is left, whatever its column (here one named « Validated »).
   * Mutation: read the column name again, archive through the task's
   * completion or the storage, or read another user's requests, and this test
   * fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  void theFinishedRequestsOfTheUserAreArchivedThroughTheCheckedArchive() throws Exception {
    Work pendingInAValidatedColumn = work(5L, PENDING);
    pendingInAValidatedColumn.setStatus("Validated");
    select(work(1L, CLOSED), work(2L, CLOSED), work(3L, CANCELED), work(4L, PENDING), pendingInAValidatedColumn);
    doReturn(null).when(processesService).archiveWork(anyLong(), eq(true), eq(USER_ID));

    assertEquals(3, processesService.archiveFinishedWorks(USER_ID));

    verify(processesService).archiveWork(1L, true, USER_ID);
    verify(processesService).archiveWork(2L, true, USER_ID);
    verify(processesService).archiveWork(3L, true, USER_ID);
    verify(processesService, never()).archiveWork(eq(4L), anyBoolean(), anyLong());
    verify(processesService, never()).archiveWork(eq(5L), anyBoolean(), anyLong());
    verify(processesService, never()).updateWorkCompleted(nullable(Long.class), anyBoolean());
    verify(processesStorage, never()).updateWorkCompleted(anyLong(), anyBoolean());
    ArgumentCaptor<WorkFilter> filter = ArgumentCaptor.forClass(WorkFilter.class);
    verify(processesStorage).getWorks(eq(USER_ID), filter.capture(), eq(0), eq(0));
    assertEquals(Boolean.FALSE, filter.getValue().getArchived());
    assertEquals(Boolean.TRUE, filter.getValue().getCompleted());
    verify(processesService, never()).updateWorkCompleted(nullable(Long.class), anyBoolean(), anyLong());
  }

  /**
   * A request whose re-read at its turn says it is no longer finished, or
   * already archived, is left: the state and the archive are read again at
   * each turn. Mutation: archive from the selection's copy and this test
   * fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  void aRequestThatChangedBeforeItsTurnIsLeft() throws Exception {
    select(work(1L, CLOSED), work(2L, CLOSED), work(3L, CANCELED));
    when(processesStorage.getWorkById(1L)).thenReturn(work(1L, PENDING));
    Work archivedMeanwhile = work(2L, CLOSED);
    archivedMeanwhile.setArchived(true);
    when(processesStorage.getWorkById(2L)).thenReturn(archivedMeanwhile);
    doReturn(null).when(processesService).archiveWork(3L, true, USER_ID);

    assertEquals(1, processesService.archiveFinishedWorks(USER_ID));

    verify(processesService, never()).archiveWork(eq(1L), anyBoolean(), anyLong());
    verify(processesService, never()).archiveWork(eq(2L), anyBoolean(), anyLong());
    verify(processesService).archiveWork(3L, true, USER_ID);
  }

  /**
   * An archive that changes the next candidate (the same request listed twice
   * here) is seen at the next turn: the request now archived is not archived
   * again nor counted twice. Mutation: skip the re-read and this test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  void anArchiveThatChangesTheNextCandidateIsSeenAtItsTurn() throws Exception {
    select(work(1L, CLOSED), work(1L, CLOSED));
    Work archived = work(1L, CLOSED);
    archived.setArchived(true);
    when(processesStorage.getWorkById(1L)).thenReturn(work(1L, CLOSED), archived);
    doReturn(null).when(processesService).archiveWork(1L, true, USER_ID);

    assertEquals(1, processesService.archiveFinishedWorks(USER_ID));

    verify(processesService).archiveWork(1L, true, USER_ID);
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
  void aGoneOrRefusedRequestIsSkippedAndNotCounted() throws Exception {
    select(work(1L, CLOSED), work(2L, CLOSED), work(3L, CANCELED), work(4L, CLOSED));
    when(processesStorage.getWorkById(4L)).thenReturn(null);
    doThrow(new IllegalAccessException("not allowed")).when(processesService).archiveWork(1L, true, USER_ID);
    doThrow(new ObjectNotFoundException("gone")).when(processesService).archiveWork(2L, true, USER_ID);
    doReturn(null).when(processesService).archiveWork(3L, true, USER_ID);

    assertEquals(1, processesService.archiveFinishedWorks(USER_ID));

    verify(processesService).archiveWork(3L, true, USER_ID);
    verify(processesService, never()).archiveWork(eq(4L), anyBoolean(), anyLong());
  }

  /**
   * A re-read that fails stops the run with its failure, after the requests
   * archived before it, and archives none after it. Mutation: swallow the
   * failure, or archive the request without its re-read, and this test fails.
   *
   * @throws Exception never, the mocks throw nothing but the stubbed failure
   */
  @Test
  void aFailingReReadStopsTheRunAfterTheArchivedOnes() throws Exception {
    select(work(1L, CLOSED), work(2L, CLOSED), work(3L, CANCELED));
    when(processesStorage.getWorkById(2L)).thenThrow(new IllegalStateException("storage down"));
    doReturn(null).when(processesService).archiveWork(anyLong(), eq(true), eq(USER_ID));

    assertThrows(IllegalStateException.class, () -> processesService.archiveFinishedWorks(USER_ID));

    verify(processesService).archiveWork(1L, true, USER_ID);
    verify(processesService, never()).archiveWork(eq(2L), anyBoolean(), anyLong());
    verify(processesService, never()).archiveWork(eq(3L), anyBoolean(), anyLong());
  }

  /**
   * With no finished request, nothing is archived and the count is 0.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  void nothingIsArchivedWithoutAFinishedRequest() throws Exception {
    select(work(4L, PENDING));

    assertEquals(0, processesService.archiveFinishedWorks(USER_ID));

    verify(processesService, never()).archiveWork(anyLong(), anyBoolean(), anyLong());
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
      org.mockito.Mockito.lenient().when(processesStorage.getWorkById(listed.getId())).thenReturn(copy(listed));
    }
  }

  /**
   * Builds a request in a state, in a column that names no state.
   *
   * @param id the request id
   * @param state one of {@link #PENDING}, {@link #CLOSED}, {@link #CANCELED}
   * @return the request
   */
  private static Work work(long id, int state) {
    Work work = new Work();
    work.setId(id);
    work.setStatus("Column " + id);
    work.setCompleted(state != PENDING);
    work.setCanceled(state == CANCELED);
    return work;
  }

  /**
   * Copies a request as a re-read returns it.
   *
   * @param work the request
   * @return its copy
   */
  private static Work copy(Work work) {
    Work copy = new Work();
    copy.setId(work.getId());
    copy.setStatus(work.getStatus());
    copy.setCompleted(work.isCompleted());
    copy.setCanceled(work.isCanceled());
    copy.setArchived(work.isArchived());
    return copy;
  }
}
