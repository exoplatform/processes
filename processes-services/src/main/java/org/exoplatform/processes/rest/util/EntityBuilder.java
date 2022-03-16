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
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.rest.model.WorkEntity;
import org.exoplatform.processes.rest.model.WorkFlowEntity;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.commons.utils.HTMLSanitizer;
import org.exoplatform.task.service.StatusService;

public class EntityBuilder {
  private static final Log LOG = ExoLogger.getExoLogger(EntityBuilder.class);

  private EntityBuilder() {
  }

  public static WorkFlow fromEntity(WorkFlowEntity workFlowEntity) {
    if (workFlowEntity == null) {
      return null;
    }
    return new WorkFlow(workFlowEntity.getId(),
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
                        null,
                        workFlowEntity.getAttachments());
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
                              null,
                              ProcessesUtils.getProjectParentSpace(workFlow.getProjectId()));
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

  public static Work toWork(ProcessesService processesService, WorkEntity workEntity) {
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
                         workEntity.getProjectId());
    if (workEntity.getWorkFlow() != null) {
      try {
        WorkFlow workFlow = processesService.getWorkFlow(workEntity.getWorkFlow().getId());
        if (workFlow != null) {
          work.setProjectId(workFlow.getProjectId());
        }
      } catch (IllegalAccessException e) {
        LOG.warn("cannot get workFlow of work {}", work.getId());
      }
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

    try {
      workEntity.setDescription(HTMLSanitizer.sanitize(work.getDescription()));
    } catch (Exception e) {
      LOG.warn("Work description cannot be sanitized", e);
    }
    if (expandProperties.contains("workFlow")) {
      workEntity.setWorkFlow(toEntity(processesService.getWorkFlowByProjectId(work.getProjectId()), ""));
    }
    return workEntity;
  }

  public static WorkEntity toEntity(Work work) {
    if (work == null) {
      return null;
    }

    WorkEntity WorkEntity = new WorkEntity(work.getId(),
                                                          work.getTitle(),
                                                          work.getDescription(),
                                                          work.getCreatorId(),
                                                          work.getCreatedDate(),
                                                          work.getModifiedDate(),
                                                          work.getTaskId(),
                                                          work.getIsDraft(),
                                                          toEntity(work.getWorkFlow()));

    return WorkEntity;
  }

  public static List<WorkEntity> toWorkEntityList(ProcessesService processesService, List<Work> works, String expand) {
    if (CollectionUtils.isEmpty(works)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      List<WorkEntity> workEntityList = works.stream()
                                             .map(work -> toWorkEntity(processesService, work, expand))
                                             .collect(Collectors.toList());
      return workEntityList;
    }
  }

  public static List<WorkEntity> toWorkEntityList(List<Work> works) {
    if (CollectionUtils.isEmpty(works)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      List<WorkEntity> WorkEntityList = works.stream()
              .map(workDraft -> toEntity(workDraft))
              .collect(Collectors.toList());
      return WorkEntityList;
    }
  }

}
