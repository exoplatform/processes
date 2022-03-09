/*
 * Copyright (C) 2022 eXo Platform SAS
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
package org.exoplatform.processes.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Work {

  private long     id;

  private String   title;

  private String   description;

  private String   status;

  private boolean  completed;

  private String   createdBy;

  private Date     startDate;

  private Date     endDate;

  private Date     dueDate;

  private long     projectId;

  private long     creatorId;

  private Date     createdDate;

  private Date     modifiedDate;

  private Long     taskId;

  private Boolean  isDraft;

  private Long     draftId;

  private WorkFlow workFlow;

  /**
   * constructor for Work task object
   *
   * @param id Work id
   * @param title Work title
   * @param description Work description
   * @param status Work task status
   * @param completed Work task completed property
   * @param createdBy Work task creator
   * @param createdDate Work task creation date
   * @param startDate Work task start date
   * @param endDate Work task end date
   * @param dueDate Work task due date
   * @param isDraft When the object is a draft object, which will be used to create the work
   * @param draftId The already saved draft id, which will be deleted once the work created from the draft
   * @param projectId Work task project
   */
  public Work(long id,
                  String title,
                  String description,
                  String status,
                  boolean completed,
                  String createdBy,
                  Date createdDate,
                  Date startDate,
                  Date endDate,
                  Date dueDate,
                  Boolean isDraft,
                  Long draftId,
                  long projectId) {
    this.id = id;
    this.title = title;
    this.description = description;
    this.status = status;
    this.completed = completed;
    this.createdBy = createdBy;
    this.createdDate = createdDate;
    this.startDate = startDate;
    this.endDate = endDate;
    this.dueDate = dueDate;
    this.isDraft = isDraft;
    this.draftId = draftId;
    this.projectId = projectId;
  }

  /**
   * Constructor for a work object, can be a work draft
   *
   * @param id Work id
   * @param title Work title
   * @param description Work description
   * @param creatorId Work creator id
   * @param createdDate Work created date
   * @param modifiedDate Work modified date
   * @param taskId Work task id if it's a draft of already created work or null
   * @param workFlow Related workflow
   */
  public Work(long id,
              String title,
              String description,
              long creatorId,
              Date createdDate,
              Date modifiedDate,
              Long taskId,
              Boolean isDraft,
              WorkFlow workFlow) {
    
    this.id = id;
    this.title = title;
    this.description = description;
    this.creatorId = creatorId;
    this.createdDate = createdDate;
    this.modifiedDate = modifiedDate;
    this.taskId = taskId;
    this.isDraft = isDraft;
    this.workFlow = workFlow;
  }
  
}
