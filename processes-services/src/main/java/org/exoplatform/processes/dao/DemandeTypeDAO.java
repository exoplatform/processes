package org.exoplatform.processes.dao;

import java.util.Collections;
import java.util.List;

import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;
import org.exoplatform.processes.entity.DemandeTypeEntity;
import org.exoplatform.processes.model.DemandeType;

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
  public List<DemandeTypeEntity> findEnabledDemandeTypesByUser(long userId, int offset, int limit) {
    TypedQuery<DemandeTypeEntity> query = getEntityManager().createNamedQuery("DemandeType.findEnabledDemandeTypesByUser",
                                                                              DemandeTypeEntity.class);
    query.setParameter("userId", userId);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<DemandeTypeEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public List<DemandeTypeEntity> findAllDemandeTypes(int offset, int limit) {
    TypedQuery<DemandeTypeEntity> query = getEntityManager().createNamedQuery("DemandeType.findAllDemandeTypes",
                                                                              DemandeTypeEntity.class);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<DemandeTypeEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }


  public List<DemandeTypeEntity> findEnabledDemandeTypes(int offset, int limit) {
    TypedQuery<DemandeTypeEntity> query = getEntityManager().createNamedQuery("DemandeType.findEnabledDemandeTypes",
                                                                              DemandeTypeEntity.class);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<DemandeTypeEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }


  public DemandeTypeEntity getDemandeTypeByProjectId(long projectId) {
    TypedQuery<DemandeTypeEntity> query = getEntityManager().createNamedQuery("DemandeType.getDemandeTypeByProjectId",
                                                                              DemandeTypeEntity.class);
    query.setParameter("projectId", projectId);
    try {
      return query.getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

}
