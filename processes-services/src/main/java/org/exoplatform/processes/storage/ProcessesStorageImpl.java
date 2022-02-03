package org.exoplatform.processes.storage;

import java.util.Date;
import java.util.List;

import org.exoplatform.processes.Utils.EntityMapper;
import org.exoplatform.processes.dao.DemandeTypeDAO;
import org.exoplatform.processes.entity.DemandeTypeEntity;
import org.exoplatform.processes.model.DemandeType;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

public class ProcessesStorageImpl implements ProcessesStorage {

  private static final Log LOG = ExoLogger.getLogger(ProcessesStorageImpl.class);

  private DemandeTypeDAO   demandeTypeDAO;

  private IdentityManager  identityManager;

  public ProcessesStorageImpl(DemandeTypeDAO demandeTypeDAO, IdentityManager identityManager) {
    this.demandeTypeDAO = demandeTypeDAO;
    this.identityManager = identityManager;
  }

  @Override
  public List<DemandeType> getDemandeTypes(ProcessesFilter filter, int offset, int limit, long userIdentityId) {
    // TODO: add filter props to the request
    return EntityMapper.fromEntities(demandeTypeDAO.findAllChallengesByUser(userIdentityId, offset, limit));
  }

  @Override
  public DemandeType getDemandeTypeById(long id) {
    return EntityMapper.fromEntity(demandeTypeDAO.find(id));
  }

  @Override
  public DemandeType saveDemandeType(DemandeType demandeType, long userId) throws IllegalArgumentException {
    if (demandeType == null) {
      throw new IllegalArgumentException("saveChallenge argument is null");
    }
    Identity identity = identityManager.getIdentity(String.valueOf(userId));
    if (identity == null) {
      throw new IllegalArgumentException("identity is not exist");
    }
    DemandeTypeEntity demandeTypeEntity = EntityMapper.toEntity(demandeType);
    if (demandeType.getId() == 0) {
      demandeTypeEntity.setId(null);
      demandeTypeEntity.setCreatedDate(new Date());
      demandeTypeEntity.setCreatorId(userId);
      demandeTypeEntity = demandeTypeDAO.create(demandeTypeEntity);
    } else {
      demandeTypeEntity.setModifiedDate(new Date());
      demandeTypeEntity.setModifierId(userId);
      demandeTypeEntity = demandeTypeDAO.update(demandeTypeEntity);
    }

    return EntityMapper.fromEntity(demandeTypeEntity);
  }
}
