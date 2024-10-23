package org.exoplatform.processes.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.processes.entity.WorkEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.*;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.GroupHandler;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dto.StatusDto;
import org.exoplatform.task.dto.TaskDto;

public class EntityMapper {
  private static final Log LOG = ExoLogger.getLogger(EntityMapper.class);

  private static final String PROCESSES_GROUP =  "/platform/processes";

  private EntityMapper() {
  }

  public static WorkFlow fromEntity(WorkFlowEntity workFlowEntity, List<String> memberships) {
    if (workFlowEntity == null) {
      return null;
    }
    return new WorkFlow(workFlowEntity.getId(),
                        workFlowEntity.getTitle(),
                        workFlowEntity.getDescription(),
                        workFlowEntity.getSummary(),
                        workFlowEntity.getImage(),
                        workFlowEntity.getHelpLink(),
                        workFlowEntity.isEnabled(),
                        workFlowEntity.getCreatorId(),
                        workFlowEntity.getCreatedDate(),
                        workFlowEntity.getModifierId(),
                        workFlowEntity.getModifiedDate(),
                        workFlowEntity.getProjectId(),
                        "",
                        getACL(workFlowEntity, memberships),
                        null,
                        new IllustrativeAttachment(workFlowEntity.getIllustrationImageId()),
                        workFlowEntity.getManager(),
                        workFlowEntity.getParticipator(),
                        false,
                        fromGroupToIdentity(workFlowEntity.getManager()));
  }

  static List<CreatorIdentityEntity> fromGroupToIdentity(Set<String> managers) {
    List<CreatorIdentityEntity> identityEntities = new ArrayList<>();
    if (managers == null) {
      return identityEntities;
    }
    SpaceService spaceService = CommonsUtils.getService(SpaceService.class);
    OrganizationService organizationService = CommonsUtils.getService(OrganizationService.class);
    GroupHandler groupHandler = organizationService.getGroupHandler();
    for (String manager : managers) {
      Space space = spaceService.getSpaceByGroupId(manager);
      if (space != null) {
        ProfileEntity profile = new ProfileEntity(
                                                  StringUtils.isNotEmpty(space.getAvatarUrl()) ? space.getAvatarUrl()
                                                                                               : "/portal/rest/v1/social/spaces/"
                                                                                                   + space.getPrettyName()
                                                                                                   + "/avatar",
                                                  space.getDisplayName());
        IdentityEntity identityEntity = new IdentityEntity("", space.getPrettyName(), "space", profile);
        identityEntities.add(new CreatorIdentityEntity(identityEntity));
      } else {
        try {
          Group group = manager.contains("/platform/") ? groupHandler.findGroupById(manager) : groupHandler.findGroupById("/platform/" + manager);
          ProfileEntity profile = new ProfileEntity(null, group.getLabel());
          IdentityEntity identityEntity = new IdentityEntity("group:" + group.getGroupName(), group.getId(), "group", profile);
          identityEntities.add(new CreatorIdentityEntity(identityEntity));
        } catch (Exception e) {
          LOG.warn("Cannot get group from managers list");
        }
      }
    }
    return identityEntities;
  }

  public static ProcessPermission getACL(WorkFlowEntity workFlowEntity, List<String> memberships) {

    if (memberships == null)
      return new ProcessPermission(true, true, true, true);
    ProcessPermission permission = new ProcessPermission(false, false, false, false);
    for (String member : memberships) {
      for (String manager : workFlowEntity.getManager()) {
        if (member.contains(manager)) {
          permission.setCanAddRequest(true);
          break;
        }
      }
      for (String participator : workFlowEntity.getParticipator()) {
        if (member.equals(participator)) {
          permission.setCanAccess(true);
          permission.setCanEdit(true);
          break;
        }
      }
      if (member.contains(PROCESSES_GROUP)) {
        permission.setCanDelete(true);
        permission.setCanEdit(true);
      }
      if (permission.isCanAddRequest() && permission.isCanAccess() && permission.isCanDelete() && permission.isCanEdit()) {
        break;
      }
    }
    return permission;
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
    return new Work(task.getId(),
                    task.getTitle(),
                    task.getDescription(),
                    task.getStatus().getName(),
                    task.isCompleted(),
                    task.getCreatedBy(),
                    task.getCreatedTime(),
                    task.getStartDate(),
                    task.getEndDate(),
                    task.getDueDate(),
                    false,
                    null,
                    task.getStatus().getProject().getId());
  }

  public static List<Work> tasksToWorkList(List<TaskDto> tasks) {
    if (CollectionUtils.isEmpty(tasks)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      return tasks.stream().map(EntityMapper::taskToWork).collect(Collectors.toList());
    }
  }

}
