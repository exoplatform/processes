/*
 * Copyright (C) 2022 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.processes.entity;

import java.io.Serializable;
import java.util.Date;

import io.meeds.common.persistence.PortableSequence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import lombok.Data;

@Entity(name = "Work")
@Table(name = "PROCESSES_WORK")
@Data
@NamedQuery(name = "Work.findAllWorkDraftsByUser", query = "SELECT DISTINCT w FROM Work w where w.creatorId = :userId and w.isDraft = true order by w.id desc")
@NamedQuery(name = "Work.getWorkDraftByTaskId", query = "SELECT DISTINCT w FROM Work w where w.taskId = :taskId and w.isDraft = true")
@NamedQuery(name = "Work.findAllWorkDraftsByWorkflowId", query = "SELECT DISTINCT w FROM Work w where w.workFlow.id = :workflowId and w.isDraft = true")
public class WorkEntity implements Serializable {

    private static final long serialVersionUID = -8490912256477367410L;

    @Id
    @PortableSequence(name = "SEQ_WORK_ID")
    @Column(name = "WORK_ID", nullable = false)
    private Long   id;

    @Column(name = "TITLE", nullable = false)
    private String title;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "CREATOR_ID")
    private Long   creatorId;

    @Column(name = "TASK_ID")
    private Long   taskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "WORKFLOW_ID", referencedColumnName = "WORK_FLOW_ID")
    private WorkFlowEntity workFlow;

    @Column(name = "IS_DRAFT")
    private Boolean   isDraft;

    @Column(name = "CREATED_DATE", nullable = false)
    private Date createdDate;

    @Column(name = "MODIFIED_DATE", nullable = false)
    private Date   modifiedDate;

}
