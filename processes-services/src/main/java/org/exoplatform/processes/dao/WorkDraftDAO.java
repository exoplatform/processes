/*
 * Copyright (C) 2022 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
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

    public List<WorkEntity> getDraftsByWorkflowId(long workflowId) {
      TypedQuery<WorkEntity> query = getEntityManager().createNamedQuery("Work.findAllWorkDraftsByWorkflowId", WorkEntity.class);
      query.setParameter("workflowId", workflowId);
      List<WorkEntity> resultList = query.getResultList();
      return resultList == null ? Collections.emptyList() : resultList;

    }
  }
