package org.exoplatform.processes.notification.utils;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.service.LinkProvider;

import java.util.*;

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

  public static <S, D> void broadcast(ListenerService listenerService, String eventName, S source, D data) {
    try {
      listenerService.broadcast(eventName, source, data);
    } catch (Exception e) {
      LOG.error("Error while broadcasting event: {}", eventName, e);
    }
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
}
