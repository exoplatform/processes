/*
 * Copyright (C) 2025 eXo Platform SAS
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

package org.exoplatform.processes.service;

import java.util.List;

import org.exoplatform.services.attachments.model.Attachment;

public interface ProcessAttachmentService {

  /**
   * Links a list of attachments to entity
   *
   * @param attachments list of attachments
   * @param userName user name
   * @param entityId entity id
   * @param entityType entity type
   * @param projectId task project id
   */
  void linkAttachmentsToEntity(Attachment[] attachments, String userName, Long entityId, String entityType, Long projectId);

  /**
   * Move attachments from source entity to a dest entity
   *
   * @param userName user name
   * @param sourceEntityId source entity of attachments
   * @param sourceEntityType target entity type to attach files from source entity
   * @param destEntityId target entity id
   * @param destEntityType target entity type
   * @param projectId task project id
   */
  void moveAttachmentsToEntity(String userName,
                               Long sourceEntityId,
                               String sourceEntityType,
                               Long destEntityId,
                               String destEntityType,
                               Long projectId);

  /**
   * Move attachments from source entity to a dest entity
   *
   * @param attachments list of attachment
   * @param userName user name
   * @param sourceEntityId source entity of attachments
   * @param sourceEntityType target entity type to attach files from source entity
   * @param destEntityId target entity id
   * @param destEntityType target entity type
   * @param projectId task project id
   */
  void moveAttachmentsToEntity(List<Attachment> attachments,
                               String userName,
                               Long sourceEntityId,
                               String sourceEntityType,
                               Long destEntityId,
                               String destEntityType,
                               Long projectId);

  /**
   * Copy attachments from source entity to a dest entity
   *
   * @param userName user name
   * @param sourceEntityId source entity of attachments
   * @param sourceEntityType target entity type to attach files from source entity
   * @param destEntityId target entity id
   * @param destEntityType target entity type
   * @param projectId task project id
   */
  void copyAttachmentsToEntity(String userName,
                               Long sourceEntityId,
                               String sourceEntityType,
                               Long destEntityId,
                               String destEntityType,
                               Long projectId);

  /**
   * Creates a new onlyoffice document form
   *
   * @param userName user name
   * @param title document title
   * @param path document path
   * @param pathDrive drive path
   * @param templateName document template name
   * @param entityType entity type to attach created document
   * @param entityId entity id
   * @return {@link Attachment}
   * @throws Exception if an error occurs while creating the document
   */
  Attachment createNewFormDocument(String userName,
                                   String title,
                                   String path,
                                   String pathDrive,
                                   String templateName,
                                   String entityType,
                                   Long entityId) throws Exception;
}
