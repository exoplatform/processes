package org.exoplatform.processes.entity;

import lombok.Data;
import org.exoplatform.commons.api.persistence.ExoEntity;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity(name = "Work")
@ExoEntity
@Table(name = "PROCESSES_WORK")
@Data
@NamedQueries({
    @NamedQuery(name = "Work.findAllWorkDraftsByUser", query = "SELECT DISTINCT w FROM Work w where w.creatorId = :userId and w.isDraft = true order by w.id desc"),
    @NamedQuery(name = "Work.getWorkDraftByTaskId", query = "SELECT DISTINCT w FROM Work w where w.taskId = :taskId and w.isDraft = true"), })
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
