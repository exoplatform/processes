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

import static org.exoplatform.processes.Utils.ProcessesUtils.*;

import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.model.IllustrativeAttachment;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.model.WorkStatus;
import org.exoplatform.processes.storage.ProcessStorage;
import org.exoplatform.services.security.Identity;

@Service
public class ProcessServiceImpl implements ProcessService {

  private final ProcessStorage processStorage;

  private final UserACL        userAcl;

  public ProcessServiceImpl(ProcessStorage processStorage, UserACL userAcl) {
    this.processStorage = processStorage;
    this.userAcl = userAcl;
  }

  @Override
  public List<WorkFlow> getWorkFlows(ProcessesFilter filter, int offset, int limit, String userName) {
    return processStorage.findWorkFlows(filter, userName, offset, limit);
  }

  @Override
  public int countWorkFlows(ProcessesFilter filter, String userName) {
    return processStorage.countWorkFlows(filter);
  }

  @Override
  public WorkFlow getWorkFlow(long id, String userName) throws IllegalAccessException, ObjectNotFoundException {
    WorkFlow workFlow = getWorkFlow(id);
    if (!canAccessProcess(workFlow, userAcl.getUserIdentity(userName))) {
      throw new IllegalAccessException("User " + userName + "  does not have the rights to access Process");
    }
    return workFlow;
  }

  @Override
  public WorkFlow getWorkFlow(long id) throws ObjectNotFoundException {
    WorkFlow workFlow = processStorage.getWorkFlowById(id);
    if (workFlow == null) {
      throw new ObjectNotFoundException("Workflow does not exist");
    }
    return workFlow;
  }

  @Override
  public WorkFlow createWorkFlow(WorkFlow workFlow, String userName) throws IllegalAccessException {
    if (StringUtils.isEmpty(userName)) {
      throw new IllegalArgumentException("userName is mandatory");
    }
    if (workFlow == null) {
      throw new IllegalArgumentException("workFlow is mandatory");
    }
    if (workFlow.getId() != 0) {
      throw new IllegalArgumentException("workFlow id must be equal to 0");
    }

    if (!canAddProcess(userAcl.getUserIdentity(userName))) {
      throw new IllegalAccessException("User  " + userName + " does not have the rights to add Process");
    }
    return processStorage.saveWorkFlow(workFlow, userName);
  }

  @Override
  public WorkFlow updateWorkFlow(WorkFlow workFlow, String userName) throws IllegalArgumentException,
                                                                     ObjectNotFoundException,
                                                                     IllegalAccessException {
    if (workFlow == null || workFlow.getId() == 0) {
      throw new IllegalArgumentException("Workflow Type is mandatory and its id must not be equal to 0");
    }
    WorkFlow oldWorkFlow = getWorkFlow(workFlow.getId());
    if (oldWorkFlow.equals(workFlow)) {
      throw new IllegalArgumentException("there are no changes to save");
    }
    if (!canEditProcess(oldWorkFlow, userAcl.getUserIdentity(userName))) {
      throw new IllegalAccessException("User  " + userName + " does not have the rights to update this Process");
    }
    oldWorkFlow.setTitle(workFlow.getTitle());
    oldWorkFlow.setDescription(workFlow.getDescription());
    oldWorkFlow.setAcl(workFlow.getAcl());
    oldWorkFlow.setManager(workFlow.getManager());
    oldWorkFlow.setCanShowPending(workFlow.isCanShowPending());
    oldWorkFlow.setEnabled(workFlow.isEnabled());
    oldWorkFlow.setIllustrativeAttachment(workFlow.getIllustrativeAttachment());
    oldWorkFlow.setProjectId(workFlow.getProjectId());
    oldWorkFlow.setRequestsCreators(workFlow.getRequestsCreators());
    oldWorkFlow.setSummary(workFlow.getSummary());
    oldWorkFlow.setSpaceId(workFlow.getSpaceId());
    oldWorkFlow.setParticipator(workFlow.getParticipator());

    return processStorage.saveWorkFlow(oldWorkFlow, userName);
  }

  @Override
  public WorkFlow getWorkFlowByProjectId(long projectId) throws ObjectNotFoundException {
    WorkFlow workFlow = processStorage.getWorkFlowByProjectId(projectId);
    if (workFlow == null) {
      throw new ObjectNotFoundException("Workflow does not exist");
    }
    return workFlow;
  }

  @Override
  public WorkFlow getWorkFlowByProjectId(long projectId, String userName) throws IllegalAccessException, ObjectNotFoundException {
    WorkFlow workFlow = getWorkFlowByProjectId(projectId);
    if (!canAccessProcess(workFlow, userAcl.getUserIdentity(userName))) {
      throw new IllegalAccessException("User  " + userName + " does not have the rights to access Process");
    }
    return workFlow;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteWorkflowById(Long workflowId, String userName) throws IllegalAccessException, ObjectNotFoundException {

    WorkFlow workFlow = getWorkFlow(workflowId, userName);
    if (!canDeleteProcess(workFlow, userAcl.getUserIdentity(userName))) {
      throw new IllegalAccessException("User  " + userName + " does not have the rights to delete Process");
    }
    this.processStorage.deleteWorkflowById(workFlow.getId());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int countWorksByWorkflow(Long projectId, String userName, Boolean isCompleted) throws ObjectNotFoundException,
                                                                                        IllegalAccessException {
    if (projectId == null) {
      throw new IllegalArgumentException("Project Id is mandatory");
    }
    if (isCompleted == null) {
      throw new IllegalArgumentException("isCompleted should not be null");
    }
    WorkFlow workFlow = getWorkFlowByProjectId(projectId);
    if (!canAccessProcess(workFlow, userAcl.getUserIdentity(userName))) {
      throw new IllegalAccessException("User  " + userName + " does not have the rights to count requests for the process");
    }
    return processStorage.countWorksByWorkflow(projectId, isCompleted);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<WorkStatus> getAvailableWorkStatuses() {
    return processStorage.getAvailableWorkStatuses();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public IllustrativeAttachment getIllustrationImageById(Long illustrationId, String userName) {
    if (illustrationId == null) {
      throw new IllegalArgumentException("IllustrationId id is mandatory");
    }
    return processStorage.getIllustrationImageById(illustrationId);
  }

  @Override
  public boolean canAccessProcess(WorkFlow workFlow, Identity identity) {
    return isPlatformAdmin(identity) || isProcessAdmin(identity) || isProcessManager(identity, workFlow)
        || isProcessParticipant(identity, workFlow);
  }

  @Override
  public boolean canEditProcess(WorkFlow workFlow, Identity identity) {
    return isPlatformAdmin(identity) || isProcessAdmin(identity) || isProcessManager(identity, workFlow);
  }

  @Override
  public boolean canAddProcess(Identity identity) {
    return isPlatformAdmin(identity) || isProcessAdmin(identity);
  }

  @Override
  public boolean canDeleteProcess(WorkFlow workFlow, Identity identity) {
    return isPlatformAdmin(identity) || isProcessAdmin(identity) || isProcessManager(identity, workFlow);
  }

}
