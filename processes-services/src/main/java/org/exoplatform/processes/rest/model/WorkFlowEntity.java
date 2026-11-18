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
import java.util.List;
import java.util.Map;

import org.exoplatform.processes.model.CreatorIdentityEntity;
import org.exoplatform.processes.model.IllustrativeAttachment;
import org.exoplatform.processes.model.ProcessPermission;
import org.exoplatform.processes.model.WorkFlowLabel;
import org.exoplatform.services.attachments.model.Attachment;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.task.dto.StatusDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class WorkFlowEntity {

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

  private Attachment[]                attachments;

  private List<StatusDto>             statuses;

  private ProcessPermission           acl;

  private Space                       parentSpace;

  private boolean                     canShowPending;

  private IllustrativeAttachment      illustrativeAttachment;

  private List<CreatorIdentityEntity> requestsCreators;

  /**
   * How a create chooses the task project: NEW (the default) or EXISTING, the
   * project then being the one projectId names
   */
  private String                      projectMode;

  /**
   * The labels the process applies to each request it creates: [{id, name}],
   * a label with no id being a name to create in the process's project. Null
   * when not sent: an update then keeps the stored labels.
   */
  private List<WorkFlowLabel>         labels;

  /**
   * The title per language to save with the process, {fr: …, pt-BR: …} by
   * language tag: a flat map, the shape the REST JSON reader binds. Null when
   * not sent: the stored translations then stay. Never answered: a read gives
   * the texts in the reader's language instead.
   */
  private Map<String, String>         titleTranslations;

  /**
   * The description per language to save with the process.
   *
   * @see #titleTranslations
   */
  private Map<String, String>         descriptionTranslations;

  /**
   * The summary per language to save with the process.
   *
   * @see #titleTranslations
   */
  private Map<String, String>         summaryTranslations;

  /**
   * The form a save chooses for the process's requests: absent to keep the
   * stored one, 0 for no form, else the form id.
   */
  private Long                        requestFormId;

  /**
   * The forms bound to the process itself, read for a user who can edit it;
   * null otherwise.
   */
  private List<Long>                  requestFormIds;

}
