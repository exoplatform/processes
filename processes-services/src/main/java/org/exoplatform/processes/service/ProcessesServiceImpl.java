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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import io.meeds.common.ContainerTransactional;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.constant.ProjectMode;
import org.exoplatform.processes.model.*;
import org.exoplatform.processes.plugin.RequestValuesPlugin;
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

  /** The statuses of a process's task project, in their column order */
  private static final List<String> PROCESS_STATUSES   = List.of("Request", "RequestInProgress", "Validated", "Refused", "Canceled");

  private static final String      PROJECT_NOT_ELIGIBLE = "processes.project.notEligible";

  private static final String      STATUS_NAME_CONFLICT = "processes.project.statusNameConflict";

  private static final String      LABEL_NOT_IN_PROJECT = "processes.label.notInProject";

  private static final String      LABEL_INVALID_NAME   = "processes.label.invalidName";

  private static final String      FORM_UNSUPPORTED     = "processes.workflow.formUnsupported";

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
    return processesStorage.findWorkFlows(filter, userIdentityId, offset, limit);
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
      // An illustration with no file name is removed by the storage; with
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
    try {
      return saveWorkFlowRows(workFlow, userId);
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
    checkSpaceKept(oldWorkFlow, workFlow.getSpaceId());
    // The process keeps its project and its creation data, whatever the client sent
    workFlow.setProjectId(oldWorkFlow.getProjectId());
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
    // to be of the process's project before the first write
    List<LabelChoice> labelChoices = null;
    ProjectDto project = null;
    if (workFlow.getLabels() == null) {
      workFlow.setLabels(oldWorkFlow.getLabels());
    } else {
      project = findProject(oldWorkFlow.getProjectId());
      labelChoices = checkLabels(workFlow.getLabels(), project, false, getAclIdentity(userId));
      workFlow.setLabels(toLabels(labelChoices));
    }
    // The chosen form is checked before the first write; a form already the
    // process's own is no change
    String formSpaceId = StringUtils.isNotBlank(workFlow.getSpaceId()) ? workFlow.getSpaceId() : toSpaceId(getCurrentSpace(oldWorkFlow));
    workFlow.setRequestFormId(checkProcessForm(oldWorkFlow.getId(),
                                               formSpaceId,
                                               workFlow.getRequestFormId(),
                                               getAclIdentity(userId)));
    if (oldWorkFlow.equals(workFlow) && workFlow.getRequestFormId() == null) {
      throw new IllegalArgumentException("there are no changes to save");
    }
    workFlow.setAttachments(processesAttachmentService.selectProcessAttachments(userId,
                                                                                workFlow.getId(),
                                                                                oldWorkFlow.getProjectId(),
                                                                                workFlow.getAttachments()));
    if (labelChoices != null) {
      workFlow.setLabels(createLabels(labelChoices, project, getAclIdentity(userId).getUserId()));
    }
    WorkFlow savedWorkFlow = saveWorkFlowRows(workFlow, userId, labelChoices);
    processesStorage.linkWorkFlowAttachments(savedWorkFlow, workFlow.getAttachments(), userId);
    saveTranslations(savedWorkFlow, workFlow);
    processTranslationService.keepDefaultLanguage(savedWorkFlow);
    return savedWorkFlow;
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
  static boolean isAttachedToSpace(ProjectDto project, String spaceGroupId) {
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

  private Work getManageableWork(long workId, long userIdentityId) throws ObjectNotFoundException, IllegalAccessException {
    Work work = processesStorage.getWorkById(workId);
    if (work == null) {
      throw new ObjectNotFoundException("Work " + workId + " not found");
    }
    if (processesStorage.getWorkFlowByProjectId(work.getProjectId()) == null) {
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
