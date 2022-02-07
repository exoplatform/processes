package org.exoplatform.processes.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.*;

import org.exoplatform.commons.api.persistence.ExoEntity;

import lombok.Data;

@Entity(name = "WorkFlow")
@ExoEntity
@Table(name = "PROCESSES_WORK_FLOW")
@Data
@NamedQueries({
        @NamedQuery(name = "WorkFlow.getWorkFlowByProjectId", query = "SELECT DISTINCT c FROM WorkFlow c where c.projectId = :projectId"),
        @NamedQuery(name = "WorkFlow.findAllWorkFlowsByUser", query = "SELECT DISTINCT c FROM WorkFlow c where c.creatorId = :userId order by c.id desc"),
        @NamedQuery(name = "WorkFlow.findEnabledWorkFlowsByUser", query = "SELECT DISTINCT c FROM WorkFlow c where c.creatorId = :userId and  c.enabled = true order by c.id desc"),
        @NamedQuery(name = "WorkFlow.findAllWorkFlows", query = "SELECT DISTINCT c FROM WorkFlow c order by c.id desc"),
        @NamedQuery(name = "WorkFlow.findEnabledWorkFlows", query = "SELECT DISTINCT c FROM WorkFlow c where c.enabled = true order by c.id desc"),
})

public class WorkFlowEntity implements Serializable {

  @Id
  @SequenceGenerator(name = "SEQ_WORK_FLOW_ID", sequenceName = "SEQ_WORK_FLOW_ID", allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_WORK_FLOW_ID")
  @Column(name = "WORK_FLOW_ID", nullable = false)
  private Long   id;

  @Column(name = "TITLE", nullable = false)
  private String title;

  @Column(name = "DESCRIPTION")
  private String description;

  @Column(name = "SUMMARY")
  private String summary;

  @Column(name = "ENABLED")
  private boolean enabled;

  @Column(name = "HELP_LINK")
  private String helpLink;

  @Column(name = "IMAGE")
  private String image;

  @Column(name = "CREATOR_ID")
  private Long   creatorId;

  @Column(name = "CREATED_DATE", nullable = false)
  private Date   createdDate;

  @Column(name = "MODIFIER_ID")
  private Long   modifierId;

  @Column(name = "MODIFIED_DATE", nullable = false)
  private Date   modifiedDate;

  @Column(name = "PROJECT_ID")
  private Long   projectId;

}
