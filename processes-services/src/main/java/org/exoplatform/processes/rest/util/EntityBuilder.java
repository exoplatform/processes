/*
 * Copyright (C) 2021 eXo Platform SAS
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
package org.exoplatform.processes.rest.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.HTMLSanitizer;
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.constant.ProjectMode;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.rest.model.WorkEntity;
import org.exoplatform.processes.rest.model.WorkFlowEntity;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.service.StatusService;

public class EntityBuilder {
  private static final Log LOG = ExoLogger.getExoLogger(EntityBuilder.class);

  private EntityBuilder() {
  }

  /**
   * Builds the process a REST body describes.
   *
   * @param workFlowEntity the REST body
   * @return the process, or null when no body is given
   * @throws IllegalArgumentException processes.project.invalidMode when the
   *           project mode is neither NEW nor EXISTING
   */
  public static WorkFlow fromEntity(WorkFlowEntity workFlowEntity) {
    if (workFlowEntity == null) {
      return null;
    }
    WorkFlow workFlow = new WorkFlow(workFlowEntity.getId(),
                        workFlowEntity.getTitle(),
                        workFlowEntity.getDescription(),
                        workFlowEntity.getSummary(),
                        workFlowEntity.getImage(),
                        workFlowEntity.getHelpLink(),
                        workFlowEntity.isEnabled(),
                        workFlowEntity.getCreatorId(),
                        workFlowEntity.getCreatedDate(),
                        workFlowEntity.getModifierId(),
                        workFlowEntity.getModifiedDate(),
                        workFlowEntity.getProjectId(),
                        workFlowEntity.getParentSpace() != null
                            && workFlowEntity.getParentSpace().getId() != null ? workFlowEntity.getParentSpace().getId() : "",
                        null,
                        workFlowEntity.getAttachments(),
                        workFlowEntity.getIllustrativeAttachment(),
                        null,
                        null,
                        false,
                        workFlowEntity.getRequestsCreators());
    workFlow.setProjectMode(toProjectMode(workFlowEntity.getProjectMode()));
    // The Service checks every label against the process's project
    workFlow.setLabels(workFlowEntity.getLabels());
    return workFlow;
  }

  /**
   * Reads the project mode a REST body states.
   *
   * @param projectMode the mode as sent, null or blank when not stated
   * @return the mode, or null when not stated
   * @throws IllegalArgumentException processes.project.invalidMode for any
   *           other value than NEW or EXISTING
   */
  public static ProjectMode toProjectMode(String projectMode) {
    if (StringUtils.isBlank(projectMode)) {
      return null;
    }
    try {
      return ProjectMode.valueOf(projectMode);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("processes.project.invalidMode");
    }
  }

  public static Work fromEntity(WorkEntity workEntity) {
    if (workEntity == null) {
      return null;
    }
    return new Work(workEntity.getId(),
                    workEntity.getTitle(),
                    workEntity.getDescription(),
                    workEntity.getCreatorId(),
                    workEntity.getCreatedTime(),
                    workEntity.getModifiedTime(),
                    workEntity.getTaskId(),
                    workEntity.getIsDraft(),
                    fromEntity(workEntity.getWorkFlow()));
  }

  /**
   * Builds the REST view of a process, with its project's statuses and its
   * managing space.
   *
   * @param workFlow the process
   * @param expand the properties to expand, none is supported yet
   * @return the REST view, or null when no process is given
   */
  public static WorkFlowEntity toEntity(WorkFlow workFlow, String expand) {
    if (workFlow == null) {
      return null;
    }
    StatusService statusService = CommonsUtils.getService(StatusService.class);
    List<String> expandProperties =
                                  StringUtils.isBlank(expand) ? Collections.emptyList()
                                                              : Arrays.asList(StringUtils.split(expand.replaceAll(" ", ""), ","));
    // TODO: add expand properties
    return new WorkFlowEntity(workFlow.getId(),
                              workFlow.getTitle(),
                              workFlow.getDescription(),
                              workFlow.getSummary(),
                              workFlow.getImage(),
                              workFlow.getHelpLink(),
                              workFlow.isEnabled(),
                              workFlow.getCreatorId(),
                              workFlow.getCreatedDate(),
                              workFlow.getModifierId(),
                              workFlow.getModifiedDate(),
                              workFlow.getProjectId(),
                              workFlow.getAttachments(),
                              statusService.getStatuses(workFlow.getProjectId()),
                              workFlow.getAcl(),
                              getManagingSpace(workFlow),
                              workFlow.isCanShowPending(),
                              workFlow.getIllustrativeAttachment(),
                              workFlow.getRequestsCreators(),
                              null,
                              workFlow.getLabels());
  }

  /**
   * Reads the managing space a process is shown with: its stored space, else,
   * for a process stored before it had one, the space of its task project, so
   * that the editor sends back the space the update checks against.
   *
   * @param workFlow the process
   * @return the space, or null when neither is known
   */
  private static Space getManagingSpace(WorkFlow workFlow) {
    if (StringUtils.isNotBlank(workFlow.getSpaceId())) {
      Space storedSpace = CommonsUtils.getService(SpaceService.class).getSpaceById(workFlow.getSpaceId());
      if (storedSpace != null) {
        return storedSpace;
      }
    }
    return ProcessesUtils.getProjectParentSpace(workFlow.getProjectId());
  }

  public static WorkFlowEntity toEntity(WorkFlow workFlow) {
    if (workFlow == null) {
      return null;
    }
    WorkFlowEntity workFlowEntity = new WorkFlowEntity();

    workFlowEntity.setId(workFlow.getId());
    workFlowEntity.setTitle(workFlow.getTitle());
    workFlowEntity.setDescription(workFlow.getDescription());
    workFlowEntity.setSummary(workFlow.getSummary());
    workFlowEntity.setImage(workFlow.getImage());
    workFlowEntity.setHelpLink(workFlow.getHelpLink());
    workFlowEntity.setEnabled(workFlow.isEnabled());
    workFlowEntity.setCreatorId(workFlow.getCreatorId());
    workFlowEntity.setCreatedDate(workFlow.getCreatedDate());
    workFlowEntity.setModifierId(workFlow.getModifierId());
    workFlowEntity.setModifiedDate(workFlow.getModifiedDate());
    workFlowEntity.setProjectId(workFlow.getProjectId());
    workFlowEntity.setIllustrativeAttachment(workFlow.getIllustrativeAttachment());
    workFlowEntity.setAcl(workFlow.getAcl());
    return workFlowEntity;
  }

  public static List<WorkFlow> fromRestEntities(List<WorkFlowEntity> workFlowEntities) {
    if (CollectionUtils.isEmpty(workFlowEntities)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      List<WorkFlow> workFlows = workFlowEntities.stream().map(workEntity -> fromEntity(workEntity)).collect(Collectors.toList());
      return workFlows;
    }
  }

  public static List<WorkFlowEntity> toRestEntities(List<WorkFlow> workFlowList, String expand) {
    if (CollectionUtils.isEmpty(workFlowList)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      List<WorkFlowEntity> workFlowEntities = workFlowList.stream()
                                                          .map(workFlow -> toEntity(workFlow, expand))
                                                          .collect(Collectors.toList());
      return workFlowEntities;
    }
  }

  /**
   * Builds the request a REST body describes. The process it is submitted to is
   * the body's workFlow id; the project id the body names is never kept: a new
   * request goes to its process's stored project, and an updated one stays in
   * its own.
   *
   * @param processesService the processes service, unused
   * @param workEntity the REST body
   * @return the request, or null when no body is given
   */
  public static Work toWork(ProcessesService processesService, WorkEntity workEntity) { // NOSONAR the signature is the callers'
    if (workEntity == null) {
      return null;
    }

    Work work = new Work(workEntity.getId(),
                         workEntity.getTitle(),
                         workEntity.getDescription(),
                         workEntity.getStatus(),
                         workEntity.isCompleted(),
                         workEntity.getCreatedBy(),
                         workEntity.getCreatedTime(),
                         null,
                         null,
                         null,
                         workEntity.getIsDraft(),
                         workEntity.getDraftId(),
                         0);
    work.setAttachments(workEntity.getAttachments());
    if (workEntity.getWorkFlow() != null) {
      work.setWorkFlowId(workEntity.getWorkFlow().getId());
    }
    return work;
  }

  public static WorkEntity toWorkEntity(ProcessesService processesService, Work work, String expand) {
    if (work == null) {
      return null;
    }
    List<String> expandProperties =
                                  StringUtils.isBlank(expand) ? Collections.emptyList()
                                                              : Arrays.asList(StringUtils.split(expand.replaceAll(" ", ""), ","));

    WorkEntity workEntity = new WorkEntity(work.getId(),
                                           work.getTitle(),
                                           work.getDescription(),
                                           work.getStatus(),
                                           work.isCompleted(),
                                           work.getCreatedBy(),
                                           work.getCreatedDate(),
                                           work.getProjectId());
    if (expandProperties.contains("comments")) {
      // TODO: Add comments
    }
    workEntity.setAttachments(work.getAttachments());

    try {
      workEntity.setDescription(HTMLSanitizer.sanitize(work.getDescription()));
    } catch (Exception e) {
      LOG.warn("Work description cannot be sanitized", e);
    }
    if (expandProperties.contains("workFlow")) {
      workEntity.setWorkFlow(toEntity(getWorkFlowOfWork(processesService, work), ""));
    }
    return workEntity;
  }

  /**
   * Reads the process a request belongs to, for its display: the process it was
   * created for when the request names it, else the first process of its
   * project. The read is the platform's own, so its permissions are all false.
   *
   * @param processesService the processes service
   * @param work the request
   * @return the process, or null when none is found
   */
  @SuppressWarnings("removal")
  private static WorkFlow getWorkFlowOfWork(ProcessesService processesService, Work work) {
    if (work.getWorkFlowId() > 0) {
      try {
        return processesService.getWorkFlow(work.getWorkFlowId(), null);
      } catch (IllegalAccessException e) {
        LOG.debug("The process {} of the request {} cannot be read", work.getWorkFlowId(), work.getId(), e);
        return null;
      }
    }
    return processesService.getWorkFlowByProjectId(work.getProjectId());
  }

  public static WorkEntity toEntity(Work work) {
    if (work == null) {
      return null;
    }

    return new WorkEntity(work.getId(),
                          work.getTitle(),
                          work.getDescription(),
                          work.getCreatorId(),
                          work.getCreatedDate(),
                          work.getModifiedDate(),
                          work.getTaskId(),
                          work.getIsDraft(),
                          toEntity(work.getWorkFlow()));
  }

  public static List<WorkEntity> toWorkEntityList(ProcessesService processesService, List<Work> works, String expand) {
    if (CollectionUtils.isEmpty(works)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      return works.stream().map(work -> toWorkEntity(processesService, work, expand)).collect(Collectors.toList());
    }
  }

  public static List<WorkEntity> toWorkEntityList(List<Work> works) {
    if (CollectionUtils.isEmpty(works)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      return works.stream().map(EntityBuilder::toEntity).collect(Collectors.toList());
    }
  }

}
