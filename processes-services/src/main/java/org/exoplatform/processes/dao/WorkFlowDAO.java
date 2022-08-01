package org.exoplatform.processes.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;

import org.apache.commons.lang.StringUtils;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.ProcessesFilter;

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

  private String  buildWorkflowQuery(ProcessesFilter processesFilter, List<String> memberships) {
    String q = processesFilter.getQuery();
    Boolean enabled = processesFilter.getEnabled();
    Boolean manager = processesFilter.getManager();
    String queryString = "SELECT DISTINCT workFlow FROM WorkFlow workFlow";
    if(memberships != null) {
      queryString = queryString + " LEFT JOIN workFlow.manager manager "
              + " LEFT JOIN workFlow.participator participator ";
    }
    if(StringUtils.isNotEmpty(q) || memberships != null || enabled != null){
      queryString = queryString + " WHERE";
      if (StringUtils.isNotEmpty(q)){
        queryString = queryString + " ( workFlow.title like '%" + q + "%'";
        queryString = queryString + " OR workFlow.description like '%" + q + "%'";
        queryString = queryString + " OR workFlow.summary like '%" + q + "%' )";
        queryString = queryString + " AND";
      }
      if ( enabled != null){
        queryString = queryString + " workFlow.enabled = " + enabled;
        queryString = queryString + " AND";
      }
      if ( memberships != null){
        queryString = queryString + " manager IN ('"+String.join("','", getMembersShipGroup(memberships))+"') ";
        if ( Boolean.FALSE.equals(manager)){
          queryString = queryString + " OR participator IN ('"+String.join("','", memberships)+"') ";
        }
      }
      if (queryString.endsWith(" AND")) {
        queryString = queryString.substring(0, queryString.length() - 4);
      }
    }

    return queryString;
  }

  private List<String> getMembersShipGroup(List<String> memberships) {
    return memberships.stream()
                             .map(s -> (!s.startsWith("manager:/") && !s.startsWith("member:/")) ? s : s.replace("manager:","").replace("member:","") )
                             .collect(Collectors.toList());
  }

  private Query buildWorkflowQueryCriteria(ProcessesFilter processesFilter) {
    String q = processesFilter.getQuery();
    Boolean enabled = processesFilter.getEnabled();
    List<Predicate> predicates = new ArrayList<>();
    CriteriaBuilder criteriaBuilder = getEntityManager().getCriteriaBuilder();
    CriteriaQuery<WorkFlowEntity> criteriaQuery = criteriaBuilder.createQuery(WorkFlowEntity.class);
    Root<WorkFlowEntity> root = criteriaQuery.from(WorkFlowEntity.class);
    if (q != null) {
      List<Predicate> qPredicates = new ArrayList<>();
      qPredicates.add(criteriaBuilder.like(root.get("title"), "%" + q + "%"));
      qPredicates.add(criteriaBuilder.like(root.get("description"), "%" + q + "%"));
      qPredicates.add(criteriaBuilder.like(root.get("summary"), "%" + q + "%"));
      predicates.add(criteriaBuilder.or(qPredicates.toArray(new Predicate[0])));
    }
    if(enabled != null) {
      predicates.add(criteriaBuilder.and(criteriaBuilder.equal(root.get("enabled"), enabled)));
    }
    criteriaQuery.select(root).where(predicates.toArray(new Predicate[0]));
    criteriaQuery.orderBy(criteriaBuilder.desc(root.get("id")));
    Query query = getEntityManager().createQuery(criteriaQuery);
    return query;
  }

  public List<WorkFlowEntity> findWorkFlows(ProcessesFilter processesFilter, int offset, int limit) {
    Query query = buildWorkflowQueryCriteria(processesFilter);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<WorkFlowEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public int countWorkFlows(ProcessesFilter processesFilter) {
    Query query = buildWorkflowQueryCriteria(processesFilter);
    return query.getMaxResults();
  }

  public List<WorkFlowEntity> findWorkFlows(ProcessesFilter processesFilter, List<String> memberships, int offset, int limit) {

    String queryString = buildWorkflowQuery(processesFilter, memberships);
    Query query = getEntityManager().createQuery(queryString);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<WorkFlowEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public int countWorkFlows(ProcessesFilter processesFilter, List<String> memberships) {
    String queryString = buildWorkflowQuery(processesFilter, memberships);
    Query query = getEntityManager().createQuery(queryString, Long.class);
    return query.getMaxResults();
  }


  public List<WorkFlowEntity> findEnabledWorkFlows(int offset, int limit) {
    TypedQuery<WorkFlowEntity> query = getEntityManager().createNamedQuery("WorkFlow.findEnabledWorkFlows", WorkFlowEntity.class);
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
