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
package org.exoplatform.processes.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;
import org.exoplatform.processes.entity.RequestEntity;

/**
 * Stores the request records, keyed by the id of the request's task.
 */
public class RequestDAO extends GenericDAOJPAImpl<RequestEntity, Long> {

  /** The most task ids one IN clause carries. */
  public static final int REQUEST_CHUNK_SIZE = 1000;

  /**
   * Reads the processes of the tasks that are requests, by chunks of
   * {@link #REQUEST_CHUNK_SIZE} task ids: one query per chunk.
   *
   * @param taskIds the task ids
   * @return task id → process id, for the tasks that have a request record
   */
  public Map<Long, Long> findWorkFlowIdsByTaskIds(Collection<Long> taskIds) {
    Map<Long, Long> workFlowIds = new HashMap<>();
    List<Long> ids = new ArrayList<>(taskIds);
    for (int from = 0; from < ids.size(); from += REQUEST_CHUNK_SIZE) {
      List<Long> chunk = ids.subList(from, Math.min(from + REQUEST_CHUNK_SIZE, ids.size()));
      getEntityManager().createNamedQuery("ProcessesRequest.findByTaskIds", RequestEntity.class)
                        .setParameter("taskIds", chunk)
                        .getResultList()
                        .forEach(request -> workFlowIds.put(request.getTaskId(), request.getWorkFlowId()));
    }
    return workFlowIds;
  }

  /**
   * Reads the request records of the tasks that are requests, by chunks of
   * {@link #REQUEST_CHUNK_SIZE} task ids: one query per chunk.
   *
   * @param taskIds the task ids
   * @return task id → request record, for the tasks that have one
   */
  public Map<Long, RequestEntity> findByTaskIds(Collection<Long> taskIds) {
    Map<Long, RequestEntity> requests = new HashMap<>();
    List<Long> ids = new ArrayList<>(taskIds);
    for (int from = 0; from < ids.size(); from += REQUEST_CHUNK_SIZE) {
      List<Long> chunk = ids.subList(from, Math.min(from + REQUEST_CHUNK_SIZE, ids.size()));
      getEntityManager().createNamedQuery("ProcessesRequest.findByTaskIds", RequestEntity.class)
                        .setParameter("taskIds", chunk)
                        .getResultList()
                        .forEach(request -> requests.put(request.getTaskId(), request));
    }
    return requests;
  }

  /**
   * Reads a page of the request records whose state was never set, neither
   * cancelled nor archived being known: the records of the requests created
   * before EXO-94923, which the state upgrade fills. A keyset read by task id,
   * so the records a page fills change none of the next.
   *
   * @param afterTaskId the last task id of the previous page, 0 for the first
   * @param limit the page size, strictly positive
   * @return the records, by ascending task id
   */
  public List<RequestEntity> findWithoutStateAfter(long afterTaskId, int limit) {
    return getEntityManager().createNamedQuery("ProcessesRequest.findWithoutStateAfter", RequestEntity.class)
                             .setParameter("afterTaskId", afterTaskId)
                             .setMaxResults(limit)
                             .getResultList();
  }

  /**
   * Reads the task ids of every request record of a process, whether the task
   * still exists or not.
   *
   * @param workFlowId the process id
   * @return the task ids, ascending
   */
  public List<Long> findTaskIdsByWorkFlowId(long workFlowId) {
    return getEntityManager().createNamedQuery("ProcessesRequest.findTaskIdsByWorkFlowId", Long.class)
                             .setParameter("workFlowId", workFlowId)
                             .getResultList();
  }

  /**
   * Reads a page of the task ids of a process's request records, by task id:
   * a keyset read, so moving the tasks of a page changes none of the next.
   *
   * @param workFlowId the process id
   * @param afterTaskId the last task id of the previous page, 0 for the first
   * @param limit the page size, strictly positive
   * @return the task ids, ascending
   */
  public List<Long> findTaskIdsAfter(long workFlowId, long afterTaskId, int limit) {
    return getEntityManager().createNamedQuery("ProcessesRequest.findTaskIdsAfter", Long.class)
                             .setParameter("workFlowId", workFlowId)
                             .setParameter("afterTaskId", afterTaskId)
                             .setMaxResults(limit)
                             .getResultList();
  }

  /**
   * Deletes the request record of a task, in the caller's transaction.
   *
   * @param taskId the task id
   * @return how many records were deleted
   */
  public int deleteByTaskId(long taskId) {
    return getEntityManager().createNamedQuery("ProcessesRequest.deleteByTaskId")
                             .setParameter("taskId", taskId)
                             .executeUpdate();
  }

  /**
   * Builds the restriction of a query to the tasks that are requests of some
   * processes: an EXISTS on the request records of those processes, correlated
   * by the task id, which the index on (WORK_FLOW_ID, TASK_ID) serves. It needs
   * no entity manager, and is built once for each query that carries it.
   *
   * @param cb the criteria builder of the query
   * @param query the query, to create the subquery from
   * @param taskId the task id expression of the query
   * @param workFlowIds the process ids
   * @return the restriction; one that no task meets when no process is given
   */
  public static Predicate requestOf(CriteriaBuilder cb,
                                    CommonAbstractCriteria query,
                                    Expression<Long> taskId,
                                    Collection<Long> workFlowIds) {
    if (workFlowIds == null || workFlowIds.isEmpty()) {
      return cb.disjunction();
    }
    Subquery<Long> requests = query.subquery(Long.class);
    Root<RequestEntity> request = requests.from(RequestEntity.class);
    requests.select(request.<Long> get("taskId"))
            .where(cb.equal(request.<Long> get("taskId"), taskId), request.<Long> get("workFlowId").in(workFlowIds));
    return cb.exists(requests);
  }

  /**
   * Builds the restriction of a query to the tasks that are requests of any
   * process, whatever project the task is in: an EXISTS on the request
   * records, correlated by the task id, which the primary key serves.
   *
   * @param cb the criteria builder of the query
   * @param query the query, to create the subquery from
   * @param taskId the task id expression of the query
   * @return the restriction
   */
  public static Predicate isRequest(CriteriaBuilder cb, CommonAbstractCriteria query, Expression<Long> taskId) {
    Subquery<Long> requests = query.subquery(Long.class);
    Root<RequestEntity> request = requests.from(RequestEntity.class);
    requests.select(request.<Long> get("taskId")).where(cb.equal(request.<Long> get("taskId"), taskId));
    return cb.exists(requests);
  }

  /**
   * Builds the restriction of a query to the tasks that are requests of any
   * process, archived or not by their requester: an EXISTS on the request
   * records, correlated by the task id, which the primary key serves. A record
   * whose archive flag is null is not archived.
   *
   * @param cb the criteria builder of the query
   * @param query the query, to create the subquery from
   * @param taskId the task id expression of the query
   * @param archived true for the archived requests only, false for the others
   *          only, null for both
   * @return the restriction
   */
  public static Predicate isRequest(CriteriaBuilder cb, CommonAbstractCriteria query, Expression<Long> taskId, Boolean archived) {
    if (archived == null) {
      return isRequest(cb, query, taskId);
    }
    Subquery<Long> requests = query.subquery(Long.class);
    Root<RequestEntity> request = requests.from(RequestEntity.class);
    Expression<Boolean> archivedFlag = request.<Boolean> get("archived");
    Predicate archiveCondition = archived.booleanValue() ? cb.isTrue(archivedFlag)
                                                         : cb.or(cb.isNull(archivedFlag), cb.isFalse(archivedFlag));
    requests.select(request.<Long> get("taskId")).where(cb.equal(request.<Long> get("taskId"), taskId), archiveCondition);
    return cb.exists(requests);
  }

  /**
   * Builds the restriction of a Task query to the tasks that are not in a
   * project: in another project, or in none. The status and its project are
   * joined LEFT, so a task with no status, or a status with no project, is
   * outside too; a count over the query counts distinct tasks, so the joins,
   * each to one row, add none.
   *
   * @param cb the criteria builder of the query
   * @param task the task root of the query, whose status is its {@code status}
   *          attribute and the status's project its {@code project} attribute
   * @param projectId the project id
   * @return the restriction
   */
  public static Predicate outsideProject(CriteriaBuilder cb, From<?, ?> task, long projectId) {
    Join<?, ?> status = task.join("status", JoinType.LEFT);
    Join<?, ?> project = status.join("project", JoinType.LEFT);
    return cb.or(cb.isNull(project.get("id")), cb.notEqual(project.<Long> get("id"), projectId));
  }

  /**
   * Deletes the request records of a process, in the caller's transaction. The
   * request tasks are not touched.
   *
   * @param workFlowId the process id
   * @return how many records were deleted
   */
  public int deleteByWorkFlowId(long workFlowId) {
    return getEntityManager().createNamedQuery("ProcessesRequest.deleteByWorkFlowId")
                             .setParameter("workFlowId", workFlowId)
                             .executeUpdate();
  }
}
