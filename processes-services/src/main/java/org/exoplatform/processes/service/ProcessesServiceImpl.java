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
import org.exoplatform.processes.model.*;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class ProcessesServiceImpl implements ProcessesService {

  private static final Log       LOG = ExoLogger.getLogger(ProcessesServiceImpl.class);

  private final ProcessesStorage processesStorage;

  public ProcessesServiceImpl(ProcessesStorage processesStorage) {
    this.processesStorage = processesStorage;
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

    // TODO check permissions to create types

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
    // TODO check permissions to update types

    WorkFlow oldWorkFlow = processesStorage.getWorkFlowById(workFlow.getId());
    if (oldWorkFlow == null) {
      throw new ObjectNotFoundException("oldWorkFlow is not exist");
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
  public Work createWork(Work work, long userId) throws IllegalAccessException {
    if (work == null) {
      throw new IllegalArgumentException("work is mandatory");
    }
    if (work.getId() != 0) {
      throw new IllegalArgumentException("work id must be equal to 0");
    }

    // TODO check permissions to create types
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
    // TODO check permissions to update types

    Work oldWork = processesStorage.getWorkById(work.getId());
    if (oldWork == null) {
      throw new ObjectNotFoundException("oldWork is not exist");
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
  public void deleteWorkflowById(Long workflowId) {
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
  public Work createWorkDraft(Work work, long userId) throws IllegalArgumentException {
    if (work == null) {
      throw new IllegalArgumentException("WorkDraft is mandatory");
    }
    if (work.getId() != 0) {
      throw new IllegalArgumentException("WorkDraft id must be equal to 0");
    }
    return processesStorage.saveWorkDraft(work, userId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Work updateWorkDraft(Work work, long userId) throws IllegalArgumentException, ObjectNotFoundException {
    if (work == null) {
      throw new IllegalArgumentException("WorkDraft Type is mandatory");
    }
    if (work.getId() == 0) {
      throw new IllegalArgumentException("WorkDraft type id must not be equal to 0");
    }

    Work oldWork = processesStorage.getWorkDraftyId(work.getId());
    if (oldWork == null) {
      throw new ObjectNotFoundException("oldWorkDraft is not exist");
    }
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
}
