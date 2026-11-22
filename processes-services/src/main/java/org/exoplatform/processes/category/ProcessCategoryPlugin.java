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
package org.exoplatform.processes.category;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import org.exoplatform.processes.constant.ProcessCategories;
import org.exoplatform.processes.model.ProcessPermission;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.social.category.model.CategoryObject;
import io.meeds.social.category.plugin.CategoryPlugin;
import io.meeds.social.category.service.CategoryPluginService;

import jakarta.annotation.PostConstruct;

/**
 * Files processes under the platform's categories, object type
 * {@value ProcessCategories#OBJECT_TYPE}. The platform asks it who may see a
 * process's categories, who may change them, and which categories a user may
 * filter the processes by. Who sees a process sees its categories; only a
 * manager of the process changes them. It carries no rule of its own:
 * {@link ProcessesService} holds them.
 */
@Component
public class ProcessCategoryPlugin implements CategoryPlugin {

  private static final Log            LOG = ExoLogger.getLogger(ProcessCategoryPlugin.class);

  private final ProcessesService      processesService;

  private final IdentityManager       identityManager;

  private final CategoryPluginService categoryPluginService;

  /**
   * Builds the plug-in.
   *
   * @param processesService the processes service, which holds the rights
   * @param identityManager the identity manager, which resolves a user name
   * @param categoryPluginService the categories' plug-in registry
   */
  public ProcessCategoryPlugin(ProcessesService processesService,
                               IdentityManager identityManager,
                               CategoryPluginService categoryPluginService) {
    this.processesService = processesService;
    this.identityManager = identityManager;
    this.categoryPluginService = categoryPluginService;
  }

  /**
   * Registers the plug-in with the categories once the bean is built: without
   * it, the categories answer for processes with their default rules.
   */
  @PostConstruct
  public void init() {
    categoryPluginService.addPlugin(this);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getType() {
    return ProcessCategories.OBJECT_TYPE;
  }

  /**
   * Tells whether a user sees a process's categories: they see the process,
   * participating in it or allowed to add a request to it.
   *
   * @param objectId the process id
   * @param username the user name
   * @return true when the user sees the process
   */
  @Override
  public boolean canAccess(String objectId, String username) {
    ProcessPermission acl = getPermission(objectId, username);
    return acl != null && (acl.isCanAccess() || acl.isCanAddRequest());
  }

  /**
   * Tells whether a user changes a process's categories: they manage the
   * process.
   *
   * @param objectId the process id
   * @param username the user name
   * @return true when the user may edit the process
   */
  @Override
  public boolean canEdit(String objectId, String username) {
    ProcessPermission acl = getPermission(objectId, username);
    return acl != null && acl.isCanEdit();
  }

  /**
   * Stores a process's link with the process id in its canonical form, the
   * one the bulk reads of the categories match as is; a non-numeric id is
   * left as sent.
   *
   * @param object the linked object
   * @return the object with a canonical process id
   */
  @Override
  public CategoryObject getObject(CategoryObject object) {
    if (object == null || !ProcessCategories.OBJECT_TYPE.equals(object.getType()) || object.getId() == null) {
      return object;
    }
    try {
      String id = String.valueOf(Long.parseLong(object.getId().trim()));
      return id.equals(object.getId()) ? object
                                       : new CategoryObject(ProcessCategories.OBJECT_TYPE,
                                                            id,
                                                            object.getParentId(),
                                                            object.getSpaceId());
    } catch (NumberFormatException e) {
      return object;
    }
  }

  /**
   * Answers no category: the categories of processes are always read for a
   * user, never for the whole type.
   *
   * @return an empty list
   */
  @Override
  public List<Long> getCategoryIds() {
    return Collections.emptyList();
  }

  /**
   * Answers no category: the categories of processes are always read for a
   * user, never for a whole space.
   *
   * @param spaceId the space id
   * @return an empty list
   */
  @Override
  public List<Long> getCategoryIds(long spaceId) {
    return Collections.emptyList();
  }

  /**
   * Reads the categories a user may filter the processes by: those of the
   * processes the user sees, whatever the space.
   *
   * @param spaceId the space id, not read: the Processes page lists every
   *          space's processes
   * @param username the user name
   * @return the category ids, empty for an unknown or inactive user
   */
  @Override
  public List<Long> getCategoryIds(long spaceId, String username) {
    Long userIdentityId = getUserIdentityId(username);
    if (userIdentityId == null) {
      return Collections.emptyList();
    }
    try {
      return processesService.getCategoryIds(userIdentityId);
    } catch (IllegalAccessException e) {
      LOG.debug("User {} lists no process to filter by category", username, e);
      return Collections.emptyList();
    }
  }

  /**
   * Reads a user's permission on a process.
   *
   * @param objectId the process id
   * @param username the user name
   * @return the permission, or null when the process or the user is unknown,
   *         or the user doesn't see the process
   */
  private ProcessPermission getPermission(String objectId, String username) {
    Long userIdentityId = getUserIdentityId(username);
    Long workflowId = toWorkflowId(objectId);
    if (userIdentityId == null || workflowId == null) {
      return null;
    }
    try {
      WorkFlow workFlow = processesService.getWorkFlow(workflowId, userIdentityId);
      return workFlow == null ? null : workFlow.getAcl();
    } catch (IllegalAccessException e) {
      LOG.debug("User {} can't see the process {}", username, objectId, e);
      return null;
    }
  }

  /**
   * Resolves a user name to its identity id.
   *
   * @param username the user name
   * @return the identity id, or null for a blank or unknown user
   */
  private Long getUserIdentityId(String username) {
    if (StringUtils.isBlank(username)) {
      return null;
    }
    Identity identity = identityManager.getOrCreateUserIdentity(username);
    return identity == null ? null : Long.valueOf(identity.getId());
  }

  /**
   * Reads a process id.
   *
   * @param objectId the object id
   * @return the process id, or null when it is no number
   */
  private static Long toWorkflowId(String objectId) {
    try {
      return objectId == null ? null : Long.valueOf(objectId.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
