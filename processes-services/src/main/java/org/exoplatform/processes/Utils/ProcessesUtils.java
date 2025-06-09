package org.exoplatform.processes.Utils;

import java.util.*;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.model.CreatorIdentityEntity;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;

public class ProcessesUtils {

  private static final Log LOG = ExoLogger.getLogger(ProcessesUtils.class);

  public static final String PROCESSES_GROUP = "/platform/processes";

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

  public static boolean isPlatformAdmin(org.exoplatform.services.security.Identity identity) {
    UserACL userAcl = CommonsUtils.getService(UserACL.class);
    return userAcl.isAdministrator(identity);
  }

  public static boolean isProcessAdmin(org.exoplatform.services.security.Identity identity) {
    UserACL userAcl = CommonsUtils.getService(UserACL.class);
    return userAcl.isMemberOf(identity, PROCESSES_GROUP);
  }

  public static boolean isProcessManager(org.exoplatform.services.security.Identity identity, WorkFlow workFlow) {
    UserACL userAcl = CommonsUtils.getService(UserACL.class);
    return userAcl.isMemberOf(identity, Objects.requireNonNull(getProjectParentSpace(workFlow.getProjectId())).getGroupId());
  }

  public static boolean isProcessParticipant(org.exoplatform.services.security.Identity identity, WorkFlow workFlow) {
    UserACL userAcl = CommonsUtils.getService(UserACL.class);
    return getGroupsFromRequestCreators(workFlow.getRequestsCreators()).stream().anyMatch(m -> userAcl.isMemberOf(identity, m));
  }

  public static Set<String> getGroupsFromRequestCreators(List<CreatorIdentityEntity> requestsCreators) {
    SpaceService spaceService = CommonsUtils.getService(SpaceService.class);
    List<String> groups = new ArrayList<>();
    for (CreatorIdentityEntity id : requestsCreators) {
      if (id.getIdentity().getProviderId().equals("space")) {
        Space space = spaceService.getSpaceByPrettyName(id.getIdentity().getRemoteId());
        if (space != null) {
          groups.add(space.getGroupId());
        }
      } else {
        groups.add(id.getIdentity().getRemoteId());
      }
    }
    return new HashSet<>(groups);
  }

  public static <S, D> void broadcast(ListenerService listenerService, String eventName, S source, D data) {
    try {
      listenerService.broadcast(eventName, source, data);
    } catch (Exception e) {
      LOG.error("Error while broadcasting event: {}", eventName, e);
    }
  }
}
