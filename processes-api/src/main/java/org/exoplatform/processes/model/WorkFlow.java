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

package org.exoplatform.processes.model;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.exoplatform.processes.constant.ProjectMode;
import org.exoplatform.services.attachments.model.Attachment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WorkFlow {

  private long                        id;

  private String                      title;

  private String                      description;

  private String                      summary;

  private String                      image;

  private String                      helpLink;

  private boolean                     enabled;

  private long                        creatorId;

  private Date                        createdDate;

  private long                        modifierId;

  private Date                        modifiedDate;

  private long                        projectId;

  private String                      spaceId;

  private ProcessPermission           acl;

  private Attachment[]                attachments;

  private IllustrativeAttachment      illustrativeAttachment;

  private Set<String>                 manager;

  private Set<String>                 participator;

  private boolean canShowPending;

  private List<CreatorIdentityEntity> requestsCreators;

  /**
   * The labels the process applies to each request it creates, all of its task
   * project. Null when the caller does not state them: an update then keeps
   * the stored ones.
   */
  private List<WorkFlowLabel>         labels;

  /**
   * How a save chooses the process's task project: a new one, or the existing
   * one {@link #projectId} names. It is an instruction of the save, not a state
   * of the process, so it is left out of the comparison of two processes. Null
   * when the caller does not state it.
   */
  @EqualsAndHashCode.Exclude
  private ProjectMode                 projectMode;

  /**
   * The title per language a save writes with the process, by language tag;
   * the default language's value is the title itself. Null when the caller
   * does not state it: the stored translations then stay. The three
   * translation maps are part of the comparison of two processes, so that a
   * save changing translations only is a change: a process read from
   * storage never carries them.
   */
  private Map<String, String>         titleTranslations;

  /**
   * The description per language a save writes with the process.
   *
   * @see #titleTranslations
   */
  private Map<String, String>         descriptionTranslations;

  /**
   * The summary per language a save writes with the process.
   *
   * @see #titleTranslations
   */
  private Map<String, String>         summaryTranslations;

  /**
   * The form a save chooses for the process's requests: null when the save
   * does not state it, the stored form being kept; 0 for no form; else the id
   * of the form. It is an instruction of the save, not a state of the process:
   * the process stores no form id, and it is left out of the comparison of two
   * processes.
   */
  @EqualsAndHashCode.Exclude
  private Long                        requestFormId;

  /**
   * Builds a process with every field but the save's project mode.
   *
   * @param id the process id, 0 for a new process
   * @param title its title
   * @param description its description
   * @param summary its summary
   * @param image its image
   * @param helpLink its help link
   * @param enabled whether requests may be added to it
   * @param creatorId its creator's identity id
   * @param createdDate its creation date
   * @param modifierId its last modifier's identity id
   * @param modifiedDate its last modification date
   * @param projectId its task project id
   * @param spaceId its managing space id, blank when unknown
   * @param acl the permissions of the reading user on it
   * @param attachments its attachments
   * @param illustrativeAttachment its illustration
   * @param manager the memberships and users who may add requests to it
   * @param participator the memberships of its managing space
   * @param canShowPending whether the reading user sees its pending requests
   * @param requestsCreators the identities who may add requests to it
   */
  @SuppressWarnings("java:S107")
  public WorkFlow(long id,
                  String title,
                  String description,
                  String summary,
                  String image,
                  String helpLink,
                  boolean enabled,
                  long creatorId,
                  Date createdDate,
                  long modifierId,
                  Date modifiedDate,
                  long projectId,
                  String spaceId,
                  ProcessPermission acl,
                  Attachment[] attachments,
                  IllustrativeAttachment illustrativeAttachment,
                  Set<String> manager,
                  Set<String> participator,
                  boolean canShowPending,
                  List<CreatorIdentityEntity> requestsCreators) {
    this.id = id;
    this.title = title;
    this.description = description;
    this.summary = summary;
    this.image = image;
    this.helpLink = helpLink;
    this.enabled = enabled;
    this.creatorId = creatorId;
    this.createdDate = createdDate;
    this.modifierId = modifierId;
    this.modifiedDate = modifiedDate;
    this.projectId = projectId;
    this.spaceId = spaceId;
    this.acl = acl;
    this.attachments = attachments;
    this.illustrativeAttachment = illustrativeAttachment;
    this.manager = manager;
    this.participator = participator;
    this.canShowPending = canShowPending;
    this.requestsCreators = requestsCreators;
  }

}
