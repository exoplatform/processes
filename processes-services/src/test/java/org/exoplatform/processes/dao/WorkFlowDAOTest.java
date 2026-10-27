/*
 * Copyright (C) 2026 eXo Platform SAS
 *  
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.processes.dao;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import org.exoplatform.commons.persistence.impl.EntityManagerHolder;
import org.exoplatform.processes.model.ProcessesFilter;

public class WorkFlowDAOTest {

  /**
   * A user's memberships always restrict the listing, whatever the filter's
   * process manager flag says. Mutation: list every process when the flag is
   * set and this test fails.
   */
  @Test
  @SuppressWarnings("deprecation")
  public void membershipsRestrictTheListingWhateverTheProcessManagerFlag() {
    List<String> memberships = List.of("john", "member:/spaces/hr");
    ProcessesFilter filter = new ProcessesFilter();
    filter.setIsProcessManager(true);

    String jpql = findWorkFlows(filter, memberships);

    assertTrue(jpql, jpql.contains("participator IN :memberships"));
  }

  /**
   * Null memberships, a processes administrator's, list every process, and an
   * unset process manager flag is no error. Mutation: unbox the flag into a
   * primitive boolean and this test fails.
   */
  @Test
  public void nullMembershipsListEveryProcess() {
    ProcessesFilter filter = new ProcessesFilter();

    String jpql = findWorkFlows(filter, null);

    assertFalse(jpql, jpql.contains("participator"));
    assertFalse(jpql, jpql.contains("WHERE"));
  }

  private String findWorkFlows(ProcessesFilter filter, List<String> memberships) {
    EntityManager entityManager = mock(EntityManager.class);
    Query query = mock(Query.class);
    when(entityManager.createQuery(anyString())).thenReturn(query);
    when(query.getResultList()).thenReturn(Collections.emptyList());
    try (MockedStatic<EntityManagerHolder> entityManagerHolder = mockStatic(EntityManagerHolder.class)) {
      entityManagerHolder.when(EntityManagerHolder::get).thenReturn(entityManager);
      new WorkFlowDAO().findWorkFlows(filter, memberships, 0, 0);
    }
    ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
    verify(entityManager).createQuery(jpql.capture());
    if (memberships == null) {
      verify(query, never()).setParameter(eq("memberships"), any());
    } else {
      verify(query).setParameter("memberships", memberships);
    }
    return jpql.getValue();
  }
}
