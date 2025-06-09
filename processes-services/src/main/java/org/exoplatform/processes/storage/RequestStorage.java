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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import org.exoplatform.processes.Utils.EntityMapper;
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.dao.WorkDraftDAO;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.WorkEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFilter;
import org.exoplatform.processes.service.ProcessAttachmentService;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
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

@Component
public class RequestStorage {

  private static final Log               LOG                    = ExoLogger.getLogger(RequestStorage.class);

  private static final String            WORK_DRAFT_ENTITY_TYPE = "workdraft";

  private static final String            TASK_ENTITY_TYPE       = "task";

  private static final String            WORKFLOW_ENTITY_TYPE   = "workflow";

  private static final String[]          DEFAULT_PROCESS_STATUS = { "Request", "RequestInProgress", "Validated", "Refused",
      "Canceled" };

  private final WorkFlowDAO              workFlowDAO;

  private final WorkDraftDAO             workDraftDAO;

  private final IdentityManager          identityManager;

  private final TaskService              taskService;

  private final ProjectService           projectService;

  private final StatusService            statusService;

  private final ListenerService          listenerService;

  private final ProcessAttachmentService processAttachmentService;

  private final String                   DATE_FORMAT            = "yyyy/MM/dd";

  private final DateTimeFormatter        formatter              = DateTimeFormatter.ofPattern(DATE_FORMAT);

  public RequestStorage(WorkFlowDAO workFlowDAO,
                        WorkDraftDAO workDraftDAO,
                        TaskService taskService,
                        ProjectService projectService,
                        StatusService statusService,
                        IdentityManager identityManager,
                        ListenerService listenerService,
                        ProcessAttachmentService processAttachmentService) {
    this.workFlowDAO = workFlowDAO;
    this.workDraftDAO = workDraftDAO;
    this.identityManager = identityManager;
    this.taskService = taskService;
    this.projectService = projectService;
    this.statusService = statusService;
    this.listenerService = listenerService;
    this.processAttachmentService = processAttachmentService;
  }

  /**
   * Retrieves list of filtered works
   *
   * @param userName user name
   * @param workFilter works filter
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @return {@link List} of {@link Work}
   */
  public List<Work> getWorks(String userName, WorkFilter workFilter, int offset, int limit) {
    List<WorkFlowEntity> workFlows = workFlowDAO.findAllWorkFlows(0, 0);
    List<Long> projectsIds = workFlows.stream().map(WorkFlowEntity::getProjectId).collect(Collectors.toList());
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
    taskQuery.setCreatedBy(userName);
    List<TaskDto> tasks;
    try {
      tasks = taskService.findTasks(taskQuery, offset, limit);
    } catch (Exception e) {
      return null;
    }
    return (EntityMapper.tasksToWorkList(tasks));
  }

  /**
   * get a request by its given id
   *
   * @param id request id
   */
  public Work getWorkById(long id) {
    try {
      return EntityMapper.taskToWork(taskService.getTask(id));
    } catch (EntityNotFoundException e) {
      return null;
    }
  }

  /**
   * Saving a work and deletes its related draft if it was created from draft
   *
   * @param work Work Object
   * @param userName user name
   * @return {@link Work}
   */
  public Work saveWork(Work work, String userName) {
    Identity identity = identityManager.getOrCreateUserIdentity(userName);
    if (work.getId() == 0) {
      TaskDto taskDto = createWorkTask(work, identity);
      ProjectDto projectDto = taskDto.getStatus().getProject();
      if (work.getDraftId() != null) {
        processAttachmentService.moveAttachmentsToEntity(work.getAttachments(),
                                                         userName,
                                                         work.getDraftId(),
                                                         WORK_DRAFT_ENTITY_TYPE,
                                                         taskDto.getId(),
                                                         TASK_ENTITY_TYPE,
                                                         projectDto.getId());
        deleteWorkDraftById(work.getDraftId());
      }
      Work newWork = EntityMapper.taskToWork(taskDto);
      newWork.setCreatorId(Long.parseLong(identity.getId()));
      ProcessesUtils.broadcast(listenerService, "exo.process.request.created", newWork, projectDto);
      return newWork;
    } else {
      TaskDto taskDto = updateWorkTask(work);
      return EntityMapper.taskToWork(taskDto);
    }
  }

  /**
   * Delete a work by its given id
   *
   * @param workId: Work id
   */
  public void deleteWorkById(Long workId) {
    try {
      TaskDto taskDto = taskService.getTask(workId);
      if (taskDto != null) {
        taskService.removeTask(workId);
        ProjectDto projectDto = taskDto.getStatus().getProject();
        ProcessesUtils.broadcast(listenerService, "exo.process.request.removed", taskDto, projectDto);
      }
    } catch (EntityNotFoundException e) {
      LOG.error("Request not found", e);
    }
  }

  /**
   * update the completed property of the task of a work to completed or
   * uncompleted
   *
   * @param workId work id
   * @param completed work completed property, can be true or false
   * @return {@link Work}
   */

  public Work updateWorkCompleted(Long workId, boolean completed) {
    TaskDto taskDto;
    try {
      taskDto = taskService.getTask(workId);
      if (taskDto != null) {
        taskDto.setCompleted(completed);
        taskDto = taskService.updateTask(taskDto);
      }
    } catch (EntityNotFoundException e) {
      throw new IllegalArgumentException("work not found");
    }
    return EntityMapper.taskToWork(taskDto);
  }

  /**
   * Retrieves a list of accessible WorkDraft, for a selected user.
   *
   * @param workFilter work filter
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @param userIdentityId user Identity id
   * @return {@link List} of {@link Work}
   */
  public List<Work> findAllWorkDraftsByUser(WorkFilter workFilter, int offset, int limit, long userIdentityId) {
    return EntityMapper.fromWorkEntities(workDraftDAO.findAllWorkDraftsByUser(workFilter, userIdentityId, offset, limit));
  }

  /**
   * Save a draft of a request
   *
   * @param work work draft object
   * @param userName user name
   * @return {@link Work}
   */
  public Work saveWorkDraft(Work work, String userName) {
    Identity identity = identityManager.getOrCreateUserIdentity(userName);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }
    WorkEntity workEntity = EntityMapper.toEntity(work);
    workEntity.setModifiedDate(new Date());
    if (work.getId() == 0) {
      workEntity.setId(null);
      workEntity.setCreatedDate(new Date());
      workEntity.setCreatorId(Long.parseLong(identity.getId()));
      workEntity = workDraftDAO.create(workEntity);
      processAttachmentService.copyAttachmentsToEntity(userName,
                                                       work.getWorkFlow().getId(),
                                                       WORKFLOW_ENTITY_TYPE,
                                                       workEntity.getId(),
                                                       WORK_DRAFT_ENTITY_TYPE,
                                                       work.getWorkFlow().getProjectId());
    } else {
      workEntity = workDraftDAO.update(workEntity);
    }

    return EntityMapper.fromEntity(workEntity);
  }

  /**
   * Retrieves a work draft by its given id
   *
   * @param id Work draft id
   * @return {@link Work}
   */
  public Work getWorkDraftyId(long id) {
    return EntityMapper.fromEntity(workDraftDAO.find(id));
  }

  /**
   * Delete a work draft by its given id
   *
   * @param id Work draft id
   */
  public void deleteWorkDraftById(long id) {
    WorkEntity workEntity = workDraftDAO.find(id);
    if (workEntity == null) {
      return;
    }
    workDraftDAO.delete(workEntity);
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
      taskDto.setTitle(LocalDateTime.now().format(formatter) + " - " + identity.getProfile().getFullName());
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

}
