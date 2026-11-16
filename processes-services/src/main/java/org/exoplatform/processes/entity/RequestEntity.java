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

}
