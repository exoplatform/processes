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
package org.exoplatform.processes.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The record of a request: the task a process created, the process that
 * created it and its requester. It tells a request apart from any other task
 * of a task project, which several processes may feed.
 */
@Entity(name = "ProcessesRequest")
@NamedQuery(name = "ProcessesRequest.deleteByWorkFlowId", query = "DELETE FROM ProcessesRequest r WHERE r.workFlowId = :workFlowId")
@NamedQuery(name = "ProcessesRequest.findByTaskIds", query = "SELECT r FROM ProcessesRequest r WHERE r.taskId IN :taskIds")
@NamedQuery(name = "ProcessesRequest.findTaskIdsByWorkFlowId", query = "SELECT r.taskId FROM ProcessesRequest r WHERE r.workFlowId = :workFlowId ORDER BY r.taskId")
@NamedQuery(name = "ProcessesRequest.findTaskIdsAfter", query = "SELECT r.taskId FROM ProcessesRequest r WHERE r.workFlowId = :workFlowId AND r.taskId > :afterTaskId ORDER BY r.taskId")
@NamedQuery(name = "ProcessesRequest.deleteByTaskId", query = "DELETE FROM ProcessesRequest r WHERE r.taskId = :taskId")
@NamedQuery(name = "ProcessesRequest.findWithoutStateAfter", query = "SELECT r FROM ProcessesRequest r WHERE r.canceled IS NULL AND r.archived IS NULL AND r.taskId > :afterTaskId ORDER BY r.taskId")
@Table(name = "PROCESSES_REQUEST")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestEntity implements Serializable {

  private static final long serialVersionUID = -2786440862394017245L;

  /** The id of the request's task, in the Task add-on */
  @Id
  @Column(name = "TASK_ID", nullable = false)
  private Long              taskId;

  /** The id of the process that created the request */
  @Column(name = "WORK_FLOW_ID", nullable = false)
  private Long              workFlowId;

  /** The identity id of the requester */
  @Column(name = "CREATOR_ID", nullable = false)
  private Long              creatorId;

  /**
   * Whether the requester cancelled the request; null, read as false, until
   * the request is cancelled or the upgrade filled the row.
   */
  @Column(name = "CANCELED")
  private Boolean           canceled;

  /**
   * Whether the requester archived the request from « My requests »; null,
   * read as false, until the request is archived or the upgrade filled the
   * row. The task is not touched by an archive.
   */
  @Column(name = "ARCHIVED")
  private Boolean           archived;

  /**
   * Creates a request record whose state is not known: the record of a request
   * created before EXO-94923, which the state upgrade fills. A new request's
   * record names its state.
   *
   * @param taskId the request's task id
   * @param workFlowId the process that created it
   * @param creatorId the requester's identity id
   */
  public RequestEntity(Long taskId, Long workFlowId, Long creatorId) {
    this(taskId, workFlowId, creatorId, null, null);
  }

  /**
   * Tells whether the requester cancelled the request.
   *
   * @return true when cancelled, false when not or not known
   */
  public boolean isCanceledRequest() {
    return Boolean.TRUE.equals(canceled);
  }

  /**
   * Tells whether the requester archived the request.
   *
   * @return true when archived, false when not or not known
   */
  public boolean isArchivedRequest() {
    return Boolean.TRUE.equals(archived);
  }

}
