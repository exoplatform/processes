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

package org.exoplatform.processes.service;

import static org.exoplatform.processes.Utils.ProcessesUtils.isProcessManager;
import static org.exoplatform.processes.Utils.ProcessesUtils.isProcessParticipant;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFilter;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.storage.RequestStorage;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

@Service
public class RequestServiceImpl implements RequestService {

  private final ProcessService  ProcessService;

  private final RequestStorage  requestStorage;

  private final UserACL         userAcl;

  private final IdentityManager identityManager;

  public RequestServiceImpl(RequestStorage requestStorage,
                            ProcessService ProcessService,
                            UserACL userAcl,
                            IdentityManager identityManager) {
    this.requestStorage = requestStorage;
    this.ProcessService = ProcessService;
    this.userAcl = userAcl;
    this.identityManager = identityManager;
  }

  @Override
  public List<Work> getWorks(String userName, WorkFilter workFilter, int offset, int limit) {

    return requestStorage.getWorks(userName, workFilter, offset, limit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work createWork(Work work, String userName) throws IllegalAccessException, ObjectNotFoundException {
    if (work == null) {
      throw new IllegalArgumentException("work is mandatory");
    }
    if (work.getId() != 0) {
      throw new IllegalArgumentException("work id must be equal to 0");
    }
    if (!canAddRequest(ProcessService.getWorkFlowByProjectId(work.getProjectId()), userAcl.getUserIdentity(userName))) {
      throw new IllegalAccessException("User  " + userName + " does not have the rights to create requests");
    }
    return requestStorage.saveWork(work, userName);
  }

  @Override
  public Work updateWork(Work work,
                         String userName) throws IllegalArgumentException, ObjectNotFoundException, IllegalAccessException {
    if (work == null) {
      throw new IllegalArgumentException("Work is mandatory");
    }
    if (work.getId() == 0) {
      throw new IllegalArgumentException("work id must not be equal to 0");
    }
    if (!canEditRequest(ProcessService.getWorkFlowByProjectId(work.getProjectId()), userAcl.getUserIdentity(userName))) {
      throw new IllegalAccessException("User  " + userName + "  does not have the rights to update the request");
    }

    Work oldWork = requestStorage.getWorkById(work.getId());
    if (oldWork == null) {
      throw new ObjectNotFoundException("oldWork does not exist");
    }
    if (oldWork.getTitle().equals(work.getTitle()) && oldWork.getDescription().equals(work.getDescription())
        && oldWork.isCompleted() == (work.isCompleted()) && oldWork.getStatus().equals(work.getStatus())) {
      throw new IllegalArgumentException("there are no changes to save");
    }
    oldWork.setTitle(work.getTitle());
    oldWork.setDescription(work.getDescription());
    oldWork.setCompleted(work.isCompleted());
    oldWork.setStatus(work.getStatus());
    return requestStorage.saveWork(oldWork, userName);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteWorkById(Long workId, String userName) throws ObjectNotFoundException, IllegalAccessException {
    if (workId == null) {
      throw new IllegalArgumentException("Work id is mandatory");
    }
    Work work = getWorkById(userName, workId);
    if (work == null) {
      throw new ObjectNotFoundException("Work is not found");
    }
    if (!canDeleteRequest(ProcessService.getWorkFlowByProjectId(work.getProjectId()), userAcl.getUserIdentity(userName))) {
      throw new IllegalAccessException("User  " + userName + " does not have the rights to delete the request");
    }
    requestStorage.deleteWorkById(workId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work updateWorkCompleted(Long workId, String userName, boolean completed) throws ObjectNotFoundException,
                                                                                   IllegalAccessException {

    if (workId == null) {
      throw new IllegalArgumentException("Work id is mandatory");
    }
    Work work = getWorkById(userName, workId);
    if (work == null) {
      throw new ObjectNotFoundException("Work is not found");
    }
    if (!canCompleteRequest(work, userAcl.getUserIdentity(userName))) {
      throw new IllegalAccessException("User  " + userName + " does not have the rights to update the request");
    }
    return requestStorage.updateWorkCompleted(workId, completed);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work createWorkDraft(Work work, String userName) throws IllegalArgumentException, IllegalAccessException {
    if (work == null || work.getId() != 0) {
      throw new IllegalArgumentException("WorkDraft is mandatory and it's id must be equal to 0");
    }
    if (!canAddRequest(work.getWorkFlow(), userAcl.getUserIdentity(userName))) {
      throw new IllegalAccessException("User  " + userName + " does not have the rights to create requests");
    }

    return requestStorage.saveWorkDraft(work, userName);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work updateWorkDraft(Work work,
                              String userName) throws IllegalArgumentException, ObjectNotFoundException, IllegalAccessException {
    if (work == null) {
      throw new IllegalArgumentException("WorkDraft Type is mandatory");
    }
    if (work.getId() == 0) {
      throw new IllegalArgumentException("WorkDraft type id must not be equal to 0");
    }
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getOrCreateUserIdentity(userName);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }
    Work oldWork = requestStorage.getWorkDraftyId(work.getId());
    if (oldWork == null) {
      throw new ObjectNotFoundException("oldWorkDraft is not exist");
    }
    if (oldWork.equals(work)) {
      throw new IllegalArgumentException("there are no changes to save");
    }
    if (oldWork.getCreatorId() != Long.parseLong(identity.getId())) {
      throw new IllegalAccessException("User  " + userName + " does not have the rights to update this draft");
    }
    oldWork.setTitle(work.getTitle());
    oldWork.setDescription(work.getDescription());
    oldWork.setIsDraft(work.getIsDraft());
    return requestStorage.saveWorkDraft(oldWork, userName);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Work> getWorkDrafts(String userName, WorkFilter workFilter, int offset, int limit) {
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getOrCreateUserIdentity(userName);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }
    return requestStorage.findAllWorkDraftsByUser(workFilter, offset, limit, Long.parseLong(identity.getId()));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteWorkDraftById(Long draftId, String userName) throws IllegalAccessException, ObjectNotFoundException {
    if (draftId == null) {
      throw new IllegalArgumentException("WorkDraft id is mandatory");
    }
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getOrCreateUserIdentity(userName);
    if (identity == null) {
      throw new IllegalArgumentException("identity does not exist");
    }
    Work workDraft = requestStorage.getWorkDraftyId(draftId);
    if (workDraft == null) {
      throw new ObjectNotFoundException("WorkDraft is not found");
    }

    if (workDraft.getCreatorId() != Long.parseLong(identity.getId())) {
      throw new IllegalAccessException("User  " + userName + " does not have the rights to delete the draft");
    }
    requestStorage.deleteWorkDraftById(draftId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work getWorkById(String userName, Long workId) throws IllegalAccessException, ObjectNotFoundException {
    if (workId == null) {
      throw new IllegalArgumentException("Work id is mandatory");
    }
    Work work = requestStorage.getWorkById(workId);
    if (work == null) {
      throw new ObjectNotFoundException("Work not found");
    }
    if (!StringUtils.equals(work.getCreatedBy(), userName)
        && !isProcessManager(userAcl.getUserIdentity(userName), ProcessService.getWorkFlowByProjectId(work.getProjectId()))) {
      throw new IllegalAccessException("User  " + userName + " does not have the rights to access the request");
    }

    return work;
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

  @Override
  public boolean canCompleteRequest(Work work, Identity identity) throws ObjectNotFoundException {
    return StringUtils.equals(work.getCreatedBy(), identity.getUserId())
        || isProcessManager(identity, ProcessService.getWorkFlowByProjectId(work.getProjectId()));
  }
}
