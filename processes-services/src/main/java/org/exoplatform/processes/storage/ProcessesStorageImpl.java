package org.exoplatform.processes.storage;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.exoplatform.processes.Utils.EntityMapper;
import org.exoplatform.processes.Utils.Utils;
import org.exoplatform.processes.dao.DemandeTypeDAO;
import org.exoplatform.processes.entity.DemandeTypeEntity;
import org.exoplatform.processes.model.Demande;
import org.exoplatform.processes.model.DemandeType;
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

  private final DemandeTypeDAO   demandeTypeDAO;

  private final IdentityManager  identityManager;

  private final TaskService      taskService;

  public ProcessesStorageImpl(DemandeTypeDAO demandeTypeDAO, TaskService taskService, IdentityManager identityManager) {
    this.demandeTypeDAO = demandeTypeDAO;
    this.identityManager = identityManager;
    this.taskService = taskService;
  }

  @Override
  public List<DemandeType> findAllDemandeTypesByUser(ProcessesFilter filter, int offset, int limit, long userIdentityId) {
    // TODO: add filter props to the request
    return EntityMapper.fromEntities(demandeTypeDAO.findAllDemandeTypesByUser(userIdentityId, offset, limit));
  }

  @Override
  public List<DemandeType> findEnabledDemandeTypesByUser(ProcessesFilter filter, int offset, int limit, long userIdentityId) {
    // TODO: add filter props to the request
    return EntityMapper.fromEntities(demandeTypeDAO.findEnabledDemandeTypesByUser(userIdentityId, offset, limit));
  }

  @Override
  public List<DemandeType> findAllDemandeTypes(int offset, int limit) {
    return EntityMapper.fromEntities(demandeTypeDAO.findAllDemandeTypes(offset, limit));
  }

  @Override
  public List<DemandeType> findEnabledDemandeTypes(int offset, int limit) {
    return EntityMapper.fromEntities(demandeTypeDAO.findEnabledDemandeTypes(offset, limit));
  }

  @Override
  public DemandeType getDemandeTypeById(long id) {
    return EntityMapper.fromEntity(demandeTypeDAO.find(id));
  }

  @Override
  public DemandeType getDemandeTypeByProjectId(long projectId) {
    return EntityMapper.fromEntity(demandeTypeDAO.getDemandeTypeByProjectId(projectId));
  }

  @Override
  public DemandeType saveDemandeType(DemandeType demandeType, long userId) throws IllegalArgumentException {
    if (demandeType == null) {
      throw new IllegalArgumentException("demande type argument is null");
    }
    Identity identity = identityManager.getIdentity(String.valueOf(userId));
    if (identity == null) {
      throw new IllegalArgumentException("identity is not exist");
    }
    DemandeTypeEntity demandeTypeEntity = EntityMapper.toEntity(demandeType);
    if (demandeType.getId() == 0) {
      demandeTypeEntity.setId(null);
      demandeTypeEntity.setCreatedDate(new Date());
      demandeTypeEntity.setCreatorId(userId);
      demandeTypeEntity = demandeTypeDAO.create(demandeTypeEntity);
    } else {
      demandeTypeEntity.setModifiedDate(new Date());
      demandeTypeEntity.setModifierId(userId);
      demandeTypeEntity = demandeTypeDAO.update(demandeTypeEntity);
    }

    return EntityMapper.fromEntity(demandeTypeEntity);
  }

  @Override
  public List<Demande> getDemandes(long userIdentityId, int offset, int limit) throws Exception {
    List<DemandeType> demandeTypes = findEnabledDemandeTypes(0,0);
    List<Long> projectsIds =
            demandeTypes.stream()
                    .map(DemandeType::getProjectId)
                    .collect(Collectors.toList());
    TaskQuery taskQuery = new TaskQuery();
    taskQuery.setProjectIds(projectsIds);
    taskQuery.setCreatedBy(Utils.getUserNameByIdentityId(identityManager, userIdentityId));
      List<TaskDto> tasks = taskService.findTasks(taskQuery,offset,limit);
      return (EntityMapper.taskstoDemandeList(tasks));
  }
}
