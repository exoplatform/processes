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
