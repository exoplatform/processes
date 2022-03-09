package org.exoplatform.processes.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;

import org.exoplatform.processes.entity.WorkEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.task.dto.TaskDto;

public class EntityMapper {
  private static final Log LOG = ExoLogger.getLogger(EntityMapper.class);

  private EntityMapper() {
  }

  public static WorkFlow fromEntity(WorkFlowEntity workFlowEntity) {
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
                           null);
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
                    fromEntity(workEntity.getWorkFlow()));
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
                                                          .map(workEntity -> fromEntity(workEntity))
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

  public static  TaskDto workToTask(Work work) {
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
      List<Work> workList = tasks.stream()
              .map(EntityMapper::taskToWork)
              .collect(Collectors.toList());
      return workList;
    }
  }

}
