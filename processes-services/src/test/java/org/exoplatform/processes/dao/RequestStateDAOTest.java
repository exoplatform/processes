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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import org.exoplatform.commons.persistence.impl.EntityManagerHolder;
import org.exoplatform.processes.entity.RequestEntity;

/**
 * Runs the request state reads of {@link RequestDAO} (EXO-94923) through
 * Hibernate over an in-memory HSQLDB database, so the engine parses and
 * executes them: the records by task ids, the records without state the
 * upgrade fills, and the restriction to archived or not archived requests. A
 * small task entity stands for the Task add-on's. Index usage and the
 * production databases' dialects are not covered here.
 */
public class RequestStateDAOTest {

  private static EntityManagerFactory       entityManagerFactory;

  private EntityManager                     entityManager;

  private MockedStatic<EntityManagerHolder> entityManagerHolder;

  private final RequestDAO                  requestDAO = new RequestDAO();

  /**
   * Stands for the Task add-on's task: an id only.
   */
  @Entity(name = "RequestStateTestTask")
  @Table(name = "REQUEST_STATE_TEST_TASK")
  public static class TestTaskEntity {

    @Id
    @Column(name = "ID")
    private Long id;

    /** Builds an empty task, for Hibernate. */
    public TestTaskEntity() {
    }

    /**
     * @param id the task id
     */
    TestTaskEntity(long id) {
      this.id = id;
    }
  }

  /**
   * Creates the tasks 1 to 5 and the records: 1 archived, 2 not archived, 3
   * without state, 4 cancelled and not archived, 5 a plain task; the records
   * 11 to 1300 have no state and no task, beyond one chunk.
   */
  @BeforeClass
  public static void createRequests() {
    entityManagerFactory = new HibernatePersistenceConfiguration("processes-request-state-dao-test")
                                                                                                    .managedClass(RequestEntity.class)
                                                                                                    .managedClass(TestTaskEntity.class)
                                                                                                    .property("jakarta.persistence.jdbc.url",
                                                                                                              "jdbc:hsqldb:mem:processes-request-state-dao;shutdown=true")
                                                                                                    .property("jakarta.persistence.jdbc.user",
                                                                                                              "SA")
                                                                                                    .property("jakarta.persistence.jdbc.password",
                                                                                                              "")
                                                                                                    .property("hibernate.hbm2ddl.auto",
                                                                                                              "create-drop")
                                                                                                    .createEntityManagerFactory();
    EntityManager em = entityManagerFactory.createEntityManager();
    em.getTransaction().begin();
    LongStream.rangeClosed(1, 5).forEach(id -> em.persist(new TestTaskEntity(id)));
    em.persist(new RequestEntity(1L, 7L, 100L, Boolean.FALSE, Boolean.TRUE));
    em.persist(new RequestEntity(2L, 7L, 100L, Boolean.FALSE, Boolean.FALSE));
    em.persist(new RequestEntity(3L, 7L, 100L));
    em.persist(new RequestEntity(4L, 7L, 100L, Boolean.TRUE, Boolean.FALSE));
    LongStream.rangeClosed(11, 1300).forEach(taskId -> em.persist(new RequestEntity(taskId, 8L, 100L)));
    em.getTransaction().commit();
    em.close();
  }

  @AfterClass
  public static void dropRequests() {
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
   * The archived restriction keeps the archived requests only, and the not
   * archived one keeps the others, a record without state among them, never a
   * plain task; with no flag, every request. Mutation: read a null flag as
   * archived, drop the correlation on the task id, or drop the flag, and this
   * test fails.
   */
  @Test
  public void isRequestFiltersOnTheRequestersArchive() {
    assertEquals(Set.of(1L), tasks(Boolean.TRUE));
    assertEquals(Set.of(2L, 3L, 4L), tasks(Boolean.FALSE));
    assertEquals(Set.of(1L, 2L, 3L, 4L), tasks(null));
  }

  /**
   * The records of tasks are read with their state, by chunks, so more task
   * ids than one chunk holds are answered in full; a plain task is left out.
   * Mutation: read the first chunk only, or drop the state columns from the
   * entity, and this test fails.
   */
  @Test
  public void findByTaskIdsReadsEveryChunkWithTheState() {
    Map<Long, RequestEntity> records = requestDAO.findByTaskIds(LongStream.rangeClosed(1, 1300).boxed().toList());

    assertEquals(4 + 1290, records.size());
    assertTrue(records.get(1L).isArchivedRequest());
    assertFalse(records.get(1L).isCanceledRequest());
    assertTrue(records.get(4L).isCanceledRequest());
    assertFalse(records.get(3L).isCanceledRequest());
    assertFalse(records.get(3L).isArchivedRequest());
    assertEquals(Long.valueOf(8L), records.get(1300L).getWorkFlowId());
    assertFalse(records.containsKey(5L));
  }

  /**
   * The upgrade reads the records whose state is unknown only, by ascending
   * task id, after the last task id of the previous page, a page at a time.
   * Mutation: drop the null conditions, the keyset or the limit, and this
   * test fails.
   */
  @Test
  public void findWithoutStateAfterReadsTheUnfilledRecordsByKeyset() {
    List<Long> first = requestDAO.findWithoutStateAfter(0L, 3).stream().map(RequestEntity::getTaskId).toList();
    List<Long> next = requestDAO.findWithoutStateAfter(11L, 2).stream().map(RequestEntity::getTaskId).toList();

    assertEquals(List.of(3L, 11L, 12L), first);
    assertEquals(List.of(12L, 13L), next);
  }

  /**
   * Reads the test tasks that are requests, archived or not as asked.
   *
   * @param archived the archive flag, or null for both
   * @return their ids
   */
  private Set<Long> tasks(Boolean archived) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<TestTaskEntity> query = cb.createQuery(TestTaskEntity.class);
    Root<TestTaskEntity> task = query.from(TestTaskEntity.class);
    query.select(task).where(RequestDAO.isRequest(cb, query, task.<Long> get("id"), archived));
    return entityManager.createQuery(query).getResultList().stream().map(t -> t.id).collect(Collectors.toSet());
  }
}
