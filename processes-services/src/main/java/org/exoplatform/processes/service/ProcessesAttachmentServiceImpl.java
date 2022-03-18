package org.exoplatform.processes.service;

import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.services.attachments.model.Attachment;
import org.exoplatform.services.attachments.service.AttachmentService;
import org.exoplatform.services.attachments.utils.Utils;
import org.exoplatform.services.cms.documents.DocumentService;
import org.exoplatform.services.cms.drives.DriveData;
import org.exoplatform.services.cms.drives.ManageDriveService;
import org.exoplatform.services.cms.link.NodeFinder;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.wcm.core.NodetypeConstant;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;

import javax.jcr.*;
import java.util.*;
import java.util.stream.IntStream;

public class ProcessesAttachmentServiceImpl implements ProcessesAttachmentService {
  private static final Log             LOG                  = ExoLogger.getLogger(ProcessesAttachmentServiceImpl.class);

  private final AttachmentService      attachmentService;

  private final RepositoryService      repositoryService;

  private final SessionProviderService sessionProviderService;

  private final ManageDriveService     manageDriveService;

  private final NodeHierarchyCreator   nodeHierarchyCreator;

  private final NodeFinder             nodeFinder;

  private final ProjectService         projectService;

  private static final String          PRIVATE_FOLDER       = "/Private";

  private static final String          PUBLIC_FOLDER        = "/Public";

  private static final String          GROUP_ADMINISTRATORS = "*:/platform/administrators";

  private static final String          GROUP_PROCESSES      = "*:/platform/processes";

  public ProcessesAttachmentServiceImpl(AttachmentService attachmentService,
                                        RepositoryService repositoryService,
                                        SessionProviderService sessionProviderService,
                                        ManageDriveService manageDriveService,
                                        NodeHierarchyCreator nodeHierarchyCreator,
                                        NodeFinder nodeFinder,
                                        ProjectService projectService) {
    this.attachmentService = attachmentService;
    this.repositoryService = repositoryService;
    this.sessionProviderService = sessionProviderService;
    this.manageDriveService = manageDriveService;
    this.nodeHierarchyCreator = nodeHierarchyCreator;
    this.nodeFinder = nodeFinder;
    this.projectService = projectService;
  }

  @Override
  public void linkAttachmentsToEntity(Attachment[] attachments, Long userId, Long entityId, String entityType, Long projectId) {
    if (attachments != null && attachments.length > 0) {
      Arrays.stream(attachments).map(Attachment::getId).forEach(attachmentId -> {
        try {
          attachmentService.linkAttachmentToEntity(userId, entityId, entityType, attachmentId);
        } catch (Exception e) {
          LOG.error("Error while attaching files to entity", e);
        }
      });
      moveOrCopyAttachmentsJcrNodes(Arrays.asList(attachments), entityId, entityType, false, projectId);
    }
  }

  private void linkFromEntityToEntity(Long userId,
                                      List<Attachment> attachments,
                                      Long sourceEntityId,
                                      String sourceEntityType,
                                      Long destEntityId,
                                      String destEntityType,
                                      boolean deleteSource) {
    attachments.stream().map(Attachment::getId).forEach(attachmentId -> {
      try {
        attachmentService.linkAttachmentToEntity(userId, destEntityId, destEntityType, attachmentId);
      } catch (Exception e) {
        LOG.error("Error while attaching files to entity", e);
      }
    });
    if (attachments.size() != 0 && deleteSource) {
      try {
        attachmentService.deleteAllEntityAttachments(userId, sourceEntityId, sourceEntityType);
      } catch (Exception e) {
        LOG.error("Error while removing attachments to entity source", e);
      }
    }

  }

  private void moveOrCopyAttachmentsJcrNodes(List<Attachment> attachments,
                                             Long destEntityId,
                                             String destEntityType,
                                             boolean copy,
                                             Long projectId) {
    Map<String, String[]> permissions = new HashMap<>();
    Map<Integer, Attachment> updatedAttachments = new HashMap<>();
    String currentUser = ConversationState.getCurrent().getIdentity().getUserId();
    permissions.put(GROUP_ADMINISTRATORS, PermissionType.ALL);
    permissions.put(GROUP_PROCESSES, PermissionType.ALL);
    permissions.put(currentUser, PermissionType.ALL);
    ProjectDto projectDto;
    try {
      projectDto = projectService.getProject(projectId);
      projectDto.getManager().forEach(manager -> permissions.put(manager, PermissionType.ALL));
      projectDto.getParticipator().forEach(participator -> permissions.put(participator, new String[] { PermissionType.READ }));
    } catch (EntityNotFoundException e) {
      LOG.error("Task project not found", e);
      return;
    }
    Session jcrSession;
    try {
      jcrSession = Utils.getSystemSession(this.sessionProviderService, this.repositoryService);
    } catch (RepositoryException e) {
      LOG.error("Error while getting jcr session", e);
      return;
    }
    final Session session = jcrSession;
    final ProjectDto project = projectDto;
    IntStream.range(0, attachments.size()).forEach(index -> {
      String attachmentId = attachments.get(index).getId();
      try {
        Space space = ProcessesUtils.getProjectParentSpace(projectId);
        DriveData driveData;
        Node rootNode;
        if (space != null) {
          driveData = manageDriveService.getGroupDrive(space.getGroupId());
          rootNode = (Node) session.getItem(driveData.getHomePath());
        } else {
          String user = project.getManager().iterator().next();
          driveData = manageDriveService.getUserDrive(user);
          String publicFolderPath = driveData.getHomePath().replace(PRIVATE_FOLDER, PUBLIC_FOLDER);
          rootNode = (Node) session.getItem(publicFolderPath);
        }

        Node destEntityNode;
        if (!rootNode.hasNode(destEntityType)) {
          destEntityNode = rootNode.addNode(destEntityType, NodetypeConstant.NT_FOLDER);
        } else {
          destEntityNode = rootNode.getNode(destEntityType);
        }
        if (!destEntityNode.hasNode(String.valueOf(destEntityId))) {
          destEntityNode.addNode(String.valueOf(destEntityId), NodetypeConstant.NT_FOLDER);
        }
        String newPath = destEntityType.concat("/").concat(String.valueOf(destEntityId));
        Node attachmentNode = session.getNodeByUUID(attachmentId);
        Node destNode = Utils.getParentFolderNode(session,
                                                  this.manageDriveService,
                                                  this.nodeHierarchyCreator,
                                                  this.nodeFinder,
                                                  driveData.getName(),
                                                  newPath);
        if (destNode.canAddMixin(NodetypeConstant.EXO_PRIVILEGEABLE)) {
          destNode.addMixin(NodetypeConstant.EXO_PRIVILEGEABLE);
        }
        ((ExtendedNode) destNode).setPermissions(permissions);
        String destPath = destNode.getPath().concat("/").concat(attachmentNode.getName());
        if (copy) {
          session.save();
          Workspace workspace = session.getWorkspace();
          workspace.copy(attachmentNode.getPath(), destPath);
          Node copyNode = (Node) session.getItem(destPath);
          Attachment copyAttachment = attachmentService.getAttachmentById(copyNode.getUUID());
          updatedAttachments.put(index, copyAttachment);
        } else {
          Node sourceEntityIdNode = attachmentNode.getParent();
          session.move(attachmentNode.getPath(), destPath);
          if (attachments.size() - 1 == index && sourceEntityIdNode != null
              && sourceEntityIdNode.getPrimaryNodeType().isNodeType(NodetypeConstant.NT_FOLDER)) {
            sourceEntityIdNode.remove();
          }
          session.save();
        }
      } catch (Exception e) {
        LOG.error("Error while moving or copying attachments", e);
      }
    });
    if (session != null) {
      session.logout();
    }
    updatedAttachments.forEach(attachments::set);
  }

  @Override
  public void moveAttachmentsToEntity(Long userId,
                                      Long sourceEntityId,
                                      String sourceEntityType,
                                      Long destEntityId,
                                      String destEntityType,
                                      Long projectId) {
    List<Attachment> attachments = new ArrayList<>();
    try {
      attachments = attachmentService.getAttachmentsByEntity(userId, sourceEntityId, sourceEntityType);
    } catch (Exception e) {
      LOG.error("Error while getting entity attachments", e);
    }
    if (!attachments.isEmpty()) {
      moveOrCopyAttachmentsJcrNodes(attachments, destEntityId, destEntityType, false, projectId);
      linkFromEntityToEntity(userId, attachments, sourceEntityId, sourceEntityType, destEntityId, destEntityType, true);
    }
  }

  @Override
  public void copyAttachmentsToEntity(Long userId,
                                      Long sourceEntityId,
                                      String sourceEntityType,
                                      Long destEntityId,
                                      String destEntityType,
                                      Long projectId) {
    List<Attachment> attachments = new ArrayList<>();
    try {
      attachments = attachmentService.getAttachmentsByEntity(userId, sourceEntityId, sourceEntityType);
    } catch (Exception e) {
      LOG.error("Error while getting entity attachments", e);
    }
    if (!attachments.isEmpty()) {
      moveOrCopyAttachmentsJcrNodes(attachments, destEntityId, destEntityType, true, projectId);
      linkFromEntityToEntity(userId, attachments, sourceEntityId, sourceEntityType, destEntityId, destEntityType, false);
    }
  }
}
