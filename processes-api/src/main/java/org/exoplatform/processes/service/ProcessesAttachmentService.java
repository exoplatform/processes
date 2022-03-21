package org.exoplatform.processes.service;

import org.exoplatform.services.attachments.model.Attachment;

public interface ProcessesAttachmentService {

    /**
     * Links a list of attachments to entity
     *
     * @param attachments list of attachments
     * @param userId user identity id
     * @param entityId entity id
     * @param entityType entity type
     * @param projectId task project id
     */
    void linkAttachmentsToEntity(Attachment[] attachments, Long userId, Long entityId, String entityType, Long projectId);

    /**
     * Move attachments from source entity to a dest entity
     *
     * @param userId user identity id
     * @param sourceEntityId source entity of attachments
     * @param sourceEntityType target entity type to attach files from source entity
     * @param destEntityId target entity id
     * @param destEntityType target entity type
     * @param projectId task project id
     */
    void moveAttachmentsToEntity(Long userId, Long sourceEntityId, String sourceEntityType, Long destEntityId, String destEntityType, Long projectId);

    /**
     * Copy attachments from source entity to a dest entity
     *
     * @param userId user identity id
     * @param sourceEntityId source entity of attachments
     * @param sourceEntityType target entity type to attach files from source entity
     * @param destEntityId target entity id
     * @param destEntityType target entity type
     * @param projectId task project id
     */
    void copyAttachmentsToEntity(Long userId, Long sourceEntityId, String sourceEntityType, Long destEntityId, String destEntityType, Long projectId);
}
