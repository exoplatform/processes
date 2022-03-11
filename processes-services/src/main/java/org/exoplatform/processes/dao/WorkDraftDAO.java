package org.exoplatform.processes.dao;

import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;
import org.exoplatform.processes.entity.WorkEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.WorkFilter;

import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorkDraftDAO extends GenericDAOJPAImpl<WorkEntity, Long> {

  private Query buildWorkQueryCriteria(WorkFilter workFilter, Long userId) {
    String q = workFilter.getQuery();
    Boolean isDraft = workFilter.getIsDraft();
    List<Predicate> predicates = new ArrayList<>();
    CriteriaBuilder criteriaBuilder = getEntityManager().getCriteriaBuilder();
    CriteriaQuery<WorkEntity> criteriaQuery = criteriaBuilder.createQuery(WorkEntity.class);
    Root<WorkEntity> root = criteriaQuery.from(WorkEntity.class);
    Join<WorkEntity, WorkFlowEntity> work = root.join("workFlow", JoinType.LEFT);
    if (q != null) {
      List<Predicate> qPredicates = new ArrayList<>();
      qPredicates.add(criteriaBuilder.like(work.get("title"), "%" + q + "%"));
      qPredicates.add(criteriaBuilder.like(root.get("title"), "%" + q + "%"));
      qPredicates.add(criteriaBuilder.like(root.get("description"), "%" + q + "%"));
      predicates.add(criteriaBuilder.or(qPredicates.toArray(new Predicate[0])));
    }
    if (isDraft != null) {
      predicates.add(criteriaBuilder.and(criteriaBuilder.equal(root.get("isDraft"), isDraft)));
    }
    if (userId != null) {
      predicates.add(criteriaBuilder.and(criteriaBuilder.equal(root.get("creatorId"), userId)));
    }
    criteriaQuery.select(root).where(predicates.toArray(new Predicate[0]));

    Query query = getEntityManager().createQuery(criteriaQuery);
    return query;
  }


  public List<WorkEntity> findAllWorkDraftsByUser(WorkFilter workFilter, long userId, int offset, int limit) {
    Query query = buildWorkQueryCriteria(workFilter, userId);
    query.setFirstResult(offset);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    List<WorkEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;
  }

  public List<WorkEntity> getDraftsByWorkflowId(long workflowId) {
    TypedQuery<WorkEntity> query = getEntityManager().createNamedQuery("Work.findAllWorkDraftsByWorkflowId", WorkEntity.class);
    query.setParameter("workflowId", workflowId);
    List<WorkEntity> resultList = query.getResultList();
    return resultList == null ? Collections.emptyList() : resultList;

  }
}
