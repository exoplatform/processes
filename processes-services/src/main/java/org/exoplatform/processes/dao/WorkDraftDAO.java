package org.exoplatform.processes.dao;

import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;
import org.exoplatform.processes.entity.WorkEntity;

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
}
