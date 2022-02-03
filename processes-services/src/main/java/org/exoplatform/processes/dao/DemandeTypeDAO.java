package org.exoplatform.processes.dao;

import java.util.Collections;
import java.util.List;

import javax.persistence.TypedQuery;

import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;
import org.exoplatform.processes.entity.DemandeTypeEntity;

public class DemandeTypeDAO extends GenericDAOJPAImpl<DemandeTypeEntity, Long> {

  public List<DemandeTypeEntity> findAllDemandeTypesByUser(long userId, int offset, int limit) {
    TypedQuery<DemandeTypeEntity> query = getEntityManager().createNamedQuery("DemandeType.findAllDemandeTypesByUser",
                                                                              DemandeTypeEntity.class);
    query.setParameter("userId", userId);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<DemandeTypeEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

}
