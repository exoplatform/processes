/*
 * Copyright (C) 2026 eXo Platform SAS
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
package org.exoplatform.processes.upgrade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.EntityManager;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.dao.RequestDAO;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.RequestEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.service.ProcessesServiceImpl;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dao.OrderBy;
import org.exoplatform.task.dao.TaskQuery;
import org.exoplatform.task.dao.condition.PredicateCondition;
import org.exoplatform.task.domain.Task;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.TaskService;

/**
 * Records the tasks of the processes created before each request named its
 * process as their requests, and gives those processes their managing space.
 * Before, a task project fed exactly one process, so every task of a project
 * fed by one process is a request of that process; a project several processes
 * feed is left as it is, with a warning. An existing record is never
 * overwritten, so a rerun records only what is missing.
 * <p>
 * Best effort for one case only: a task whose creator has no social identity
 * is skipped with a warning, since no later run could record it. Any other
 * failure is thrown, so the upgrade is not recorded as done: after a failed
 * upgrade from an earlier version, commons runs it again at the next startup;
 * on an instance whose version did not change (the plugin deployed on a
 * running version), it is not run again until the next version change.
 */
public class ProcessesRequestUpgradePlugin extends UpgradeProductPlugin {

  private static final Log      LOG       = ExoLogger.getLogger(ProcessesRequestUpgradePlugin.class);

  /** How many tasks one page reads. */
  static final int              PAGE_SIZE = 200;

  private final WorkFlowDAO     workFlowDAO;

  private final RequestDAO      requestDAO;

  private final TaskService     taskService;

  private final ProjectService  projectService;

  private final IdentityManager identityManager;

  private final SpaceService    spaceService;

  private final EntityManagerService entityManagerService;

  /**
   * @param initParams the plugin's parameters
   * @param workFlowDAO the processes' storage
   * @param requestDAO the request records' storage
   * @param taskService the Task add-on's tasks
   * @param projectService the Task add-on's projects
   * @param identityManager the social identities
   * @param spaceService the spaces
   * @param entityManagerService the entity manager of the startup, cleared
   *          after each page so that a page's flush does not grow with the
   *          pages before it
   */
  public ProcessesRequestUpgradePlugin(InitParams initParams,
                                       WorkFlowDAO workFlowDAO,
                                       RequestDAO requestDAO,
                                       TaskService taskService,
                                       ProjectService projectService,
                                       IdentityManager identityManager,
                                       SpaceService spaceService,
                                       EntityManagerService entityManagerService) {
    super(initParams);
    this.workFlowDAO = workFlowDAO;
    this.requestDAO = requestDAO;
    this.taskService = taskService;
    this.projectService = projectService;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.entityManagerService = entityManagerService;
  }

  /**
   * Gives each process its managing space when it has none, then records the
   * tasks of each project fed by one process as that process's requests.
   *
   * @param oldVersion the version upgraded from
   * @param newVersion the version upgraded to
   */
  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long start = System.currentTimeMillis();
    List<WorkFlowEntity> workFlows = workFlowDAO.findAll();
    Map<Long, List<WorkFlowEntity>> processesOfProject = new LinkedHashMap<>();
    int spacesSet = 0;
    for (WorkFlowEntity workFlow : workFlows) {
      if (backfillSpace(workFlow)) {
        spacesSet++;
      }
      if (hasProject(workFlow)) {
        processesOfProject.computeIfAbsent(workFlow.getProjectId(), projectId -> new ArrayList<>()).add(workFlow);
      }
    }
    BackfillCount count = new BackfillCount();
    int sharedProjects = 0;
    for (Map.Entry<Long, List<WorkFlowEntity>> entry : processesOfProject.entrySet()) {
      if (entry.getValue().size() > 1) {
        sharedProjects++;
        LOG.warn("The task project {} feeds the processes {}: its tasks are not recorded as requests, since none tells which process created them",
                 entry.getKey(),
                 entry.getValue().stream().map(WorkFlowEntity::getId).toList());
      } else {
        backfillRequests(entry.getKey(), entry.getValue().get(0).getId(), count);
      }
    }
    LOG.info("Processes requests upgrade: {} process spaces set, {} requests recorded, {} already recorded, {} skipped for a creator with no identity, {} shared projects skipped, in {} ms",
             spacesSet,
             count.recorded,
             count.alreadyRecorded,
             count.noCreatorIdentity,
             sharedProjects,
             System.currentTimeMillis() - start);
  }

  /**
   * Sets the managing space of a process that has none: the space its own
   * participants name, else the space its task project is attached to.
   *
   * @param workFlow the process
   * @return true when its space was set
   */
  boolean backfillSpace(WorkFlowEntity workFlow) {
    if (workFlow.getSpaceId() != null) {
      return false;
    }
    Space space = getParticipatorsSpace(workFlow.getParticipator());
    if (space == null && hasProject(workFlow)) {
      space = getAttachedSpace(workFlow.getProjectId());
    }
    if (space == null) {
      LOG.warn("No managing space found for the process {}: it keeps none", workFlow.getId());
      return false;
    }
    workFlow.setSpaceId(Long.parseLong(space.getId()));
    workFlowDAO.update(workFlow);
    return true;
  }

  /**
   * Reads the space a task project is attached to, by the rule the process
   * editor applies to the projects it offers: the space whose manager
   * membership its managers hold, with no group membership of another space.
   *
   * @param projectId the task project
   * @return the space, or null when the project is attached to none
   */
  private Space getAttachedSpace(long projectId) {
    Space space = ProcessesUtils.getProjectParentSpace(projectService, spaceService, projectId);
    if (space == null) {
      return null;
    }
    try {
      ProjectDto project = projectService.getProject(projectId);
      return project != null && ProcessesServiceImpl.isAttachedToSpace(project, space.getGroupId()) ? space : null;
    } catch (EntityNotFoundException e) {
      return null;
    }
  }

  /**
   * @param workFlow a process
   * @return true when it names a task project
   */
  private static boolean hasProject(WorkFlowEntity workFlow) {
    return workFlow.getProjectId() != null && workFlow.getProjectId() > 0;
  }

  /**
   * Reads the space a process's participants name: processes writes them as
   * {@code manager:} and {@code member:} memberships of its managing space.
   *
   * @param participators the process's participants
   * @return the space, or null when none names an existing space
   */
  private Space getParticipatorsSpace(Set<String> participators) {
    if (participators == null) {
      return null;
    }
    for (String participator : participators) {
      String groupId = StringUtils.substringAfter(participator, ":");
      if (StringUtils.startsWith(groupId, "/spaces/")) {
        Space space = spaceService.getSpaceByGroupId(groupId);
        if (space != null) {
          return space;
        }
      }
    }
    return null;
  }

  /**
   * Records each task of a project as a request of the one process it feeds,
   * page by page in task id order, leaving the tasks already recorded as they
   * are. A page's records are written in one transaction, then the entity
   * manager is cleared, so the run grows linearly with the tasks.
   *
   * @param projectId the task project
   * @param workFlowId the process it feeds
   * @param count the counts of the upgrade, incremented
   */
  void backfillRequests(long projectId, long workFlowId, BackfillCount count) {
    Long afterTaskId = null;
    List<TaskDto> page;
    do {
      page = findTasks(projectId, afterTaskId);
      Map<Long, Long> recorded = requestDAO.findWorkFlowIdsByTaskIds(page.stream().map(TaskDto::getId).toList());
      List<RequestEntity> records = new ArrayList<>();
      for (TaskDto task : page) {
        if (recorded.containsKey(task.getId())) {
          count.alreadyRecorded++;
          continue;
        }
        Identity creator = StringUtils.isBlank(task.getCreatedBy()) ? null
                                                                    : identityManager.getOrCreateUserIdentity(task.getCreatedBy());
        if (creator == null) {
          count.noCreatorIdentity++;
          LOG.warn("The task {} of the process {} is not recorded as a request: its creator '{}' has no identity",
                   task.getId(),
                   workFlowId,
                   task.getCreatedBy());
          continue;
        }
        records.add(new RequestEntity(task.getId(), workFlowId, Long.parseLong(creator.getId())));
      }
      if (!records.isEmpty()) {
        requestDAO.createAll(records);
        count.recorded += records.size();
      }
      if (!page.isEmpty()) {
        afterTaskId = page.get(page.size() - 1).getId();
      }
      EntityManager entityManager = entityManagerService.getEntityManager();
      if (entityManager != null) {
        entityManager.clear();
      }
    } while (page.size() == PAGE_SIZE);
  }

  /**
   * Reads a page of a project's tasks, in task id order, after a task id.
   *
   * @param projectId the task project
   * @param afterTaskId the last task id of the previous page, null for the
   *          first page
   * @return the page
   */
  private List<TaskDto> findTasks(long projectId, Long afterTaskId) {
    TaskQuery taskQuery = new TaskQuery();
    taskQuery.setProjectIds(List.of(projectId));
    if (afterTaskId != null) {
      taskQuery.add(new PredicateCondition<Task>((cb, query, root) -> cb.greaterThan(root.<Long> get("id"), afterTaskId)));
    }
    taskQuery.setOrderBy(List.of(new OrderBy.ASC("id")));
    try {
      return taskService.findTasks(taskQuery, 0, PAGE_SIZE);
    } catch (Exception e) {
      throw new IllegalStateException("The tasks of the project " + projectId + " could not be read", e);
    }
  }

  /**
   * The counts an upgrade logs.
   */
  static class BackfillCount {

    /** The requests recorded. */
    int recorded;

    /** The tasks that already had a record. */
    int alreadyRecorded;

    /** The tasks skipped for a creator with no identity. */
    int noCreatorIdentity;
  }
}
