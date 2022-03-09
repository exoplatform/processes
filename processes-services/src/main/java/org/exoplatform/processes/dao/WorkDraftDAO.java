package org.exoplatform.processes.dao;

import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;
import org.exoplatform.processes.entity.WorkEntity;
import org.exoplatform.processes.model.Work;

import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.util.Collections;
import java.util.List;

public class WorkDraftDAO extends GenericDAOJPAImpl<WorkEntity, Long> {

    public List<WorkEntity> findAllWorkDraftsByUser(long userId, int offset, int limit) {
        TypedQuery<WorkEntity> query = getEntityManager().createNamedQuery("Work.findAllWorkDraftsByUser",
                WorkEntity.class);
        query.setParameter("userId", userId);
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
