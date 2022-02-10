package org.exoplatform.processes.storage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
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
import org.exoplatform.task.dao.TaskQuery;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.StatusService;
import org.exoplatform.task.service.TaskService;

public class ProcessesStorageImpl implements ProcessesStorage {

  private static final Log       LOG         = ExoLogger.getLogger(ProcessesStorageImpl.class);

  private final WorkFlowDAO      workFlowDAO;

  private final IdentityManager  identityManager;

  private final TaskService      taskService;

  private final ProjectService   projectService;

  private final StatusService    statusService;

  private final String           DATE_FORMAT = "yyyy/MM/dd";

  private final SimpleDateFormat formatter   = new SimpleDateFormat(DATE_FORMAT);

  public ProcessesStorageImpl(WorkFlowDAO workFlowDAO,
                              TaskService taskService,
                              ProjectService projectService,
                              StatusService statusService,
                              IdentityManager identityManager) {
    this.workFlowDAO = workFlowDAO;
    this.identityManager = identityManager;
    this.taskService = taskService;
    this.projectService = projectService;
    this.statusService = statusService;
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
}
