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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.constant.ProjectMode;
import org.exoplatform.processes.model.*;
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dao.OrderBy;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.StatusDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.StatusService;
import org.exoplatform.task.util.UserUtil;

public class ProcessesServiceImpl implements ProcessesService {

  private static final String      STATUS_CANCELED     = "Canceled";

  private static final Set<String> PENDING_STATUSES    = Set.of("Request", "RequestInProgress");

  private static final Set<String> DECIDED_STATUSES    = Set.of("Validated", "Refused");

  /** The statuses of a process's task project, in their column order */
  private static final List<String> PROCESS_STATUSES   = List.of("Request", "RequestInProgress", "Validated", "Refused", "Canceled");

  private static final String      PROJECT_NOT_ELIGIBLE = "processes.project.notEligible";

  private static final String      STATUS_NAME_CONFLICT = "processes.project.statusNameConflict";

  private final ProcessesStorage processesStorage;

  private final ProjectService   projectService;

  private final IdentityManager  identityManager;

  private final UserACL          userACL;

  private final SpaceService     spaceService;

  private final ProcessesAttachmentService processesAttachmentService;

  private final StatusService    statusService;

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
   */
  public ProcessesServiceImpl(ProcessesStorage processesStorage,
                              ProjectService projectService,
                              IdentityManager identityManager,
                              UserACL userACL,
                              SpaceService spaceService,
                              ProcessesAttachmentService processesAttachmentService,
                              StatusService statusService) {
    this.processesStorage = processesStorage;
    this.projectService = projectService;
    this.identityManager = identityManager;
    this.userACL = userACL;
    this.spaceService = spaceService;
    this.processesAttachmentService = processesAttachmentService;
    this.statusService = statusService;
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
    if (!isProcessesAdministrator(getAclIdentity(userId))) {
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
    workFlow.setAttachments(processesAttachmentService.selectProcessAttachments(userId, 0, 0, workFlow.getAttachments()));
    if (existingProject != null) {
      // Task commits each status it creates, before the process is saved
      ensureProcessStatuses(existingProject);
    }
    return processesStorage.saveWorkFlow(workFlow, userId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EligibleProjects listEligibleProjects(String spaceId,
                                               int offset,
                                               int limit,
                                               long userIdentityId) throws ObjectNotFoundException, IllegalAccessException {
    Space space = StringUtils.isBlank(spaceId) ? null : spaceService.getSpaceById(spaceId);
    if (space == null) {
      throw new ObjectNotFoundException("Space " + spaceId + " not found");
    }
    org.exoplatform.services.security.Identity aclIdentity = getAclIdentity(userIdentityId);
    if (!isProcessesAdministrator(aclIdentity) && !spaceService.isManager(space, aclIdentity.getUserId())) {
      throw new IllegalAccessException("User " + userIdentityId + " isn't allowed to list the projects of the space " + spaceId);
    }
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
    if (oldWorkFlow.equals(workFlow)) {
      throw new IllegalArgumentException("there are no changes to save");
    }
    workFlow.setAttachments(processesAttachmentService.selectProcessAttachments(userId,
                                                                                workFlow.getId(),
                                                                                oldWorkFlow.getProjectId(),
                                                                                workFlow.getAttachments()));
    return processesStorage.saveWorkFlow(workFlow, userId);
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
  public Work createWork(Work work, long userId) throws IllegalAccessException, ObjectNotFoundException {
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
    return processesStorage.saveWork(work, userId);
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
    this.processesStorage.deleteWorkflowById(workflowId);
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
    this.processesStorage.deleteWorkflowById(workflowId);
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
    return processesStorage.saveWorkDraft(work, userId);
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
    // The draft keeps its creator and its process, whatever the client sent
    work.setCreatorId(oldWork.getCreatorId());
    work.setWorkFlow(oldWork.getWorkFlow());
    if (oldWork.equals(work)) {
      throw new IllegalArgumentException("there are no changes to save");
    }
    return processesStorage.saveWorkDraft(work, userId);
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
    processesStorage.deleteWorkDraftById(draftId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteWorkDraftById(Long draftId, long userIdentityId) throws ObjectNotFoundException, IllegalAccessException {
    if (draftId == null) {
      throw new IllegalArgumentException("WorkDraft id is mandatory");
    }
    getOwnWorkDraft(draftId, userIdentityId);
    processesStorage.deleteWorkDraftById(draftId);
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
    if (draft == null) {
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
   * manager of both the current and the target space
   */
  private void checkSpaceMove(WorkFlow storedWorkFlow, String targetSpaceId, long userIdentityId) throws IllegalAccessException {
    if (targetSpaceId == null) {
      return;
    }
    Space currentSpace = ProcessesUtils.getProjectParentSpace(projectService, spaceService, storedWorkFlow.getProjectId());
    if (currentSpace == null || currentSpace.getId().equals(targetSpaceId)) {
      return;
    }
    org.exoplatform.services.security.Identity aclIdentity = getAclIdentity(userIdentityId);
    if (isProcessesAdministrator(aclIdentity)) {
      return;
    }
    Space targetSpace = spaceService.getSpaceById(targetSpaceId);
    if (targetSpace == null) {
      throw new IllegalArgumentException("Space " + targetSpaceId + " not found");
    }
    if (!spaceService.isManager(currentSpace, aclIdentity.getUserId())
        || !spaceService.isManager(targetSpace, aclIdentity.getUserId())) {
      throw new IllegalAccessException("User " + userIdentityId + " isn't allowed to move the process "
          + storedWorkFlow.getId() + " to the space " + targetSpaceId);
    }
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
