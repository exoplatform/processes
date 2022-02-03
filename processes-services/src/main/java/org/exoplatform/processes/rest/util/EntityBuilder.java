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

import org.exoplatform.processes.model.DemandeType;
import org.exoplatform.processes.rest.model.DemandeTypeEntity;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class EntityBuilder {
  private static final Log LOG = ExoLogger.getExoLogger(EntityBuilder.class);

  private EntityBuilder() {
  }

  public static DemandeType fromEntity(DemandeTypeEntity demandeTypeEntity) {
    if (demandeTypeEntity == null) {
      return null;
    }
    return new DemandeType(demandeTypeEntity.getId(),
                           demandeTypeEntity.getName(),
                           demandeTypeEntity.getDescription(),
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
                                 demandeType.getName(),
                                 demandeType.getDescription(),
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

}
