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

package org.exoplatform.processes.storage;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.*;

import org.springframework.stereotype.Component;

import org.exoplatform.commons.file.model.FileInfo;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.Utils.EntityMapper;
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.dao.WorkDraftDAO;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.WorkEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.*;
import org.exoplatform.processes.service.ProcessAttachmentService;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.Membership;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dao.TaskQuery;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.StatusService;
import org.exoplatform.task.service.TaskService;
import org.exoplatform.task.util.ProjectUtil;
import org.exoplatform.task.util.UserUtil;

@Component
public class ProcessStorage {

  private static final Log               LOG                      = ExoLogger.getLogger(ProcessStorage.class);

  private static final String            PROCESSES_GROUP          = "/platform/processes";

  private static final String            WORKFLOW_ENTITY_TYPE     = "workflow";

  private static final String[]          DEFAULT_PROCESS_STATUS   = { "Request", "RequestInProgress", "Validated", "Refused",
      "Canceled" };

  private static final String            PROCESS_FILES_NAME_SPACE = "processesApp";

  private final WorkFlowDAO              workFlowDAO;

  private final WorkDraftDAO             workDraftDAO;

  private final IdentityManager          identityManager;

  private final TaskService              taskService;

  private final ProjectService           projectService;

  private final StatusService            statusService;

  private final SpaceService             spaceService;

  private final ListenerService          listenerService;

  private final ProcessAttachmentService processAttachmentService;

  private final FileService              fileService;

  private final OrganizationService      organizationService;

  private final UserACL                  userACL;

  public ProcessStorage(WorkFlowDAO workFlowDAO,
                        WorkDraftDAO workDraftDAO,
                        TaskService taskService,
                        ProjectService projectService,
                        StatusService statusService,
                        IdentityManager identityManager,
                        SpaceService spaceService,
                        ListenerService listenerService,
                        ProcessAttachmentService processAttachmentService,
                        FileService fileService,
                        OrganizationService organizationService,
                        UserACL userACL) {
    this.workFlowDAO = workFlowDAO;
    this.workDraftDAO = workDraftDAO;
    this.identityManager = identityManager;
    this.taskService = taskService;
    this.projectService = projectService;
    this.statusService = statusService;
    this.spaceService = spaceService;
    this.listenerService = listenerService;
    this.processAttachmentService = processAttachmentService;
    this.fileService = fileService;
    this.organizationService = organizationService;
    this.userACL = userACL;
  }

  /**
   * Retrieves a list of accessible processes, for a selected user, by applying
   * the designated filter. The returned results will be of type {@link WorkFlow}
   * only. The ownerId of filter object will be used to select the list of
   * accessible processes to retrieve.
   *
   * @param filter {@link ProcessesFilter} that contains filtering criteria
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @param userIdentityId {@link Identity} technical identifier of the user
   *          acessing files
   * @return {@link List} of {@link WorkFlow}
   */
  public List<WorkFlow> findAllWorkFlowsByUser(ProcessesFilter filter, int offset, int limit, long userIdentityId) {
    // TODO: add filter props to the request
    return EntityMapper.fromWorkflowEntities(workFlowDAO.findAllWorkFlowsByUser(userIdentityId, offset, limit));
  }

  /**
   * Retrieves a list of enabled processes, for a selected user, by applying the
   * designated filter. The returned results will be of type {@link WorkFlow}
   * only. The ownerId of filter object will be used to select the list of
   * accessible processes to retrieve.
   *
   * @param filter {@link ProcessesFilter} that contains filtering criteria
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @param userIdentityId {@link Identity} technical identifier of the user
   *          acessing files
   * @return {@link List} of {@link WorkFlow}
   */
  public List<WorkFlow> findEnabledWorkFlowsByUser(ProcessesFilter filter, int offset, int limit, long userIdentityId) {
    // TODO: add filter props to the request
    return EntityMapper.fromWorkflowEntities(workFlowDAO.findEnabledWorkFlowsByUser(userIdentityId, offset, limit));
  }

  /**
   * Retrieves the list of all processes
   *
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @return {@link List} of {@link WorkFlow}
   */
  public List<WorkFlow> findAllWorkFlows(int offset, int limit) {
    return EntityMapper.fromWorkflowEntities(workFlowDAO.findAllWorkFlows(offset, limit));
  }

  /**
   * Retrieves the list of enabled processes
   *
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @return {@link List} of {@link WorkFlow}
   */
  public List<WorkFlow> findEnabledWorkFlows(int offset, int limit) {
    return EntityMapper.fromWorkflowEntities(workFlowDAO.findEnabledWorkFlows(offset, limit));
  }

  /**
   * Retrieves a list of disabled processes
   *
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @return {@link List} of {@link WorkFlow}
   */
  public List<WorkFlow> findDisabledWorkFlows(int offset, int limit) {
    return EntityMapper.fromWorkflowEntities(workFlowDAO.findDisabledWorkFlows(offset, limit));
  }

  /**
   * get a process by its given id
   *
   * @param id process id
   */
  public WorkFlow getWorkFlowById(long id) {
    return EntityMapper.fromEntity(workFlowDAO.find(id), null);
  }

  /**
   * get a process by its project id
   *
   * @param projectId process(s project id
   */
  public WorkFlow getWorkFlowByProjectId(long projectId) {
    return EntityMapper.fromEntity(workFlowDAO.getWorkFlowByProjectId(projectId), null);
  }

  /**
   * Save a process
   *
   * @param workFlow process object
   * @param userName user name
   * @return {@link WorkFlow}
   */
  public WorkFlow saveWorkFlow(WorkFlow workFlow, String userName) {
    Identity identity = identityManager.getOrCreateUserIdentity(userName);
    long userId = Long.parseLong(identity.getId());
    WorkFlowEntity workFlowEntity = EntityMapper.toEntity(workFlow);
    IllustrativeAttachment illustrativeAttachment = createIllustrativeImage(workFlow.getIllustrativeAttachment());
    if (illustrativeAttachment != null && !illustrativeAttachment.isToDelete()) {
      workFlowEntity.setIllustrationImageId(illustrativeAttachment.getId());
    } else if (illustrativeAttachment != null) {
      workFlowEntity.setIllustrationImageId(null);
    }
    workFlowEntity.setModifiedDate(new Date());
    workFlowEntity.setModifierId(userId);
    if (workFlow.getId() == 0) {
      workFlowEntity.setId(null);
      workFlowEntity.setCreatedDate(new Date());
      workFlowEntity.setCreatorId(userId);
      if (workFlow.getProjectId() == 0) {
        createProject(workFlow);
        workFlowEntity.setProjectId(workFlow.getProjectId());
        workFlowEntity.setParticipator(workFlow.getParticipator());
        workFlowEntity.setManager(getManagers(workFlow.getRequestsCreators()));
      }
      workFlowEntity = workFlowDAO.create(workFlowEntity);
      WorkFlow newWorkflow = EntityMapper.fromEntity(workFlowEntity, illustrativeAttachment, null);
      ProcessesUtils.broadcast(listenerService, "exo.process.created", userId, newWorkflow);
    } else {
      Space space = ProcessesUtils.getProjectParentSpace(workFlow.getProjectId());
      if (space != null && !space.getId().equals(workFlow.getSpaceId())) {
        Space newSpace = spaceService.getSpaceById(workFlow.getSpaceId());
        List<String> memberships = UserUtil.getSpaceMemberships(newSpace.getGroupId());
        Set<String> managers = new HashSet<>(Collections.singletonList(memberships.get(0)));

        Set<String> participators = new HashSet<>(Collections.singletonList(memberships.get(1)));
        try {
          ProjectDto project = projectService.getProject(workFlow.getProjectId());
          project.setManager(managers);
          project.setParticipator(participators);
          projectService.updateProjectNoReturn(project);
          workFlowEntity.setProjectId(project.getId());
          participators.addAll(managers);
          workFlowEntity.setParticipator(participators);
        } catch (EntityNotFoundException e) {
          throw new IllegalArgumentException("Process project does not exist");
        }
      } else {
        workFlowEntity.setParticipator(workFlowDAO.find(workFlowEntity.getId()).getParticipator());
      }
      workFlowEntity.setManager(getManagers(workFlow.getRequestsCreators()));
      workFlowEntity = workFlowDAO.update(workFlowEntity);
    }
    processAttachmentService.linkAttachmentsToEntity(workFlow.getAttachments(),
                                                     identity.getRemoteId(),
                                                     workFlowEntity.getId(),
                                                     WORKFLOW_ENTITY_TYPE,
                                                     workFlowEntity.getProjectId());
    return EntityMapper.fromEntity(workFlowEntity, illustrativeAttachment, null);
  }

  /**
   * Retrieves an illustration image by its given id
   *
   * @param illustrationId illustration file id
   * @return {@link IllustrativeAttachment}
   */
  public IllustrativeAttachment getIllustrationImageById(Long illustrationId) {
    if (illustrationId == null) {
      return null;
    }
    FileItem file;
    try {
      file = fileService.getFile(illustrationId);
    } catch (FileStorageException e) {
      return null;
    }
    if (file == null) {
      return null;
    }
    FileInfo fileInfo = file.getFileInfo();
    return new IllustrativeAttachment(fileInfo.getId(),
                                      fileInfo.getName(),
                                      file.getAsStream(),
                                      fileInfo.getMimetype(),
                                      fileInfo.getSize(),
                                      fileInfo.getUpdatedDate().getTime());
  }

  /**
   * Delete a process by its given id.
   *
   * @param workflowId : process id
   */
  public void deleteWorkflowById(Long workflowId) {
    WorkFlowEntity workFlowEntity = this.workFlowDAO.find(workflowId);
    if (workFlowEntity == null) {
      throw new IllegalArgumentException("Process not found");
    }
    try {
      ProjectDto project = projectService.getProject(workFlowEntity.getProjectId());
      if (project != null) {
        projectService.removeProject(project.getId(), true);
      }
    } catch (EntityNotFoundException e) {
      LOG.error("Error while getting process project", e);
    }
    List<WorkEntity> drafts = this.workDraftDAO.getDraftsByWorkflowId(workflowId);
    if (!drafts.isEmpty()) {
      workDraftDAO.deleteAll(drafts);
    }
    this.workFlowDAO.delete(workFlowEntity);
  }

  /**
   * Retrieves list fo filtered workflows
   *
   * @param processesFilter processes filter
   * @param offset Offset of result list
   * @param limit limit of result list
   * @return {@link List} of {@link WorkFlow}
   */
  public List<WorkFlow> findWorkFlows(ProcessesFilter processesFilter, String userName, int offset, int limit) {
    List<String> memberships = new ArrayList<>();
    boolean isMemberProcessesGroup = false;
    memberships.add(userName);
    try {
      Collection<Membership> ms = organizationService.getMembershipHandler().findMembershipsByUser(userName);
      if (ms != null) {
        for (Membership membership : ms) {
          if (membership.getGroupId().equals(PROCESSES_GROUP)) {
            isMemberProcessesGroup = true;
          }
          String membership_ = membership.getMembershipType() + ":" + membership.getGroupId();
          memberships.add(membership_);
        }
      }
    } catch (Exception e) {
      LOG.error("Error while getting the user memberships", e);
    }
    List<WorkFlowEntity> workFlowEntities;
    if (isMemberProcessesGroup) {
      workFlowEntities = workFlowDAO.findWorkFlows(processesFilter, null, offset, limit);
    } else {
      workFlowEntities = workFlowDAO.findWorkFlows(processesFilter, memberships, offset, limit);
    }
    List<WorkFlow> workFlows = new ArrayList<>();
    workFlowEntities.forEach(workflowEntity -> {
      IllustrativeAttachment illustrativeAttachment = null;
      try {
        illustrativeAttachment = getIllustrationImageById(workflowEntity.getIllustrationImageId());
        if (illustrativeAttachment != null) {
          illustrativeAttachment.setFileInputStream(null);
        }
      } catch (Exception e) {
        LOG.error("Error while getting process illustration image", e);
      }
      workFlows.add(EntityMapper.fromEntity(workflowEntity, illustrativeAttachment, userACL.getUserIdentity(userName)));
    });
    workFlows.forEach(workflow -> {
      if (workflow != null) {
        boolean canShowPending = false;
        try {
          Space space = ProcessesUtils.getProjectParentSpace(workflow.getProjectId());
          canShowPending = canShowPending(userName, space);
        } catch (Exception e) {
          LOG.error("Error while getting process can Show Pending", e);
        }
        workflow.setCanShowPending(canShowPending);
      }
    });
    return workFlows;
  }

  /**
   * Return the number of requests created for a given process
   *
   * @param projectId: Tasks project id
   * @param isCompleted: filter by completed and uncompleted tasks
   * @return Filtered tasks count
   */
  public int countWorksByWorkflow(long projectId, boolean isCompleted) {
    TaskQuery taskQuery = new TaskQuery();
    taskQuery.setProjectIds(List.of(projectId));
    taskQuery.setCompleted(isCompleted);
    try {
      return taskService.countTasks(taskQuery);
    } catch (Exception e) {
      return 0;
    }
  }

  /**
   * Retrieves the list of available statuses in all workflows
   *
   * @return {@link List} of {@link WorkStatus}
   */
  public List<WorkStatus> getAvailableWorkStatuses() {
    List<WorkStatus> statuses = new ArrayList<>();
    List<WorkFlow> workFlows = findAllWorkFlows(0, 0);
    List<Long> projectsIds = workFlows.stream().map(WorkFlow::getProjectId).toList();
    projectsIds.forEach(projectId -> statuses.addAll(EntityMapper.toWorkStatuses(statusService.getStatuses(projectId))));
    statuses.sort(Comparator.comparing(WorkStatus::getRank));
    return statuses;
  }

  /**
   * Return the number of proceses after applying a given filter
   *
   * @param processesFilter: process fleter to apply
   * @return Filtered processes count
   */
  public int countWorkFlows(ProcessesFilter processesFilter) {
    return workFlowDAO.countWorkFlows(processesFilter);
  }

  private boolean canShowPending(String authenticatedUser, Space space) {
    if (space != null) {
      return (spaceService.isSuperManager(authenticatedUser) || spaceService.isMember(space, authenticatedUser));
    } else
      return false;
  }

  private IllustrativeAttachment createIllustrativeImage(IllustrativeAttachment illustrativeAttachment) {
    if (illustrativeAttachment == null) {
      return null;
    }
    if (illustrativeAttachment.getFileName() == null) {
      illustrativeAttachment.setToDelete(true);
      return illustrativeAttachment;
    }
    if (illustrativeAttachment.getFileBody() == null) {
      return illustrativeAttachment;
    }
    FileItem fileItem;
    try {
      String data = illustrativeAttachment.getFileBody().split("base64,")[1];
      byte[] bytes = Base64.getDecoder().decode(data.getBytes(Charset.defaultCharset()));
      fileItem = new FileItem(illustrativeAttachment.getId(),
                              illustrativeAttachment.getFileName(),
                              illustrativeAttachment.getMimeType(),
                              PROCESS_FILES_NAME_SPACE,
                              illustrativeAttachment.getFileSize(),
                              new Date(),
                              null,
                              false,
                              new ByteArrayInputStream(bytes));
      if (illustrativeAttachment.getId() == null) {
        fileItem = fileService.writeFile(fileItem);
      } else {
        fileItem = fileService.updateFile(fileItem);
      }
      if (fileItem != null && fileItem.getFileInfo() != null) {
        FileInfo fileInfo = fileItem.getFileInfo();
        return new IllustrativeAttachment(fileInfo.getId(),
                                          fileInfo.getName(),
                                          fileInfo.getMimetype(),
                                          fileInfo.getSize(),
                                          fileInfo.getUpdatedDate().getTime());
      }
    } catch (Exception e) {
      LOG.error("Error while saving illustrative attachment", e);
    }
    return null;
  }

  private WorkFlow createProject(WorkFlow workFlow) {
    Space processSpace = spaceService.getSpaceById(workFlow.getSpaceId());
    if (processSpace == null) {
      throw new IllegalArgumentException("Space of processes not exist");
    }

    List<String> memberships = UserUtil.getSpaceMemberships(processSpace.getGroupId());
    Set<String> managers = new HashSet<>(Collections.singletonList(memberships.get(0)));
    Set<String> participators = new HashSet<>(Collections.singletonList(memberships.get(1)));
    ProjectDto project =
                       ProjectUtil.newProjectInstanceDto(workFlow.getTitle(), workFlow.getDescription(), managers, participators);
    project = projectService.createProject(project);
    for (String statusName : DEFAULT_PROCESS_STATUS) {
      statusService.createStatus(project, statusName);
    }
    workFlow.setProjectId(project.getId());
    workFlow.setProjectId(project.getId());
    participators.addAll(managers);
    workFlow.setManager(managers);
    workFlow.setParticipator(participators);
    return workFlow;
  }

  private Set<String> getManagers(List<CreatorIdentityEntity> requestsCreators) {
    List<String> managers = new ArrayList<>();
    for (CreatorIdentityEntity id : requestsCreators) {
      if (id.getIdentity().getProviderId().equals("space")) {
        Space space = spaceService.getSpaceByPrettyName(id.getIdentity().getRemoteId());
        if (space != null) {
          managers.add(space.getGroupId());
        }
      } else {
        managers.add(id.getIdentity().getRemoteId());
      }
    }
    return new HashSet<>(managers);
  }

}
