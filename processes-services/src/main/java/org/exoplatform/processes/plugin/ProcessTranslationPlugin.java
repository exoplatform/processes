/*
 * Copyright (C) 2026 eXo Platform SAS
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
package org.exoplatform.processes.plugin;

import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.service.ProcessTranslationService;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.social.translation.plugin.TranslationPlugin;

/**
 * Grants the translations of a process: a user reads them when they may see
 * the process, and writes or deletes them when they may edit it, the rights
 * of the process itself.
 */
public class ProcessTranslationPlugin extends TranslationPlugin {

  private final Supplier<ProcessesService> processesService;

  private final Supplier<IdentityManager>  identityManager;

  /**
   * Builds the plugin the Kernel registers on the translation service. The
   * processes service is looked up at each check, not injected: it depends,
   * through the process translations, on the translation service this plugin
   * is created for.
   */
  public ProcessTranslationPlugin() {
    this(() -> ExoContainerContext.getService(ProcessesService.class),
         () -> ExoContainerContext.getService(IdentityManager.class));
  }

  /**
   * Builds the plugin on given services.
   *
   * @param processesService the processes service
   * @param identityManager the identity manager
   */
  ProcessTranslationPlugin(Supplier<ProcessesService> processesService, Supplier<IdentityManager> identityManager) {
    this.processesService = processesService;
    this.identityManager = identityManager;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getObjectType() {
    return ProcessTranslationService.OBJECT_TYPE;
  }

  /**
   * Grants the user who may see the process.
   *
   * @param objectId the process's id
   * @param username the user
   * @return true when the user may see the process
   * @throws ObjectNotFoundException when the process doesn't exist
   */
  @Override
  public boolean hasAccessPermission(String objectId, String username) throws ObjectNotFoundException {
    return getWorkFlow(objectId, username) != null;
  }

  /**
   * Grants the user who may edit the process, the right of its update.
   *
   * @param objectId the process's id
   * @param username the user
   * @return true when the user may edit the process
   * @throws ObjectNotFoundException when the process doesn't exist
   */
  @Override
  public boolean hasEditPermission(String objectId, String username) throws ObjectNotFoundException {
    WorkFlow workFlow = getWorkFlow(objectId, username);
    return workFlow != null && workFlow.getAcl() != null && workFlow.getAcl().isCanEdit();
  }

  /**
   * A process's translations are granted by the process's own rights, not
   * by a metadata audience.
   *
   * @param objectId the process's id
   * @return 0
   */
  @Override
  public long getAudienceId(String objectId) {
    return 0;
  }

  /**
   * A process's translations belong to no space.
   *
   * @param objectId the process's id
   * @return 0
   */
  @Override
  public long getSpaceId(String objectId) {
    return 0;
  }

  /**
   * Reads a process as a user sees it.
   *
   * @param objectId the process's id
   * @param username the user
   * @return the process with the user's rights, or null when the user is no
   *         active user or may not see it
   * @throws ObjectNotFoundException when the process doesn't exist
   */
  private WorkFlow getWorkFlow(String objectId, String username) throws ObjectNotFoundException {
    long workflowId = parseId(objectId);
    if (StringUtils.isBlank(username)) {
      return null;
    }
    Identity identity = identityManager.get().getOrCreateUserIdentity(username);
    if (identity == null) {
      return null;
    }
    WorkFlow workFlow;
    try {
      workFlow = processesService.get().getWorkFlow(workflowId, Long.parseLong(identity.getId()));
    } catch (IllegalAccessException e) {
      return null;
    }
    if (workFlow == null) {
      throw new ObjectNotFoundException("Process " + objectId + " not found");
    }
    return workFlow;
  }

  /**
   * Reads a process id.
   *
   * @param objectId the id as the translation service names it
   * @return the id
   * @throws ObjectNotFoundException when it names no process
   */
  private long parseId(String objectId) throws ObjectNotFoundException {
    try {
      long workflowId = Long.parseLong(objectId);
      if (workflowId <= 0) {
        throw new ObjectNotFoundException("Process " + objectId + " not found");
      }
      return workflowId;
    } catch (NumberFormatException e) {
      throw new ObjectNotFoundException("Process " + objectId + " not found");
    }
  }
}
