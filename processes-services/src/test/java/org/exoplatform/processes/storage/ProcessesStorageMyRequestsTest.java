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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.processes.dao.RequestDAO;
import org.exoplatform.processes.dao.WorkDraftDAO;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFilter;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.task.dao.TaskQuery;
import org.exoplatform.task.dao.condition.AggregateCondition;
import org.exoplatform.task.dao.condition.Condition;
import org.exoplatform.task.dao.condition.PredicateCondition;
import org.exoplatform.task.dao.condition.SingleCondition;
import org.exoplatform.task.dto.CommentsSummaryDto;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.service.CommentService;
import org.exoplatform.task.service.TaskService;

/**
 * The reads behind « My requests »: the list and the count of a user's
 * requests ask the Task add-on the same query, and the list carries each
 * request's comments count only when asked.
 */
@ExtendWith(MockitoExtension.class)
class ProcessesStorageMyRequestsTest {

  private static final long   USER_ID      = 7L;

  private static final String USER_NAME    = "mary";

  @Mock
  private WorkFlowDAO          workFlowDAO;

  @Mock
  private WorkDraftDAO         workDraftDAO;

  @Mock
  private RequestDAO           requestDAO;

  @Mock
  private TaskService          taskService;

  @Mock
  private IdentityManager      identityManager;

  @Mock
  private CommentService       commentService;

  private ProcessesStorageImpl processesStorage;

  /**
   * Builds the storage for a user named {@link #USER_NAME}.
   */
  @BeforeEach
  void setUp() {
    processesStorage = new ProcessesStorageImpl(workFlowDAO,
                                                workDraftDAO,
                                                requestDAO,
                                                taskService,
                                                null,
                                                null,
                                                identityManager,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                commentService);
    Identity identity = mock(Identity.class);
    when(identity.getRemoteId()).thenReturn(USER_NAME);
    when(identityManager.getIdentity(String.valueOf(USER_ID))).thenReturn(identity);
  }

  /**
   * The count asks the Task add-on for the user's own request tasks, with the
   * filter's completion and status. Mutation: drop the creator, the request
   * condition, the completion or the status, or invert the completion, and
   * this test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  void theCountAsksForTheUsersOwnRequestsWithTheFilter() throws Exception {
    when(taskService.countTasks(any(TaskQuery.class))).thenReturn(12);
    WorkFilter filter = new WorkFilter();
    filter.setCompleted(true);
    filter.setStatus("Validated");

    assertEquals(12, processesStorage.countWorks(USER_ID, filter));

    ArgumentCaptor<TaskQuery> query = ArgumentCaptor.forClass(TaskQuery.class);
    verify(taskService).countTasks(query.capture());
    Map<String, SingleCondition<?>> conditions = conditionsByField(query.getValue());
    assertCondition(conditions, "createdBy", SingleCondition.EQ, USER_NAME);
    assertTrue(hasRequestCondition(query.getValue()), "the count keeps to request tasks");
    assertCondition(conditions, "completed", SingleCondition.IS_TRUE, null);
    assertCondition(conditions, "status.name", SingleCondition.EQ, "Validated");
  }

  /**
   * The list and the count read the same request set: the list's query
   * carries the same conditions as the count's, the list adding only its
   * order. Mutation: build the count from another query and this test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  void theListAndTheCountAskTheSameQuery() throws Exception {
    WorkFilter filter = new WorkFilter();
    filter.setCompleted(false);
    when(taskService.findTasks(any(TaskQuery.class), eq(0), eq(51))).thenReturn(List.of());

    processesStorage.getWorks(USER_ID, filter, 0, 51);
    processesStorage.countWorks(USER_ID, filter);

    ArgumentCaptor<TaskQuery> listQuery = ArgumentCaptor.forClass(TaskQuery.class);
    verify(taskService).findTasks(listQuery.capture(), eq(0), eq(51));
    ArgumentCaptor<TaskQuery> countQuery = ArgumentCaptor.forClass(TaskQuery.class);
    verify(taskService).countTasks(countQuery.capture());
    assertEquals(describe(conditionsByField(countQuery.getValue())), describe(conditionsByField(listQuery.getValue())));
    assertTrue(hasRequestCondition(countQuery.getValue()), "the count keeps to request tasks");
    assertTrue(hasRequestCondition(listQuery.getValue()), "the list keeps to request tasks");
    assertCondition(conditionsByField(listQuery.getValue()), "completed", SingleCondition.IS_FALSE, null);
    assertEquals("id", listQuery.getValue().getOrderBy().get(0).getFieldName());
    assertFalse(listQuery.getValue().getOrderBy().get(0).isAscending());
  }

  /**
   * A list asked with the comments count carries each request's count of
   * comments, replies included, as the request drawer counts them, read for
   * the whole page in one call; a request without comments counts 0.
   * Mutation: skip the count, read it per request, or leave a request without
   * comments unset, and this test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  void aListAskedWithTheCommentsCountCarriesItRepliesIncluded() throws Exception {
    when(taskService.findTasks(any(TaskQuery.class), anyInt(), anyInt())).thenReturn(List.of(task(100L), task(101L)));
    when(commentService.getCommentsSummaries(List.of(100L, 101L))).thenReturn(Map.of(100L,
                                                                                      new CommentsSummaryDto(100L, 5, null, "john", 9L)));
    WorkFilter filter = new WorkFilter();
    filter.setWithCommentsCount(true);

    List<Work> works = processesStorage.getWorks(USER_ID, filter, 0, 0);

    assertEquals(Integer.valueOf(5), works.get(0).getCommentsCount());
    assertEquals(Integer.valueOf(0), works.get(1).getCommentsCount());
    verify(commentService).getCommentsSummaries(List.of(100L, 101L));
    verify(commentService, never()).countComments(anyLong());
    verify(commentService, never()).countCommentsWithSubs(anyLong());
  }

  /**
   * A list asked without the comments count reads no comment. Mutation: count
   * whatever the filter says and this test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  void aListAskedWithoutTheCommentsCountReadsNoComment() throws Exception {
    when(taskService.findTasks(any(TaskQuery.class), anyInt(), anyInt())).thenReturn(List.of(task(100L)));

    List<Work> works = processesStorage.getWorks(USER_ID, new WorkFilter(), 0, 0);

    assertNull(works.get(0).getCommentsCount());
    verifyNoInteractions(commentService);
  }

  private static void assertCondition(Map<String, SingleCondition<?>> conditions, String field, String type, Object value) {
    SingleCondition<?> condition = conditions.get(field);
    assertEquals(type, condition == null ? null : condition.getType(), "condition on " + field);
    if (value != null) {
      assertEquals(value, condition.getValue(), "value of " + field);
    }
  }

  private static String describe(Map<String, SingleCondition<?>> conditions) {
    StringBuilder description = new StringBuilder();
    conditions.values().forEach(condition -> description.append(condition.getField())
                                                        .append(' ')
                                                        .append(condition.getType())
                                                        .append(' ')
                                                        .append(condition.getValue())
                                                        .append(';'));
    return description.toString();
  }

  private static Map<String, SingleCondition<?>> conditionsByField(TaskQuery query) {
    Map<String, SingleCondition<?>> conditions = new java.util.TreeMap<>();
    collect(query.getCondition(), conditions);
    return conditions;
  }

  private static void collect(Condition condition, Map<String, SingleCondition<?>> conditions) {
    if (condition instanceof AggregateCondition aggregate) {
      aggregate.getConditions().forEach(child -> collect(child, conditions));
    } else if (condition instanceof SingleCondition<?> single) {
      conditions.put(single.getField(), single);
    }
  }

  private static boolean hasRequestCondition(TaskQuery query) {
    return query.getCondition() instanceof AggregateCondition aggregate
        && aggregate.getConditions().stream().anyMatch(PredicateCondition.class::isInstance);
  }

  private static TaskDto task(long id) {
    TaskDto task = new TaskDto();
    task.setId(id);
    return task;
  }
}
