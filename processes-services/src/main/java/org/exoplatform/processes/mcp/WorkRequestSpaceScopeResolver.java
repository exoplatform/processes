/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.processes.mcp;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.mcp.server.plugin.SpaceScopeResolver;

/**
 * Tells which space a work request belongs to, for the space scope of the
 * processes tools: the space of the process its request record names, by the
 * rule of {@link ProcessSpaceScopeResolver}. A task project may be shared by
 * several processes and hold plain tasks, so a task with no request record is
 * no request and belongs to no space here, nor does a missing one. The record
 * is read as the system ({@link ProcessesService#getRequestWorkFlowId(long)}),
 * for its process id only; the tools read the request again with the
 * caller's own rights ({@code getWorkById(userIdentityId, id)}, the caller's
 * own requests only). The read gives a process id only, never returned to the
 * model, since a space scope decision needs the space of any request, whoever
 * asks. Its only visible effect is that a request id outside the call's space
 * is refused by the scope, and one inside it that isn't the caller's, by the
 * tool.
 */
@Service("workRequestSpaceScopeResolver")
@Profile("mcp-server")
public class WorkRequestSpaceScopeResolver implements SpaceScopeResolver {

  /** The name the processes tools' {@code space_scope.resolver} gives. */
  public static final String        NAME = "work_request";

  /** Logs the lookups that fail. */
  private static final Log LOG = ExoLogger.getLogger(WorkRequestSpaceScopeResolver.class);

  /** Reads the process of a request from its request record. */
  @Autowired
  private ProcessesService          processesService;

  /** Tells the space of a process. */
  @Autowired
  private ProcessSpaceScopeResolver processSpaceScopeResolver;

  /**
   * @return {@value #NAME}
   */
  @Override
  public String getName() {
    return NAME;
  }

  /**
   * @param objectId the request id, the id of its task
   * @return the id of the space of the process its request record names, or
   *         null for a task with no request record, a process with no space,
   *         an id that isn't one, or a lookup that fails
   */
  @Override
  public Long spaceOf(String objectId) {
    if (!StringUtils.isNumeric(objectId)) {
      return null;
    }
    try {
      // The request record names the process, which a task project shared by
      // several processes cannot
      Long workFlowId = processesService.getRequestWorkFlowId(Long.parseLong(objectId));
      return workFlowId == null ? null : processSpaceScopeResolver.spaceOf(String.valueOf(workFlowId));
    } catch (RuntimeException e) {
      LOG.debug("The space of request '{}' couldn't be read", objectId, e);
      return null;
    }
  }

}
