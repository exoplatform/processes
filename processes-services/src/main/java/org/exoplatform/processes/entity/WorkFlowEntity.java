package org.exoplatform.processes.entity;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import io.meeds.common.persistence.PortableSequence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import lombok.Data;

@Entity(name = "WorkFlow")
@Table(name = "PROCESSES_WORK_FLOW")
@Data
@NamedQuery(name = "WorkFlow.findProcessesOfProjects", query = "SELECT w.projectId, w.id, w.title FROM WorkFlow w WHERE w.projectId IN (:projectIds) AND w.spaceId = :spaceId ORDER BY w.title, w.id")
@NamedQuery(name = "WorkFlow.getWorkFlowByProjectId", query = "SELECT DISTINCT c FROM WorkFlow c where c.projectId = :projectId order by c.id")
@NamedQuery(name = "WorkFlow.findAllWorkFlowsByUser", query = "SELECT DISTINCT c FROM WorkFlow c where c.creatorId = :userId order by c.id desc")
@NamedQuery(name = "WorkFlow.findEnabledWorkFlowsByUser", query = "SELECT DISTINCT c FROM WorkFlow c where c.creatorId = :userId and  c.enabled = true order by c.id desc")
@NamedQuery(name = "WorkFlow.findAllWorkFlows", query = "SELECT DISTINCT c FROM WorkFlow c order by c.id desc")
@NamedQuery(name = "WorkFlow.findEnabledWorkFlows", query = "SELECT DISTINCT c FROM WorkFlow c where c.enabled = true order by c.id desc")
@NamedQuery(name = "WorkFlow.findDisabledWorkFlows", query = "SELECT DISTINCT c FROM WorkFlow c where c.enabled = false order by c.id desc")
// The move of a process's requests to another task project: only these bulk
// updates write the move columns, which the managed entity never writes
@NamedQuery(name = "WorkFlow.requestMove", query = "UPDATE WorkFlow w SET w.projectMovePending = true, w.moveSeq = w.moveSeq + 1, w.moveAttempts = 0, w.moveNextAttempt = :now, w.moveRequestedBy = :requestedBy WHERE w.id = :id")
@NamedQuery(name = "WorkFlow.claimMove", query = "UPDATE WorkFlow w SET w.moveOwner = :token, w.moveLeaseUntil = :until WHERE w.id = :id AND w.projectMovePending = true AND w.moveNextAttempt IS NOT NULL AND w.moveNextAttempt <= :now AND (w.moveOwner IS NULL OR w.moveLeaseUntil < :now)")
@NamedQuery(name = "WorkFlow.renewMove", query = "UPDATE WorkFlow w SET w.moveLeaseUntil = :until WHERE w.id = :id AND w.moveOwner = :token AND w.moveLeaseUntil >= :now")
@NamedQuery(name = "WorkFlow.releaseMove", query = "UPDATE WorkFlow w SET w.moveOwner = NULL WHERE w.id = :id AND w.moveOwner = :token")
@NamedQuery(name = "WorkFlow.stopMove", query = "UPDATE WorkFlow w SET w.moveNextAttempt = NULL WHERE w.id = :id AND w.moveSeq = :seq AND w.moveOwner = :token")
@NamedQuery(name = "WorkFlow.clearMove", query = "UPDATE WorkFlow w SET w.projectMovePending = false, w.moveAttempts = 0 WHERE w.id = :id AND w.moveSeq = :seq AND w.moveOwner = :token AND w.moveLeaseUntil >= :now")
@NamedQuery(name = "WorkFlow.verifyMove", query = "UPDATE WorkFlow w SET w.moveNextAttempt = :nextAttempt WHERE w.id = :id AND w.moveSeq = :seq AND w.moveOwner = :token AND w.moveLeaseUntil >= :now")
@NamedQuery(name = "WorkFlow.retryMove", query = "UPDATE WorkFlow w SET w.moveNextAttempt = :nextAttempt, w.moveAttempts = w.moveAttempts + 1 WHERE w.id = :id AND w.moveSeq = :seq AND w.moveOwner = :token AND w.moveLeaseUntil >= :now")
@NamedQuery(name = "WorkFlow.findMoveState", query = "SELECT w.moveSeq, w.moveRequestedBy, w.projectId, w.moveAttempts FROM WorkFlow w WHERE w.id = :id AND w.moveOwner = :token")
@NamedQuery(name = "WorkFlow.findDueMoves", query = "SELECT w.id FROM WorkFlow w WHERE w.projectMovePending = true AND w.moveNextAttempt IS NOT NULL AND w.moveNextAttempt <= :now AND (w.moveOwner IS NULL OR w.moveLeaseUntil < :now) ORDER BY w.moveNextAttempt, w.id")
@NamedQuery(name = "WorkFlow.findProjectId", query = "SELECT w.projectId FROM WorkFlow w WHERE w.id = :id")
public class WorkFlowEntity implements Serializable {

  private static final long serialVersionUID = 6804751507896811367L;

  @Id
  @PortableSequence(name = "SEQ_WORK_FLOW_ID")
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

  @Column(name = "ILLUSTRATION_IMAGE_ID")
  private Long illustrationImageId;

  /** The id of the process's managing space, null when it is not known */
  @Column(name = "SPACE_ID")
  private Long spaceId;

  // The move of the process's requests to another task project. Written only
  // by the JPQL bulk updates above: the managed entity has neither a version
  // nor dynamic updates, so a stale copy saved by a process edit would write
  // them back. Times are epoch milliseconds.

  /** True while some requests of the process may be outside its project */
  @Column(name = "PROJECT_MOVE_PENDING", insertable = false, updatable = false)
  private Boolean projectMovePending;

  /** Incremented by each save that changes the project */
  @ColumnDefault("0")
  @Column(name = "MOVE_SEQ", nullable = false, insertable = false, updatable = false)
  private Long moveSeq;

  /** The token of the pass that holds the move, null when none does */
  @Column(name = "MOVE_OWNER", insertable = false, updatable = false)
  private String moveOwner;

  /** When the holding pass's claim expires */
  @Column(name = "MOVE_LEASE_UNTIL", insertable = false, updatable = false)
  private Long moveLeaseUntil;

  /** The failed attempts since the last save that changed the project */
  @ColumnDefault("0")
  @Column(name = "MOVE_ATTEMPTS", nullable = false, insertable = false, updatable = false)
  private Integer moveAttempts;

  /** When the next pass is due, null when no pass may run */
  @Column(name = "MOVE_NEXT_ATTEMPT", insertable = false, updatable = false)
  private Long moveNextAttempt;

  /** The user id of the editor who changed the project */
  @Column(name = "MOVE_REQUESTED_BY", insertable = false, updatable = false)
  private String moveRequestedBy;

  @ElementCollection
  @CollectionTable(name = "WORK_FLOW_MANAGERS",
          joinColumns = @JoinColumn(name = "WORK_FLOW_ID"))
  private Set<String> manager = new HashSet<>();

  @ElementCollection
  @CollectionTable(name = "WORK_FLOW_PARTICIPATOR",
          joinColumns = @JoinColumn(name = "WORK_FLOW_ID"))
  private Set<String> participator = new HashSet<>();

  /**
   * The ids of the Task labels the process applies to each request it creates,
   * all of its task project. No foreign key: a label deleted in Tasks is left
   * out when the process is read.
   */
  @ElementCollection
  @CollectionTable(name = "PROCESSES_WORK_FLOW_LABELS",
          joinColumns = @JoinColumn(name = "WORK_FLOW_ID"))
  @Column(name = "LABEL_ID")
  private Set<Long> labelIds = new HashSet<>();

  /**
   * The ids of the forms of the process's task project that its requesters
   * fill when they create a request. No foreign key: the forms add-on owns
   * the forms, and a form it no longer gives the project is left out where it
   * reads the choice.
   */
  @ElementCollection
  @CollectionTable(name = "PROCESSES_WORK_FLOW_REQ_FORMS",
          joinColumns = @JoinColumn(name = "WORK_FLOW_ID"))
  @Column(name = "FORM_ID")
  private Set<Long> requesterFormIds = new HashSet<>();

}