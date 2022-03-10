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
import org.exoplatform.services.attachments.model.Attachment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkFlow {

  private long              id;

  private String            title;

  private String            description;

  private String            summary;

  private String            image;

  private String            helpLink;

  private boolean           enabled;

  private long              creatorId;

  private Date              createdDate;

  private long              modifierId;

  private Date              modifiedDate;

  private long              projectId;

  private ProcessPermission acl;

  private Attachment[]     attachments;


}
