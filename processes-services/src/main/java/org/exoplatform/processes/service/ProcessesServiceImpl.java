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
package org.exoplatform.processes.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import io.meeds.common.ContainerTransactional;
import io.meeds.social.category.model.CategoryObject;
import io.meeds.social.category.service.CategoryLinkService;
import io.meeds.social.category.service.CategoryService;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.constant.ProcessCategories;
import org.exoplatform.processes.constant.ProjectMode;
import org.exoplatform.processes.model.*;
import org.exoplatform.processes.plugin.RequestValuesPlugin;
import org.exoplatform.processes.Utils.EntityMapper;
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.Utils.RequestTitle;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dao.OrderBy;
import org.exoplatform.task.dto.LabelDto;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.StatusDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.LabelService;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.StatusService;
import org.exoplatform.task.util.UserUtil;
import java.util.Collection;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.Objects;
import org.exoplatform.processes.constant.ProcessesEvents;

public class ProcessesServiceImpl implements ProcessesService {

  private static final String      STATUS_CANCELED     = "Canceled";

  private static final Set<String> PENDING_STATUSES    = Set.of("Request", "RequestInProgress");

  private static final Set<String> DECIDED_STATUSES    = Set.of("Validated", "Refused");

  /** The statuses after which a request changes no more, which « Archive all » archives */
  private static final Set<String> FINISHED_STATUSES   = Set.of("Validated", "Refused", "Canceled");

  /** The statuses of a process's task project, in their column order */
  private static final List<String> PROCESS_STATUSES   = List.of("Request", "RequestInProgress", "Validated", "Refused", "Canceled");

  private static final String      PROJECT_NOT_ELIGIBLE = "processes.project.notEligible";

  private static final String      STATUS_NAME_CONFLICT = "processes.project.statusNameConflict";

  private static final String      LABEL_NOT_IN_PROJECT = "processes.label.notInProject";

  private static final String      LABEL_INVALID_NAME   = "processes.label.invalidName";

  private static final String      FORM_UNSUPPORTED     = "processes.workflow.formUnsupported";

  /** The refusal of a requester form id that names no form. */
  private static final String      REQUESTER_FORM_INVALID = "processes.workflow.requesterFormInvalid";

  private static final String      FORM_VALUES_UNSUPPORTED = "processes.request.formValuesUnsupported";

  /** The size of a Task label name (TASK_LABELS.NAME). */
  private static final int         LABEL_NAME_MAX_LENGTH = 50;

  private static final Log         LOG                  = ExoLogger.getLogger(ProcessesServiceImpl.class);

  private final ProcessesStorage processesStorage;

  private final ProjectService   projectService;

  private final IdentityManager  identityManager;

  private final UserACL          userACL;

  private final SpaceService     spaceService;

  private final ProcessesAttachmentService processesAttachmentService;

  private final StatusService    statusService;

  private final LabelService     labelService;

  private final ListenerService  listenerService;

  private final ProcessTranslationService processTranslationService;

  /** Registered by another add-on at its start, read by request threads. */
  private volatile RequestValuesPlugin requestValuesPlugin;

  /** The links of processes to categories: social's, resolved at first use. */
  private CategoryLinkService      categoryLinkService;

  /** The category tree: social's, resolved at first use. */
  private CategoryService          categoryService;

  /**
   * Builds the processes service.
   *
   * @param processesStorage the processes storage
   * @param projectService the Task add-on's project service
   * @param identityManager the identity manager
   * @param userACL the platform ACL
   * @param spaceService the space service
   * @param processesAttachmentService the processes attachment service
   * @param statusService the Task add-on's status service
   * @param labelService the Task add-on's label service
   * @param listenerService the listener service
   * @param processTranslationService the per-language texts of a process
   */
  public ProcessesServiceImpl(ProcessesStorage processesStorage,
                              ProjectService projectService,
                              IdentityManager identityManager,
                              UserACL userACL,
                              SpaceService spaceService,
                              ProcessesAttachmentService processesAttachmentService,
                              StatusService statusService,
                              LabelService labelService,
                              ListenerService listenerService,
                              ProcessTranslationService processTranslationService) {
    this.processesStorage = processesStorage;
    this.projectService = projectService;
    this.identityManager = identityManager;
    this.userACL = userACL;
    this.spaceService = spaceService;
    this.processesAttachmentService = processesAttachmentService;
    this.statusService = statusService;
    this.labelService = labelService;
    this.listenerService = listenerService;
    this.processTranslationService = processTranslationService;
  }

  @Override
  public List<WorkFlow> getWorkFlows(ProcessesFilter filter,
                                     int offset,
                                     int limit,
                                     long userIdentityId) throws IllegalAccessException {
    // Only an active user lists processes
    getAclIdentity(userIdentityId);
    ProcessesFilter queried = toQueriedFilter(filter);
    if (queried.getIds() != null && queried.getIds().isEmpty()) {
      // The category files no process
      return new ArrayList<>();
    }
    return processesStorage.findWorkFlows(queried, userIdentityId, offset, limit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Long> getCategoryIds(long userIdentityId) throws IllegalAccessException {
    getAclIdentity(userIdentityId);
    ProcessesFilter filter = new ProcessesFilter();
    // Every process the user sees, those they may only request included
    filter.setManager(false);
    // One unpaged listing query, mapped to the rights only: the query has no
    // order a paging could rely on
    Set<String> workflowIds = new LinkedHashSet<>();
    processesStorage.findWorkFlowRights(filter, userIdentityId)
                    .stream()
                    .filter(ProcessesServiceImpl::isShownTo)
                    .map(workFlow -> String.valueOf(workFlow.getId()))
                    .forEach(workflowIds::add);
    if (workflowIds.isEmpty()) {
      return Collections.emptyList();
    }
    return getCategoryLinkService().getLinkedIds(ProcessCategories.OBJECT_TYPE, new ArrayList<>(workflowIds))
                                   .values()
                                   .stream()
                                   .flatMap(List::stream)
                                   .distinct()
                                   .toList();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<Long, List<Long>> getCategoryIds(List<Long> workflowIds) {
    if (workflowIds == null || workflowIds.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, List<Long>> linked =
                                   getCategoryLinkService().getLinkedIds(ProcessCategories.OBJECT_TYPE,
                                                                         workflowIds.stream().map(String::valueOf).toList());
    Map<Long, List<Long>> categoryIds = new LinkedHashMap<>();
    linked.forEach((workflowId, ids) -> {
      Long id = toWorkflowId(workflowId);
      if (id != null && ids != null && !ids.isEmpty()) {
        categoryIds.put(id, ids.stream().distinct().toList());
      }
    });
    return categoryIds;
  }

  @Override
  public int countWorkFlows(ProcessesFilter filter, long userIdentityId) throws IllegalAccessException {
    return processesStorage.countWorkFlows(filter);
  }

  @Override
  public WorkFlow getWorkFlow(long id, Long userIdentityId) throws IllegalAccessException {
    if (userIdentityId == null) {
      // The platform's own read, for no user: every permission flag is false
      return processesStorage.getWorkFlowById(id, null);
    }
    getAclIdentity(userIdentityId);
    WorkFlow workFlow = processesStorage.getWorkFlowById(id, userIdentityId);
    if (workFlow != null) {
      checkCanSee(workFlow, id, userIdentityId);
    }
    return workFlow;
  }

  @Override
  public boolean isProcessesAdministrator(long userIdentityId) throws IllegalAccessException {
    return isProcessesAdministrator(getAclIdentity(userIdentityId));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public WorkFlow createWorkFlow(WorkFlow workFlow, long userId) throws IllegalAccessException {
    if (workFlow == null) {
      throw new IllegalArgumentException("workFlow is mandatory");
    }
    if (workFlow.getId() != 0) {
      throw new IllegalArgumentException("workFlow id must be equal to 0");
    }
    org.exoplatform.services.security.Identity aclIdentity = getAclIdentity(userId);
    if (!isProcessesAdministrator(aclIdentity)) {
      throw new IllegalAccessException("User " + userId + " isn't allowed to create a process");
    }
    ProjectDto existingProject = null;
    if (workFlow.getProjectMode() == ProjectMode.EXISTING) {
      // The project the client names is used only once it is shown to be one
      // the editor offers for the managing space
      existingProject = getEligibleProject(workFlow.getProjectId(), workFlow.getSpaceId());
    } else {
      // A new process gets its own project, never an existing one the client
      // names
      workFlow.setProjectId(0);
    }
    // A new process always gets its own illustration file, never an existing
    // one the client names
    if (workFlow.getIllustrativeAttachment() != null) {
      workFlow.getIllustrativeAttachment().setId(null);
    }
    applyIcon(workFlow, null);
    workFlow.setAttachments(processesAttachmentService.selectProcessAttachments(userId, 0, 0, workFlow.getAttachments()));
    // Every label is checked before the first write; a new project has no
    // label to choose yet
    List<LabelChoice> labelChoices = checkLabels(workFlow.getLabels(), existingProject, existingProject == null, aclIdentity);
    // The chosen form is checked before the first write, so a refused form
    // writes nothing; its binding is written with the process's rows
    workFlow.setRequestFormId(checkProcessForm(null,
                                               StringUtils.trimToNull(workFlow.getSpaceId()),
                                               workFlow.getRequestFormId(),
                                               aclIdentity));
    // The project forms its requesters fill are checked before the first
    // write; a new project has no form to choose yet
    List<Long> requesterFormIds = existingProject == null ? null
                                                          : checkRequesterForms(null,
                                                                                existingProject.getId(),
                                                                                workFlow.getRequesterFormIds(),
                                                                                Collections.emptyList(),
                                                                                aclIdentity);
    workFlow.setRequesterFormIds(requesterFormIds == null ? Collections.emptyList() : requesterFormIds);
    // Task commits each project, status and label it creates, before the
    // process's own rows are saved
    ProjectDto project = existingProject;
    if (existingProject != null) {
      ensureProcessStatuses(existingProject);
    } else {
      workFlow = processesStorage.createProject(workFlow);
    }
    WorkFlow savedWorkFlow;
    try {
      if (existingProject == null && labelChoices != null && !labelChoices.isEmpty()) {
        project = getProject(workFlow.getProjectId());
      }
      workFlow.setLabels(createLabels(labelChoices, project, aclIdentity.getUserId()));
      savedWorkFlow = saveWorkFlowRows(workFlow, userId, labelChoices);
    } catch (RuntimeException | IllegalAccessException e) {
      if (existingProject == null) {
        // Task committed the new project: it stays, empty
        LOG.warn("The project {} was created for a process whose save failed", workFlow.getProjectId());
      }
      throw e;
    }
    ProcessesUtils.broadcast(listenerService, "exo.process.created", userId, savedWorkFlow);
    processesStorage.linkWorkFlowAttachments(savedWorkFlow, workFlow.getAttachments(), userId);
    saveTranslations(savedWorkFlow, workFlow);
    return savedWorkFlow;
  }

  /**
   * Makes a process show either an icon or an image, never both: a new image
   * sent replaces the icon, and an icon chosen replaces the stored image.
   *
   * @param workFlow the process to save, whose image field holds the icon name
   * @param storedIllustrationId the id of the process's stored image, null when
   *          it has none
   * @throws IllegalArgumentException when the icon is not an icon name
   */
  private void applyIcon(WorkFlow workFlow, Long storedIllustrationId) {
    String icon = StringUtils.trimToNull(workFlow.getImage());
    if (icon != null && !ProcessesUtils.isProcessIcon(icon)) {
      throw new IllegalArgumentException("processes.icon.invalid");
    }
    IllustrativeAttachment illustration = workFlow.getIllustrativeAttachment();
    if (illustration != null && illustration.getFileBody() != null) {
      icon = null;
    } else if (icon != null) {
      // An illustration with no file name is unlinked by the storage; with
      // none stored, nothing is sent
      workFlow.setIllustrativeAttachment(storedIllustrationId == null ? null : new IllustrativeAttachment(storedIllustrationId));
    }
    workFlow.setImage(icon);
  }

  /**
   * Saves a process's own rows in one transaction: the process, its
   * illustration and its label ids. It writes nothing through Task, whose
   * labels it only reads back, so every write that must commit with the
   * process's rows can join it; each Task write runs
   * before it, committed by Task, and the process's creation is broadcast
   * after it. A transaction already active when it starts is joined rather
   * than begun, and a Spring transaction joins it only when it begins its own
   * (ADR-0010): it is called from the REST thread, where none is active.
   *
   * @param workFlow the process, its labels resolved to labels of its project
   * @param userId the saving user's identity id
   * @return the saved process
   */
  @ContainerTransactional(atomic = true)
  public WorkFlow saveWorkFlowRows(WorkFlow workFlow, long userId) throws IllegalAccessException {
    return writeWorkFlowRows(workFlow, userId);
  }

  /**
   * Saves a process's own rows, as {@link #saveWorkFlowRows(WorkFlow, long)}
   * does, and flags in the same transaction the move of its requests to the
   * project the save set: the next pass is due at once, and a pass started
   * before this save writes none of its conditional updates. New requests go
   * to the new project from this commit on.
   *
   * @param workFlow the process, its new project set
   * @param userId the saving user's identity id
   * @param moveRequestedBy the user id of the editor, whom the move acts as
   * @return the saved process
   */
  @ContainerTransactional(atomic = true)
  public WorkFlow saveWorkFlowRowsAndRequestMove(WorkFlow workFlow, long userId, String moveRequestedBy) throws IllegalAccessException {
    return writeWorkFlowRowsAndRequestMove(workFlow, userId, moveRequestedBy);
  }

  /**
   * The un-advised body of
   * {@link #saveWorkFlowRowsAndRequestMove(WorkFlow, long, String)}.
   *
   * @param workFlow the process, its new project set
   * @param userId the saving user's identity id
   * @param moveRequestedBy the user id of the editor, whom the move acts as
   * @return the saved process
   */
  WorkFlow writeWorkFlowRowsAndRequestMove(WorkFlow workFlow, long userId, String moveRequestedBy) throws IllegalAccessException {
    WorkFlow savedWorkFlow = writeWorkFlowRows(workFlow, userId);
    processesStorage.requestProjectMove(savedWorkFlow.getId(), moveRequestedBy, System.currentTimeMillis());
    return savedWorkFlow;
  }

  /**
   * Writes a process's own rows, then the form its save chose, which the
   * request values plugin binds once the row exists, so the binding commits
   * or rolls back with the rows. The un-advised body of
   * {@link #saveWorkFlowRows(WorkFlow, long)}.
   *
   * @param workFlow the process, its labels resolved and its form choice
   *          checked: null to keep the stored form, 0 for no form
   * @param userId the saving user's identity id
   * @return the saved process
   * @throws IllegalAccessException when the plugin refuses the editor's choice
   */
  WorkFlow writeWorkFlowRows(WorkFlow workFlow, long userId) throws IllegalAccessException {
    WorkFlow savedWorkFlow = processesStorage.saveWorkFlow(workFlow, userId);
    Long requestFormId = workFlow.getRequestFormId();
    RequestValuesPlugin plugin = requestValuesPlugin;
    if (requestFormId != null && requestFormId != 0 && plugin == null) {
      // Unregistered since the save checked the choice
      throw new IllegalArgumentException(FORM_UNSUPPORTED);
    }
    if (requestFormId != null && plugin != null) {
      plugin.setProcessForm(savedWorkFlow.getId(), requestFormId == 0 ? null : requestFormId, getAclIdentity(userId));
    }
    return savedWorkFlow;
  }

  /**
   * Writes the translations a process save states for the saved process,
   * once its rows are committed: social's translation write ends the
   * container transaction it runs in, so it can't join the rows' atomic
   * save. A failure answers the save with an error, the process saved; saving
   * again writes them.
   *
   * @param savedWorkFlow the process as saved, with its id
   * @param workFlow the process as the save states it
   */
  private void saveTranslations(WorkFlow savedWorkFlow, WorkFlow workFlow) {
    Map<String, Map<String, String>> translations = new LinkedHashMap<>();
    putTranslations(translations, ProcessTranslationService.TITLE_FIELD, workFlow.getTitleTranslations());
    putTranslations(translations, ProcessTranslationService.DESCRIPTION_FIELD, workFlow.getDescriptionTranslations());
    putTranslations(translations, ProcessTranslationService.SUMMARY_FIELD, workFlow.getSummaryTranslations());
    if (!translations.isEmpty()) {
      processTranslationService.saveTranslations(savedWorkFlow, translations);
    }
  }

  /**
   * Adds a field's translations to those a save writes, when it states them.
   *
   * @param translations the translations of the save, by field name
   * @param fieldName the translated field
   * @param labels the field's labels by language tag, or null when not stated
   */
  private static void putTranslations(Map<String, Map<String, String>> translations,
                                      String fieldName,
                                      Map<String, String> labels) {
    if (labels != null) {
      translations.put(fieldName, labels);
    }
  }

  /**
   * Saves a process's own rows, logging the labels the save created in Task
   * when the rows can't be saved: Task committed them, and they stay.
   *
   * @param workFlow the process, its labels resolved
   * @param userId the saving user's identity id
   * @param labelChoices the checked labels, or null
   * @return the saved process
   */
  private WorkFlow saveWorkFlowRows(WorkFlow workFlow, long userId, List<LabelChoice> labelChoices) throws IllegalAccessException {
    return saveWorkFlowRows(workFlow, userId, labelChoices, null);
  }

  /**
   * Saves a process's own rows, and flags the move of its requests when the
   * save changed its project, logging the labels the save created in Task
   * when the rows can't be saved: Task committed them, and they stay.
   *
   * @param workFlow the process, its labels resolved
   * @param userId the saving user's identity id
   * @param labelChoices the checked labels, or null
   * @param moveRequestedBy the user id of the editor who changed the project,
   *          null when the save keeps the project
   * @return the saved process
   */
  private WorkFlow saveWorkFlowRows(WorkFlow workFlow,
                                    long userId,
                                    List<LabelChoice> labelChoices,
                                    String moveRequestedBy) throws IllegalAccessException {
    try {
      return moveRequestedBy == null ? saveWorkFlowRows(workFlow, userId)
                                     : saveWorkFlowRowsAndRequestMove(workFlow, userId, moveRequestedBy);
    } catch (RuntimeException | IllegalAccessException e) {
      if (labelChoices != null && workFlow.getLabels() != null) {
        Set<Long> keptIds = new HashSet<>();
        labelChoices.stream().filter(choice -> choice.existing() != null).forEach(choice -> keptIds.add(choice.existing().getId()));
        List<Long> createdIds = workFlow.getLabels().stream().map(WorkFlowLabel::getId).filter(id -> !keptIds.contains(id)).toList();
        if (!createdIds.isEmpty()) {
          LOG.warn("The labels {} were created in the project {} by a process save that failed", createdIds, workFlow.getProjectId());
        }
      }
      throw e;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Long getRequestWorkFlowId(long taskId) {
    return processesStorage.getRequestWorkFlowId(taskId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<Long, Long> getRequestWorkFlowIds(Collection<Long> taskIds) {
    return processesStorage.getRequestWorkFlowIds(taskIds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Long> getRequestTaskIds(long workFlowId, Long afterTaskId, int limit) {
    return processesStorage.getRequestTaskIds(workFlowId, afterTaskId, limit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Set<Long> getRequestProjectIds(long workFlowId) {
    return processesStorage.getRequestProjectIds(workFlowId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long countRequests(long workFlowId) {
    return processesStorage.countRequests(workFlowId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long countRequests(long workFlowId, Boolean completed, long userIdentityId) throws ObjectNotFoundException,
                                                                                    IllegalAccessException {
    org.exoplatform.services.security.Identity aclIdentity = getAclIdentity(userIdentityId);
    WorkFlow workFlow = processesStorage.getWorkFlowById(workFlowId, userIdentityId);
    if (workFlow == null) {
      throw new ObjectNotFoundException("Process " + workFlowId + " not found");
    }
    if (!canCountRequests(aclIdentity, workFlow)) {
      throw new IllegalAccessException("User " + userIdentityId + " can't count the requests of the process " + workFlowId);
    }
    return completed == null ? processesStorage.countRequests(workFlowId) : processesStorage.countRequests(workFlowId, completed);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<org.exoplatform.services.attachments.model.Attachment> getDocumentsToFill(long workFlowId,
                                                                                      long userIdentityId) throws ObjectNotFoundException,
                                                                                                           IllegalAccessException {
    WorkFlow workFlow = processesStorage.getWorkFlowById(workFlowId, userIdentityId);
    ProcessPermission acl = workFlow == null ? null : workFlow.getAcl();
    if (acl == null || !(acl.isCanAddRequest() || acl.isCanAccess())) {
      throw new ObjectNotFoundException("Process " + workFlowId + " not found for the user " + userIdentityId);
    }
    return processesAttachmentService.getProcessDocuments(userIdentityId, workFlowId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long countProjectRequests(long projectId, Boolean completed, long userIdentityId) throws ObjectNotFoundException,
                                                                                           IllegalAccessException {
    org.exoplatform.services.security.Identity aclIdentity = getAclIdentity(userIdentityId);
    List<Long> workFlowIds = processesStorage.getWorkFlowIdsByProjectId(projectId);
    if (workFlowIds.isEmpty()) {
      throw new ObjectNotFoundException("No process feeds the project " + projectId);
    }
    boolean allowed = false;
    for (Long workFlowId : workFlowIds) {
      WorkFlow workFlow = processesStorage.getWorkFlowById(workFlowId, userIdentityId);
      if (workFlow != null && canCountRequests(aclIdentity, workFlow)) {
        allowed = true;
        break;
      }
    }
    if (!allowed) {
      throw new IllegalAccessException("User " + userIdentityId + " can't count the requests of the project " + projectId);
    }
    return processesStorage.countRequests(workFlowIds, completed);
  }

  /**
   * Tells whether a user may read the request counts of a process: a
   * processes administrator, a user who can edit the process, or a user who
   * sees its pending requests, by the rule of the process list read on the
   * process's managing space (its stored space, else its project's): a member
   * of that space, or a super manager of spaces.
   *
   * @param aclIdentity the user's security identity
   * @param workFlow the process, read with the user's identity
   * @return true when the user may read its request counts
   */
  private boolean canCountRequests(org.exoplatform.services.security.Identity aclIdentity, WorkFlow workFlow) {
    if (isProcessesAdministrator(aclIdentity) || (workFlow.getAcl() != null && workFlow.getAcl().isCanEdit())) {
      return true;
    }
    Space space = getCurrentSpace(workFlow);
    String userName = aclIdentity.getUserId();
    return space != null && (spaceService.isSuperManager(userName) || spaceService.isMember(space, userName));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Long getManagingSpaceId(long workFlowId) {
    WorkFlow workFlow = processesStorage.getWorkFlowById(workFlowId, null);
    if (workFlow == null) {
      return null;
    }
    Space space = getCurrentSpace(workFlow);
    return space == null ? null : Long.valueOf(space.getId());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Long> getRequesterFormIds(long workFlowId) {
    return processesStorage.getRequesterFormIds(workFlowId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Predicate requestOf(CriteriaBuilder cb,
                             CommonAbstractCriteria query,
                             Expression<Long> taskId,
                             Collection<Long> workFlowIds) {
    return processesStorage.requestOf(cb, query, taskId, workFlowIds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized void addRequestValuesPlugin(RequestValuesPlugin plugin) {
    if (plugin == null) {
      throw new IllegalArgumentException("The request values plugin is mandatory");
    }
    if (requestValuesPlugin != null && requestValuesPlugin != plugin) {
      LOG.warn("The request values plugin {} replaces {}",
               plugin.getClass().getName(),
               requestValuesPlugin.getClass().getName());
    }
    requestValuesPlugin = plugin;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized void removeRequestValuesPlugin(RequestValuesPlugin plugin) {
    if (plugin != null && requestValuesPlugin == plugin) {
      requestValuesPlugin = null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work getWorkDraft(long draftId, long userIdentityId) throws ObjectNotFoundException, IllegalAccessException {
    return getOwnWorkDraft(draftId, userIdentityId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Long getWorkDraftWorkFlowId(long draftId) {
    WorkDraftRef ref = processesStorage.getWorkDraftRefs(List.of(draftId)).get(draftId);
    return ref == null ? null : ref.workFlowId();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Long getWorkDraftOwnerId(long draftId) {
    WorkDraftRef ref = processesStorage.getWorkDraftRefs(List.of(draftId)).get(draftId);
    return ref == null ? null : ref.ownerId();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<Long, WorkDraftRef> getWorkDraftRefs(Collection<Long> draftIds) {
    return processesStorage.getWorkDraftRefs(draftIds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Long> getWorkDraftIds(long workFlowId, Long afterDraftId, int limit) {
    return processesStorage.getWorkDraftIds(workFlowId, afterDraftId, limit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long countWorkDrafts(long workFlowId) {
    return processesStorage.countWorkDrafts(workFlowId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Long> getProcessFormIds(long workFlowId, long userIdentityId) throws ObjectNotFoundException,
                                                                              IllegalAccessException {
    WorkFlow workFlow = processesStorage.getWorkFlowById(workFlowId, userIdentityId);
    if (workFlow == null) {
      throw new ObjectNotFoundException("The process " + workFlowId + " does not exist");
    }
    if (workFlow.getAcl() == null || !workFlow.getAcl().isCanEdit()) {
      throw new IllegalAccessException("User " + userIdentityId + " isn't allowed to edit the process " + workFlowId);
    }
    RequestValuesPlugin plugin = requestValuesPlugin;
    return plugin == null ? Collections.emptyList() : plugin.getProcessFormIds(workFlowId);
  }

  /**
   * Checks the form a process's save chooses, before any write of the save.
   *
   * @param workFlowId the process id, or null for a new process
   * @param spaceId the space that manages the process, or null when unknown
   * @param requestFormId the form the save sends: null to keep the stored
   *          one, 0 for no form, else the form id
   * @param editor the editor's identity
   * @return the choice to write inside the save: null when there is nothing
   *         to write, 0 for no form, else the form id
   * @throws IllegalAccessException when the plugin refuses the editor's right
   */
  private Long checkProcessForm(Long workFlowId,
                                String spaceId,
                                Long requestFormId,
                                org.exoplatform.services.security.Identity editor) throws IllegalAccessException {
    if (requestFormId == null) {
      return null;
    }
    RequestValuesPlugin plugin = requestValuesPlugin;
    if (plugin == null) {
      if (requestFormId == 0) {
        // No add-on gives a process a form: it has none
        return null;
      }
      throw new IllegalArgumentException(FORM_UNSUPPORTED);
    }
    Long formId = requestFormId == 0 ? null : requestFormId;
    if (workFlowId == null) {
      if (formId == null) {
        // A new process has no form to remove
        return null;
      }
    } else {
      List<Long> ownFormIds = plugin.getProcessFormIds(workFlowId);
      if (ownFormIds.size() <= 1 && Objects.equals(formId, ownFormIds.isEmpty() ? null : ownFormIds.get(0))) {
        // The process already has exactly that form, or none: no change
        return null;
      }
    }
    plugin.checkProcessForm(workFlowId, spaceId, formId, editor);
    return requestFormId;
  }

  /**
   * Checks the forms of the process's existing task project a save chooses
   * for its requesters to fill, before any write of the save. The forms
   * add-on refuses a newly chosen form the project does not give, or a
   * project its editor may not see; a stored form sent again is kept
   * unchecked, as it was checked when chosen, even when the project no longer
   * gives it or another editor chose it, so that it never blocks a change.
   *
   * @param workFlowId the process id, or null for a new process
   * @param projectId the id of the process's task project
   * @param formIds the form ids the save sends, null when it does not state
   *          them
   * @param storedFormIds the stored choice, empty for a new process
   * @param editor the editor's identity
   * @return the choice to save, distinct ids in id order; null to keep the
   *         stored one, when none is sent or the stored one is sent again
   * @throws IllegalAccessException when the plugin refuses the editor's right
   * @throws IllegalArgumentException {@value #REQUESTER_FORM_INVALID} for an
   *           id that names no form, {@value #FORM_UNSUPPORTED} when no
   *           add-on checks the choice
   */
  private List<Long> checkRequesterForms(Long workFlowId,
                                         long projectId,
                                         List<Long> formIds,
                                         List<Long> storedFormIds,
                                         org.exoplatform.services.security.Identity editor) throws IllegalAccessException {
    if (formIds == null) {
      return null;
    }
    if (formIds.stream().anyMatch(formId -> formId == null || formId <= 0)) {
      throw new IllegalArgumentException(REQUESTER_FORM_INVALID);
    }
    List<Long> chosen = formIds.stream().distinct().sorted().toList();
    if (chosen.equals(storedFormIds)) {
      return null;
    }
    List<Long> added = chosen.stream().filter(formId -> !storedFormIds.contains(formId)).toList();
    if (added.isEmpty()) {
      return chosen;
    }
    RequestValuesPlugin plugin = requestValuesPlugin;
    if (plugin == null) {
      throw new IllegalArgumentException(FORM_UNSUPPORTED);
    }
    plugin.checkRequesterForms(workFlowId, projectId, added, editor);
    return chosen;
  }

  /**
   * @param space a space, or null
   * @return its id, or null
   */
  private static String toSpaceId(Space space) {
    return space == null ? null : space.getId();
  }

  /**
   * @return the registered request values plugin, or null when no add-on
   *         registered one
   */
  RequestValuesPlugin getRequestValuesPlugin() {
    return requestValuesPlugin;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EligibleProjects listEligibleProjects(String spaceId,
                                               int offset,
                                               int limit,
                                               long userIdentityId) throws ObjectNotFoundException, IllegalAccessException {
    Space space = checkCanListProjects(spaceId, userIdentityId);
    if (offset < 0 || limit < 0) {
      throw new IllegalArgumentException("processes.projects.invalidPage");
    }
    List<ProjectDto> eligibleProjects = findEligibleProjects(space);
    int size = eligibleProjects.size();
    int from = Math.min(offset, size);
    int to = limit == 0 ? size : (int) Math.min((long) from + limit, size);
    List<ProjectDto> page = eligibleProjects.subList(from, to);
    Map<Long, List<ProcessReference>> processes =
                                                 processesStorage.findProcessesOfProjects(page.stream()
                                                                                              .map(ProjectDto::getId)
                                                                                              .toList(),
                                                                                          Long.parseLong(space.getId()));
    List<EligibleProject> projects = new ArrayList<>();
    for (ProjectDto project : page) {
      projects.add(new EligibleProject(project.getId(),
                                       project.getName(),
                                       processes.getOrDefault(project.getId(), Collections.emptyList())));
    }
    return new EligibleProjects(projects, size);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<WorkFlowLabel> listProjectLabels(String spaceId,
                                               long projectId,
                                               long userIdentityId) throws ObjectNotFoundException, IllegalAccessException {
    checkCanListProjects(spaceId, userIdentityId);
    ProjectDto project = getEligibleProject(projectId, spaceId);
    return labelService.findLabelsByProject(project.getId(), getAclIdentity(userIdentityId), 0, 0)
                       .stream()
                       .sorted(Comparator.comparingLong(LabelDto::getId))
                       .map(label -> new WorkFlowLabel(label.getId(), label.getName()))
                       .toList();
  }

  /**
   * Checks the user may list the task projects of a space for a process: a
   * processes administrator, or a manager of the space.
   *
   * @param spaceId the space id
   * @param userIdentityId the user's identity id
   * @return the space
   * @throws ObjectNotFoundException when the space does not exist
   * @throws IllegalAccessException when the user may not
   */
  private Space checkCanListProjects(String spaceId, long userIdentityId) throws ObjectNotFoundException, IllegalAccessException {
    Space space = StringUtils.isBlank(spaceId) ? null : spaceService.getSpaceById(spaceId);
    if (space == null) {
      throw new ObjectNotFoundException("Space " + spaceId + " not found");
    }
    org.exoplatform.services.security.Identity aclIdentity = getAclIdentity(userIdentityId);
    if (!isProcessesAdministrator(aclIdentity) && !spaceService.isManager(space, aclIdentity.getUserId())) {
      throw new IllegalAccessException("User " + userIdentityId + " isn't allowed to list the projects of the space " + spaceId);
    }
    return space;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public WorkFlow updateWorkFlow(WorkFlow workFlow,
                                 long userId) throws IllegalArgumentException, ObjectNotFoundException, IllegalAccessException {
    if (workFlow == null) {
      throw new IllegalArgumentException("Workflow Type is mandatory");
    }
    if (workFlow.getId() == 0) {
      throw new IllegalArgumentException("workflow type id must not be equal to 0");
    }
    WorkFlow oldWorkFlow = processesStorage.getWorkFlowById(workFlow.getId(), userId);
    if (oldWorkFlow == null) {
      throw new ObjectNotFoundException("oldWorkFlow is not exist");
    }
    if (oldWorkFlow.getAcl() == null || !oldWorkFlow.getAcl().isCanEdit()) {
      throw new IllegalAccessException("User " + userId + " isn't allowed to update the process " + workFlow.getId());
    }
    checkSpaceMove(oldWorkFlow, workFlow.getSpaceId(), userId);
    org.exoplatform.services.security.Identity aclIdentity = getAclIdentity(userId);
    String targetSpaceId = StringUtils.isNotBlank(workFlow.getSpaceId()) ? workFlow.getSpaceId()
                                                                         : toSpaceId(getCurrentSpace(oldWorkFlow));
    // The project is changed only as the editor offers it: a new project of
    // the managing space, or an eligible existing one; any other project the
    // client names is ignored, and the process keeps its stored one
    ProjectDto targetProject = null;
    boolean newProject = workFlow.getProjectMode() == ProjectMode.NEW;
    boolean projectChanged = newProject;
    if (workFlow.getProjectMode() == ProjectMode.EXISTING && workFlow.getProjectId() != oldWorkFlow.getProjectId()) {
      targetProject = getEligibleProject(workFlow.getProjectId(), targetSpaceId);
      projectChanged = true;
    }
    if (newProject && (StringUtils.isBlank(targetSpaceId) || spaceService.getSpaceById(targetSpaceId) == null)) {
      throw new IllegalArgumentException(PROJECT_NOT_ELIGIBLE);
    }
    if (!projectChanged) {
      // A space change comes with a project of the new space
      checkSpaceKept(oldWorkFlow, workFlow.getSpaceId());
    }
    // The process keeps its creation data, whatever the client sent, and its
    // project until the project resolved above replaces it
    workFlow.setProjectId(targetProject == null ? oldWorkFlow.getProjectId() : targetProject.getId());
    workFlow.setCreatorId(oldWorkFlow.getCreatorId());
    workFlow.setCreatedDate(oldWorkFlow.getCreatedDate());
    // The illustration sent is kept, replaced or removed, but always as the
    // stored file: the client never names another one
    IllustrativeAttachment storedIllustration = oldWorkFlow.getIllustrativeAttachment();
    if (workFlow.getIllustrativeAttachment() != null) {
      workFlow.getIllustrativeAttachment().setId(storedIllustration == null ? null : storedIllustration.getId());
    }
    applyIcon(workFlow, storedIllustration == null ? null : storedIllustration.getId());
    // Labels not sent stay as stored; labels sent replace them, each checked
    // to be of the process's project before the first write. The labels belong
    // to the project: a project change resets them to the ones sent, checked
    // against the new project, which a new project has none of yet
    List<LabelChoice> labelChoices = null;
    ProjectDto project = null;
    if (projectChanged) {
      project = targetProject;
      List<WorkFlowLabel> sentLabels = workFlow.getLabels() == null ? Collections.emptyList() : workFlow.getLabels();
      labelChoices = checkLabels(sentLabels, targetProject, newProject, aclIdentity);
      workFlow.setLabels(toLabels(labelChoices));
    } else if (workFlow.getLabels() == null) {
      workFlow.setLabels(oldWorkFlow.getLabels());
    } else {
      project = findProject(oldWorkFlow.getProjectId());
      labelChoices = checkLabels(workFlow.getLabels(), project, false, aclIdentity);
      workFlow.setLabels(toLabels(labelChoices));
    }
    // The chosen form is checked before the first write; a form already the
    // process's own is no change
    workFlow.setRequestFormId(checkProcessForm(oldWorkFlow.getId(),
                                               targetSpaceId,
                                               workFlow.getRequestFormId(),
                                               aclIdentity));
    List<Long> storedRequesterFormIds = oldWorkFlow.getRequesterFormIds() == null ? Collections.emptyList()
                                                                                  : oldWorkFlow.getRequesterFormIds();
    if (projectChanged) {
      // The forms its requesters fill are forms of the project: a project
      // change keeps those of the ticked ones the new project gives, so a
      // requester never gets a form of the former project
      List<Long> tickedFormIds = workFlow.getRequesterFormIds() == null ? storedRequesterFormIds
                                                                        : workFlow.getRequesterFormIds();
      workFlow.setRequesterFormIds(newProject ? Collections.emptyList()
                                              : keepRequesterFormsOfProject(oldWorkFlow.getId(),
                                                                            targetProject.getId(),
                                                                            tickedFormIds,
                                                                            aclIdentity));
    } else {
      // The project forms its requesters fill are checked before the first
      // write; a save that does not state them, or states the stored ones,
      // keeps the stored choice
      List<Long> requesterFormIds = checkRequesterForms(oldWorkFlow.getId(),
                                                        oldWorkFlow.getProjectId(),
                                                        workFlow.getRequesterFormIds(),
                                                        storedRequesterFormIds,
                                                        aclIdentity);
      // The stored choice is kept as read, so that a save changing nothing
      // else compares equal to the stored process
      workFlow.setRequesterFormIds(requesterFormIds == null ? oldWorkFlow.getRequesterFormIds() : requesterFormIds);
    }
    if (oldWorkFlow.equals(workFlow) && workFlow.getRequestFormId() == null) {
      throw new IllegalArgumentException("there are no changes to save");
    }
    // A save that sends the request makers keeps the stored ones the editor
    // could not show; one the group store fails to read fails it before the
    // first write. The storage reads the store again: a store that fails only
    // at that second read refuses the save after the labels were created
    if (workFlow.getRequestsCreators() != null) {
      checkRequestMakersReadable(oldWorkFlow);
    }
    workFlow.setAttachments(processesAttachmentService.selectProcessAttachments(userId,
                                                                                workFlow.getId(),
                                                                                oldWorkFlow.getProjectId(),
                                                                                workFlow.getAttachments()));
    // Task commits each project, status and label it creates, before the
    // process's own rows are saved
    if (targetProject != null) {
      ensureProcessStatuses(targetProject);
    } else if (newProject) {
      workFlow.setSpaceId(targetSpaceId);
      workFlow = processesStorage.createProject(workFlow);
      if (labelChoices != null && !labelChoices.isEmpty()) {
        project = getProject(workFlow.getProjectId());
      }
    }
    if (labelChoices != null) {
      workFlow.setLabels(createLabels(labelChoices, project, aclIdentity.getUserId()));
    }
    String moveRequestedBy = projectChanged ? aclIdentity.getUserId() : null;
    WorkFlow savedWorkFlow = saveWorkFlowRows(workFlow, userId, labelChoices, moveRequestedBy);
    if (projectChanged) {
      // The move of its requests runs in the background, after this commit
      ProcessesUtils.broadcast(listenerService, ProcessesEvents.PROCESS_PROJECT_CHANGED, savedWorkFlow.getId(), moveRequestedBy);
    }
    processesStorage.linkWorkFlowAttachments(savedWorkFlow, workFlow.getAttachments(), userId);
    saveTranslations(savedWorkFlow, workFlow);
    processTranslationService.keepDefaultLanguage(savedWorkFlow);
    return savedWorkFlow;
  }

  /**
   * Keeps, of the forms ticked for a process's requesters, those a task
   * project gives, which the editor may see: each one is checked alone, so a
   * form the project does not give, or that the editor may not see, is left
   * out rather than refusing the save.
   *
   * @param workFlowId the process id
   * @param projectId the id of the process's new task project
   * @param formIds the ticked form ids
   * @param editor the editor's identity
   * @return the kept ids, distinct, in id order; empty when no add-on checks
   *         the choice
   * @throws IllegalArgumentException {@value #REQUESTER_FORM_INVALID} for an
   *           id that names no form
   */
  private List<Long> keepRequesterFormsOfProject(long workFlowId,
                                                 long projectId,
                                                 List<Long> formIds,
                                                 org.exoplatform.services.security.Identity editor) {
    if (formIds.stream().anyMatch(formId -> formId == null || formId <= 0)) {
      throw new IllegalArgumentException(REQUESTER_FORM_INVALID);
    }
    RequestValuesPlugin plugin = requestValuesPlugin;
    if (plugin == null || formIds.isEmpty()) {
      return Collections.emptyList();
    }
    List<Long> kept = new ArrayList<>();
    for (Long formId : formIds.stream().distinct().sorted().toList()) {
      try {
        plugin.checkRequesterForms(workFlowId, projectId, List.of(formId), editor);
        kept.add(formId);
      } catch (IllegalArgumentException | IllegalAccessException e) {
        LOG.debug("The form {} is not one the project {} gives the process {}", formId, projectId, workFlowId, e);
      }
    }
    return kept;
  }

  /**
   * Checks the labels a save sends, before any write: a label with an id must
   * be a label of the process's project; a label without one gives a name,
   * trimmed, of 1 to {@value #LABEL_NAME_MAX_LENGTH} characters, which reuses
   * the project's label of the same name, case aside, or is created there at
   * save. A label sent twice counts once.
   *
   * @param labels the labels sent, or null
   * @param project the process's project, null for a new project or one
   *          deleted in Tasks
   * @param newProject whether the save creates the project, where the new
   *          labels go; otherwise a process with no project takes no label
   * @param aclIdentity the saving user
   * @return the labels to save, or null when none is sent
   * @throws IllegalArgumentException processes.label.notInProject,
   *           processes.label.invalidName
   */
  private List<LabelChoice> checkLabels(List<WorkFlowLabel> labels,
                                        ProjectDto project,
                                        boolean newProject,
                                        org.exoplatform.services.security.Identity aclIdentity) {
    if (labels == null) {
      return null;
    }
    List<LabelDto> projectLabels = project == null || labels.isEmpty() ? Collections.emptyList()
                                                                       : labelService.findLabelsByProject(project.getId(),
                                                                                                          aclIdentity,
                                                                                                          0,
                                                                                                          0);
    Map<String, LabelChoice> choices = new LinkedHashMap<>();
    for (WorkFlowLabel label : labels) {
      if (label == null) {
        continue;
      }
      if (label.getId() > 0) {
        LabelDto stored = labelService.getLabel(label.getId());
        if (stored == null || project == null || stored.getProject() == null || stored.getProject().getId() != project.getId()) {
          throw new IllegalArgumentException(LABEL_NOT_IN_PROJECT);
        }
        choices.putIfAbsent("id:" + stored.getId(), new LabelChoice(stored, stored.getName()));
      } else {
        String name = StringUtils.trimToEmpty(label.getName());
        if (name.isEmpty() || name.length() > LABEL_NAME_MAX_LENGTH) {
          throw new IllegalArgumentException(LABEL_INVALID_NAME);
        }
        if (project == null && !newProject) {
          throw new IllegalArgumentException(LABEL_NOT_IN_PROJECT);
        }
        LabelDto sameName = projectLabels.stream()
                                         .filter(projectLabel -> name.equalsIgnoreCase(StringUtils.trim(projectLabel.getName())))
                                         .min(Comparator.comparingLong(LabelDto::getId))
                                         .orElse(null);
        if (sameName != null) {
          choices.putIfAbsent("id:" + sameName.getId(), new LabelChoice(sameName, sameName.getName()));
        } else {
          choices.putIfAbsent("name:" + name.toLowerCase(Locale.ROOT), new LabelChoice(null, name));
        }
      }
    }
    return new ArrayList<>(choices.values());
  }

  /**
   * Gives the labels a save compares with the stored ones: the chosen labels
   * of the project by id order, then the names to create, with no id.
   *
   * @param choices the checked labels
   * @return the labels
   */
  private List<WorkFlowLabel> toLabels(List<LabelChoice> choices) {
    List<WorkFlowLabel> labels = new ArrayList<>();
    choices.stream()
           .filter(choice -> choice.existing() != null)
           .sorted(Comparator.comparingLong(choice -> choice.existing().getId()))
           .forEach(choice -> labels.add(new WorkFlowLabel(choice.existing().getId(), choice.existing().getName())));
    choices.stream()
           .filter(choice -> choice.existing() == null)
           .forEach(choice -> labels.add(new WorkFlowLabel(0, choice.name())));
    return labels;
  }

  /**
   * Creates in the process's project the labels a save names but the project
   * lacks, with no color, each committed by Task, and gives the labels the
   * process saves.
   *
   * @param choices the checked labels, or null to keep the process's labels
   * @param project the process's project
   * @param userName the saving user's name, the creator of the new labels
   * @return the labels with their ids, or null when choices is null
   * @throws IllegalArgumentException processes.label.notInProject when a label
   *           must be created and the process has no project
   */
  private List<WorkFlowLabel> createLabels(List<LabelChoice> choices, ProjectDto project, String userName) {
    if (choices == null) {
      return null;
    }
    List<WorkFlowLabel> labels = new ArrayList<>();
    List<Long> createdIds = new ArrayList<>();
    try {
      for (LabelChoice choice : choices) {
        LabelDto label = choice.existing();
        if (label == null) {
          if (project == null) {
            throw new IllegalArgumentException(LABEL_NOT_IN_PROJECT);
          }
          LabelDto newLabel = new LabelDto();
          newLabel.setName(choice.name());
          newLabel.setUsername(userName);
          newLabel.setProject(project);
          label = labelService.createLabel(newLabel);
          createdIds.add(label.getId());
        }
        labels.add(new WorkFlowLabel(label.getId(), label.getName()));
      }
    } catch (RuntimeException e) {
      if (!createdIds.isEmpty()) {
        LOG.warn("The labels {} were created in the project {} by a process save that failed", createdIds, project.getId());
      }
      throw e;
    }
    labels.sort(Comparator.comparingLong(WorkFlowLabel::getId));
    return labels;
  }

  /**
   * Reads a task project that must exist.
   *
   * @param projectId the project id
   * @return the project
   * @throws IllegalStateException when Task does not find it
   */
  private ProjectDto getProject(long projectId) {
    ProjectDto project = findProject(projectId);
    if (project == null) {
      throw new IllegalStateException("The project " + projectId + " was not found");
    }
    return project;
  }

  /**
   * Reads a task project, which a manager may have deleted in Tasks.
   *
   * @param projectId the project id
   * @return the project, or null
   */
  private ProjectDto findProject(long projectId) {
    if (projectId <= 0) {
      return null;
    }
    try {
      return projectService.getProject(projectId);
    } catch (EntityNotFoundException e) {
      return null;
    }
  }

  @Override
  public List<Work> getWorks(long userIdentityId, WorkFilter workFilter, int offset, int limit) throws Exception {

    return processesStorage.getWorks(userIdentityId, workFilter, offset, limit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int countWorks(long userIdentityId, WorkFilter workFilter) throws Exception {
    return processesStorage.countWorks(userIdentityId, workFilter);
  }

  /**
   * {@inheritDoc} The user's open requests are read once to select the
   * candidates; at each one's turn the request is read again and archived only
   * if it is still not archived and still finished, so a request an earlier
   * turn of the run changed is left. That read goes through the Task add-on
   * within the same request as the selection, which may serve its tasks from
   * the request's persistence context: a change another user commits while
   * the run lasts is not guaranteed to be seen, the window being the run's
   * duration. The archive then goes through
   * {@link #updateWorkCompleted(Long, boolean, long)}, which checks the user's
   * right on the request. Each archive stands alone, as a single archive does:
   * a failure other than a refusal, the re-read's included, stops the run and
   * leaves the requests already archived archived.
   */
  @Override
  public int archiveFinishedWorks(long userIdentityId) throws Exception {
    WorkFilter filter = new WorkFilter();
    filter.setCompleted(false);
    List<Work> candidates = processesStorage.getWorks(userIdentityId, filter, 0, 0);
    int archived = 0;
    for (Work candidate : candidates) {
      if (isUnarchivedAndFinished(candidate) && archiveIfStillFinished(candidate.getId(), userIdentityId)) {
        archived++;
      }
    }
    return archived;
  }

  /**
   * Archives a request as a user when, read now, it is still not archived and
   * still finished.
   *
   * @param workId the request id
   * @param userIdentityId the user's identity id
   * @return true when the request was archived, false when it changed, is gone,
   *         or the user may not archive it
   */
  private boolean archiveIfStillFinished(long workId, long userIdentityId) {
    if (!isUnarchivedAndFinished(processesStorage.getWorkById(workId))) {
      return false;
    }
    try {
      updateWorkCompleted(workId, true, userIdentityId);
      return true;
    } catch (ObjectNotFoundException | IllegalAccessException e) {
      LOG.debug("The request {} isn't archived for the user {}", workId, userIdentityId, e);
      return false;
    }
  }

  /**
   * Tells whether a request is one « Archive all » archives.
   *
   * @param work the request, or null
   * @return true when it exists, is not archived and its status is final
   */
  private static boolean isUnarchivedAndFinished(Work work) {
    // Set.of refuses a null lookup: a task that left its project has no status
    return work != null && !work.isCompleted() && work.getStatus() != null && FINISHED_STATUSES.contains(work.getStatus());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Deprecated(since = "7.3.0.50", forRemoval = true)
  public WorkFlow getWorkFlowByProjectId(long projectId) {
    return processesStorage.getWorkFlowByProjectId(projectId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work createWork(Work work, long userId, String agentTitle) throws IllegalAccessException, ObjectNotFoundException {
    if (work == null) {
      throw new IllegalArgumentException("work is mandatory");
    }
    if (work.getId() != 0) {
      throw new IllegalArgumentException("work id must be equal to 0");
    }
    if (work.getWorkFlowId() <= 0) {
      throw new IllegalArgumentException("processes.request.workflowRequired");
    }
    WorkFlow workFlow = checkCanAddRequest(work.getWorkFlowId(), userId);
    if (!workFlow.isEnabled()) {
      throw new IllegalArgumentException("Workflow is disabled");
    }
    // The request goes to its process's stored project, whatever project the
    // client named
    work.setProjectId(workFlow.getProjectId());
    if (work.getDraftId() != null) {
      Work draft = getOwnWorkDraft(work.getDraftId(), userId);
      if (draft.getWorkFlow() == null || draft.getWorkFlow().getId() != workFlow.getId()) {
        throw new IllegalArgumentException("processes.request.draftMismatch");
      }
      // The request takes its draft's stored attachments, never the ones the client names
      work.setAttachments(processesAttachmentService.selectRequestAttachments(userId,
                                                                              work.getDraftId(),
                                                                              workFlow.getId(),
                                                                              workFlow.getProjectId(),
                                                                              work.getAttachments()));
    }
    // The fixed rule names the request from its stored process, whatever title
    // the client sent; set once, never re-computed
    work.setTitle(RequestTitle.of(workFlow.getTitle(),
                                  LocalDate.now(ZoneId.systemDefault()),
                                  getRequesterName(userId),
                                  agentTitle));
    Map<Long, Map<String, List<Object>>> formValues = work.getFormValues() == null ? new LinkedHashMap<>()
                                                                                  : work.getFormValues();
    RequestValuesPlugin plugin = requestValuesPlugin;
    if (plugin == null) {
      if (!formValues.isEmpty()) {
        throw new IllegalArgumentException(FORM_VALUES_UNSUPPORTED);
      }
      return processesStorage.saveWork(work, userId);
    }
    // The values are checked before the task exists, so a refused value
    // creates nothing; the draft's own values are checked with them
    org.exoplatform.services.security.Identity requester = getAclIdentity(userId);
    plugin.checkRequestValues(workFlow.getId(), work.getDraftId(), formValues, requester);
    Work request = processesStorage.createRequest(work, userId);
    try {
      plugin.writeRequestValues(workFlow.getId(), work.getDraftId(), request.getId(), formValues, requester);
    } catch (IllegalAccessException | RuntimeException e) {
      // The values were refused once the task existed, after a change of the
      // process's form: the request is removed, its draft and the draft's
      // values are kept, and the refusal is answered
      processesStorage.removeRequest(request.getId());
      throw e;
    }
    return processesStorage.completeRequest(request, work, userId);
  }

  /**
   * Reads the requester's full name, the last part of a request's title.
   *
   * @param userId the requester's identity id
   * @return the full name, empty when the identity or its profile is unknown
   */
  private String getRequesterName(long userId) {
    Identity identity = identityManager.getIdentity(userId);
    return identity == null || identity.getProfile() == null ? "" : StringUtils.trimToEmpty(identity.getProfile().getFullName());
  }

  @Override
  public Work updateWork(Work work,
                         long userId) throws IllegalArgumentException, ObjectNotFoundException, IllegalAccessException {
    if (work == null) {
      throw new IllegalArgumentException("Work is mandatory");
    }
    if (work.getId() == 0) {
      throw new IllegalArgumentException("work id must not be equal to 0");
    }
    Work oldWork = getManageableWork(work.getId(), userId);
    // The request stays in its process project, whatever the client sent
    work.setProjectId(oldWork.getProjectId());
    if (!checkRequesterChange(oldWork, work.getStatus(), work.isCompleted(), userId)) {
      // A requester who doesn't manage the process only cancels: the content stays
      work.setTitle(oldWork.getTitle());
      work.setDescription(oldWork.getDescription());
    }
    if (oldWork.equals(work)) {
      throw new IllegalArgumentException("there are no changes to save");
    }
    return processesStorage.saveWork(work, userId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Deprecated(forRemoval = true)
  public void deleteWorkflowById(Long workflowId) {
    broadcastProcessDeleted(workflowId, this.processesStorage.deleteWorkflowById(workflowId));
    unlinkCategories(workflowId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteWorkflowById(Long workflowId, long userIdentityId) throws ObjectNotFoundException, IllegalAccessException {
    if (workflowId == null) {
      throw new IllegalArgumentException("Workflow id is mandatory");
    }
    WorkFlow workFlow = processesStorage.getWorkFlowById(workflowId, userIdentityId);
    if (workFlow == null) {
      throw new ObjectNotFoundException("Workflow " + workflowId + " not found");
    }
    if (workFlow.getAcl() == null || !workFlow.getAcl().isCanDelete()) {
      throw new IllegalAccessException("User " + userIdentityId + " isn't allowed to delete the process " + workflowId);
    }
    broadcastProcessDeleted(workflowId, this.processesStorage.deleteWorkflowById(workflowId));
    processTranslationService.deleteTranslations(workflowId);
    unlinkCategories(workflowId);
  }

  /**
   * Sets the links of processes to categories, in place of social's.
   *
   * @param categoryLinkService the category links to use
   */
  void setCategoryLinkService(CategoryLinkService categoryLinkService) {
    this.categoryLinkService = categoryLinkService;
  }

  /**
   * Sets the category tree, in place of social's.
   *
   * @param categoryService the category tree to use
   */
  void setCategoryService(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  /**
   * Builds the filter the storage queries: the client's criteria, and the ids
   * of the processes filed under its category or one of the category's
   * subcategories when it names one, never ids the client sent.
   *
   * @param filter the client's filter, or null
   * @return the queried filter, a copy
   */
  private ProcessesFilter toQueriedFilter(ProcessesFilter filter) {
    ProcessesFilter queried = new ProcessesFilter();
    if (filter == null) {
      return queried;
    }
    queried.setQuery(filter.getQuery());
    queried.setEnabled(filter.getEnabled());
    queried.setManager(filter.getManager());
    queried.setCategoryId(filter.getCategoryId());
    if (filter.getCategoryId() != null) {
      queried.setIds(getCategorizedWorkflowIds(filter.getCategoryId()));
    }
    return queried;
  }

  /**
   * Reads the processes filed under a category or one of its subcategories,
   * down to the depth the Processes page shows. It reads the subtree, then the
   * links of each of its categories: 1 + K reads, K being the subtree's size
   * down to {@link ProcessCategories#FILTER_DEPTH}, as social offers no read of
   * several categories' links at once. The ids then narrow the listing query
   * as one IN list, unchunked: a category subtree filing more processes than a
   * database's IN-list cap (1,000 on some) is beyond the expected use.
   *
   * @param categoryId the category id
   * @return the process ids, without duplicates
   */
  private List<Long> getCategorizedWorkflowIds(long categoryId) {
    List<Long> categoryIds = new ArrayList<>();
    categoryIds.add(categoryId);
    List<Long> subcategoryIds = getCategoryService().getSubcategoryIds(categoryId, 0, 0, ProcessCategories.FILTER_DEPTH);
    if (subcategoryIds != null) {
      categoryIds.addAll(subcategoryIds);
    }
    Set<Long> workflowIds = new LinkedHashSet<>();
    for (Long id : categoryIds) {
      getCategoryLinkService().getLinkedObjects(id, List.of(ProcessCategories.OBJECT_TYPE), 0, 0)
                              .stream()
                              .filter(object -> ProcessCategories.OBJECT_TYPE.equals(object.getType()))
                              .map(object -> toWorkflowId(object.getId()))
                              .filter(Objects::nonNull)
                              .forEach(workflowIds::add);
    }
    return new ArrayList<>(workflowIds);
  }

  /**
   * Unlinks a deleted process from every category it was filed under. A
   * failure is logged and leaves the deletion done.
   *
   * @param workflowId the deleted process id
   */
  private void unlinkCategories(Long workflowId) {
    CategoryObject object = new CategoryObject(ProcessCategories.OBJECT_TYPE, String.valueOf(workflowId), 0);
    List<Long> categoryIds;
    try {
      categoryIds = getCategoryLinkService().getLinkedIds(object);
    } catch (RuntimeException e) {
      LOG.warn("Deleted process {} stays filed under its categories", workflowId, e);
      return;
    }
    for (Long categoryId : categoryIds) {
      try {
        getCategoryLinkService().unlink(categoryId, object);
      } catch (RuntimeException e) {
        LOG.warn("Deleted process {} stays filed under category {}", workflowId, categoryId, e);
      }
    }
  }

  /**
   * Tells whether a user sees a listed process on the Processes page: they
   * participate in it or may add a request to it, and it is enabled unless
   * they manage it.
   *
   * @param workFlow a process listed for the user, with the user's permission
   * @return true when the user sees it
   */
  private static boolean isShownTo(WorkFlow workFlow) {
    ProcessPermission acl = workFlow.getAcl();
    return acl != null && (acl.isCanAccess() || acl.isCanAddRequest()) && (workFlow.isEnabled() || acl.isCanEdit());
  }

  /**
   * Reads a process id stored as a category link's object id.
   *
   * @param objectId the stored object id
   * @return the process id, or null when it is no number
   */
  private static Long toWorkflowId(String objectId) {
    try {
      return objectId == null ? null : Long.valueOf(objectId.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Resolves social's category links at first use: a social Spring service,
   * which may not be built yet when the Kernel builds this one.
   *
   * @return the category links
   */
  private CategoryLinkService getCategoryLinkService() {
    if (categoryLinkService == null) {
      categoryLinkService = getPlatformService(CategoryLinkService.class);
    }
    return categoryLinkService;
  }

  /**
   * Resolves social's category tree at first use.
   *
   * @return the category tree
   */
  private CategoryService getCategoryService() {
    if (categoryService == null) {
      categoryService = getPlatformService(CategoryService.class);
    }
    return categoryService;
  }

  /**
   * Reads a service from the running container, never starting one.
   *
   * @param <T> the service type
   * @param type the service class
   * @return the service
   * @throws IllegalStateException when no container runs or it holds no such
   *           service
   */
  private static <T> T getPlatformService(Class<T> type) {
    ExoContainer container = ExoContainerContext.getCurrentContainerIfPresent();
    T service = container == null ? null : container.getComponentInstanceOfType(type);
    if (service == null) {
      throw new IllegalStateException("The platform's " + type.getSimpleName() + " isn't available");
    }
    return service;
  }

  /**
   * Announces a committed process deletion, with the tasks its requests had,
   * which stay as plain tasks, then the deletion of each of its drafts.
   *
   * @param workflowId the deleted process id
   * @param deletion what the deletion removed, or null
   */
  private void broadcastProcessDeleted(Long workflowId, ProcessDeletion deletion) {
    ProcessDeletion deleted = deletion == null ? new ProcessDeletion(null, null) : deletion;
    ProcessesUtils.broadcast(listenerService, ProcessesEvents.PROCESS_DELETED, workflowId, deleted.requestTaskIds());
    // Each of its drafts was deleted with it
    for (Long draftId : deleted.draftIds()) {
      ProcessesUtils.broadcast(listenerService, ProcessesEvents.REQUEST_DRAFT_DELETED, draftId, workflowId);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Deprecated(since = "7.3.0.50", forRemoval = true)
  public int countWorksByWorkflow(Long projectId, Boolean isCompleted) throws Exception {
    if (projectId == null) {
      throw new IllegalArgumentException("Project Id is mandatory");
    }
    if (isCompleted == null) {
      throw new IllegalArgumentException("isCompleted should not be null");
    }
    return processesStorage.countWorksByWorkflow(projectId, isCompleted);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Deprecated(forRemoval = true)
  public void deleteWorkById(Long workId) {
    if (workId == null) {
      throw new IllegalArgumentException("Work Id is mandatory");
    }
    processesStorage.deleteWorkById(workId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteWorkById(Long workId, long userIdentityId) throws ObjectNotFoundException, IllegalAccessException {
    if (workId == null) {
      throw new IllegalArgumentException("Work Id is mandatory");
    }
    getManageableWork(workId, userIdentityId);
    processesStorage.deleteWorkById(workId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Deprecated(forRemoval = true)
  public Work updateWorkCompleted(Long workId, boolean completed) {
    if (workId == null) {
      throw new IllegalArgumentException("Work id is mandatory");
    }
    return processesStorage.updateWorkCompleted(workId, completed);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work updateWorkCompleted(Long workId, boolean completed, long userIdentityId) throws ObjectNotFoundException,
                                                                                       IllegalAccessException {
    if (workId == null) {
      throw new IllegalArgumentException("Work id is mandatory");
    }
    Work work = getManageableWork(workId, userIdentityId);
    checkRequesterChange(work, work.getStatus(), completed, userIdentityId);
    return processesStorage.updateWorkCompleted(workId, completed);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work createWorkDraft(Work work, long userId) throws IllegalArgumentException,
                                                      ObjectNotFoundException,
                                                      IllegalAccessException {
    if (work == null) {
      throw new IllegalArgumentException("WorkDraft is mandatory");
    }
    if (work.getId() != 0) {
      throw new IllegalArgumentException("WorkDraft id must be equal to 0");
    }
    if (work.getWorkFlow() == null) {
      throw new IllegalArgumentException("WorkDraft workflow is mandatory");
    }
    // The draft is attached to the stored process, never to the one the client sent
    work.setWorkFlow(checkCanAddRequest(work.getWorkFlow().getId(), userId));
    // The server sets a request draft's state
    work.setIsDraft(true);
    Map<Long, Map<String, List<Object>>> formValues = draftFormValues(work);
    if (formValues.isEmpty()) {
      return processesStorage.saveWorkDraft(work, userId);
    }
    Work saved = saveWorkDraftRows(work, userId, formValues, true);
    // The process's attachments are copied once the draft and its values are
    // committed, so a draft refused for its values leaves no copied file
    processesStorage.copyProcessAttachmentsToDraft(saved.getId(), work.getWorkFlow(), userId);
    return saved;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work updateWorkDraft(Work work, long userId) throws IllegalArgumentException,
                                                      ObjectNotFoundException,
                                                      IllegalAccessException {
    if (work == null) {
      throw new IllegalArgumentException("WorkDraft Type is mandatory");
    }
    if (work.getId() == 0) {
      throw new IllegalArgumentException("WorkDraft type id must not be equal to 0");
    }

    Work oldWork = getOwnWorkDraft(work.getId(), userId);
    // The draft keeps its stored creator, process, dates and task, whatever the
    // client sent: only its title and description are the client's
    work.setCreatorId(oldWork.getCreatorId());
    work.setWorkFlow(oldWork.getWorkFlow());
    work.setCreatedDate(oldWork.getCreatedDate());
    work.setModifiedDate(oldWork.getModifiedDate());
    work.setTaskId(oldWork.getTaskId());
    // The server sets a request draft's state
    work.setIsDraft(true);
    Map<Long, Map<String, List<Object>>> formValues = draftFormValues(work);
    boolean rowChanged = !Objects.equals(oldWork.getTitle(), work.getTitle())
        || !Objects.equals(oldWork.getDescription(), work.getDescription());
    if (!rowChanged && formValues.isEmpty()) {
      throw new IllegalArgumentException("there are no changes to save");
    }
    if (formValues.isEmpty()) {
      return processesStorage.saveWorkDraft(work, userId);
    }
    return saveWorkDraftRows(work, userId, formValues, rowChanged);
  }

  /**
   * Saves a request draft's row and its form values in one transaction:
   * a refused value refuses the whole save. Forms' value write joins the
   * transaction when this method begins it (ADR-0010), as the process save's
   * binding does. A creation writes its row first, since its values need its
   * id; an update writes its values first, then its row.
   *
   * @param draft the draft, its stored process set
   * @param userId the owner's identity id
   * @param formValues the sent values, not empty
   * @param writeRow whether the draft's row changed
   * @return the saved draft
   * @throws IllegalAccessException when the plugin refuses the owner's right
   */
  @ContainerTransactional(atomic = true)
  public Work saveWorkDraftRows(Work draft,
                                long userId,
                                Map<Long, Map<String, List<Object>>> formValues,
                                boolean writeRow) throws IllegalAccessException {
    return writeWorkDraftRows(draft, userId, formValues, writeRow);
  }

  /**
   * The un-advised body of {@link #saveWorkDraftRows}.
   *
   * @param draft the draft, its stored process set
   * @param userId the owner's identity id
   * @param formValues the sent values, not empty
   * @param writeRow whether the draft's row changed
   * @return the saved draft
   * @throws IllegalAccessException when the plugin refuses the owner's right
   */
  Work writeWorkDraftRows(Work draft,
                          long userId,
                          Map<Long, Map<String, List<Object>>> formValues,
                          boolean writeRow) throws IllegalAccessException {
    RequestValuesPlugin plugin = requestValuesPlugin;
    if (plugin == null) {
      // Unregistered since the save checked it
      throw new IllegalArgumentException(FORM_VALUES_UNSUPPORTED);
    }
    org.exoplatform.services.security.Identity owner = getAclIdentity(userId);
    long workFlowId = draft.getWorkFlow().getId();
    if (draft.getId() == 0) {
      Work saved = processesStorage.saveWorkDraftRow(draft, userId);
      plugin.writeDraftValues(workFlowId, saved.getId(), formValues, owner);
      return saved;
    }
    plugin.writeDraftValues(workFlowId, draft.getId(), formValues, owner);
    return writeRow ? processesStorage.saveWorkDraftRow(draft, userId) : draft;
  }

  /**
   * Reads the form values a draft's save sends, refused when no add-on takes
   * them.
   *
   * @param draft the draft as sent
   * @return the sent values, empty when none
   */
  private Map<Long, Map<String, List<Object>>> draftFormValues(Work draft) {
    Map<Long, Map<String, List<Object>>> formValues = draft.getFormValues() == null ? new LinkedHashMap<>()
                                                                                    : draft.getFormValues();
    if (!formValues.isEmpty() && requestValuesPlugin == null) {
      throw new IllegalArgumentException(FORM_VALUES_UNSUPPORTED);
    }
    return formValues;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Work> getWorkDrafts(long userIdentityId, WorkFilter workFilter, int offset, int limit) {
    return processesStorage.findAllWorkDraftsByUser(workFilter, offset, limit, userIdentityId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Deprecated(forRemoval = true)
  public void deleteWorkDraftById(Long draftId) {
    if (draftId == null) {
      throw new IllegalArgumentException("WorkDraft id is mandatory");
    }
    Work draft = processesStorage.getWorkDraftyId(draftId);
    processesStorage.deleteWorkDraftById(draftId);
    broadcastDraftDeleted(draftId, draft);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteWorkDraftById(Long draftId, long userIdentityId) throws ObjectNotFoundException, IllegalAccessException {
    if (draftId == null) {
      throw new IllegalArgumentException("WorkDraft id is mandatory");
    }
    Work draft = getOwnWorkDraft(draftId, userIdentityId);
    processesStorage.deleteWorkDraftById(draftId);
    broadcastDraftDeleted(draftId, draft);
  }

  /**
   * Announces a committed draft deletion, with the draft's process.
   *
   * @param draftId the deleted draft id
   * @param draft the draft as read before its deletion, or null
   */
  private void broadcastDraftDeleted(Long draftId, Work draft) {
    Long workFlowId = draft == null || draft.getWorkFlow() == null ? null : draft.getWorkFlow().getId();
    ProcessesUtils.broadcast(listenerService, ProcessesEvents.REQUEST_DRAFT_DELETED, draftId, workFlowId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<WorkStatus> getAvailableWorkStatuses() {
    return processesStorage.getAvailableWorkStatuses();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work getWorkById(long userIdentityId, Long workId) {
    if (workId == null) {
      throw new IllegalArgumentException("Work id is mandatory");
    }
    return processesStorage.getWorkById(userIdentityId, workId);
  }

  @Override
  public Work getWorkById(long workId) {
    return processesStorage.getWorkById(workId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public IllustrativeAttachment getIllustrationImageById(Long illustrationId) throws FileStorageException,
                                                                              ObjectNotFoundException,
                                                                              IOException {
    if (illustrationId == null) {
      throw new IllegalArgumentException("IllustrationId id is mandatory");
    }
    return processesStorage.getIllustrationImageById(illustrationId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public IllustrativeAttachment getWorkFlowIllustration(long workflowId, long userIdentityId) throws ObjectNotFoundException,
                                                                                              IllegalAccessException,
                                                                                              FileStorageException,
                                                                                              IOException {
    getAclIdentity(userIdentityId);
    WorkFlow workFlow = processesStorage.getWorkFlowById(workflowId, userIdentityId);
    if (workFlow == null) {
      throw new ObjectNotFoundException("Workflow " + workflowId + " not found");
    }
    checkCanSee(workFlow, workflowId, userIdentityId);
    Long illustrationId = workFlow.getIllustrativeAttachment() == null ? null : workFlow.getIllustrativeAttachment().getId();
    if (illustrationId == null) {
      throw new ObjectNotFoundException("Workflow " + workflowId + " has no illustration");
    }
    return processesStorage.getIllustrationImageById(illustrationId);
  }

  /**
   * Reads the projects a process of a space may feed, by name: the projects
   * attached to the space ({@link #isAttachedToSpace}) whose columns hold no
   * process status name in another case ({@link #hasStatusNameConflict}).
   *
   * @param space the managing space
   * @return the eligible projects
   */
  private List<ProjectDto> findEligibleProjects(Space space) {
    List<String> spaceMemberships = UserUtil.getSpaceMemberships(space.getGroupId());
    // Every project whose managers or participants hold one of the space's
    // memberships; the attachment rule then keeps the space's own projects
    List<ProjectDto> projects = projectService.findProjects(spaceMemberships, null, new OrderBy("name", true), 0, 0);
    if (projects == null) {
      return Collections.emptyList();
    }
    return projects.stream()
                   .filter(project -> isAttachedToSpace(project, space.getGroupId()))
                   .filter(project -> !hasStatusNameConflict(project))
                   .toList();
  }

  /**
   * Resolves the existing project a new process asks for, refusing it unless
   * {@link #findEligibleProjects} would list it for the process's space: the
   * same attachment rule and the same column check, applied to that project.
   *
   * @param projectId the project the client names
   * @param spaceId the managing space the client names
   * @return the project
   * @throws IllegalArgumentException processes.project.notEligible when the
   *           space or the project does not exist, or the project is not
   *           attached to the space; processes.project.statusNameConflict when
   *           a column of the project is named like a process status in another
   *           case
   */
  private ProjectDto getEligibleProject(long projectId, String spaceId) {
    Space space = StringUtils.isBlank(spaceId) ? null : spaceService.getSpaceById(spaceId);
    if (space == null || projectId <= 0) {
      throw new IllegalArgumentException(PROJECT_NOT_ELIGIBLE);
    }
    ProjectDto project;
    try {
      project = projectService.getProject(projectId);
    } catch (EntityNotFoundException e) {
      throw new IllegalArgumentException(PROJECT_NOT_ELIGIBLE);
    }
    if (project == null || !isAttachedToSpace(project, space.getGroupId())) {
      throw new IllegalArgumentException(PROJECT_NOT_ELIGIBLE);
    }
    if (hasStatusNameConflict(project)) {
      throw new IllegalArgumentException(STATUS_NAME_CONFLICT);
    }
    return project;
  }

  /**
   * Tells whether a task project is attached to a space: its managers hold the
   * space's manager membership, which Tasks writes for a project created in a
   * space, and neither its managers nor its participants hold a group
   * membership other than the space's manager and member ones. Users added to
   * the project one by one are allowed.
   *
   * @param project the task project
   * @param spaceGroupId the space's group id
   * @return true when the project is attached to the space
   */
  public static boolean isAttachedToSpace(ProjectDto project, String spaceGroupId) {
    String managerMembership = ProcessesUtils.MANAGER_MEMBERSHIP_TYPE + ":" + spaceGroupId;
    String memberMembership = ProcessesUtils.MEMBER_MEMBERSHIP_TYPE + ":" + spaceGroupId;
    Set<String> managers = project.getManager() == null ? Collections.emptySet() : project.getManager();
    Set<String> participators = project.getParticipator() == null ? Collections.emptySet() : project.getParticipator();
    if (!managers.contains(managerMembership)) {
      return false;
    }
    return java.util.stream.Stream.concat(managers.stream(), participators.stream())
                                  .filter(entry -> entry != null && entry.contains("/"))
                                  .allMatch(entry -> entry.equals(managerMembership) || entry.equals(memberMembership));
  }

  /**
   * Tells whether a column of a task project is named like a process status in
   * another case (request, CANCELED): Task would take it for the status and
   * create none, while processes reads the status names exactly.
   *
   * @param project the task project
   * @return true when such a column exists
   */
  private boolean hasStatusNameConflict(ProjectDto project) {
    List<StatusDto> statuses = statusService.getStatuses(project.getId());
    if (statuses == null) {
      return false;
    }
    return statuses.stream()
                   .map(StatusDto::getName)
                   .filter(name -> name != null)
                   .anyMatch(name -> PROCESS_STATUSES.stream()
                                                     .anyMatch(statusName -> statusName.equalsIgnoreCase(name)
                                                         && !statusName.equals(name)));
  }

  /**
   * Adds to an existing task project the process statuses it lacks, compared
   * exactly, after its columns. Only the missing ones: Task rewrites the whole
   * project from its copy each time a status is created, whatever its name.
   *
   * @param project the task project
   */
  private void ensureProcessStatuses(ProjectDto project) {
    List<StatusDto> statuses = statusService.getStatuses(project.getId());
    Set<String> names = statuses == null ? Collections.emptySet()
                                         : statuses.stream()
                                                   .map(StatusDto::getName)
                                                   .collect(java.util.stream.Collectors.toSet());
    for (String statusName : PROCESS_STATUSES) {
      if (!names.contains(statusName)) {
        statusService.createStatus(project, statusName);
      }
    }
  }

  private WorkFlow checkCanAddRequest(long workFlowId, long userIdentityId) throws ObjectNotFoundException,
                                                                            IllegalAccessException {
    WorkFlow workFlow = processesStorage.getWorkFlowById(workFlowId, userIdentityId);
    if (workFlow == null) {
      throw new ObjectNotFoundException("Workflow " + workFlowId + " not found");
    }
    if (workFlow.getAcl() == null || !workFlow.getAcl().isCanAddRequest()) {
      throw new IllegalAccessException("User " + userIdentityId + " isn't allowed to add a request to the process " + workFlowId);
    }
    return workFlow;
  }

  /**
   * Checks that every stored request maker of a process can be read, so that
   * the save can tell the ones its editor showed from the ones it could not.
   *
   * @param storedWorkFlow the stored process
   * @throws IllegalStateException when the group store fails to read one
   */
  private void checkRequestMakersReadable(WorkFlow storedWorkFlow) {
    if (storedWorkFlow.getManager() != null) {
      storedWorkFlow.getManager().forEach(EntityMapper::isDisplayableCreator);
    }
  }

  private Work getManageableWork(long workId, long userIdentityId) throws ObjectNotFoundException, IllegalAccessException {
    Work work = processesStorage.getWorkById(workId);
    if (work == null) {
      throw new ObjectNotFoundException("Work " + workId + " not found");
    }
    // A task is a request only when a process created it: a task created in
    // Tasks, in a project processes feed, is not one
    if (processesStorage.getRequestWorkFlowId(workId) == null) {
      throw new IllegalAccessException("Task " + workId + " isn't a request of a process");
    }
    org.exoplatform.services.security.Identity aclIdentity = getAclIdentity(userIdentityId);
    if (!aclIdentity.getUserId().equals(work.getCreatedBy()) && !isWorkFlowManager(aclIdentity, work.getProjectId())) {
      throw new IllegalAccessException("User " + userIdentityId + " isn't allowed to manage the request " + workId);
    }
    return work;
  }

  private Work getOwnWorkDraft(long draftId, long userIdentityId) throws ObjectNotFoundException, IllegalAccessException {
    Work draft = processesStorage.getWorkDraftyId(draftId);
    if (draft == null || !Boolean.TRUE.equals(draft.getIsDraft())) {
      throw new ObjectNotFoundException("oldWorkDraft is not exist");
    }
    if (draft.getCreatorId() != userIdentityId) {
      throw new IllegalAccessException("User " + userIdentityId + " isn't the creator of the request draft " + draftId);
    }
    return draft;
  }

  /**
   * Checks the change a user asks on a request they may manage: a manager of
   * the process may set any status, the requester alone may only cancel a
   * pending request, and never reopen a decided one
   *
   * @return true when the user manages the request's process, false when the
   *         user is its requester only
   */
  private boolean checkRequesterChange(Work storedWork,
                                       String status,
                                       boolean completed,
                                       long userIdentityId) throws IllegalAccessException {
    if (isWorkFlowManager(getAclIdentity(userIdentityId), storedWork.getProjectId())) {
      return true;
    }
    boolean statusChanged = status != null && !status.equals(storedWork.getStatus());
    if (statusChanged
        && !(STATUS_CANCELED.equals(status) && completed && PENDING_STATUSES.contains(storedWork.getStatus()))) {
      throw new IllegalAccessException("User " + userIdentityId + " can only cancel the pending request " + storedWork.getId());
    }
    if (!completed && DECIDED_STATUSES.contains(storedWork.getStatus())) {
      throw new IllegalAccessException("User " + userIdentityId + " can't reopen the decided request " + storedWork.getId());
    }
    return false;
  }

  /**
   * Checks a process update that moves the process to another space: the move
   * hands the process project, and so the management of its requests, to the
   * target space's managers, so it requires a processes administrator, or a
   * manager of both the current and the target space. A process whose current
   * space is not known any more gets one from a processes administrator only,
   * and only an existing one.
   *
   * @param storedWorkFlow the stored process
   * @param targetSpaceId the space the client sent, blank when not stated
   * @param userIdentityId the editor's identity id
   * @throws IllegalAccessException when the editor may not set that space
   * @throws IllegalArgumentException when that space does not exist
   */
  private void checkSpaceMove(WorkFlow storedWorkFlow, String targetSpaceId, long userIdentityId) throws IllegalAccessException {
    if (StringUtils.isBlank(targetSpaceId)) {
      // No space stated: the process keeps its stored one
      return;
    }
    Space currentSpace = getCurrentSpace(storedWorkFlow);
    if (currentSpace != null && currentSpace.getId().equals(targetSpaceId)) {
      return;
    }
    org.exoplatform.services.security.Identity aclIdentity = getAclIdentity(userIdentityId);
    if (currentSpace == null) {
      // A process whose space is not known any more, its project being gone,
      // is given one by a processes administrator only, and an existing one
      if (!isProcessesAdministrator(aclIdentity)) {
        throw new IllegalAccessException("User " + userIdentityId + " isn't allowed to set the space of the process "
            + storedWorkFlow.getId());
      }
      if (spaceService.getSpaceById(targetSpaceId) == null) {
        throw new IllegalArgumentException("processes.space.notFound");
      }
      return;
    }
    Space targetSpace = spaceService.getSpaceById(targetSpaceId);
    if (targetSpace == null) {
      throw new IllegalArgumentException("processes.space.notFound");
    }
    if (isProcessesAdministrator(aclIdentity)) {
      return;
    }
    if (!spaceService.isManager(currentSpace, aclIdentity.getUserId())
        || !spaceService.isManager(targetSpace, aclIdentity.getUserId())) {
      throw new IllegalAccessException("User " + userIdentityId + " isn't allowed to move the process "
          + storedWorkFlow.getId() + " to the space " + targetSpaceId);
    }
  }

  /**
   * Refuses an update that moves a process to another space while it keeps its
   * task project: the project may be fed by other processes and hold other
   * tasks, so its members are never handed to another space. A space change
   * needs a project of the new space, which comes with the change of project.
   *
   * @param storedWorkFlow the stored process
   * @param targetSpaceId the space the client sent, blank when not stated
   * @throws IllegalArgumentException processes.project.notEligible when the
   *           space differs from the process's current one
   */
  private void checkSpaceKept(WorkFlow storedWorkFlow, String targetSpaceId) {
    if (StringUtils.isBlank(targetSpaceId)) {
      return;
    }
    Space currentSpace = getCurrentSpace(storedWorkFlow);
    if (currentSpace != null && !currentSpace.getId().equals(targetSpaceId)) {
      throw new IllegalArgumentException(PROJECT_NOT_ELIGIBLE);
    }
  }

  /**
   * Reads the current managing space of a process: its stored space, else,
   * for a process stored before it had one, the space of its task project.
   *
   * @param storedWorkFlow the stored process
   * @return the space, or null when neither is known
   */
  private Space getCurrentSpace(WorkFlow storedWorkFlow) {
    if (StringUtils.isNotBlank(storedWorkFlow.getSpaceId())) {
      Space storedSpace = spaceService.getSpaceById(storedWorkFlow.getSpaceId());
      if (storedSpace != null) {
        return storedSpace;
      }
    }
    return ProcessesUtils.getProjectParentSpace(projectService, spaceService, storedWorkFlow.getProjectId());
  }

  private boolean isWorkFlowManager(org.exoplatform.services.security.Identity aclIdentity, long projectId) {
    if (isProcessesAdministrator(aclIdentity)) {
      return true;
    }
    try {
      ProjectDto project = projectService.getProject(projectId);
      return project != null && project.getManager() != null && project.canEdit(aclIdentity);
    } catch (EntityNotFoundException e) {
      return false;
    }
  }

  private boolean isProcessesAdministrator(org.exoplatform.services.security.Identity aclIdentity) {
    return ProcessesUtils.isProcessesAdministrator(userACL, aclIdentity);
  }

  // A user sees a process they participate in or may add a request to
  private void checkCanSee(WorkFlow workFlow, long workflowId, long userIdentityId) throws IllegalAccessException {
    ProcessPermission acl = workFlow.getAcl();
    if (acl == null || !(acl.isCanAccess() || acl.isCanAddRequest())) {
      throw new IllegalAccessException("User " + userIdentityId + " can't see the process " + workflowId);
    }
  }

  private org.exoplatform.services.security.Identity getAclIdentity(long userIdentityId) throws IllegalAccessException {
    Identity identity = identityManager.getIdentity(userIdentityId);
    if (identity == null || !OrganizationIdentityProvider.NAME.equals(identity.getProviderId()) || identity.isDeleted()
        || !identity.isEnable()) {
      throw new IllegalAccessException("User " + userIdentityId + " isn't an active user");
    }
    org.exoplatform.services.security.Identity aclIdentity = userACL.getUserIdentity(identity.getRemoteId());
    if (aclIdentity == null) {
      throw new IllegalAccessException("User " + userIdentityId + " isn't an active user");
    }
    return aclIdentity;
  }
}
