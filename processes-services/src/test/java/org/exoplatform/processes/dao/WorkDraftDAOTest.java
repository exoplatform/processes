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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mockStatic;

import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.exoplatform.commons.persistence.impl.EntityManagerHolder;
import org.exoplatform.processes.entity.WorkEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;

public class WorkDraftDAOTest {

  private static EntityManagerFactory       entityManagerFactory;

  private static long                       hrProcess;

  private static long                       hrDraft;

  private static long                       hrRowNotFlagged;

  private static long                       hrRowWithoutFlag;

  private static long                       itDraft;

  private EntityManager                     entityManager;

  private MockedStatic<EntityManagerHolder> entityManagerHolder;

  private final WorkDraftDAO                workDraftDAO = new WorkDraftDAO();

  @BeforeClass
  public static void createDrafts() {
    entityManagerFactory = new HibernatePersistenceConfiguration("processes-draft-dao-test").managedClass(WorkFlowEntity.class)
                                                                                            .managedClass(WorkEntity.class)
                                                                                            .property("jakarta.persistence.jdbc.url",
                                                                                                      "jdbc:hsqldb:mem:processes-draft-dao;shutdown=true")
                                                                                            .property("jakarta.persistence.jdbc.user",
                                                                                                      "SA")
                                                                                            .property("jakarta.persistence.jdbc.password",
                                                                                                      "")
                                                                                            .property("hibernate.hbm2ddl.auto",
                                                                                                      "create-drop")
                                                                                            .createEntityManagerFactory();
    EntityManager em = entityManagerFactory.createEntityManager();
    em.getTransaction().begin();
    WorkFlowEntity hr = createProcess(em, "Expense report");
    WorkFlowEntity it = createProcess(em, "Laptop request");
    hrProcess = hr.getId();
    hrDraft = createWork(em, hr, true);
    hrRowNotFlagged = createWork(em, hr, false);
    hrRowWithoutFlag = createWork(em, hr, null);
    itDraft = createWork(em, it, true);
    em.getTransaction().commit();
    em.close();
  }

  @AfterClass
  public static void dropDrafts() {
    entityManagerFactory.close();
  }

  @Before
  public void openEntityManager() {
    entityManager = entityManagerFactory.createEntityManager();
    entityManagerHolder = mockStatic(EntityManagerHolder.class);
    entityManagerHolder.when(EntityManagerHolder::get).thenReturn(entityManager);
  }

  @After
  public void closeEntityManager() {
    entityManagerHolder.close();
    entityManager.close();
  }

  /**
   * A process's rows are all listed for its deletion, the rows stored with the
   * draft flag unset or false included, and no other process's row. Mutation: list the rows
   * flagged as drafts only, and this test fails.
   */
  @Test
  public void everyRowOfAProcessIsListed() {
    Set<Long> rows = workDraftDAO.getWorksByWorkflowId(hrProcess).stream().map(WorkEntity::getId).collect(Collectors.toSet());

    assertEquals(Set.of(hrDraft, hrRowNotFlagged, hrRowWithoutFlag), rows);
  }

  private static WorkFlowEntity createProcess(EntityManager em, String title) {
    WorkFlowEntity workFlow = new WorkFlowEntity();
    workFlow.setTitle(title);
    workFlow.setEnabled(true);
    workFlow.setCreatedDate(new Date());
    workFlow.setModifiedDate(new Date());
    em.persist(workFlow);
    return workFlow;
  }

  private static long createWork(EntityManager em, WorkFlowEntity workFlow, Boolean draft) {
    WorkEntity work = new WorkEntity();
    work.setTitle("request");
    work.setWorkFlow(workFlow);
    work.setIsDraft(draft);
    work.setCreatedDate(new Date());
    work.setModifiedDate(new Date());
    em.persist(work);
    return work.getId();
  }
}
