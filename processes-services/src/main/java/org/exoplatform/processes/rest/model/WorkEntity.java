/*
 * Copyright (C) 2021 eXo Platform SAS
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

package org.exoplatform.processes.rest.model;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkEntity {

  private long           id;

  private String         title;

  private String         description;

  private String         status;

  private boolean        completed;

  private String         createdBy;

  private Date           createdTime;

  private Date           modifiedTime;

  private Date           startDate;

  private Date           endDate;

  private Date           dueDate;

  private long           projectId;

  private WorkFlowEntity workFlow;

  private long           creatorId;

  private Long           draftId;

  private Long           taskId;

  private Boolean        isDraft;

  public WorkEntity(long id,
                    String title,
                    String description,
                    String status,
                    boolean completed,
                    String createdBy,
                    Date createdTime,
                    long projectId) {
    this.id = id;
    this.title = title;
    this.description = description;
    this.status = status;
    this.completed = completed;
    this.createdBy = createdBy;
    this.createdTime = createdTime;
    this.projectId = projectId;
  }

  public WorkEntity(long id,
                    String title,
                    String description,
                    long creatorId,
                    Date createdTime,
                    Date modifiedTime,
                    Long taskId,
                    Boolean isDraft,
                    WorkFlowEntity workFlow) {
    this.id = id;
    this.title = title;
    this.description = description;
    this.creatorId = creatorId;
    this.createdTime = createdTime;
    this.modifiedTime = modifiedTime;
    this.taskId = taskId;
    this.isDraft = isDraft;
    this.workFlow = workFlow;
  }
}
