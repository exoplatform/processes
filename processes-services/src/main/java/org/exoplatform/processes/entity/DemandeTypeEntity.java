package org.exoplatform.processes.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.*;

import org.exoplatform.commons.api.persistence.ExoEntity;

import lombok.Data;

@Entity(name = "DamandeType")
@ExoEntity
@Table(name = "PROCESSES_DEMANDE_TYPE")
@Data
@NamedQueries({
    @NamedQuery(name = "DamandeType.findAllDamandeTypesByUser", query = "SELECT DISTINCT c FROM DamandeType c where c.creatorId = :userId order by c.id desc") })

public class DemandeTypeEntity implements Serializable {

  @Id
  @SequenceGenerator(name = "SEQ_DEMANDE_TYPE_ID", sequenceName = "SEQ_DEMANDE_TYPE_ID", allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_DEMANDE_TYPE_ID")
  @Column(name = "DEMANDE_TYPE_ID", nullable = false)
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
