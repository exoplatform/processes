/*
 * Copyright (C) 2021 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.processes.rest.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.processes.model.Demande;
import org.exoplatform.processes.model.DemandeType;
import org.exoplatform.processes.rest.model.DemandeEntity;
import org.exoplatform.processes.rest.model.DemandeTypeEntity;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.task.dto.TaskDto;

public class EntityBuilder {
  private static final Log LOG = ExoLogger.getExoLogger(EntityBuilder.class);

  private EntityBuilder() {
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

  public static DemandeTypeEntity toEntity(DemandeType demandeType, String expand) {
    if (demandeType == null) {
      return null;
    }
    List<String> expandProperties =
                                  StringUtils.isBlank(expand) ? Collections.emptyList()
                                                              : Arrays.asList(StringUtils.split(expand.replaceAll(" ", ""), ","));
    // TODO: add expand properties
    return new DemandeTypeEntity(demandeType.getId(),
                                 demandeType.getTitle(),
                                 demandeType.getDescription(),
                                 demandeType.getSummary(),
                                 demandeType.getImage(),
                                 demandeType.getHelpLink(),
                                 demandeType.isEnabled(),
                                 demandeType.getCreatorId(),
                                 demandeType.getCreatedDate(),
                                 demandeType.getModifierId(),
                                 demandeType.getModifiedDate(),
                                 demandeType.getProjectId(),
                                 null);
  }

  public static List<DemandeType> fromRestEntities(List<DemandeTypeEntity> demandeTypeEntities) {
    if (CollectionUtils.isEmpty(demandeTypeEntities)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      List<DemandeType> demandeTypes = demandeTypeEntities.stream()
                                                          .map(demandeEntity -> fromEntity(demandeEntity))
                                                          .collect(Collectors.toList());
      return demandeTypes;
    }
  }

  public static List<DemandeTypeEntity> toRestEntities(List<DemandeType> demandeTypeList, String expand) {
    if (CollectionUtils.isEmpty(demandeTypeList)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      List<DemandeTypeEntity> demandeTypeEntities = demandeTypeList.stream()
                                                                   .map(demandeType -> toEntity(demandeType, expand))
                                                                   .collect(Collectors.toList());
      return demandeTypeEntities;
    }
  }

  public static DemandeEntity toDemandeEntity(ProcessesService processesService, Demande demande, String expand) {
    if (demande == null) {
      return null;
    }
    List<String> expandProperties =
            StringUtils.isBlank(expand) ? Collections.emptyList()
                    : Arrays.asList(StringUtils.split(expand.replaceAll(" ", ""), ","));

    DemandeEntity demandeEntity = new DemandeEntity(demande.getId(),
            demande.getTitle(),
            demande.getDescription(),
            demande.getStatus(),
            demande.isCompleted(),
            demande.getCreatedBy(),
            demande.getCreatedTime(),
            demande.getProjectId());
    if (expandProperties.contains("comments")) {
      // TODO: Add comments
    }

    if (expandProperties.contains("demandeType")) {
      demandeEntity.setDemandeType(toEntity(processesService.getDemandeTypeByProjectId(demande.getProjectId()),""));
    }
    return demandeEntity;
  }

  public static List<DemandeEntity> toDemandeEntityList(ProcessesService processesService,List<Demande> demandes, String expand) {
    if (CollectionUtils.isEmpty(demandes)) {
      return new ArrayList<>(Collections.emptyList());
    } else {
      List<DemandeEntity> demandeEntityList = demandes.stream()
              .map(demande -> toDemandeEntity(processesService, demande,expand))
              .collect(Collectors.toList());
      return demandeEntityList;
    }
  }


}
