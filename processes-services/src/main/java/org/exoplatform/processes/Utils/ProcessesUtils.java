package org.exoplatform.processes.Utils;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;

public class ProcessesUtils {

  public static final String PROCESSES_GROUP         = "/platform/processes";

  /**
   * The membership type of a space's administrators: in a process's managing
   * space, they are the process managers
   */
  public static final String MANAGER_MEMBERSHIP_TYPE = "manager";

  private static final Log   LOG                     = ExoLogger.getLogger(ProcessesUtils.class);

  /**
   * Checks whether a user administers every process: a member of
   * /platform/processes or a platform administrator creates, edits and deletes
   * any process, and manages any request
   *
   * @param userACL the platform ACL service
   * @param aclIdentity the user's security identity
   * @return true when the user administers every process
   */
  public static boolean isProcessesAdministrator(UserACL userACL, org.exoplatform.services.security.Identity aclIdentity) {
    return aclIdentity != null && (aclIdentity.isMemberOf(PROCESSES_GROUP) || userACL.isAdministrator(aclIdentity));
  }

  public static String getUserNameByIdentityId(IdentityManager identityManager, long identityId) {
    Identity identity = identityManager.getIdentity(String.valueOf(identityId));
    return identity != null ? identity.getRemoteId() : "";
  }

  public static Space getProjectParentSpace(Long projectId) {
    return getProjectParentSpace(CommonsUtils.getService(ProjectService.class),
                                 CommonsUtils.getService(SpaceService.class),
                                 projectId);
  }

  public static Space getProjectParentSpace(ProjectService projectService, SpaceService spaceService, Long projectId) {
    try {
      ProjectDto projectDto = projectService.getProject(projectId);
      boolean isProjectInSpace = projectDto.getManager().stream().anyMatch(manager -> manager.contains("/spaces/"));
      if (isProjectInSpace) {
        String participator = projectDto.getParticipator().iterator().next();
        String groupId = participator.substring(participator.indexOf(":") + 1);
        return spaceService.getSpaceByGroupId(groupId);
      }
    } catch (EntityNotFoundException e) {
      LOG.error("Project Not found", e);
    }
    return null;
  }

  public static <S, D> void broadcast(ListenerService listenerService, String eventName, S source, D data) {
    try {
      listenerService.broadcast(eventName, source, data);
    } catch (Exception e) {
      LOG.error("Error while broadcasting event: {}", eventName, e);
    }
  }
}
