package org.exoplatform.processes.dao;

import java.util.Collections;
import java.util.List;

import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;
import org.exoplatform.processes.entity.WorkFlowEntity;

public class WorkFlowDAO extends GenericDAOJPAImpl<WorkFlowEntity, Long> {

  public List<WorkFlowEntity> findAllWorkFlowsByUser(long userId, int offset, int limit) {
    TypedQuery<WorkFlowEntity> query = getEntityManager().createNamedQuery("WorkFlow.findAllWorkFlowsByUser",
            WorkFlowEntity.class);
    query.setParameter("userId", userId);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<WorkFlowEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }
  public List<WorkFlowEntity> findEnabledWorkFlowsByUser(long userId, int offset, int limit) {
    TypedQuery<WorkFlowEntity> query = getEntityManager().createNamedQuery("WorkFlow.findEnabledWorkFlowsByUser",
            WorkFlowEntity.class);
    query.setParameter("userId", userId);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<WorkFlowEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public List<WorkFlowEntity> findAllWorkFlows(int offset, int limit) {
    TypedQuery<WorkFlowEntity> query = getEntityManager().createNamedQuery("WorkFlow.findAllWorkFlows",
            WorkFlowEntity.class);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<WorkFlowEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }


  public List<WorkFlowEntity> findEnabledWorkFlows(int offset, int limit) {
    TypedQuery<WorkFlowEntity> query = getEntityManager().createNamedQuery("WorkFlow.findEnabledWorkFlows",
            WorkFlowEntity.class);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<WorkFlowEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public List<WorkFlowEntity> findDisabledWorkFlows(int offset, int limit) {
    TypedQuery<WorkFlowEntity> query = getEntityManager().createNamedQuery("WorkFlow.findDisabledWorkFlows",
            WorkFlowEntity.class);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<WorkFlowEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public WorkFlowEntity getWorkFlowByProjectId(long projectId) {
    TypedQuery<WorkFlowEntity> query = getEntityManager().createNamedQuery("WorkFlow.getWorkFlowByProjectId",
            WorkFlowEntity.class);
    query.setParameter("projectId", projectId);
    try {
      return query.getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

}
