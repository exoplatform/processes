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

import java.util.List;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.Utils.EntityMapper;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.manager.IdentityManager;

public class ProcessesServiceImpl implements ProcessesService {

  private static final Log LOG = ExoLogger.getLogger(ProcessesServiceImpl.class);

  private IdentityManager  identityManager;

  private ProcessesStorage processesStorage;

  public ProcessesServiceImpl(ProcessesStorage processesStorage, IdentityManager identityManager) {
    this.identityManager = identityManager;
    this.processesStorage = processesStorage;
  }

  @Override
  public List<WorkFlow> getWorkFlows(ProcessesFilter filter,
                                           int offset,
                                           int limit,
                                           long userIdentityId) throws IllegalAccessException {
    return processesStorage.findEnabledWorkFlowsByUser(filter, offset, limit, userIdentityId);
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
  public WorkFlow updateWorkFlow(WorkFlow workFlow, long userId) throws IllegalArgumentException,
                                                                             ObjectNotFoundException,
                                                                             IllegalAccessException {
    if (workFlow == null) {
      throw new IllegalArgumentException("Work Type is mandatory");
    }
    if (workFlow.getId() == 0) {
      throw new IllegalArgumentException("work type id must not be equal to 0");
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
  public List<Work> getWorks (long userIdentityId, int offset, int limit) throws Exception {

    return processesStorage.getWorks(userIdentityId, offset, limit);
  }

  @Override
  public WorkFlow getWorkFlowByProjectId(long projectId) {
    return processesStorage.getWorkFlowByProjectId(projectId);
  }


}
