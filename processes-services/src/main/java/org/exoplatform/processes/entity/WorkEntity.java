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

import lombok.Data;
import org.exoplatform.commons.api.persistence.ExoEntity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity(name = "Work")
@ExoEntity
@Table(name = "PROCESSES_WORK")
@Data
@NamedQueries({
    @NamedQuery(name = "Work.findAllWorkDraftsByUser", query = "SELECT DISTINCT w FROM Work w where w.creatorId = :userId and w.isDraft = true order by w.id desc"),
    @NamedQuery(name = "Work.getWorkDraftByTaskId", query = "SELECT DISTINCT w FROM Work w where w.taskId = :taskId and w.isDraft = true"),
    @NamedQuery(name = "Work.findAllWorkDraftsByWorkflowId", query = "SELECT DISTINCT w FROM Work w where w.workFlow.id = :workflowId and w.isDraft = true"),})
public class WorkEntity implements Serializable {

    @Id
    @SequenceGenerator(name = "SEQ_WORK_ID", sequenceName = "SEQ_WORK_ID", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_WORK_ID")
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
