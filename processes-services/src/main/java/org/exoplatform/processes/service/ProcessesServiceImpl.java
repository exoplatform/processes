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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.model.*;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.MembershipEntry;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.picocontainer.Startable;

public class ProcessesServiceImpl implements ProcessesService, Startable {

  private static final Log LOG = ExoLogger.getLogger(ProcessesServiceImpl.class);

  private IdentityManager  identityManager;

  private ProcessesStorage processesStorage;

  private static final String PROCESSES_SPACE_NAME          = "Processes Space";

  private static final String PROCESSES_SPACE_TEMPLATE      = "community";

  private static final String PROCESSES_SPACE_GROUP_ID      = "/spaces/processes_space";

  private static final String PROCESSES_SPACE_PRETTY_NAME   = "processes_space";

  private static final String PROCESSES_SPACE_NAME_PROPERTY = "processes.app.default.spaceName";

  private static final String PROCESSES_GROUP               = "/platform/processes";

  private static final String PROCESSES_SPACE_DESCRIPTION   = "Space where all processes will be gathered in order to manage requests";

  public ProcessesServiceImpl(ProcessesStorage processesStorage, IdentityManager identityManager) {
    this.identityManager = identityManager;
    this.processesStorage = processesStorage;
  }

  @Override
  public List<WorkFlow> getWorkFlows(ProcessesFilter filter,
                                           int offset,
                                           int limit,
                                           long userIdentityId) throws IllegalAccessException {
   return processesStorage.findWorkFlows(filter, offset, limit);
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
  public List<Work> getWorks (long userIdentityId, WorkFilter workFilter, int offset, int limit) throws Exception {

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

  @Override
  public void start() {
    LOG.info("Processes Service start and default space initialize...");
    PortalContainer container = PortalContainer.getInstance();
    RequestLifeCycle.begin(container);
    try {
      String spaceName = PropertyManager.getProperty(PROCESSES_SPACE_NAME_PROPERTY);
      SpaceService spaceService = container.getComponentInstanceOfType(SpaceService.class);
      UserACL userACL = container.getComponentInstanceOfType(UserACL.class);
      List<MembershipEntry> membershipEntries = new ArrayList<>();
      membershipEntries.add(new MembershipEntry(userACL.getAdminGroups(), "*"));
      Identity superUserIdentity = new Identity(userACL.getSuperUser(), membershipEntries);
      OrganizationService organizationService = container.getComponentInstanceOfType(OrganizationService.class);

      Space existSpace = spaceService.getSpaceByGroupId(PROCESSES_SPACE_GROUP_ID);
      ListAccess<User> list = organizationService.getUserHandler().findUsersByGroupId(PROCESSES_GROUP);
      List<String> managers = new ArrayList<>();
      if (existSpace == null) {
        Space space = new Space();
        space.setDisplayName(PROCESSES_SPACE_NAME);
        if (spaceName != null) {
          space.setDisplayName(spaceName);
        }
        space.setVisibility(Space.HIDDEN);
        space.setRegistration(Space.CLOSED);
        space.setPriority(Space.INTERMEDIATE_PRIORITY);
        space.setTemplate(PROCESSES_SPACE_TEMPLATE);
        space.setDescription(PROCESSES_SPACE_DESCRIPTION);
        space.setPrettyName(PROCESSES_SPACE_PRETTY_NAME);
        Arrays.stream(list.load(0, list.getSize())).map(User::getUserName).forEach(userName -> {
          managers.add(userName);
        });
        space.setManagers(managers.toArray(new String[managers.size()]));
        spaceService.createSpace(space, superUserIdentity.getUserId());
        LOG.info("Processes app default space has been successfully initialized");
      } else {
        LOG.info("Processes Space already exist, skip its creation...");
        Arrays.stream(list.load(0, list.getSize())).map(User::getUserName).forEach(userName -> {
          managers.add(userName);
        });
        existSpace.setManagers(managers.toArray(new String[managers.size()]));
        spaceService.updateSpace(existSpace);
      }
    } catch (Exception e) {
      LOG.error("Error while creating Processes app default space", e);
    } finally {
      RequestLifeCycle.end();
      LOG.info("Processes Service started!");
    }
  }

  @Override
  public void stop() {
    //Nothing
  }
}
