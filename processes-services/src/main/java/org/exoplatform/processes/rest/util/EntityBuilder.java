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
import java.util.Locale;
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
import org.exoplatform.processes.service.ProcessTranslationService;
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
    return toEntity(workFlow, expand, null);
  }

  /**
   * Builds the REST view of a process in a reader's language, with its
   * project's statuses and its managing space.
   *
   * @param workFlow the process
   * @param expand the properties to expand, none is supported yet
   * @param locale the reader's language, or null for the stored texts
   * @return the REST view, or null when no process is given
   */
  public static WorkFlowEntity toEntity(WorkFlow workFlow, String expand, Locale locale) {
    WorkFlowEntity workFlowEntity = newEntity(workFlow, expand);
    if (workFlowEntity != null) {
      translate(workFlowEntity, workFlow, locale);
    }
    return workFlowEntity;
  }

  /**
   * Builds the REST view of a process with its stored texts.
   *
   * @param workFlow the process
   * @param expand the properties to expand, none is supported yet
   * @return the REST view, or null when no process is given
   */
  private static WorkFlowEntity newEntity(WorkFlow workFlow, String expand) {
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
   * Puts a process's title, description and summary in a reader's language on
   * its REST view, leaving the process itself as stored.
   *
   * @param workFlowEntity the REST view
   * @param workFlow the process, as stored
   * @param locale the reader's language, or null to keep the stored texts
   */
  private static void translate(WorkFlowEntity workFlowEntity, WorkFlow workFlow, Locale locale) {
    if (locale == null) {
      return;
    }
    ProcessTranslationService processTranslationService = CommonsUtils.getService(ProcessTranslationService.class);
    workFlowEntity.setTitle(processTranslationService.getTitle(workFlow, locale));
    workFlowEntity.setDescription(processTranslationService.getDescription(workFlow, locale));
    workFlowEntity.setSummary(processTranslationService.getSummary(workFlow, locale));
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

  /**
   * Builds the short REST view of a process, the one a request draft carries,
   * with its stored texts.
   *
   * @param workFlow the process
   * @return the REST view, or null when no process is given
   */
  public static WorkFlowEntity toEntity(WorkFlow workFlow) {
    return toEntity(workFlow, (Locale) null);
  }

  /**
   * Builds the short REST view of a process, the one a request draft carries,
   * in a reader's language.
   *
   * @param workFlow the process
   * @param locale the reader's language, or null for the stored texts
   * @return the REST view, or null when no process is given
   */
  public static WorkFlowEntity toEntity(WorkFlow workFlow, Locale locale) {
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
    translate(workFlowEntity, workFlow, locale);
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

  /**
   * Builds the REST views of processes with their stored texts.
   *
   * @param workFlowList the processes
   * @param expand the properties to expand
   * @return the REST views
   */
  public static List<WorkFlowEntity> toRestEntities(List<WorkFlow> workFlowList, String expand) {
    return toRestEntities(workFlowList, expand, null);
  }

  /**
   * Builds the REST views of processes in a reader's language.
   *
   * @param workFlowList the processes
   * @param expand the properties to expand
   * @param locale the reader's language, or null for the stored texts
   * @return the REST views
   */
  public static List<WorkFlowEntity> toRestEntities(List<WorkFlow> workFlowList, String expand, Locale locale) {
    if (CollectionUtils.isEmpty(workFlowList)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      List<WorkFlowEntity> workFlowEntities = workFlowList.stream()
                                                          .map(workFlow -> toEntity(workFlow, expand, locale))
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

  /**
   * Builds the REST view of a request, its expanded process with its stored
   * texts.
   *
   * @param processesService the processes service
   * @param work the request
   * @param expand the properties to expand
   * @return the REST view, or null when no request is given
   */
  public static WorkEntity toWorkEntity(ProcessesService processesService, Work work, String expand) {
    return toWorkEntity(processesService, work, expand, null);
  }

  /**
   * Builds the REST view of a request, its expanded process in a reader's
   * language.
   *
   * @param processesService the processes service
   * @param work the request
   * @param expand the properties to expand
   * @param locale the reader's language, or null for the stored texts
   * @return the REST view, or null when no request is given
   */
  public static WorkEntity toWorkEntity(ProcessesService processesService, Work work, String expand, Locale locale) {
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
      workEntity.setWorkFlow(toEntity(getWorkFlowOfWork(processesService, work), "", locale));
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

  /**
   * Builds the REST view of a request draft, its process with its stored
   * texts.
   *
   * @param work the draft
   * @return the REST view, or null when no draft is given
   */
  public static WorkEntity toEntity(Work work) {
    return toEntity(work, null);
  }

  /**
   * Builds the REST view of a request draft, its process in a reader's
   * language.
   *
   * @param work the draft
   * @param locale the reader's language, or null for the stored texts
   * @return the REST view, or null when no draft is given
   */
  public static WorkEntity toEntity(Work work, Locale locale) {
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
                          toEntity(work.getWorkFlow(), locale));
  }

  /**
   * Builds the REST views of requests, their expanded processes with their
   * stored texts.
   *
   * @param processesService the processes service
   * @param works the requests
   * @param expand the properties to expand
   * @return the REST views
   */
  public static List<WorkEntity> toWorkEntityList(ProcessesService processesService, List<Work> works, String expand) {
    return toWorkEntityList(processesService, works, expand, null);
  }

  /**
   * Builds the REST views of requests, their expanded processes in a reader's
   * language.
   *
   * @param processesService the processes service
   * @param works the requests
   * @param expand the properties to expand
   * @param locale the reader's language, or null for the stored texts
   * @return the REST views
   */
  public static List<WorkEntity> toWorkEntityList(ProcessesService processesService,
                                                  List<Work> works,
                                                  String expand,
                                                  Locale locale) {
    if (CollectionUtils.isEmpty(works)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      return works.stream().map(work -> toWorkEntity(processesService, work, expand, locale)).collect(Collectors.toList());
    }
  }

  /**
   * Builds the REST views of request drafts, their processes with their
   * stored texts.
   *
   * @param works the drafts
   * @return the REST views
   */
  public static List<WorkEntity> toWorkEntityList(List<Work> works) {
    return toWorkEntityList(works, null);
  }

  /**
   * Builds the REST views of request drafts, their processes in a reader's
   * language.
   *
   * @param works the drafts
   * @param locale the reader's language, or null for the stored texts
   * @return the REST views
   */
  public static List<WorkEntity> toWorkEntityList(List<Work> works, Locale locale) {
    if (CollectionUtils.isEmpty(works)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      return works.stream().map(work -> toEntity(work, locale)).collect(Collectors.toList());
    }
  }

}
