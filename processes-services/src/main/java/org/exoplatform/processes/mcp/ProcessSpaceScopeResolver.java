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

import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;

import io.meeds.mcp.server.plugin.SpaceScopeResolver;

/**
 * Tells which space a process (a workflow) belongs to, for the space scope of
 * the processes tools: the space of the process's project, when every manager
 * of that project is a role of one and the same space group
 * ({@code <role>:/spaces/<name>}), the rule of the task add-on's project
 * resolver. A project with a user manager, a group outside the spaces or a
 * second space among its managers, a missing process, project or space
 * belong to no space. The process is read as the system ({@code null} user
 * id), for its project id only, never for an authorization flag: the tools
 * read the process again with the caller's memberships and gate on its
 * {@code acl}. The read gives a project id only, never returned to the model,
 * since a space scope decision needs the space of any process, whoever asks.
 * Its only visible effect is that a process id outside the call's space is
 * refused by the scope, and one inside it that the caller may not submit to,
 * by the tool's own check.
 */
@Service("processSpaceScopeResolver")
@Profile("mcp-server")
public class ProcessSpaceScopeResolver implements SpaceScopeResolver {

  /** The name the processes tools' {@code space_scope.resolver} gives. */
  public static final String NAME           = "process";

  /** Logs the lookups that fail. */
  private static final Log LOG = ExoLogger.getLogger(ProcessSpaceScopeResolver.class);

  /** The prefix of the group id of a space. */
  private static final String SPACES_PREFIX = "/spaces/";

  /** Reads the process. */
  @Autowired
  private ProcessesService    processesService;

  /** Reads the process's project. */
  @Autowired
  private ProjectService      projectService;

  /** Reads the space of a group. */
  @Autowired
  private SpaceService        spaceService;

  /**
   * @return {@value #NAME}
   */
  @Override
  public String getName() {
    return NAME;
  }

  /**
   * @param objectId the process (workflow) id
   * @return the id of the space of the process's project, or null for a
   *         project that isn't managed through exactly one space, a missing
   *         process, project or space, an id that isn't one, or a lookup
   *         that fails
   */
  @Override
  public Long spaceOf(String objectId) {
    if (!StringUtils.isNumeric(objectId)) {
      return null;
    }
    try {
      WorkFlow workFlow = processesService.getWorkFlow(Long.parseLong(objectId), null);
      return workFlow == null ? null : spaceOfProject(workFlow.getProjectId());
    } catch (IllegalAccessException | RuntimeException e) {
      LOG.debug("The space of process '{}' couldn't be read", objectId, e);
      return null;
    }
  }

  /**
   * @param projectId the id of a process project
   * @return the id of the space whose group all the project's managers are
   *         roles of, else null: any manager that is a user, a group outside
   *         the spaces or a role of another group gives none, and so does a
   *         missing project, a zero id included
   */
  public Long spaceOfProject(long projectId) {
    ProjectDto project;
    try {
      project = projectService.getProject(projectId);
    } catch (EntityNotFoundException e) {
      return null;
    }
    Set<String> groupIds = project == null || project.getManager() == null ? Set.of() :
                                                                           project.getManager()
                                                                                  .stream()
                                                                                  .map(ProcessSpaceScopeResolver::groupIdOf)
                                                                                  .collect(Collectors.toSet());
    if (groupIds.size() != 1) {
      return null;
    }
    String groupId = groupIds.iterator().next();
    if (!groupId.startsWith(SPACES_PREFIX)) {
      return null;
    }
    Space space = spaceService.getSpaceByGroupId(groupId);
    return space != null && StringUtils.isNumeric(space.getId()) ? Long.valueOf(space.getId()) : null;
  }

  /**
   * @param permission a project manager permission, {@code <role>:<group>} or
   *          a username
   * @return the group of the permission, empty when it names none
   */
  private static String groupIdOf(String permission) {
    int index = permission == null ? -1 : permission.indexOf(':');
    return index < 0 ? "" : permission.substring(index + 1);
  }

}
