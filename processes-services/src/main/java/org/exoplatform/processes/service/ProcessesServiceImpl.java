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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;
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
    if (filter.getEnabled() != null && filter.getEnabled()) {
      return processesStorage.findEnabledWorkFlows(offset, limit);
    } else if (filter.getEnabled() != null && !filter.getEnabled()) {
      return processesStorage.findDisabledWorkFlows(offset, limit);
    }
    return processesStorage.findAllWorkFlows(offset, limit);
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
  public Work updateWork(Work work, long userId) throws IllegalArgumentException,
          ObjectNotFoundException,
          IllegalAccessException {
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


}
