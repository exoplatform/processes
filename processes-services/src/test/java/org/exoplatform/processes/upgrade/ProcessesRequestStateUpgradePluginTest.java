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
package org.exoplatform.processes.upgrade;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.processes.dao.RequestDAO;
import org.exoplatform.processes.entity.RequestEntity;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.task.dto.StatusDto;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.TaskService;

/**
 * The one-time upgrade that carries the meaning of the requests created before
 * EXO-94923, read from the names of the columns processes created, over to
 * their own state.
 */
@ExtendWith(MockitoExtension.class)
class ProcessesRequestStateUpgradePluginTest {

  @Mock
  private RequestDAO                         requestDAO;

  @Mock
  private TaskService                        taskService;

  @Mock
  private ProcessesStorage                   processesStorage;

  @Mock
  private EntityManagerService               entityManagerService;

  @Mock
  private EntityManager                      entityManager;

  private ProcessesRequestStateUpgradePlugin plugin;

  /**
   * Builds the plugin over mocked storages.
   */
  @BeforeEach
  void setUp() {
    InitParams params = new InitParams();
    ValueParam groupId = new ValueParam();
    groupId.setName("product.group.id");
    groupId.setValue("org.exoplatform.processes");
    params.addParameter(groupId);
    plugin = new ProcessesRequestStateUpgradePlugin(params, requestDAO, taskService, processesStorage, entityManagerService);
    lenient().when(entityManagerService.getEntityManager()).thenReturn(entityManager);
  }

  /**
   * Each legacy case gets its state in one write: a cancelled request is
   * cancelled, archived when its task was completed, its open task completed;
   * a request in a decided column is completed when open, archived when
   * completed; another completed request is archived and stays completed; an
   * open one stays pending; a record whose task is gone gets a state too. The
   * names are compared trimmed and case aside. Mutation: archive the open
   * decided request, reopen the archived-while-open one, leave the open
   * cancelled task open, or skip the record with no task, and this test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  void eachLegacyCaseGetsItsState() throws Exception {
    page(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
    task(1L, "Canceled", true);
    task(2L, " canceled ", false);
    task(3L, "Validated", false);
    task(4L, "REFUSED", true);
    task(5L, "To Do", true);
    task(6L, "RequestInProgress", false);
    when(taskService.getTask(7L)).thenThrow(new EntityNotFoundException(7L, TaskDto.class));
    task(8L, null, false);

    plugin.processUpgrade("7.2", "7.3");

    verify(processesStorage).saveRequestState(1L, Boolean.TRUE, Boolean.TRUE, null);
    verify(processesStorage).saveRequestState(2L, Boolean.TRUE, Boolean.FALSE, Boolean.TRUE);
    verify(processesStorage).saveRequestState(3L, Boolean.FALSE, Boolean.FALSE, Boolean.TRUE);
    verify(processesStorage).saveRequestState(4L, Boolean.FALSE, Boolean.TRUE, null);
    verify(processesStorage).saveRequestState(5L, Boolean.FALSE, Boolean.TRUE, null);
    verify(processesStorage).saveRequestState(6L, Boolean.FALSE, Boolean.FALSE, null);
    verify(processesStorage).saveRequestState(7L, Boolean.FALSE, Boolean.FALSE, null);
    verify(processesStorage).saveRequestState(8L, Boolean.FALSE, Boolean.FALSE, null);
    verify(processesStorage, times(8)).saveRequestState(anyLong(), any(), any(), nullable(Boolean.class));
    verify(entityManager).clear();
  }

  /**
   * The records are read by pages after the last task id of the previous
   * page, each page's entity manager cleared, until a short page; only the
   * records without state are read, so a rerun reads what a failure left.
   * Mutation: restart every page from 0, or stop after the first page, and
   * this test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  void theRecordsAreReadPageByPageAfterTheLastTaskId() throws Exception {
    long[] first = LongStream.rangeClosed(1, ProcessesRequestStateUpgradePlugin.PAGE_SIZE).toArray();
    page(0L, first);
    page(ProcessesRequestStateUpgradePlugin.PAGE_SIZE, 900L);
    lenient().when(taskService.getTask(anyLong())).thenAnswer(invocation -> openTask(invocation.getArgument(0)));

    plugin.processUpgrade("7.2", "7.3");

    verify(requestDAO).findWithoutStateAfter(0L, ProcessesRequestStateUpgradePlugin.PAGE_SIZE);
    verify(requestDAO).findWithoutStateAfter(ProcessesRequestStateUpgradePlugin.PAGE_SIZE, ProcessesRequestStateUpgradePlugin.PAGE_SIZE);
    verify(processesStorage).saveRequestState(900L, Boolean.FALSE, Boolean.FALSE, null);
    verify(processesStorage, times(ProcessesRequestStateUpgradePlugin.PAGE_SIZE + 1)).saveRequestState(anyLong(),
                                                                                                         any(),
                                                                                                         any(),
                                                                                                         nullable(Boolean.class));
    verify(entityManager, times(2)).clear();
  }

  /**
   * A failing write stops the upgrade with its failure, so commons does not
   * record it as done, and the records after it are left for the next run.
   * Mutation: swallow the failure and the next record is written.
   */
  @Test
  void aFailingWriteStopsTheUpgrade() {
    page(0L, 1L, 2L);
    task(1L, "Canceled", true);
    when(processesStorage.saveRequestState(eq(1L), any(), any(), nullable(Boolean.class))).thenThrow(new IllegalStateException("db down"));

    assertThrows(IllegalStateException.class, () -> plugin.processUpgrade("7.2", "7.3"));

    verify(processesStorage, never()).saveRequestState(eq(2L), any(), any(), nullable(Boolean.class));
  }

  /**
   * Makes the DAO answer a page of records without state.
   *
   * @param afterTaskId the page's start
   * @param taskIds the records' task ids
   */
  private void page(long afterTaskId, long... taskIds) {
    List<RequestEntity> records = new ArrayList<>();
    for (long taskId : taskIds) {
      records.add(new RequestEntity(taskId, 1L, 5L));
    }
    when(requestDAO.findWithoutStateAfter(afterTaskId, ProcessesRequestStateUpgradePlugin.PAGE_SIZE)).thenReturn(records);
  }

  /**
   * Makes Task answer a task in a column.
   *
   * @param id the task id
   * @param column the column's name, or null for a task with no column
   * @param completed its completion
   * @throws EntityNotFoundException never, the mock throws nothing
   */
  private void task(long id, String column, boolean completed) {
    TaskDto task = new TaskDto();
    task.setId(id);
    task.setCompleted(completed);
    if (column != null) {
      StatusDto status = new StatusDto();
      status.setName(column);
      task.setStatus(status);
    }
    try {
      when(taskService.getTask(id)).thenReturn(task);
    } catch (EntityNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Builds an open task in a column that names no legacy state.
   *
   * @param id the task id
   * @return the task
   */
  private static TaskDto openTask(long id) {
    TaskDto task = new TaskDto();
    task.setId(id);
    StatusDto status = new StatusDto();
    status.setName("To Do");
    task.setStatus(status);
    return task;
  }
}
