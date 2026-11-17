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

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.processes.service.ProcessTranslationService;
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
import org.exoplatform.social.core.utils.MentionUtils;

import java.util.*;
import java.util.stream.Collectors;

public class NotificationUtils {
  private static final Log    LOG             = ExoLogger.getLogger(NotificationUtils.class);

  public static List<String> getProcessAdmins(String currentUser) {
    List<String> admins = new ArrayList<>();
    ListAccess<User> list;
    try {
      OrganizationService organizationService = CommonsUtils.getService(OrganizationService.class);
      list = organizationService.getUserHandler().findUsersByGroupId(ProcessesUtils.PROCESSES_GROUP);
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

  public static String getRequestCommentsLink(Long taskId) {
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

  public static List<String> getSpacesMembers(String groupId) {
    List<String> members = new ArrayList<>();
    SpaceService spaceService = CommonsUtils.getService(SpaceService.class);
    Space space = spaceService.getSpaceByGroupId(groupId);
    if (space != null) {
      members.addAll(Arrays.stream(space.getMembers()).collect(Collectors.toList()));
    }
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
      String spaceGroupId = workFlow.getParticipator().iterator().next().split(":")[1];
      receivers.addAll(getSpacesMembers(spaceGroupId));
    }
    receivers = receivers.stream().distinct().collect(Collectors.toList());
    receivers.remove(requester);
    return receivers;
  }

  /**
   * Names a request's process in a notification: its id, from which each
   * recipient reads its title in their own language, and its stored title, in
   * the default language, for the readers of the stored value. A process that
   * can't be read is named by the given title only.
   *
   * @param ctx the notification context
   * @param processesService the processes service
   * @param workflowId the request's process id, or null when unknown
   * @param fallbackTitle the title to store when the process can't be read
   */
  public static void appendProcess(NotificationContext ctx,
                                   ProcessesService processesService,
                                   Long workflowId,
                                   String fallbackTitle) {
    WorkFlow workFlow = null;
    if (workflowId != null && workflowId > 0) {
      try {
        workFlow = processesService.getWorkFlow(workflowId, null);
      } catch (Exception e) {
        LOG.debug("Can't read the process {} of a notification", workflowId, e);
      }
    }
    if (workFlow == null) {
      ctx.append(NotificationArguments.REQUEST_PROCESS, fallbackTitle);
    } else {
      ctx.append(NotificationArguments.REQUEST_PROCESS, workFlow.getTitle());
      ctx.append(NotificationArguments.WORKFLOW_ID, String.valueOf(workFlow.getId()));
    }
  }

  /**
   * Returns the title of a notification's process in a recipient's language:
   * the process the notification names by its id, read as the platform, its
   * title translated, or the title stored in the notification when it names
   * no process or its process is gone.
   *
   * @param notification the notification
   * @param language the recipient's language, as the notification channel
   *          names it, or null for the stored title
   * @return the process title
   */
  public static String getProcessTitle(NotificationInfo notification, String language) {
    return getProcessTitle(notification, toLocale(language));
  }

  /**
   * Returns the title of a notification's process in a recipient's language.
   *
   * @param notification the notification
   * @param locale the recipient's language, or null for the stored title
   * @return the process title
   * @see #getProcessTitle(NotificationInfo, String)
   */
  public static String getProcessTitle(NotificationInfo notification, Locale locale) {
    String storedTitle = notification.getValueOwnerParameter(NotificationArguments.REQUEST_PROCESS.getKey());
    String workflowId = notification.getValueOwnerParameter(NotificationArguments.WORKFLOW_ID.getKey());
    if (locale == null || StringUtils.isBlank(workflowId)) {
      return storedTitle;
    }
    try {
      WorkFlow workFlow = CommonsUtils.getService(ProcessesService.class).getWorkFlow(Long.parseLong(workflowId), null);
      if (workFlow == null) {
        return storedTitle;
      }
      return CommonsUtils.getService(ProcessTranslationService.class).getTitle(workFlow, locale);
    } catch (Exception e) {
      LOG.debug("Can't read the process {} of a notification, its stored title is used", workflowId, e);
      return storedTitle;
    }
  }

  /**
   * Reads a language as a notification channel names it.
   *
   * @param language a language tag, with a dash or an underscore
   * @return the locale, or null for a blank language
   */
  private static Locale toLocale(String language) {
    return StringUtils.isBlank(language) ? null : Locale.forLanguageTag(language.replace('_', '-'));
  }

  public static String formatMention(String comment){
    UserPortalConfigService userPortalConfigService = CommonsUtils.getService(UserPortalConfigService.class);
    String portalOwner = null;
    try {
      portalOwner = Util.getPortalRequestContext().getPortalOwner();
    } catch (Exception e){
      //default value for testing and social
      portalOwner = userPortalConfigService.getMetaPortal();
    }
    return MentionUtils.substituteUsernames(portalOwner, comment);
  }
}
