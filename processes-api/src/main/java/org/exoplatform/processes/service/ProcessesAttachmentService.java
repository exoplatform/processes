package org.exoplatform.processes.service;

import org.exoplatform.services.attachments.model.Attachment;

import java.util.List;

public interface ProcessesAttachmentService {

    /**
     * Links a list of attachments to entity, moving or copying their files
     * with the platform's rights: the caller passes only attachments selected
     * by {@link #selectProcessAttachments(long, long, long, Attachment[])}, or
     * a document the user has just created
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
     * Move attachments from source entity to a dest entity, with the
     * platform's rights: the caller passes only attachments selected by
     * {@link #selectRequestAttachments(long, long, long, List)}
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
     * Selects the attachments a request created from a draft takes: the
     * attachments stored on the draft, never a list the client sends. An
     * attachment in the draft's folder, {@code workdraft/<draftId>} at the root
     * of the drive of an existing space, where the processes place the draft's
     * copies of the process attachments, is kept and moved;
     * any other one must be readable by the user, and is moved only when the
     * user may remove it and didn't pick it from a drive, otherwise copied.
     * Nothing is written.
     *
     * @param userId identity id of the user creating the request, the current
     *          user
     * @param draftId id of the request draft
     * @param projectId task project id of the draft's process
     * @param sentAttachments attachments the client sent, read only to know
     *          which ones were picked from a drive
     * @return the attachments to move or copy, a copy being marked by
     *         {@link Attachment#isEXoDrive()}
     * @throws IllegalAccessException when the user can't read one of the
     *           draft's attachments, or isn't the current user while the
     *           draft has one
     */
    List<Attachment> selectRequestAttachments(long userId,
                                              long draftId,
                                              long projectId,
                                              List<Attachment> sentAttachments) throws IllegalAccessException;

    /**
     * Selects, among the attachments sent for a process, the ones to link to
     * it. An attachment the processes placed in the process folder is kept;
     * any other one must be readable by the user, and is moved only when the
     * user may remove it and didn't pick it from a drive, otherwise copied. On
     * an update, an attachment already linked to the process that the user
     * can't read is left as it is. Nothing is written.
     *
     * @param userId identity id of the user creating or updating the process,
     *          the current user
     * @param workflowId id of the process, 0 for a process to create
     * @param projectId task project id of the process, 0 for a process to
     *          create
     * @param sentAttachments attachments the client sent
     * @return the attachments to link, move or copy, a copy being marked by
     *         {@link Attachment#isEXoDrive()}
     * @throws IllegalAccessException when the user can't read an attachment
     *           that isn't linked to the process yet, or isn't the current user
     *           while an attachment is sent
     */
    Attachment[] selectProcessAttachments(long userId,
                                          long workflowId,
                                          long projectId,
                                          Attachment[] sentAttachments) throws IllegalAccessException;

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
