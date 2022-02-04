package org.exoplatform.processes.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;

import org.exoplatform.processes.entity.DemandeTypeEntity;
import org.exoplatform.processes.model.Demande;
import org.exoplatform.processes.model.DemandeType;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.task.dto.TaskDto;

public class EntityMapper {
  private static final Log LOG = ExoLogger.getLogger(EntityMapper.class);

  private EntityMapper() {
  }

  public static DemandeType fromEntity(DemandeTypeEntity demandeTypeEntity) {
    if (demandeTypeEntity == null) {
      return null;
    }
    return new DemandeType(demandeTypeEntity.getId(),
                           demandeTypeEntity.getTitle(),
                           demandeTypeEntity.getDescription(),
                           demandeTypeEntity.getSummary(),
                           demandeTypeEntity.getImage(),
                           demandeTypeEntity.getHelpLink(),
                           demandeTypeEntity.isEnabled(),
                           demandeTypeEntity.getCreatorId(),
                           demandeTypeEntity.getCreatedDate(),
                           demandeTypeEntity.getModifierId(),
                           demandeTypeEntity.getModifiedDate(),
                           demandeTypeEntity.getProjectId(),
                           null);
  }

  public static DemandeTypeEntity toEntity(DemandeType demandeType) {
    if (demandeType == null) {
      return null;
    }
    DemandeTypeEntity demandeTypeEntity = new DemandeTypeEntity();

    demandeTypeEntity.setId(demandeType.getId());
    demandeTypeEntity.setTitle(demandeType.getTitle());
    demandeTypeEntity.setDescription(demandeType.getDescription());
    demandeTypeEntity.setSummary(demandeType.getSummary());
    demandeTypeEntity.setImage(demandeType.getImage());
    demandeTypeEntity.setHelpLink(demandeType.getHelpLink());
    demandeTypeEntity.setEnabled(demandeType.isEnabled());
    demandeTypeEntity.setCreatorId(demandeType.getCreatorId());
    demandeTypeEntity.setCreatedDate(demandeType.getCreatedDate());
    demandeTypeEntity.setModifierId(demandeType.getModifierId());
    demandeTypeEntity.setModifiedDate(demandeType.getModifiedDate());
    demandeTypeEntity.setProjectId(demandeType.getProjectId());
    return demandeTypeEntity;
  }

  public static List<DemandeType> fromEntities(List<DemandeTypeEntity> demandeTypeEntities) {
    if (CollectionUtils.isEmpty(demandeTypeEntities)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      List<DemandeType> demandeTypes = demandeTypeEntities.stream()
                                                          .map(demandeEntity -> fromEntity(demandeEntity))
                                                          .collect(Collectors.toList());
      return demandeTypes;
    }
  }

  public static List<DemandeTypeEntity> fromDemandeTypes(List<DemandeType> demandeTypeList) {
    if (CollectionUtils.isEmpty(demandeTypeList)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      List<DemandeTypeEntity> demandeTypeEntities = demandeTypeList.stream()
                                                                   .map(demandeType -> toEntity(demandeType))
                                                                   .collect(Collectors.toList());
      return demandeTypeEntities;
    }
  }

  public static Demande tasktoDemande(TaskDto task) {
    if (task == null) {
      return null;
    }
    return new Demande(task.getId(),
            task.getTitle(),
            task.getDescription(),
            task.getStatus().getName(),
            task.isCompleted(),
            task.getCreatedBy(),
            task.getCreatedTime(),
            task.getStartDate(),
            task.getEndDate(),
            task.getDueDate(),
            task.getStatus().getProject().getId());
  }

  public static List<Demande> taskstoDemandeList(List<TaskDto> tasks) {
    if (CollectionUtils.isEmpty(tasks)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      List<Demande> demandeList = tasks.stream()
              .map(task -> tasktoDemande(task))
              .collect(Collectors.toList());
      return demandeList;
    }
  }

}
