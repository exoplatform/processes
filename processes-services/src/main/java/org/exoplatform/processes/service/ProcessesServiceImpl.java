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
package org.exoplatform.processes.service;

import java.util.List;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.model.DemandeType;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.manager.IdentityManager;

public class ProcessesServiceImpl implements ProcessesService {

  private static final Log LOG = ExoLogger.getLogger(ProcessesServiceImpl.class);

  private IdentityManager  identityManager;

  private ProcessesStorage processesStorage;

  public ProcessesServiceImpl(ProcessesStorage processesStorage, IdentityManager identityManager) {
    this.identityManager = identityManager;
    this.processesStorage = processesStorage;
  }

  @Override
  public List<DemandeType> getDemandeTypes(ProcessesFilter filter,
                                           int offset,
                                           int limit,
                                           long userIdentityId) throws IllegalAccessException, ObjectNotFoundException {
    return processesStorage.getDemandeTypes(filter, offset, limit, userIdentityId);
  }

  @Override
  public DemandeType createDemandeType(DemandeType demandeType, long userId) throws IllegalAccessException {
    if (demandeType == null) {
      throw new IllegalArgumentException("demandeType is mandatory");
    }
    if (demandeType.getId() != 0) {
      throw new IllegalArgumentException("demandeType id must be equal to 0");
    }

    // TODO check permissions to create types

    return processesStorage.saveDemandeType(demandeType, userId);
  }

  @Override
  public DemandeType updateDemandeType(DemandeType demandeType, long userId) throws IllegalArgumentException,
                                                                             ObjectNotFoundException,
                                                                             IllegalAccessException {
    if (demandeType == null) {
      throw new IllegalArgumentException("Challenge is mandatory");
    }
    if (demandeType.getId() == 0) {
      throw new IllegalArgumentException("challenge id must not be equal to 0");
    }
    // TODO check permissions to update types

    DemandeType oldDemandeType = processesStorage.getDemandeTypeById(demandeType.getId());
    if (oldDemandeType == null) {
      throw new ObjectNotFoundException("oldDemandeType is not exist");
    }
    if (oldDemandeType.equals(demandeType)) {
      throw new IllegalArgumentException("there are no changes to save");
    }
    return processesStorage.saveDemandeType(demandeType, userId);
  }

}
