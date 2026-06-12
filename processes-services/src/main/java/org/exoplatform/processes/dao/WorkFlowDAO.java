package org.exoplatform.processes.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;

import org.apache.commons.lang3.StringUtils;
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

  private String buildWorkflowQuery(ProcessesFilter processesFilter, List<String> memberships) {

    String q = processesFilter.getQuery();
    Boolean enabled = processesFilter.getEnabled();
    Boolean manager = processesFilter.getManager();
    boolean isProcessManager = processesFilter.getIsProcessManager();

    StringBuilder queryString =
      new StringBuilder("SELECT DISTINCT workFlow FROM WorkFlow workFlow");

    if (enabled != null || Boolean.TRUE.equals(manager) || !isProcessManager) {

      if (memberships != null) {
        if (Boolean.FALSE.equals(manager)) {
          queryString.append(" LEFT JOIN workFlow.manager manager");
        }
        queryString.append(" LEFT JOIN workFlow.participator participator");
      }
      if (StringUtils.isNotEmpty(q) || memberships != null || enabled != null) {
        List<String> predicates = new ArrayList<>();
        if (StringUtils.isNotEmpty(q)) {
          predicates.add("""
              (workFlow.title LIKE :query
              OR workFlow.description LIKE :query
              OR workFlow.summary LIKE :query)
              """);
        }
        if (enabled != null) {
          predicates.add("workFlow.enabled = :enabled");
        }
        if (memberships != null) {
          if (Boolean.FALSE.equals(manager)) {
            predicates.add("(manager IN :managers OR participator IN :memberships)");
          } else {
            predicates.add("participator IN :memberships");
          }
        }
        if (!predicates.isEmpty()) {
          queryString.append(" WHERE ")
            .append(StringUtils.join(predicates, " AND "));
        }
      }
    } else {
      if (StringUtils.isNotEmpty(q)) {
        queryString.append(" WHERE (workFlow.title LIKE :query")
          .append(" OR workFlow.description LIKE :query")
          .append(" OR workFlow.summary LIKE :query)");
      }
    }

    return queryString.toString();
  }

  private Query createQuery(String queryString, ProcessesFilter processesFilter, List<String> memberships) {
    Query query = getEntityManager().createQuery(queryString);

    if (StringUtils.isNotEmpty(processesFilter.getQuery())) {
      query.setParameter("query", "%" + processesFilter.getQuery() + "%");
    }

    if (processesFilter.getEnabled() != null) {
      query.setParameter("enabled", processesFilter.getEnabled());
    }

    if (memberships != null) {
      query.setParameter("memberships", memberships);
      if (Boolean.FALSE.equals(processesFilter.getManager())) {
        query.setParameter("managers", getMembershipGroups(memberships));
      }
    }
    return query;
  }

  private List<String> getMembershipGroups(List<String> memberships) {
    return memberships.stream()
                             .map(s -> (!s.startsWith("manager:/") && !s.startsWith("member:/") && !s.startsWith("*:/"))
                                     ? s : s.replace("manager:","").replace("member:","").replace("*:",""))
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
    Query query = createQuery(queryString, processesFilter, memberships);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<WorkFlowEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public int countWorkFlows(ProcessesFilter processesFilter, List<String> memberships) {
    String queryString = buildWorkflowQuery(processesFilter, memberships);
    Query query = createQuery(queryString, processesFilter, memberships);
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
