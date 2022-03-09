package org.exoplatform.processes.storage;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.processes.Utils.EntityMapper;
import org.exoplatform.processes.Utils.Utils;
import org.exoplatform.processes.dao.WorkDraftDAO;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.WorkEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.*;
import org.exoplatform.processes.notification.utils.NotificationUtils;
import org.exoplatform.services.attachments.model.Attachment;
import org.exoplatform.services.attachments.service.AttachmentService;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dao.TaskQuery;
import org.exoplatform.task.domain.Priority;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.StatusService;
import org.exoplatform.task.service.TaskService;
import org.exoplatform.task.util.ProjectUtil;
import org.exoplatform.task.util.UserUtil;


public class ProcessesStorageImpl implements ProcessesStorage {

  private static final Log       LOG         = ExoLogger.getLogger(ProcessesStorageImpl.class);

  private final WorkFlowDAO      workFlowDAO;

  private final WorkDraftDAO     workDraftDAO;

  private final IdentityManager  identityManager;

  private final TaskService      taskService;

  private final ProjectService   projectService;

  private final StatusService    statusService;

  private final SpaceService    spaceService;
  
  private final ListenerService listenerService;

  private final AttachmentService attachmentService;

  private final String           DATE_FORMAT = "yyyy/MM/dd";

  private static final String PROCESSES_SPACE_GROUP_ID      = "/spaces/processes_space";

  private final SimpleDateFormat formatter   = new SimpleDateFormat(DATE_FORMAT);

  public ProcessesStorageImpl(WorkFlowDAO workFlowDAO,
                              WorkDraftDAO workDraftDAO,
                              TaskService taskService,
                              ProjectService projectService,
                              StatusService statusService,
                              IdentityManager identityManager,
                              SpaceService spaceService,
                              ListenerService listenerService, 
                              AttachmentService attachmentService) {
    this.workFlowDAO = workFlowDAO;
    this.workDraftDAO = workDraftDAO;
    this.identityManager = identityManager;
    this.taskService = taskService;
    this.projectService = projectService;
    this.statusService = statusService;
    this.spaceService = spaceService;
    this.listenerService = listenerService;
    this.attachmentService = attachmentService;
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
    return EntityMapper.fromEntity(workFlowDAO.find(id));
  }

  @Override
  public WorkFlow getWorkFlowByProjectId(long projectId) {
    return EntityMapper.fromEntity(workFlowDAO.getWorkFlowByProjectId(projectId));
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
    if (workFlow.getId() == 0) {
      workFlowEntity.setId(null);
      workFlowEntity.setCreatedDate(new Date());
      workFlowEntity.setCreatorId(userId);
      if(workFlow.getProjectId() == 0){
        long projectId = createProject(workFlow);
        workFlowEntity.setProjectId(projectId);
      }
      workFlowEntity = workFlowDAO.create(workFlowEntity);
    } else {
      workFlowEntity.setModifiedDate(new Date());
      workFlowEntity.setModifierId(userId);
      workFlowEntity = workFlowDAO.update(workFlowEntity);
    }

    return EntityMapper.fromEntity(workFlowEntity);
  }

  @Override
  public List<Work> getWorks(long userIdentityId, int offset, int limit) throws Exception {
    List<WorkFlow> workFlows = findAllWorkFlows(0, 0);
    List<Long> projectsIds = workFlows.stream().map(WorkFlow::getProjectId).collect(Collectors.toList());
    TaskQuery taskQuery = new TaskQuery();
    taskQuery.setProjectIds(projectsIds);
    taskQuery.setCreatedBy(Utils.getUserNameByIdentityId(identityManager, userIdentityId));
    List<TaskDto> tasks = taskService.findTasks(taskQuery, offset, limit);
    return (EntityMapper.tasksToWorkList(tasks));
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
    taskDto = taskService.updateTask(taskDto);
    return taskDto;
  }
  
  private void linkDraftAttachmentsToTask(long userId, long draftId, long taskId) {
    try {
      List<Attachment> attachments = attachmentService.getAttachmentsByEntity(userId, draftId, "workdraft");
      attachments.stream().map(Attachment::getId).forEach(attachmentId -> {
        try {
          attachmentService.linkAttachmentToEntity(userId, taskId, "task", attachmentId);
        } catch (IllegalAccessException e) {
          LOG.error("Error while attaching files to task entity", e);
        }
      });
      attachmentService.deleteAllEntityAttachments(userId, draftId, "workdraft");
    } catch (Exception e) {
      LOG.error("Error while getting attachments of work draft", e);
    }
    deleteWorkDraftById(draftId);
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
        linkDraftAttachmentsToTask(userId, work.getDraftId(), taskDto.getId());
      }
      Work newWork = EntityMapper.taskToWork(taskDto);
      NotificationUtils.broadcast(listenerService, "exo.process.request.created", newWork, projectDto);
      return newWork;
    } else {
      TaskDto taskDto = updateWorkTask(work);
      Work newWork = EntityMapper.taskToWork(taskDto);
      return newWork;
    }
  }

  private long createProject(WorkFlow workFlow) {
    Space processSpace = spaceService.getSpaceByGroupId(PROCESSES_SPACE_GROUP_ID);
    if (processSpace == null) {
      throw new IllegalArgumentException("Space of processes not exist");
    }

    List<String> memberships = UserUtil.getSpaceMemberships(processSpace.getGroupId());
    Set<String> managers = new HashSet<String>(Arrays.asList(memberships.get(0)));
    Set<String> participators = new HashSet<String>(Arrays.asList(memberships.get(1)));
    ProjectDto project = ProjectUtil.newProjectInstanceDto(workFlow.getTitle(), workFlow.getDescription(), managers, participators);
    project = projectService.createProject(project);
    statusService.createInitialStatuses(project);
    return project.getId();
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
    if (drafts.size() != 0) {
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
        NotificationUtils.broadcast(listenerService, "exo.process.request.removed", taskDto, projectDto);
      }
    } catch (EntityNotFoundException e) {
      LOG.error("Work not found", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Work> findAllWorkDraftsByUser(int offset, int limit, long userIdentityId) {
    return EntityMapper.fromWorkEntities(workDraftDAO.findAllWorkDraftsByUser(userIdentityId, offset, limit));
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
    WorkEntity WorkEntity = EntityMapper.toEntity(work);
    if (work.getId() == 0) {
      WorkEntity.setId(null);
      WorkEntity.setCreatedDate(new Date());
      WorkEntity.setCreatorId(userId);
      WorkEntity = workDraftDAO.create(WorkEntity);
    } else {
      WorkEntity.setModifiedDate(new Date());
      WorkEntity = workDraftDAO.update(WorkEntity);
    }

    return EntityMapper.fromEntity(WorkEntity);
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
  public void deleteWorkDraftById(long id) throws javax.persistence.EntityNotFoundException{
    WorkEntity WorkEntity = workDraftDAO.find(id);
    if (WorkEntity == null) {
      throw new javax.persistence.EntityNotFoundException("Work Draft not found");
    }
    workDraftDAO.delete(WorkEntity);
  }

}
