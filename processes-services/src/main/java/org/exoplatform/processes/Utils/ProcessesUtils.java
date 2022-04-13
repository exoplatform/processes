package org.exoplatform.processes.Utils;

import org.exoplatform.commons.utils.CommonsUtils;
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

  private static final Log LOG = ExoLogger.getLogger(ProcessesUtils.class);

  public static String getUserNameByIdentityId(IdentityManager identityManager, long identityId) {
    Identity identity = identityManager.getIdentity(String.valueOf(identityId));
    return identity != null ? identity.getRemoteId() : "";
  }

  public static Space getProjectParentSpace(Long projectId) {
    ProjectService projectService = CommonsUtils.getService(ProjectService.class);
    SpaceService spaceService = CommonsUtils.getService(SpaceService.class);
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
