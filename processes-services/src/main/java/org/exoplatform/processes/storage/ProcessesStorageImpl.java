package org.exoplatform.processes.storage;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.exoplatform.processes.Utils.EntityMapper;
import org.exoplatform.processes.Utils.Utils;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.task.dao.TaskQuery;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.service.TaskService;

public class ProcessesStorageImpl implements ProcessesStorage {

  private static final Log LOG = ExoLogger.getLogger(ProcessesStorageImpl.class);

  private final WorkFlowDAO   workFlowDAO;

  private final IdentityManager  identityManager;

  private final TaskService      taskService;

  public ProcessesStorageImpl(WorkFlowDAO workFlowDAO, TaskService taskService, IdentityManager identityManager) {
    this.workFlowDAO = workFlowDAO;
    this.identityManager = identityManager;
    this.taskService = taskService;
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
      throw new IllegalArgumentException("work type argument is null");
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
    List<WorkFlow> workFlows = findEnabledWorkFlows(0,0);
    List<Long> projectsIds =
            workFlows.stream()
                    .map(WorkFlow::getProjectId)
                    .collect(Collectors.toList());
    TaskQuery taskQuery = new TaskQuery();
    taskQuery.setProjectIds(projectsIds);
    taskQuery.setCreatedBy(Utils.getUserNameByIdentityId(identityManager, userIdentityId));
      List<TaskDto> tasks = taskService.findTasks(taskQuery,offset,limit);
      return (EntityMapper.taskstoWorkList(tasks));
  }
}
