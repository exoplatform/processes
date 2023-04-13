package org.exoplatform.processes.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.model.FileInfo;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.processes.Utils.EntityMapper;
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.dao.WorkDraftDAO;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.WorkEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.*;
import org.exoplatform.processes.service.ProcessesAttachmentService;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.Membership;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dao.OrderBy;
import org.exoplatform.task.dao.TaskQuery;
import org.exoplatform.task.domain.Priority;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.StatusDto;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.StatusService;
import org.exoplatform.task.service.TaskService;
import org.exoplatform.task.util.ProjectUtil;
import org.exoplatform.task.util.UserUtil;

public class ProcessesStorageImpl implements ProcessesStorage {

  private static final Log                 LOG                      = ExoLogger.getLogger(ProcessesStorageImpl.class);
  private static final String              PROCESSES_GROUP          = "/platform/processes";
  private static final String              WORK_DRAFT_ENTITY_TYPE   = "workdraft";
  private static final String              TASK_ENTITY_TYPE         = "task";
  private static final String              WORKFLOW_ENTITY_TYPE     = "workflow";
  private static final String[]            DEFAULT_PROCESS_STATUS   = { "Request", "RequestInProgress", "Validated", "Refused",
      "Canceled" };
  private static final String              PROCESS_FILES_NAME_SPACE = "processesApp";
  private final WorkFlowDAO                workFlowDAO;
  private final WorkDraftDAO               workDraftDAO;
  private final IdentityManager            identityManager;
  private final TaskService                taskService;
  private final ProjectService             projectService;
  private final StatusService              statusService;
  private final SpaceService               spaceService;
  private final ListenerService            listenerService;
  private final ProcessesAttachmentService processesAttachmentService;
  private final FileService                fileService;
  private final OrganizationService        organizationService;
  private final String                     DATE_FORMAT              = "yyyy/MM/dd";
  private final SimpleDateFormat           formatter                = new SimpleDateFormat(DATE_FORMAT);

  public ProcessesStorageImpl(WorkFlowDAO workFlowDAO,
                              WorkDraftDAO workDraftDAO,
                              TaskService taskService,
                              ProjectService projectService,
                              StatusService statusService,
                              IdentityManager identityManager,
                              SpaceService spaceService,
                              ListenerService listenerService,
                              ProcessesAttachmentService processesAttachmentService,
                              FileService fileService,
                              OrganizationService organizationService) {
    this.workFlowDAO = workFlowDAO;
    this.workDraftDAO = workDraftDAO;
    this.identityManager = identityManager;
    this.taskService = taskService;
    this.projectService = projectService;
    this.statusService = statusService;
    this.spaceService = spaceService;
    this.listenerService = listenerService;
    this.processesAttachmentService = processesAttachmentService;
    this.fileService = fileService;
    this.organizationService = organizationService;
  }

  @Override
  public List<WorkFlow> findAllWorkFlowsByUser(ProcessesFilter filter, int offset, int limit, long userIdentityId) {
    // TODO: add filter props to the request
    return EntityMapper.fromWorkflowEntities(workFlowDAO.findAllWorkFlowsByUser(userIdentityId, offset, limit));
  }

  @Override
  public List<WorkFlow> findEnabledWorkFlowsByUser(ProcessesFilter filter, int offset, int limit, long userIdentityId) {
    // TODO: add filter props to the request
    return EntityMapper.fromWorkflowEntities(workFlowDAO.findEnabledWorkFlowsByUser(userIdentityId, offset, limit));
  }

  @Override
  public List<WorkFlow> findAllWorkFlows(int offset, int limit) {
    return EntityMapper.fromWorkflowEntities(workFlowDAO.findAllWorkFlows(offset, limit));
  }

  @Override
  public List<WorkFlow> findEnabledWorkFlows(int offset, int limit) {
    return EntityMapper.fromWorkflowEntities(workFlowDAO.findEnabledWorkFlows(offset, limit));
  }

  @Override
  public List<WorkFlow> findDisabledWorkFlows(int offset, int limit) {
    return EntityMapper.fromWorkflowEntities(workFlowDAO.findDisabledWorkFlows(offset, limit));
  }

  @Override
  public WorkFlow getWorkFlowById(long id) {
    return EntityMapper.fromEntity(workFlowDAO.find(id), null);
  }

  @Override
  public WorkFlow getWorkFlowByProjectId(long projectId) {
    return EntityMapper.fromEntity(workFlowDAO.getWorkFlowByProjectId(projectId), null);
  }

  @Override
  public WorkFlow saveWorkFlow(WorkFlow workFlow, long userId) throws IllegalArgumentException {
    if (workFlow == null) {
      throw new IllegalArgumentException("workflow argument is null");
    }
    Identity identity = identityManager.getIdentity(String.valueOf(userId));
    if (identity == null) {
      throw new IllegalArgumentException("identity is not exist");
    }
    WorkFlowEntity workFlowEntity = EntityMapper.toEntity(workFlow);
    IllustrativeAttachment illustrativeAttachment = createIllustrativeImage(workFlow.getIllustrativeAttachment());
    if (illustrativeAttachment != null && !illustrativeAttachment.isToDelete()) {
      workFlowEntity.setIllustrationImageId(illustrativeAttachment.getId());
    } else if (illustrativeAttachment != null && illustrativeAttachment.isToDelete()) {
      workFlowEntity.setIllustrationImageId(null);
    }
    if (workFlow.getId() == 0) {
      workFlowEntity.setId(null);
      workFlowEntity.setCreatedDate(new Date());
      workFlowEntity.setCreatorId(userId);
      if (workFlow.getProjectId() == 0) {
        workFlow = createProject(workFlow);
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
        Set<String> managers = new HashSet<>(Arrays.asList(memberships.get(0)));

        Set<String> participators = new HashSet<>(Arrays.asList(memberships.get(1)));
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
      workFlowEntity.setModifiedDate(new Date());
      workFlowEntity.setModifierId(userId);
      workFlowEntity = workFlowDAO.update(workFlowEntity);
    }
    processesAttachmentService.linkAttachmentsToEntity(workFlow.getAttachments(),
                                                       userId,
                                                       workFlowEntity.getId(),
                                                       WORKFLOW_ENTITY_TYPE,
                                                       workFlowEntity.getProjectId());
    return EntityMapper.fromEntity(workFlowEntity, illustrativeAttachment, null);
  }

  Set<String> getManagers(List<CreatorIdentityEntity> requestsCreators) {
    List<String> managers = new ArrayList();
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

  /**
   * {@inheritDoc}
   */
  @Override
  public IllustrativeAttachment getIllustrationImageById(Long illustrationId) throws FileStorageException,
                                                                              ObjectNotFoundException,
                                                                              IOException {
    if (illustrationId == null) {
      return null;
    }
    FileItem file = fileService.getFile(illustrationId);
    if (file == null) {
      throw new ObjectNotFoundException("Illustration image not found");
    }
    FileInfo fileInfo = file.getFileInfo();
    return new IllustrativeAttachment(fileInfo.getId(),
                                      fileInfo.getName(),
                                      file.getAsStream(),
                                      fileInfo.getMimetype(),
                                      fileInfo.getSize(),
                                      fileInfo.getUpdatedDate().getTime());
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
      byte[] bytes = Base64.getDecoder().decode(data.getBytes(Charset.defaultCharset().name()));
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

  @Override
  public List<Work> getWorks(long userIdentityId, WorkFilter workFilter, int offset, int limit) throws Exception {
    List<WorkFlow> workFlows = findAllWorkFlows(0, 0);
    List<Long> projectsIds = workFlows.stream().map(WorkFlow::getProjectId).collect(Collectors.toList());
    TaskQuery taskQuery = new TaskQuery();
    taskQuery.setProjectIds(projectsIds);
    if (workFilter.getStatus() != null) {
      taskQuery.setStatusName(workFilter.getStatus());
    }
    if (workFilter.getQuery() != null) {
      taskQuery.setKeyword(workFilter.getQuery());
    }
    if (workFilter.getCompleted() != null) {
      taskQuery.setCompleted(workFilter.getCompleted());
    }
    List<OrderBy> orderByList = new ArrayList<>();
    orderByList.add(new OrderBy("id", false));
    taskQuery.setOrderBy(orderByList);
    taskQuery.setCreatedBy(ProcessesUtils.getUserNameByIdentityId(identityManager, userIdentityId));
    List<TaskDto> tasks = taskService.findTasks(taskQuery, offset, limit);
    return (EntityMapper.tasksToWorkList(tasks));
  }

  @Override
  public Work getWorkById(long userIdentityId, long workId) {
    Identity identity = identityManager.getIdentity(String.valueOf(userIdentityId));
    if (identity == null) {
      throw new IllegalArgumentException("identity is not exist");
    }
    TaskDto taskDto = null;
    TaskQuery taskQuery = new TaskQuery();
    try {
      taskQuery.setId(workId);
      taskQuery.setCreatedBy(identity.getRemoteId());
      List<TaskDto> list = taskService.findTasks(taskQuery, 0, 0);
      if (!list.isEmpty()) {
        taskDto = list.get(0);
      }
    } catch (EntityNotFoundException e) {
      throw new javax.persistence.EntityNotFoundException("work not found");
    } catch (Exception e) {
      LOG.error("Error while getting work");
    }
    return EntityMapper.taskToWork(taskDto);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int countWorksByWorkflow(long projectId, boolean isCompleted) throws Exception {
    TaskQuery taskQuery = new TaskQuery();
    taskQuery.setProjectIds(Arrays.asList(projectId));
    taskQuery.setCompleted(isCompleted);
    int tasksCount = taskService.countTasks(taskQuery);
    return tasksCount;
  }

  @Override
  public Work getWorkById(long id) {
    try {
      return EntityMapper.taskToWork(taskService.getTask(id));
    } catch (EntityNotFoundException e) {
      return null;
    }
  }

  private TaskDto createWorkTask(Work work, Identity identity) {
    TaskDto taskDto;
    try {
      projectService.getProject(work.getProjectId());
    } catch (EntityNotFoundException e) {
      throw new IllegalArgumentException("Task's project not found");
    }
    taskDto = EntityMapper.workToTask(work);
    if (StringUtils.isEmpty(taskDto.getTitle())) {
      taskDto.setTitle(formatter.format(new Date()) + " - " + identity.getProfile().getFullName());
    }
    taskDto.setStatus(statusService.getDefaultStatus(work.getProjectId()));
    taskDto.setCreatedBy(identity.getRemoteId());
    taskDto.setCreatedTime(new Date());
    taskDto.setPriority(Priority.NONE);
    taskDto = taskService.createTask(taskDto);
    return taskDto;
  }

  private TaskDto updateWorkTask(Work work) {
    TaskDto taskDto;
    try {
      taskDto = taskService.getTask(work.getId());
    } catch (EntityNotFoundException e) {
      throw new IllegalArgumentException("Task not found");
    }
    taskDto.setDescription(work.getDescription());
    taskDto.setTitle(work.getTitle());
    taskDto.setCompleted(work.isCompleted());
    long projectId = work.getProjectId();
    List<StatusDto> statuses = statusService.getStatuses(projectId);
    StatusDto status = statuses.stream().filter(statusDto -> work.getStatus().equals(statusDto.getName())).findAny().orElse(null);
    if (status != null) {
      taskDto.setStatus(status);
    }
    taskDto = taskService.updateTask(taskDto);
    if (taskDto.isCompleted() && taskDto.getStatus().getName().equals(DEFAULT_PROCESS_STATUS[4])) {
      ProjectDto projectDto = taskDto.getStatus().getProject();
      ProcessesUtils.broadcast(listenerService, "exo.process.request.canceled", taskDto, projectDto);
    }
    return taskDto;
  }

  @Override
  public Work saveWork(Work work, long userId) throws IllegalArgumentException {
    if (work == null) {
      throw new IllegalArgumentException("work argument is null");
    }
    Identity identity = identityManager.getIdentity(String.valueOf(userId));
    if (identity == null) {
      throw new IllegalArgumentException("identity is not exist");
    }
    if (work.getId() == 0) {
      TaskDto taskDto = createWorkTask(work, identity);
      ProjectDto projectDto = taskDto.getStatus().getProject();
      if (work.getDraftId() != null) {
        processesAttachmentService.moveAttachmentsToEntity(userId,
                                                           work.getDraftId(),
                                                           WORK_DRAFT_ENTITY_TYPE,
                                                           taskDto.getId(),
                                                           TASK_ENTITY_TYPE,
                                                           projectDto.getId());
        deleteWorkDraftById(work.getDraftId());
      }
      Work newWork = EntityMapper.taskToWork(taskDto);
      newWork.setCreatorId(userId);
      ProcessesUtils.broadcast(listenerService, "exo.process.request.created", newWork, projectDto);
      return newWork;
    } else {
      TaskDto taskDto = updateWorkTask(work);
      return EntityMapper.taskToWork(taskDto);
    }
  }

  private WorkFlow createProject(WorkFlow workFlow) {
    Space processSpace = spaceService.getSpaceById(workFlow.getSpaceId());
    if (processSpace == null) {
      throw new IllegalArgumentException("Space of processes not exist");
    }

    List<String> memberships = UserUtil.getSpaceMemberships(processSpace.getGroupId());
    Set<String> managers = new HashSet<>(Arrays.asList(memberships.get(0)));
    Set<String> participators = new HashSet<>(Arrays.asList(memberships.get(1)));
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

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteWorkflowById(Long workflowId) throws javax.persistence.EntityNotFoundException {
    WorkFlowEntity workFlowEntity = this.workFlowDAO.find(workflowId);
    if (workFlowEntity == null) {
      throw new javax.persistence.EntityNotFoundException("Workflow not found");
    }
    try {
      ProjectDto project = projectService.getProject(workFlowEntity.getProjectId());
      if (project != null) {
        projectService.removeProject(project.getId(), true);
      }
    } catch (EntityNotFoundException e) {
      LOG.error("Error while getting workflow project", e);
    }
    List<WorkEntity> drafts = this.workDraftDAO.getDraftsByWorkflowId(workflowId);
    if (!drafts.isEmpty()) {
      workDraftDAO.deleteAll(drafts);
    }
    this.workFlowDAO.delete(workFlowEntity);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteWorkById(Long workId) {
    try {
      TaskDto taskDto = taskService.getTask(workId);
      if (taskDto != null) {
        taskService.removeTask(workId);
        ProjectDto projectDto = taskDto.getStatus().getProject();
        ProcessesUtils.broadcast(listenerService, "exo.process.request.removed", taskDto, projectDto);
      }
    } catch (EntityNotFoundException e) {
      LOG.error("Work not found", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work updateWorkCompleted(Long workId, boolean completed) {
    TaskDto taskDto;
    try {
      taskDto = taskService.getTask(workId);
      if (taskDto != null) {
        taskDto.setCompleted(completed);
        taskDto = taskService.updateTask(taskDto);
      }
    } catch (EntityNotFoundException e) {
      throw new javax.persistence.EntityNotFoundException("work not found");
    }
    return EntityMapper.taskToWork(taskDto);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Work> findAllWorkDraftsByUser(WorkFilter workFilter, int offset, int limit, long userIdentityId) {
    return EntityMapper.fromWorkEntities(workDraftDAO.findAllWorkDraftsByUser(workFilter, userIdentityId, offset, limit));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work saveWorkDraft(Work work, long userId) {
    Identity identity = identityManager.getIdentity(String.valueOf(userId));
    if (identity == null) {
      throw new IllegalArgumentException("identity is not exist");
    }
    WorkEntity workEntity = EntityMapper.toEntity(work);
    if (work.getId() == 0) {
      workEntity.setId(null);
      workEntity.setCreatedDate(new Date());
      workEntity.setCreatorId(userId);
      workEntity = workDraftDAO.create(workEntity);
      processesAttachmentService.copyAttachmentsToEntity(userId,
                                                         work.getWorkFlow().getId(),
                                                         WORKFLOW_ENTITY_TYPE,
                                                         workEntity.getId(),
                                                         WORK_DRAFT_ENTITY_TYPE,
                                                         work.getWorkFlow().getProjectId());
    } else {
      workEntity.setModifiedDate(new Date());
      workEntity = workDraftDAO.update(workEntity);
    }

    return EntityMapper.fromEntity(workEntity);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work getWorkDraftyId(long id) {
    return EntityMapper.fromEntity(workDraftDAO.find(id));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteWorkDraftById(long id) throws javax.persistence.EntityNotFoundException {
    WorkEntity workEntity = workDraftDAO.find(id);
    if (workEntity == null) {
      throw new javax.persistence.EntityNotFoundException("Work Draft not found");
    }
    workDraftDAO.delete(workEntity);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<WorkStatus> getAvailableWorkStatuses() {
    List<WorkStatus> statuses = new ArrayList<>();
    List<WorkFlow> workFlows = findAllWorkFlows(0, 0);
    List<Long> projectsIds = workFlows.stream().map(WorkFlow::getProjectId).collect(Collectors.toList());
    projectsIds.forEach(projectId -> statuses.addAll(EntityMapper.toWorkStatuses(statusService.getStatuses(projectId))));
    statuses.sort(Comparator.comparing(WorkStatus::getRank));
    return statuses;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<WorkFlow> findWorkFlows(ProcessesFilter processesFilter, long userIdentityId, int offset, int limit) {
    String userName = "";
    List<String> memberships = new ArrayList<>();
    boolean isMemberProcessesGroup = false;
    if (userIdentityId > 0) {
      Identity identity = identityManager.getIdentity(String.valueOf(userIdentityId));
      if (identity != null) {
        userName = identity.getRemoteId();
        memberships.add(userName);
        try {
          Collection<Membership> ms = organizationService.getMembershipHandler().findMembershipsByUser(userName);
          if (ms != null) {
            for (Membership membership : ms) {
              isMemberProcessesGroup = false;
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
      }
    }
    List<WorkFlowEntity> workFlowEntities;
    if (isMemberProcessesGroup) {
      workFlowEntities = workFlowDAO.findWorkFlows(processesFilter, null, offset, limit);
    } else {
      workFlowEntities = workFlowDAO.findWorkFlows(processesFilter, memberships, offset, limit);
    }
    List<WorkFlow> workFlows = new ArrayList<>();
    List<String> finalMemberships = memberships;
    workFlowEntities.forEach(workflowEntity -> {
      IllustrativeAttachment illustrativeAttachment = null;
      try {
        illustrativeAttachment = getIllustrationImageById(workflowEntity.getIllustrationImageId());
        if (illustrativeAttachment != null) {
          illustrativeAttachment.setFileInputStream(null);
        }
      } catch (Exception e) {
        LOG.error("Error while getting workflow illustration image", e);
      }
      workFlows.add(EntityMapper.fromEntity(workflowEntity, illustrativeAttachment, finalMemberships));
    });
    String finalUserName = userName;
    workFlows.forEach(workflow -> {
      if (workflow != null) {
        boolean canShowPending = false;
        try {
          Space space = ProcessesUtils.getProjectParentSpace(workflow.getProjectId());
          canShowPending = canShowPending(finalUserName, space);
        } catch (Exception e) {
          LOG.error("Error while getting workflow can Show Pending", e);
        }
        workflow.setCanShowPending(canShowPending);
      }
    });
    return workFlows;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int countWorkFlows(ProcessesFilter processesFilter) {
    return workFlowDAO.countWorkFlows(processesFilter);
  }

  private boolean canShowPending(String authenticatedUser, Space space) {
    if (space != null) {
      return (spaceService.isSuperManager(authenticatedUser) || spaceService.isMember(space, authenticatedUser));
    } else
      return false;
  }

}
