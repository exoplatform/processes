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

import static org.exoplatform.processes.Utils.ProcessesUtils.*;

import java.io.IOException;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.model.*;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;


public class ProcessesServiceImpl implements ProcessesService {

  private static final Log       LOG = ExoLogger.getLogger(ProcessesServiceImpl.class);

  private final ProcessesStorage processesStorage;

  private UserACL userAcl;
  private IdentityManager identityManager;

  public ProcessesServiceImpl(ProcessesStorage processesStorage, UserACL userAcl, IdentityManager identityManager) {
    this.processesStorage = processesStorage;
    this.userAcl = userAcl;
    this.identityManager = identityManager;
  }

  @Override
  public List<WorkFlow> getWorkFlows(ProcessesFilter filter,
                                     int offset,
                                     int limit,
                                     long userIdentityId) throws IllegalAccessException {
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getIdentity(userIdentityId);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }
    return processesStorage.findWorkFlows(filter, userIdentityId, offset, limit);
  }

  @Override
  public int countWorkFlows(ProcessesFilter filter, long userIdentityId) throws IllegalAccessException {
    return processesStorage.countWorkFlows(filter);
  }

  @Override
  public WorkFlow getWorkFlow(long id, long userIdentityId) throws IllegalAccessException {
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getIdentity(userIdentityId);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }
    WorkFlow workFlow = getWorkFlow(id);
    if (workFlow != null && !canAccess(workFlow, userAcl.getUserIdentity(identity.getRemoteId()))) {
      throw new IllegalAccessException("User with identity Id = " + userIdentityId + "  does not have the rights to access Process");
    }
    return workFlow;
  }

  @Override
  public WorkFlow getWorkFlow(long id) throws IllegalAccessException {
    return processesStorage.getWorkFlowById(id);
  }

  @Override
  public WorkFlow createWorkFlow(WorkFlow workFlow, long userId) throws IllegalAccessException {
    if (workFlow == null) {
      throw new IllegalArgumentException("workFlow is mandatory");
    }
    if (workFlow.getId() != 0) {
      throw new IllegalArgumentException("workFlow id must be equal to 0");
    }
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getIdentity(userId);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }

    if (!canAdd(userAcl.getUserIdentity(identity.getRemoteId()))) {
      throw new IllegalAccessException("User with identity Id = " + userId + " does not have the rights to add Process");
    }
    return processesStorage.saveWorkFlow(workFlow, identity);
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
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getIdentity(userId);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }
    WorkFlow oldWorkFlow = processesStorage.getWorkFlowById(workFlow.getId());
    if (oldWorkFlow == null) {
      throw new ObjectNotFoundException("oldWorkFlow does not exist");
    }
    if (oldWorkFlow.equals(workFlow)) {
      throw new IllegalArgumentException("there are no changes to save");
    }
    if (!canEdit(oldWorkFlow, userAcl.getUserIdentity(identity.getRemoteId()))) {
      throw new IllegalAccessException("User with identity Id = " + userId + " does not have the rights to update this Process");
    }

    return processesStorage.saveWorkFlow(workFlow, identity);
  }

  @Override
  public List<Work> getWorks(long userIdentityId, WorkFilter workFilter, int offset, int limit) throws Exception {

    return processesStorage.getWorks(userIdentityId, workFilter, offset, limit);
  }

  @Override
  public WorkFlow getWorkFlowByProjectId(long projectId) {
    return processesStorage.getWorkFlowByProjectId(projectId);
  }

  @Override
  public WorkFlow getWorkFlowByProjectId(long projectId, long userId) throws IllegalAccessException {
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getIdentity(userId);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }
    WorkFlow workFlow = getWorkFlowByProjectId(projectId);
    if (!canAccess(workFlow, userAcl.getUserIdentity(identity.getRemoteId()))) {
      throw new IllegalAccessException("User with identity Id = " + userId + " does not have the rights to access Process");
    }
    return workFlow;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work createWork(Work work, long userId) throws IllegalAccessException {
    if (work == null) {
      throw new IllegalArgumentException("work is mandatory");
    }
    if (work.getId() != 0) {
      throw new IllegalArgumentException("work id must be equal to 0");
    }
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getIdentity(userId);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }
    if (!canAddRequest(getWorkFlowByProjectId(work.getProjectId()), userAcl.getUserIdentity(identity.getRemoteId()))) {
      throw new IllegalAccessException("User with identity Id = " + userId + " does not have the rights to create requests");
    }
    return processesStorage.saveWork(work, identity);
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
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getIdentity(userId);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }
    if (!canAddRequest(getWorkFlowByProjectId(work.getProjectId()), userAcl.getUserIdentity(identity.getRemoteId()))) {
      throw new IllegalAccessException("User with identity Id = " + userId + "  does not have the rights to update the request");
    }

    Work oldWork = processesStorage.getWorkById(work.getId());
    if (oldWork == null) {
      throw new ObjectNotFoundException("oldWork does not exist");
    }
    if (oldWork.equals(work)) {
      throw new IllegalArgumentException("there are no changes to save");
    }
    return processesStorage.saveWork(work, identity);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteWorkflowById(Long workflowId, long userIdentityId) throws IllegalAccessException, ObjectNotFoundException {
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getIdentity(userIdentityId);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }
    WorkFlow workFlow = getWorkFlow(workflowId, userIdentityId);
    if (workFlow == null) {
      throw new ObjectNotFoundException("Workflow does not exist");
    }
    if (!canDelete(workFlow, userAcl.getUserIdentity(identity.getRemoteId()))) {
      throw new IllegalAccessException("User with identity Id = " + userIdentityId + " does not have the rights to delete Process");
    }
    this.processesStorage.deleteWorkflowById(workFlow.getId());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int countWorksByWorkflow(Long projectId, long userIdentityId, Boolean isCompleted) throws Exception {
    if (projectId == null) {
      throw new IllegalArgumentException("Project Id is mandatory");
    }
    if (isCompleted == null) {
      throw new IllegalArgumentException("isCompleted should not be null");
    }
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getIdentity(userIdentityId);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }
    WorkFlow workFlow = getWorkFlowByProjectId(projectId);
    if (workFlow == null) {
      throw new ObjectNotFoundException("Workflow related to the project Id " + projectId + " not found");
    }
    if (!canAccess(workFlow, userAcl.getUserIdentity(identity.getRemoteId()))) {
      throw new IllegalAccessException("User with identity Id = " + userIdentityId + " does not have the rights to count requests for the process");
    }
    return processesStorage.countWorksByWorkflow(projectId, isCompleted);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteWorkById(Long workId, long userId) throws ObjectNotFoundException, IllegalAccessException {
    if (workId == null) {
      throw new IllegalArgumentException("Work id is mandatory");
    }
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getIdentity(userId);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }
    Work work = getWorkById(workId, userId);
    if (work == null) {
      throw new ObjectNotFoundException("Work is not found");
    }
    if (!canDeleteRequest(getWorkFlowByProjectId(work.getProjectId()), userAcl.getUserIdentity(identity.getRemoteId()))) {
      throw new IllegalAccessException("User with identity Id = " + userId + " does not have the rights to delete the request");
    }
    processesStorage.deleteWorkById(workId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work updateWorkCompleted(Long workId, long userId, boolean completed) throws ObjectNotFoundException, IllegalAccessException {

    if (workId == null) {
      throw new IllegalArgumentException("Work id is mandatory");
    }
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getIdentity(userId);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }
    Work work = getWorkById(userId, workId);
    if (work == null) {
      throw new ObjectNotFoundException("Work is not found");
    }
    if (!(StringUtils.equals(work.getCreatedBy(), identity.getRemoteId()) || isProcessManager(userAcl.getUserIdentity(identity.getRemoteId()), getWorkFlowByProjectId(work.getProjectId())))) {
      throw new IllegalAccessException("User with identity Id = " + userId + " does not have the rights to update the request");
    }
    return processesStorage.updateWorkCompleted(workId, completed);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work createWorkDraft(Work work, long userId) throws IllegalArgumentException, IllegalAccessException {
    if (work == null) {
      throw new IllegalArgumentException("WorkDraft is mandatory");
    }
    if (work.getId() != 0) {
      throw new IllegalArgumentException("WorkDraft id must be equal to 0");
    }
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getIdentity(userId);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }
    if (!canAddRequest(work.getWorkFlow(), userAcl.getUserIdentity(identity.getRemoteId()))) {
      throw new IllegalAccessException("User with identity Id = " + userId + " does not have the rights to create requests");
    }

    return processesStorage.saveWorkDraft(work, userId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work updateWorkDraft(Work work, long userId) throws IllegalArgumentException, ObjectNotFoundException, IllegalAccessException {
    if (work == null) {
      throw new IllegalArgumentException("WorkDraft Type is mandatory");
    }
    if (work.getId() == 0) {
      throw new IllegalArgumentException("WorkDraft type id must not be equal to 0");
    }
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getIdentity(userId);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }
    Work oldWork = processesStorage.getWorkDraftyId(work.getId());
    if (oldWork == null) {
      throw new ObjectNotFoundException("oldWorkDraft is not exist");
    }
    if (oldWork.equals(work)) {
      throw new IllegalArgumentException("there are no changes to save");
    }
    if (oldWork.getCreatorId() != userId) {
      throw new IllegalAccessException("User with identity Id = " + userId + " does not have the rights to update this draft");
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
  public void deleteWorkDraftById(Long draftId, long userId) throws IllegalAccessException, ObjectNotFoundException {
    if (draftId == null) {
      throw new IllegalArgumentException("WorkDraft id is mandatory");
    }
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getIdentity(userId);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }
    Work workDraft = processesStorage.getWorkDraftyId(draftId);
    if (workDraft == null) {
      throw new ObjectNotFoundException("WorkDraft is not found");
    }

    if (workDraft.getCreatorId() != userId) {
      throw new IllegalAccessException("User with identity Id = " + userId + " does not have the rights to delete the draft");
    }
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
  public Work getWorkById(long userId, Long workId) throws IllegalAccessException {
    if (workId == null) {
      throw new IllegalArgumentException("Work id is mandatory");
    }
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getIdentity(userId);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }
    Work work = processesStorage.getWorkById(userId, workId);
    if (work != null && !StringUtils.equals(work.getCreatedBy(), identity.getRemoteId()) && !isProcessManager(userAcl.getUserIdentity(identity.getRemoteId()), getWorkFlowByProjectId(work.getProjectId()))) {
      throw new IllegalAccessException("User with identity Id = " + userId + " does not have the rights to access the request");
    }
    return work;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public IllustrativeAttachment getIllustrationImageById(Long illustrationId, long userIdentityId) throws FileStorageException,
                                                                              ObjectNotFoundException,
                                                                              IOException {
    if (illustrationId == null) {
      throw new IllegalArgumentException("IllustrationId id is mandatory");
    }
    return processesStorage.getIllustrationImageById(illustrationId);
  }

  @Override
  public boolean canAccess(WorkFlow workFlow, Identity identity) {
    return canEdit(workFlow, identity);
  }

  @Override
  public boolean canEdit(WorkFlow workFlow, Identity identity) {
    return isPlatformAdmin(identity) || isProcessAdmin(identity) || isProcessManager(identity, workFlow);
  }

  @Override
  public boolean canAdd(Identity identity) {
    return isPlatformAdmin(identity) || isProcessAdmin(identity);
  }

  @Override
  public boolean canDelete(WorkFlow workFlow, Identity identity) {
    return isPlatformAdmin(identity) || isProcessAdmin(identity) || isProcessManager(identity, workFlow);
  }

  @Override
  public boolean canAddRequest(WorkFlow workFlow, Identity identity) {
    return isProcessManager(identity, workFlow) || isProcessParticipant(identity, workFlow);
  }

  @Override
  public boolean canEditRequest(WorkFlow workFlow, Identity identity) {
    return isProcessManager(identity, workFlow);
  }

  @Override
  public boolean canDeleteRequest(WorkFlow workFlow, Identity identity) {
    return isProcessManager(identity, workFlow);
  }

}
