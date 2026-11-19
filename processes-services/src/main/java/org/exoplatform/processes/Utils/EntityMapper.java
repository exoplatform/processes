package org.exoplatform.processes.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.processes.entity.WorkEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.*;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.GroupHandler;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.security.MembershipEntry;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dto.StatusDto;
import org.exoplatform.task.dto.TaskDto;

public class EntityMapper {
  private static final Log LOG = ExoLogger.getLogger(EntityMapper.class);

  private EntityMapper() {
  }

  /**
   * Maps a stored process to its Service object, with the permissions the given
   * memberships grant on it.
   *
   * @param workFlowEntity the stored process
   * @param memberships the user's queried memberships, null for a processes
   *          administrator
   * @return the process, or null when none is given
   */
  public static WorkFlow fromEntity(WorkFlowEntity workFlowEntity, List<String> memberships) {
    if (workFlowEntity == null) {
      return null;
    }
    return new WorkFlow(workFlowEntity.getId(),
                        workFlowEntity.getTitle(),
                        workFlowEntity.getDescription(),
                        workFlowEntity.getSummary(),
                        // Only an icon name reads back: another value an API
                        // client stored shows no icon, and the next save of
                        // the process, which sends none, clears it
                        ProcessesUtils.isProcessIcon(workFlowEntity.getImage()) ? workFlowEntity.getImage() : null,
                        workFlowEntity.getHelpLink(),
                        workFlowEntity.isEnabled(),
                        workFlowEntity.getCreatorId(),
                        workFlowEntity.getCreatedDate(),
                        workFlowEntity.getModifierId(),
                        workFlowEntity.getModifiedDate(),
                        workFlowEntity.getProjectId(),
                        workFlowEntity.getSpaceId() == null ? "" : String.valueOf(workFlowEntity.getSpaceId()),
                        getACL(workFlowEntity, memberships),
                        null,
                        new IllustrativeAttachment(workFlowEntity.getIllustrationImageId()),
                        workFlowEntity.getManager(),
                        workFlowEntity.getParticipator(),
                        false,
                        fromGroupToIdentity(workFlowEntity.getManager()));
  }

  /**
   * Reads the request makers of a process as the identities its editor shows:
   * a space or a group the platform finds. An entry that is neither, such as a
   * user name, a membership or a deleted group, is left out.
   *
   * @param managers the stored request makers of the process
   * @return the identities, an empty list when none is given
   */
  static List<CreatorIdentityEntity> fromGroupToIdentity(Set<String> managers) {
    List<CreatorIdentityEntity> identityEntities = new ArrayList<>();
    if (managers == null) {
      return identityEntities;
    }
    for (String manager : managers) {
      CreatorIdentityEntity identityEntity = toCreatorIdentity(manager);
      if (identityEntity != null) {
        identityEntities.add(identityEntity);
      }
    }
    return identityEntities;
  }

  /**
   * Tells whether the editor of a process shows one of its stored request
   * makers, so that a save, which sends only what the editor shows, may
   * remove it.
   *
   * @param manager a stored request maker
   * @return true when it reads as a space or a group the platform finds
   * @throws IllegalStateException when the group store fails to answer: the
   *           save can't tell whether the user removed it, so it is not saved
   */
  public static boolean isDisplayableCreator(String manager) {
    if (StringUtils.isBlank(manager)) {
      return false;
    }
    if (toSpaceIdentity(manager) != null) {
      return true;
    }
    try {
      return toGroupIdentity(manager) != null;
    } catch (Exception e) {
      throw new IllegalStateException("The request maker " + manager + " of a process could not be read as a group", e);
    }
  }

  /**
   * Reads one stored request maker as the identity the editor shows, a group
   * the group store fails to read being left out.
   *
   * @param manager a stored request maker
   * @return the space or group identity, or null when it is neither a space
   *         nor a group the platform finds, or the group can't be read
   */
  private static CreatorIdentityEntity toCreatorIdentity(String manager) {
    if (StringUtils.isBlank(manager)) {
      return null;
    }
    CreatorIdentityEntity spaceIdentity = toSpaceIdentity(manager);
    if (spaceIdentity != null) {
      return spaceIdentity;
    }
    try {
      return toGroupIdentity(manager);
    } catch (Exception e) {
      LOG.warn("Cannot read the request maker {} of a process as a group: {}", manager, e.getMessage());
      LOG.debug("Cannot read the request maker {} of a process as a group", manager, e);
      return null;
    }
  }

  /**
   * Reads one stored request maker as the space whose group it names.
   *
   * @param manager a stored request maker
   * @return the space identity, or null when it names no space
   */
  private static CreatorIdentityEntity toSpaceIdentity(String manager) {
    SpaceService spaceService = CommonsUtils.getService(SpaceService.class);
    Space space = spaceService.getSpaceByGroupId(manager);
    if (space == null) {
      return null;
    }
    ProfileEntity profile = new ProfileEntity(
                                              StringUtils.isNotEmpty(space.getAvatarUrl()) ? space.getAvatarUrl()
                                                                                           : "/portal/rest/v1/social/spaces/"
                                                                                               + space.getPrettyName()
                                                                                               + "/avatar",
                                              space.getDisplayName());
    IdentityEntity identityEntity = new IdentityEntity("", space.getPrettyName(), "space", profile);
    return new CreatorIdentityEntity(identityEntity);
  }

  /**
   * Reads one stored request maker as the group it names.
   *
   * @param manager a stored request maker
   * @return the group identity, or null when no group has that id
   * @throws Exception when the group store fails to answer
   */
  private static CreatorIdentityEntity toGroupIdentity(String manager) throws Exception {
    GroupHandler groupHandler = CommonsUtils.getService(OrganizationService.class).getGroupHandler();
    Group group = groupHandler.findGroupById(manager);
    if (group == null) {
      return null;
    }
    ProfileEntity profile = new ProfileEntity(null, group.getLabel());
    IdentityEntity identityEntity = new IdentityEntity("group:" + group.getGroupName(), group.getId(), "group", profile);
    return new CreatorIdentityEntity(identityEntity);
  }

  public static ProcessPermission getACL(WorkFlowEntity workFlowEntity, List<String> memberships) {

    if (memberships == null)
      return new ProcessPermission(true, true, true, true);
    ProcessPermission permission = new ProcessPermission(false, false, false, false);
    for (String member : memberships) {
      for (String manager : workFlowEntity.getManager()) {
        if (isGranted(member, manager)) {
          permission.setCanAddRequest(true);
          break;
        }
      }
      for (String participator : workFlowEntity.getParticipator()) {
        if (isGranted(member, participator)) {
          permission.setCanAccess(true);
          if (isManagerMembership(participator)) {
            // The managing space's administrators are the process managers
            permission.setCanEdit(true);
            permission.setCanDelete(true);
            permission.setCanAddRequest(true);
          }
        }
      }
      if (isGranted(member, ProcessesUtils.PROCESSES_GROUP)) {
        permission.setCanDelete(true);
        permission.setCanEdit(true);
      }
      if (permission.isCanAddRequest() && permission.isCanAccess() && permission.isCanDelete() && permission.isCanEdit()) {
        break;
      }
    }
    return permission;
  }

  /**
   * Checks whether a user's membership grants a workflow permission entry
   *
   * @param member a user's membership, 'type:/group', or the user's name
   * @param permission a permission entry: a group id, which any membership type
   *          of that group grants, a 'type:/group' membership, where the type
   *          '*' stands for any type, or a user name
   * @return true when the entry designates exactly that group, membership or
   *         user
   */
  static boolean isGranted(String member, String permission) {
    if (StringUtils.isBlank(member) || StringUtils.isBlank(permission)) {
      return false;
    }
    MembershipEntry memberEntry = MembershipEntry.parse(member);
    if (memberEntry == null) {
      return member.equals(permission);
    }
    return memberEntry.equals(toMembershipEntry(permission));
  }

  /**
   * @param permission a workflow permission entry
   * @return true when the entry is the membership of a group's administrators,
   *         not of all its members
   */
  static boolean isManagerMembership(String permission) {
    if (StringUtils.isBlank(permission)) {
      return false;
    }
    MembershipEntry permissionEntry = toMembershipEntry(permission);
    return permissionEntry != null && ProcessesUtils.MANAGER_MEMBERSHIP_TYPE.equals(permissionEntry.getMembershipType());
  }

  private static MembershipEntry toMembershipEntry(String permission) {
    return permission.startsWith("/") ? new MembershipEntry(permission) : MembershipEntry.parse(permission);
  }

  public static WorkFlow fromEntity(WorkFlowEntity workFlowEntity,
                                    IllustrativeAttachment illustrativeAttachment,
                                    List<String> memberships) {
    if (workFlowEntity == null) {
      return null;
    }
    WorkFlow workFlow = fromEntity(workFlowEntity, memberships);
    if (illustrativeAttachment != null) {
      workFlow.setIllustrativeAttachment(illustrativeAttachment);
    }
    return workFlow;
  }

  public static Work fromEntity(WorkEntity workEntity) {
    if (workEntity == null) {
      return null;
    }
    return new Work(workEntity.getId(),
                    workEntity.getTitle(),
                    workEntity.getDescription(),
                    workEntity.getCreatorId(),
                    workEntity.getCreatedDate(),
                    workEntity.getModifiedDate(),
                    workEntity.getTaskId(),
                    workEntity.getIsDraft(),
                    fromEntity(workEntity.getWorkFlow(), null));
  }

  public static WorkStatus toWorkStatus(StatusDto statusDto) {
    if (statusDto == null) {
      return null;
    }
    return new WorkStatus(statusDto.getId(), statusDto.getName(), statusDto.getRank());
  }

  public static List<WorkStatus> toWorkStatuses(List<StatusDto> statuses) {
    if (CollectionUtils.isEmpty(statuses)) {
      return new ArrayList<>(Collections.emptyList());
    }
    return statuses.stream().map(EntityMapper::toWorkStatus).collect(Collectors.toList());
  }

  /**
   * Maps a process to the entity the storage saves.
   *
   * @param workFlow the process
   * @return the entity, or null when no process is given
   * @throws IllegalArgumentException when the process's space id is not a
   *           number
   */
  public static WorkFlowEntity toEntity(WorkFlow workFlow) {
    if (workFlow == null) {
      return null;
    }
    WorkFlowEntity workFlowEntity = new WorkFlowEntity();

    workFlowEntity.setId(workFlow.getId());
    workFlowEntity.setTitle(workFlow.getTitle());
    workFlowEntity.setDescription(workFlow.getDescription());
    workFlowEntity.setSummary(workFlow.getSummary());
    workFlowEntity.setImage(workFlow.getImage());
    workFlowEntity.setHelpLink(workFlow.getHelpLink());
    workFlowEntity.setEnabled(workFlow.isEnabled());
    workFlowEntity.setCreatorId(workFlow.getCreatorId());
    workFlowEntity.setCreatedDate(workFlow.getCreatedDate());
    workFlowEntity.setModifierId(workFlow.getModifierId());
    workFlowEntity.setModifiedDate(workFlow.getModifiedDate());
    workFlowEntity.setProjectId(workFlow.getProjectId());
    workFlowEntity.setSpaceId(toSpaceId(workFlow.getSpaceId()));
    workFlowEntity.setManager(workFlow.getManager());
    workFlowEntity.setParticipator(workFlow.getParticipator());

    if (workFlow.getIllustrativeAttachment() != null) {
      workFlowEntity.setIllustrationImageId(workFlow.getIllustrativeAttachment().getId());
    }
    return workFlowEntity;
  }

  public static WorkEntity toEntity(Work work) {
    if (work == null) {
      return null;
    }
    WorkEntity workEntity = new WorkEntity();

    workEntity.setId(work.getId());
    workEntity.setTitle(work.getTitle());
    workEntity.setDescription(work.getDescription());
    workEntity.setCreatorId(work.getCreatorId());
    workEntity.setCreatedDate(work.getCreatedDate());
    workEntity.setModifiedDate(work.getModifiedDate());
    workEntity.setTaskId(work.getTaskId());
    workEntity.setIsDraft(work.getIsDraft());
    workEntity.setWorkFlow(toEntity(work.getWorkFlow()));
    return workEntity;
  }

  public static List<WorkFlow> fromWorkflowEntities(List<WorkFlowEntity> workFlowEntities) {
    if (CollectionUtils.isEmpty(workFlowEntities)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      List<WorkFlow> workFlows = workFlowEntities.stream()
                                                 .map(workflowEntity -> fromEntity(workflowEntity, null))
                                                 .collect(Collectors.toList());
      return workFlows;
    }
  }

  public static List<Work> fromWorkEntities(List<WorkEntity> workDraftEntities) {
    if (CollectionUtils.isEmpty(workDraftEntities)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      return workDraftEntities.stream().map(EntityMapper::fromEntity).collect(Collectors.toList());
    }
  }

  public static List<WorkFlowEntity> fromWorkFlows(List<WorkFlow> workFlowList) {
    if (CollectionUtils.isEmpty(workFlowList)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      List<WorkFlowEntity> workFlowEntities = workFlowList.stream()
                                                          .map(workFlow -> toEntity(workFlow))
                                                          .collect(Collectors.toList());
      return workFlowEntities;
    }
  }

  /**
   * Reads the id of a process's managing space as it is stored.
   *
   * @param spaceId the space id a {@link WorkFlow} carries, blank when unknown
   * @return the space id, or null when it is blank
   * @throws IllegalArgumentException when the id is not a number
   */
  public static Long toSpaceId(String spaceId) {
    if (StringUtils.isBlank(spaceId)) {
      return null;
    }
    try {
      return Long.parseLong(spaceId.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("processes.space.invalidId");
    }
  }

  public static TaskDto workToTask(Work work) {
    if (work == null) {
      return null;
    }
    TaskDto newTask = new TaskDto();
    newTask.setId(work.getId());
    newTask.setTitle(work.getTitle());
    newTask.setDescription(work.getDescription());
    newTask.setCompleted(false);
    newTask.setCreatedBy(work.getCreatedBy());
    newTask.setCreatedTime(work.getCreatedDate());
    return newTask;
  }

  public static Work taskToWork(TaskDto task) {
    if (task == null) {
      return null;
    }
    // A task outside any project, such as a personal task, has no status
    StatusDto status = task.getStatus();
    return new Work(task.getId(),
                    task.getTitle(),
                    task.getDescription(),
                    status == null ? null : status.getName(),
                    task.isCompleted(),
                    task.getCreatedBy(),
                    task.getCreatedTime(),
                    task.getStartDate(),
                    task.getEndDate(),
                    task.getDueDate(),
                    false,
                    null,
                    status == null || status.getProject() == null ? 0 : status.getProject().getId());
  }

  public static List<Work> tasksToWorkList(List<TaskDto> tasks) {
    if (CollectionUtils.isEmpty(tasks)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      return tasks.stream().map(EntityMapper::taskToWork).collect(Collectors.toList());
    }
  }

}
