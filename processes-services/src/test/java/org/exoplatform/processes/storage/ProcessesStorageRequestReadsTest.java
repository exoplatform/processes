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
package org.exoplatform.processes.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.exoplatform.processes.dao.RequestDAO;
import org.exoplatform.task.dao.TaskQuery;
import org.exoplatform.task.dao.condition.AggregateCondition;
import org.exoplatform.task.dao.condition.Condition;
import org.exoplatform.task.dao.condition.PredicateCondition;
import org.exoplatform.task.domain.Task;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.service.TaskService;

/**
 * The request reads of the processes storage: how they ask the Task add-on for
 * the request tasks of a process. The restriction itself runs through a real
 * engine in {@code RequestDAOTest}.
 */
@RunWith(MockitoJUnitRunner.class)
public class ProcessesStorageRequestReadsTest {

  @Mock
  private RequestDAO           requestDAO;

  @Mock
  private org.exoplatform.processes.dao.WorkFlowDAO  workFlowDAO;

  @Mock
  private org.exoplatform.processes.dao.WorkDraftDAO workDraftDAO;

  @Mock
  private TaskService          taskService;

  private ProcessesStorageImpl processesStorage;

  @Before
  public void setUp() {
    processesStorage = new ProcessesStorageImpl(workFlowDAO,
                                                workDraftDAO,
                                                requestDAO,
                                                taskService,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null);
  }

  /**
   * A page of request tasks is read through the Task add-on, by ascending id,
   * from the first task, and limited to the page size. Mutation: drop the order
   * or the limit and this test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  public void aFirstPageOfRequestTasksIsReadByAscendingIdAndLimited() throws Exception {
    when(taskService.findTasks(any(TaskQuery.class), eq(0), eq(50))).thenReturn(List.of(task(3), task(8)));

    assertEquals(List.of(3L, 8L), processesStorage.getRequestTaskIds(7L, null, 50));

    TaskQuery query = capturedFindQuery(50);
    assertEquals(1, conditions(query).size());
    assertEquals("id", query.getOrderBy().get(0).getFieldName());
    assertTrue(query.getOrderBy().get(0).isAscending());
  }

  /**
   * A next page starts strictly after the last task id of the previous one.
   * Mutation: compare with greater-or-equal, or drop the condition, and this
   * test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  @SuppressWarnings("unchecked")
  public void aNextPageStartsStrictlyAfterThePreviousPage() throws Exception {
    when(taskService.findTasks(any(TaskQuery.class), eq(0), eq(50))).thenReturn(List.of());

    processesStorage.getRequestTaskIds(7L, 8L, 50);

    List<Condition> conditions = conditions(capturedFindQuery(50));
    assertEquals(2, conditions.size());
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    Root<Task> root = mock(Root.class);
    Path<Object> id = mock(Path.class);
    when(root.get("id")).thenReturn(id);
    ((PredicateCondition<Task>) conditions.get(1)).getBuilder().build(cb, mock(CriteriaQuery.class), root);
    verify(cb).greaterThan((Path) id, (Comparable) 8L);
    verify(cb, never()).greaterThanOrEqualTo(any(Path.class), any(Comparable.class));
  }

  /**
   * A page of no task is refused before any read, so a caller never reads every
   * request at once. Mutation: let a zero limit through, which the Task add-on
   * reads as no limit, and this test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  public void aPageOfNoTaskIsRefused() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> processesStorage.getRequestTaskIds(7L, null, 0));
    verify(taskService, never()).findTasks(any(TaskQuery.class), anyInt(), anyInt());
  }

  /**
   * The projects of a process's requests are read as their tasks' current
   * projects, in one query restricted to the process's requests.
   */
  @Test
  public void theProjectsOfTheRequestsAreTheirTasksCurrentProjects() {
    when(taskService.<Long> selectTaskField(any(TaskQuery.class), eq("status.project.id"))).thenReturn(List.of(10L, 11L));

    assertEquals(Set.of(10L, 11L), processesStorage.getRequestProjectIds(7L));

    ArgumentCaptor<TaskQuery> query = ArgumentCaptor.forClass(TaskQuery.class);
    verify(taskService).selectTaskField(query.capture(), eq("status.project.id"));
    assertEquals(1, conditions(query.getValue()).size());
  }

  /**
   * The processes of no task are answered without a query.
   */
  @Test
  public void theProcessesOfNoTaskAreReadWithoutAQuery() {
    assertEquals(Map.of(), processesStorage.getRequestWorkFlowIds(List.of()));
    verifyNoInteractions(requestDAO);
  }

  /**
   * The count goes through the Task add-on's count, restricted to the process's
   * requests, so a request whose task was deleted is not counted.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  public void theRequestsAreCountedThroughTheirTasks() throws Exception {
    when(taskService.countTasks(any(TaskQuery.class))).thenReturn(3);

    assertEquals(3L, processesStorage.countRequests(7L));

    ArgumentCaptor<TaskQuery> query = ArgumentCaptor.forClass(TaskQuery.class);
    verify(taskService).countTasks(query.capture());
    List<Condition> conditions = conditions(query.getValue());
    assertEquals(1, conditions.size());
    assertFalse(conditions.get(0) instanceof AggregateCondition);
  }

  /**
   * A process's deletion answers the task ids its request records held, read
   * before the records are deleted, in the same transaction. Mutation: read
   * them after the deletion and the answer is empty.
   */
  @Test
  public void aDeletionAnswersTheRequestTasksReadBeforeTheRecordsGo() {
    org.exoplatform.processes.entity.WorkFlowEntity workFlow = new org.exoplatform.processes.entity.WorkFlowEntity();
    workFlow.setId(7L);
    when(workFlowDAO.find(7L)).thenReturn(workFlow);
    org.exoplatform.processes.entity.WorkEntity draft = new org.exoplatform.processes.entity.WorkEntity();
    draft.setId(50L);
    when(workDraftDAO.getDraftsByWorkflowId(7L)).thenReturn(List.of(draft));
    when(requestDAO.findTaskIdsByWorkFlowId(7L)).thenReturn(List.of(100L, 101L));

    org.exoplatform.processes.model.ProcessDeletion deletion = processesStorage.deleteWorkflowRows(7L);
    assertEquals(List.of(100L, 101L), deletion.requestTaskIds());
    assertEquals(List.of(50L), deletion.draftIds());

    org.mockito.InOrder order = org.mockito.Mockito.inOrder(requestDAO, workFlowDAO);
    order.verify(requestDAO).findTaskIdsByWorkFlowId(7L);
    order.verify(requestDAO).deleteByWorkFlowId(7L);
    order.verify(workFlowDAO).delete(workFlow);
  }

  private TaskQuery capturedFindQuery(int limit) throws Exception {
    ArgumentCaptor<TaskQuery> query = ArgumentCaptor.forClass(TaskQuery.class);
    verify(taskService).findTasks(query.capture(), eq(0), eq(limit));
    return query.getValue();
  }

  private static List<Condition> conditions(TaskQuery query) {
    return ((AggregateCondition) query.getCondition()).getConditions();
  }

  private static TaskDto task(long id) {
    TaskDto task = new TaskDto();
    task.setId(id);
    return task;
  }
}
