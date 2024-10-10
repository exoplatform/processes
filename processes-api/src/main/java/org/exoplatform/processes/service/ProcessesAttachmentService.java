package org.exoplatform.processes.service;

import org.exoplatform.services.attachments.model.Attachment;

import java.util.List;

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
     * Move attachments from source entity to a dest entity
     *
     * @param attachments list of attachment
     * @param userId user identity id
     * @param sourceEntityId source entity of attachments
     * @param sourceEntityType target entity type to attach files from source entity
     * @param destEntityId target entity id
     * @param destEntityType target entity type
     * @param projectId task project id
     */
    void moveAttachmentsToEntity(List<Attachment> attachments,
                                 Long userId,
                                 Long sourceEntityId,
                                 String sourceEntityType,
                                 Long destEntityId,
                                 String destEntityType,
                                 Long projectId);

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

    /**
     * Creates a new onlyoffice document form
     *
     * @param userIdentityId user identity id
     * @param title document title
     * @param path document path
     * @param pathDrive drive path
     * @param templateName document template name
     * @param entityType entity type to attach created document
     * @param entityId entity id
     * @return {@link Attachment}
     * @throws Exception
     */
    Attachment createNewFormDocument(Long userIdentityId,
                                     String title,
                                     String path,
                                     String pathDrive,
                                     String templateName,
                                     String entityType,
                                     Long entityId) throws Exception;
}
