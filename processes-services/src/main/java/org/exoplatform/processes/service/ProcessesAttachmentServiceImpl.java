package org.exoplatform.processes.service;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.services.attachments.model.Attachment;
import org.exoplatform.services.attachments.service.AttachmentService;
import org.exoplatform.services.attachments.utils.Utils;
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
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.wcm.core.NodetypeConstant;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;

import javax.jcr.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.IntStream;

public class ProcessesAttachmentServiceImpl implements ProcessesAttachmentService {
  private static final Log             LOG                  = ExoLogger.getLogger(ProcessesAttachmentServiceImpl.class);

  private final AttachmentService      attachmentService;

  private final RepositoryService      repositoryService;

  private final SessionProviderService sessionProviderService;

  private final ManageDriveService     manageDriveService;

  private final NodeHierarchyCreator   nodeHierarchyCreator;
  
  private final IdentityManager        identityManager;

  private final NodeFinder             nodeFinder;

  private final ProjectService         projectService;

  private static final String          PRIVATE_FOLDER       = "/Private";

  private static final String          PUBLIC_FOLDER        = "/Public";

  private static final String          GROUP_ADMINISTRATORS = "*:/platform/administrators";

  private static final String          GROUP_PROCESSES      = "*:/platform/processes";

  private static final String          DOC_OFORM_MIMETYPE   = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.form";

  private static final String          DOCXF_EXTENSION      = ".docxf";

  private static final String          OFORM_EXTENSION      = ".oform";
  
  private static final String          WORKFLOW_ENTITY_TYPE = "workflow";

  private static DateTimeFormatter     formatter            = DateTimeFormatter.ofPattern("yyyyMMdd");

  public ProcessesAttachmentServiceImpl(AttachmentService attachmentService,
                                        RepositoryService repositoryService,
                                        SessionProviderService sessionProviderService,
                                        ManageDriveService manageDriveService,
                                        NodeHierarchyCreator nodeHierarchyCreator,
                                        NodeFinder nodeFinder,
                                        ProjectService projectService,
                                        IdentityManager identityManager) {
    this.attachmentService = attachmentService;
    this.repositoryService = repositoryService;
    this.sessionProviderService = sessionProviderService;
    this.manageDriveService = manageDriveService;
    this.nodeHierarchyCreator = nodeHierarchyCreator;
    this.nodeFinder = nodeFinder;
    this.projectService = projectService;
    this.identityManager = identityManager;
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
    Space space = ProcessesUtils.getProjectParentSpace(projectId);
    try {
      projectDto = projectService.getProject(projectId);
      projectDto.getParticipator().forEach(participator -> permissions.put(participator, new String[] { PermissionType.READ }));
      projectDto.getManager().forEach(manager -> permissions.put(manager, PermissionType.ALL));
      if (space != null) {
        String participator = projectDto.getParticipator().iterator().next();
        String groupId = participator.substring(participator.indexOf(":") + 1);
        boolean spaceHasARedactor = space != null && space.getRedactors() != null && space.getRedactors().length > 0;
        if (spaceHasARedactor){
          permissions.put("redactor:" + groupId, PermissionType.ALL);
        } else {
          permissions.put("*:" + groupId, PermissionType.ALL);
        }
      }
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
        Map<String, String[]> unmodifiablePermissions = Collections.unmodifiableMap(permissions);
        ((ExtendedNode) destNode).setPermissions(unmodifiablePermissions);
        String destPath = destNode.getPath().concat("/").concat(attachmentNode.getName());
        if (copy) {
          session.save();
          Workspace workspace = session.getWorkspace();
          workspace.copy(attachmentNode.getPath(), destPath);
          Node copyNode = (Node) session.getItem(destPath);
          processDocument(copyNode, currentUser);
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
    } else {
      createWorkflowTaskFolder(userId, projectId, destEntityType, destEntityId);
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

  @Override
  public Attachment createNewFormDocument(Long userIdentityId,
                                          String title,
                                          String path,
                                          String pathDrive,
                                          String templateName,
                                          String entityType,
                                          Long entityId) throws Exception {
    Identity identity = ConversationState.getCurrent().getIdentity();
    Attachment attachment = attachmentService.createNewDocument(identity, title, path, pathDrive, templateName);
    if (entityId != null && entityType != null && Objects.equals(entityType, WORKFLOW_ENTITY_TYPE)) {
      ProcessesService processesService = CommonsUtils.getService(ProcessesService.class);
      WorkFlow workFlow = processesService.getWorkFlow(entityId);
      linkAttachmentsToEntity(new Attachment[] { attachment },
                              userIdentityId,
                              entityId,
                              entityType,
                              workFlow.getProjectId());
    }
    return attachmentService.getAttachmentById(attachment.getId());
  }

  private void processDocument(Node node, String currentUser) {
    try {
      org.exoplatform.social.core.identity.model.Identity identity =
                                                                   identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME,
                                                                                                       currentUser);
      String newNameSuffix = "";
      String name = node.getName();
      int pointIndex = name.lastIndexOf(".");
      String extension = pointIndex != -1 ? name.substring(pointIndex) : "";
      if (identity != null) {
        Profile profile = identity.getProfile();
        newNameSuffix = " - ".concat(profile.getFullName() + " - ").concat(LocalDate.now().format(formatter));
      }
      String newName = pointIndex != -1 ? name.substring(0, pointIndex).concat(newNameSuffix) : name.concat(newNameSuffix);
      if (name.endsWith(DOCXF_EXTENSION)) {
        newName = newName.concat(OFORM_EXTENSION);
        Node content = node.getNode(NodetypeConstant.JCR_CONTENT);
        content.setProperty(NodetypeConstant.JCR_MIME_TYPE, DOC_OFORM_MIMETYPE);
      } else {
        newName = newName.concat(extension);
      }
      if (node.hasProperty(NodetypeConstant.EXO_TITLE)) {
        node.setProperty(NodetypeConstant.EXO_TITLE, newName);
      }
      if (node.hasProperty(NodetypeConstant.EXO_NAME)) {
        node.setProperty(NodetypeConstant.EXO_NAME, newName);
      }
      node.save();
    } catch (RepositoryException e) {
      LOG.error("Error while processing docxf file", e);
    }

  }

  private void createWorkflowTaskFolder(long userId, long projectId, String entityType, long entityId) {
    org.exoplatform.social.core.identity.model.Identity userIdentity = identityManager.getIdentity(String.valueOf(userId));
    Map<String, String[]> permissions = new HashMap<>();
    permissions.put(GROUP_ADMINISTRATORS, PermissionType.ALL);
    permissions.put(GROUP_PROCESSES, PermissionType.ALL);
    permissions.put(userIdentity.getRemoteId(), PermissionType.ALL);
    ProjectDto projectDto;
    Space space = ProcessesUtils.getProjectParentSpace(projectId);
    try {
      projectDto = projectService.getProject(projectId);
      projectDto.getParticipator().forEach(participator -> permissions.put(participator, new String[]{PermissionType.READ}));
      projectDto.getManager().forEach(manager -> permissions.put(manager, PermissionType.ALL));
      if (space != null) {
        String participator = projectDto.getParticipator().iterator().next();
        String groupId = participator.substring(participator.indexOf(":") + 1);
        boolean spaceHasARedactor = space != null && space.getRedactors() != null && space.getRedactors().length > 0;
        if (spaceHasARedactor) {
          permissions.put("redactor:" + groupId, PermissionType.ALL);
        } else {
          permissions.put("*:" + groupId, PermissionType.ALL);
        }
      }
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
    try {
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
      if (!rootNode.hasNode(entityType)) {
        destEntityNode = rootNode.addNode(entityType, NodetypeConstant.NT_FOLDER);
      } else {
        destEntityNode = rootNode.getNode(entityType);
      }
      if (!destEntityNode.hasNode(String.valueOf(entityId))) {
        destEntityNode = destEntityNode.addNode(String.valueOf(entityId), NodetypeConstant.NT_FOLDER);
      } else {
        destEntityNode = destEntityNode.getNode(String.valueOf(entityId));
      }
      if (destEntityNode.canAddMixin(NodetypeConstant.EXO_PRIVILEGEABLE)) {
        destEntityNode.addMixin(NodetypeConstant.EXO_PRIVILEGEABLE);
      }
      Map<String, String[]> unmodifiablePermissions = Collections.unmodifiableMap(permissions);
      ((ExtendedNode) destEntityNode).setPermissions(unmodifiablePermissions);
      session.save();
    } catch (Exception e) {
      LOG.error("Error while Creating entity folder", e);
    }
  }
}
