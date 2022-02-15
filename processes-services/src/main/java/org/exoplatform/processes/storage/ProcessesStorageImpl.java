package org.exoplatform.processes.storage;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import org.exoplatform.processes.Utils.EntityMapper;
import org.exoplatform.processes.Utils.Utils;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dao.TaskQuery;
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

  private final IdentityManager  identityManager;

  private final TaskService      taskService;

  private final ProjectService   projectService;

  private final StatusService    statusService;

  private final SpaceService    spaceService;

  private final String           DATE_FORMAT = "yyyy/MM/dd";

  private static final String PROCESSES_SPACE_GROUP_ID      = "/spaces/processes_space";

  private final SimpleDateFormat formatter   = new SimpleDateFormat(DATE_FORMAT);

  public ProcessesStorageImpl(WorkFlowDAO workFlowDAO,
                              TaskService taskService,
                              ProjectService projectService,
                              StatusService statusService,
                              IdentityManager identityManager,
                              SpaceService spaceService) {
    this.workFlowDAO = workFlowDAO;
    this.identityManager = identityManager;
    this.taskService = taskService;
    this.projectService = projectService;
    this.statusService = statusService;
    this.spaceService = spaceService;
  }

  @Override
  public List<WorkFlow> findAllWorkFlowsByUser(ProcessesFilter filter, int offset, int limit, long userIdentityId) {
    // TODO: add filter props to the request
    return EntityMapper.fromEntities(workFlowDAO.findAllWorkFlowsByUser(userIdentityId, offset, limit));
  }

  @Override
  public List<WorkFlow> findEnabledWorkFlowsByUser(ProcessesFilter filter, int offset, int limit, long userIdentityId) {
    // TODO: add filter props to the request
    return EntityMapper.fromEntities(workFlowDAO.findEnabledWorkFlowsByUser(userIdentityId, offset, limit));
  }

  @Override
  public List<WorkFlow> findAllWorkFlows(int offset, int limit) {
    return EntityMapper.fromEntities(workFlowDAO.findAllWorkFlows(offset, limit));
  }

  @Override
  public List<WorkFlow> findEnabledWorkFlows(int offset, int limit) {
    return EntityMapper.fromEntities(workFlowDAO.findEnabledWorkFlows(offset, limit));
  }

  @Override
  public List<WorkFlow> findDisabledWorkFlows(int offset, int limit) {
    return EntityMapper.fromEntities(workFlowDAO.findDisabledWorkFlows(offset, limit));
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
    List<WorkFlow> workFlows = findEnabledWorkFlows(0, 0);
    List<Long> projectsIds = workFlows.stream().map(WorkFlow::getProjectId).collect(Collectors.toList());
    TaskQuery taskQuery = new TaskQuery();
    taskQuery.setProjectIds(projectsIds);
    taskQuery.setCreatedBy(Utils.getUserNameByIdentityId(identityManager, userIdentityId));
    List<TaskDto> tasks = taskService.findTasks(taskQuery, offset, limit);
    return (EntityMapper.taskstoWorkList(tasks));
  }

  @Override
  public Work getWorkById(long id) {
    try {
      return EntityMapper.tasktoWork(taskService.getTask(id));
    } catch (EntityNotFoundException e) {
      return null;
    }
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
      try {
        projectService.getProject(work.getProjectId());
      } catch (EntityNotFoundException e) {
        throw new IllegalArgumentException("Task's project not found");
      }
      TaskDto taskDto = EntityMapper.worktoTask(work);
      if (StringUtils.isEmpty(taskDto.getTitle())) {
        taskDto.setTitle(formatter.format(new Date()) + " - " + identity.getProfile().getFullName());
      }
      taskDto.setStatus(statusService.getDefaultStatus(work.getProjectId()));
      taskDto.setCreatedBy(identity.getRemoteId());
      taskDto.setCreatedTime(new Date());
      taskDto = taskService.createTask(taskDto);
      return EntityMapper.tasktoWork(taskDto);
    } else {
      TaskDto taskDto = null;
      try {
        taskDto = taskService.getTask(work.getId());
      } catch (EntityNotFoundException e) {
        throw new IllegalArgumentException("Task not found");
      }
      taskDto.setDescription(work.getDescription());
      taskDto.setTitle(work.getTitle());
      taskDto.setCompleted(work.isCompleted());
      taskDto = taskService.updateTask(taskDto);
      return EntityMapper.tasktoWork(taskDto);
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
    this.workFlowDAO.delete(workFlowEntity);
  }
}
