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
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mockStatic;

import java.util.Date;
import java.util.List;
import java.util.Map;

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
import org.exoplatform.processes.model.WorkDraftRef;

/**
 * Runs the request draft reads of {@link WorkDraftDAO} through Hibernate over
 * an in-memory HSQLDB database, so the engine parses and executes them. Index
 * usage and the production databases' dialects are not covered here.
 */
public class WorkDraftDAOTest {

  private static EntityManagerFactory       entityManagerFactory;

  private static long                       process;

  private static long                       otherProcess;

  private static long                       firstDraft;

  private static long                       secondDraft;

  private static long                       thirdDraft;

  private static long                       notADraft;

  private static long                       otherProcessDraft;

  private EntityManager                     entityManager;

  private MockedStatic<EntityManagerHolder> entityManagerHolder;

  private final WorkDraftDAO                workDraftDAO = new WorkDraftDAO();

  @BeforeClass
  public static void createDrafts() {
    entityManagerFactory = new HibernatePersistenceConfiguration("processes-draft-dao-test")
                                                                                            .managedClass(WorkFlowEntity.class)
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
    WorkFlowEntity processEntity = workFlow(em, "Leave");
    WorkFlowEntity otherProcessEntity = workFlow(em, "Laptop");
    process = processEntity.getId();
    otherProcess = otherProcessEntity.getId();
    firstDraft = work(em, processEntity, 11L, true);
    secondDraft = work(em, processEntity, 12L, true);
    thirdDraft = work(em, processEntity, 11L, true);
    notADraft = work(em, processEntity, 13L, false);
    otherProcessDraft = work(em, otherProcessEntity, 11L, true);
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
   * A draft's owner, process, title and dates are read; a row that is not a
   * draft is not answered. Mutation: drop the draft-state restriction and the
   * row that is not a draft is answered.
   */
  @Test
  public void draftRefsAnswerTheDraftsOnly() {
    Map<Long, WorkDraftRef> refs = workDraftDAO.findDraftRefs(List.of(firstDraft, notADraft, otherProcessDraft, 999999L));

    assertEquals(2, refs.size());
    WorkDraftRef first = refs.get(firstDraft);
    assertEquals(11L, first.ownerId());
    assertEquals(process, first.workFlowId());
    assertEquals("draft", first.title());
    assertEquals(otherProcess, refs.get(otherProcessDraft).workFlowId());
    assertNull(refs.get(notADraft));
  }

  /**
   * A process's drafts are paged by ascending id, strictly after the previous
   * page; another process's drafts and a row that is not a draft are left out.
   * Mutation: compare with greater-or-equal, or drop the process or draft
   * restriction, and this test fails.
   */
  @Test
  public void draftIdsArePagedByAscendingIdWithinTheProcess() {
    assertEquals(List.of(firstDraft, secondDraft), workDraftDAO.findDraftIdsByWorkflowId(process, null, 2));
    assertEquals(List.of(thirdDraft), workDraftDAO.findDraftIdsByWorkflowId(process, secondDraft, 2));
    assertEquals(List.of(), workDraftDAO.findDraftIdsByWorkflowId(process, thirdDraft, 2));
  }

  /**
   * The count of a process's drafts leaves out another process's drafts and a
   * row that is not a draft.
   */
  @Test
  public void theDraftCountKeepsTheProcessDraftsOnly() {
    assertEquals(3L, workDraftDAO.countDraftsByWorkflowId(process));
    assertEquals(1L, workDraftDAO.countDraftsByWorkflowId(otherProcess));
  }

  private static WorkFlowEntity workFlow(EntityManager em, String title) {
    WorkFlowEntity workFlow = new WorkFlowEntity();
    workFlow.setTitle(title);
    workFlow.setEnabled(true);
    workFlow.setCreatedDate(new Date());
    workFlow.setModifiedDate(new Date());
    em.persist(workFlow);
    return workFlow;
  }

  private static long work(EntityManager em, WorkFlowEntity workFlow, long creatorId, boolean draft) {
    WorkEntity work = new WorkEntity();
    work.setTitle("draft");
    work.setCreatorId(creatorId);
    work.setWorkFlow(workFlow);
    work.setIsDraft(draft);
    work.setCreatedDate(new Date());
    work.setModifiedDate(new Date());
    em.persist(work);
    return work.getId();
  }
}
