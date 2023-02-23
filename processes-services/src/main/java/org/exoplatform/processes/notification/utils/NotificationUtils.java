/*
 * Copyright (C) 2022 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.processes.notification.utils;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.service.LinkProvider;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import java.util.*;
import java.util.stream.Collectors;

public class NotificationUtils {
  private static final Log    LOG             = ExoLogger.getLogger(NotificationUtils.class);

  private static final String PROCESSES_GROUP = "/platform/processes";

  public static List<String> getProcessAdmins(String currentUser) {
    List<String> admins = new ArrayList<>();
    ListAccess<User> list;
    try {
      OrganizationService organizationService = CommonsUtils.getService(OrganizationService.class);
      list = organizationService.getUserHandler().findUsersByGroupId(PROCESSES_GROUP);
      Arrays.stream(list.load(0, list.getSize()))
            .map(User::getUserName)
            .filter(userName -> !userName.equals(currentUser))
            .forEach(userName -> {
              admins.add(userName);
            });
    } catch (Exception e) {
      LOG.error("Error while getting process admins", e);
    }
    return admins;
  }

  public static Profile getUserProfile(String userName) {
    IdentityManager identityManager = CommonsUtils.getService(IdentityManager.class);
    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, userName);
    if (identity != null) {
      return identity.getProfile();
    }
    return null;
  }

  public static String getProcessLink(Long projectId) {
    StringBuilder stringBuilder = new StringBuilder();
    String portalOwner = CommonsUtils.getCurrentPortalOwner();
    String domain = CommonsUtils.getCurrentDomain();
    stringBuilder.append(domain)
                 .append("/")
                 .append(LinkProvider.getPortalName(null))
                 .append("/")
                 .append(portalOwner)
                 .append("/tasks/projectDetail/")
                 .append(projectId);
    return stringBuilder.toString();
  }

  public static String getRequestLink(Long taskId) {
    StringBuilder stringBuilder = new StringBuilder();
    String portalOwner = CommonsUtils.getCurrentPortalOwner();
    String domain = CommonsUtils.getCurrentDomain();
    stringBuilder.append(domain)
                 .append("/")
                 .append(LinkProvider.getPortalName(null))
                 .append("/")
                 .append(portalOwner)
                 .append("/tasks/taskDetail/")
                 .append(taskId);
    return stringBuilder.toString();
  }

  public static Object getRequestCommentsLink(Long taskId) {
    StringBuilder stringBuilder = new StringBuilder();
    String portalOwner = CommonsUtils.getCurrentPortalOwner();
    String domain = CommonsUtils.getCurrentDomain();
    stringBuilder.append(domain)
            .append("/")
            .append(LinkProvider.getPortalName(null))
            .append("/")
            .append(portalOwner)
            .append("/processes/myRequests/requestDetails/")
            .append(taskId)
            .append("/comments");
    return stringBuilder.toString();
  }

  /**
   * retrieves the members of spaces
   **/

  public static List<String> getSpacesMembers(Set<String> spacesGroupsId) {
    List<String> members = new ArrayList<>();
    SpaceService spaceService = CommonsUtils.getService(SpaceService.class);
    spacesGroupsId.forEach(groupId -> {
      Space space = spaceService.getSpaceByGroupId(groupId);
      if (space != null) {
        members.addAll(Arrays.stream(space.getMembers()).collect(Collectors.toList()));
      }
    });
    return members;
  }

  public static WorkFlow getWorkFlowByProjectId(long workflowProjectId) {
    ProcessesService processesService = CommonsUtils.getService(ProcessesService.class);
    return processesService != null ? processesService.getWorkFlowByProjectId(workflowProjectId) : null;
  }

  public static List<String> getReceivers(long workflowProjectId, String requester, boolean withAdministrators) {
    List<String> receivers = new ArrayList<>();
    if (withAdministrators) {
      receivers.addAll(getProcessAdmins(requester));
    }
    WorkFlow workFlow = getWorkFlowByProjectId(workflowProjectId);
    if (workFlow != null) {
      receivers.addAll(getSpacesMembers(workFlow.getManager()));
    }
    receivers = receivers.stream().distinct().collect(Collectors.toList());
    receivers.remove(requester);
    return receivers;
  }
}
