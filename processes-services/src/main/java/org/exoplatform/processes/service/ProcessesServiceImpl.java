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
import java.util.List;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.model.*;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;

public class ProcessesServiceImpl implements ProcessesService {

  private static final String    PROCESSES_GROUP = "/platform/processes";

  private final ProcessesStorage processesStorage;

  private final ProjectService   projectService;

  private final IdentityManager  identityManager;

  private final UserACL          userACL;

  public ProcessesServiceImpl(ProcessesStorage processesStorage,
                              ProjectService projectService,
                              IdentityManager identityManager,
                              UserACL userACL) {
    this.processesStorage = processesStorage;
    this.projectService = projectService;
    this.identityManager = identityManager;
    this.userACL = userACL;
  }

  @Override
  public List<WorkFlow> getWorkFlows(ProcessesFilter filter,
                                     int offset,
                                     int limit,
                                     long userIdentityId) throws IllegalAccessException {
    return processesStorage.findWorkFlows(filter, userIdentityId, offset, limit);
  }

  @Override
  public int countWorkFlows(ProcessesFilter filter, long userIdentityId) throws IllegalAccessException {
    return processesStorage.countWorkFlows(filter);
  }

  @Override
  public WorkFlow getWorkFlow(long id, Long userIdentityId) throws IllegalAccessException {
    return processesStorage.getWorkFlowById(id, userIdentityId);
  }

  @Override
  public WorkFlow createWorkFlow(WorkFlow workFlow, long userId) throws IllegalAccessException {
    if (workFlow == null) {
      throw new IllegalArgumentException("workFlow is mandatory");
    }
    if (workFlow.getId() != 0) {
      throw new IllegalArgumentException("workFlow id must be equal to 0");
    }
    if (!isProcessesManager(getAclIdentity(userId))) {
      throw new IllegalAccessException("User " + userId + " isn't allowed to create a process");
    }
    // A new process always gets its own project and illustration file, never
    // existing ones the client names
    workFlow.setProjectId(0);
    if (workFlow.getIllustrativeAttachment() != null) {
      workFlow.getIllustrativeAttachment().setId(null);
    }
    return processesStorage.saveWorkFlow(workFlow, userId);
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
    return processesStorage.saveWorkFlow(workFlow, userId);
  }

  @Override
  public List<Work> getWorks(long userIdentityId, WorkFilter workFilter, int offset, int limit) throws Exception {

    return processesStorage.getWorks(userIdentityId, workFilter, offset, limit);
  }

  @Override
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
    WorkFlow workFlow = checkCanAddRequest(getWorkFlowIdByProjectId(work.getProjectId()), userId);
    if (!workFlow.isEnabled()) {
      throw new IllegalArgumentException("Workflow is disabled");
    }
    if (work.getDraftId() != null) {
      getOwnWorkDraft(work.getDraftId(), userId);
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
    getManageableWork(workId, userIdentityId);
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
    WorkFlow workFlow = processesStorage.getWorkFlowById(workflowId, userIdentityId);
    if (workFlow == null) {
      throw new ObjectNotFoundException("Workflow " + workflowId + " not found");
    }
    ProcessPermission acl = workFlow.getAcl();
    if (acl == null || !(acl.isCanAccess() || acl.isCanAddRequest())) {
      throw new IllegalAccessException("User " + userIdentityId + " can't see the process " + workflowId);
    }
    Long illustrationId = workFlow.getIllustrativeAttachment() == null ? null : workFlow.getIllustrativeAttachment().getId();
    if (illustrationId == null) {
      throw new ObjectNotFoundException("Workflow " + workflowId + " has no illustration");
    }
    return processesStorage.getIllustrationImageById(illustrationId);
  }

  private long getWorkFlowIdByProjectId(long projectId) throws ObjectNotFoundException {
    WorkFlow workFlow = processesStorage.getWorkFlowByProjectId(projectId);
    if (workFlow == null) {
      throw new ObjectNotFoundException("No process found for project " + projectId);
    }
    return workFlow.getId();
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

  private boolean isWorkFlowManager(org.exoplatform.services.security.Identity aclIdentity, long projectId) {
    if (isProcessesManager(aclIdentity) || userACL.isAdministrator(aclIdentity)) {
      return true;
    }
    try {
      ProjectDto project = projectService.getProject(projectId);
      return project != null && project.getManager() != null && project.canEdit(aclIdentity);
    } catch (EntityNotFoundException e) {
      return false;
    }
  }

  private boolean isProcessesManager(org.exoplatform.services.security.Identity aclIdentity) {
    return aclIdentity.isMemberOf(PROCESSES_GROUP);
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
