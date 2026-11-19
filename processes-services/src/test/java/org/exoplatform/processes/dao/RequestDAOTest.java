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

import java.util.Collection;
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
 * Runs the request record reads of {@link RequestDAO} through Hibernate over an
 * in-memory HSQLDB database, so the engine parses and executes them. A small
 * task entity stands for the Task add-on's, whose queries the
 * {@link RequestDAO#requestOf} restriction is added to. Index usage and the
 * production databases' dialects are not covered here.
 */
public class RequestDAOTest {

  /** Process 7 has requests on tasks 1, 2 and 3; process 8 on task 4. */
  private static final long                 PROCESS = 7L;

  private static final long                 OTHER_PROCESS = 8L;

  private static EntityManagerFactory       entityManagerFactory;

  private EntityManager                     entityManager;

  private MockedStatic<EntityManagerHolder> entityManagerHolder;

  private final RequestDAO                  requestDAO    = new RequestDAO();

  /**
   * Stands for the Task add-on's task: an id and a project.
   */
  @Entity(name = "RequestOfTestTask")
  @Table(name = "REQUEST_OF_TEST_TASK")
  public static class TestTaskEntity {

    @Id
    @Column(name = "ID")
    private Long id;

    @Column(name = "PROJECT_ID")
    private Long projectId;

    /** Builds an empty task, for Hibernate. */
    public TestTaskEntity() {
    }

    /**
     * @param id the task id
     * @param projectId the task's project id
     */
    TestTaskEntity(long id, long projectId) {
      this.id = id;
      this.projectId = projectId;
    }
  }

  @BeforeClass
  public static void createRequests() {
    entityManagerFactory = new HibernatePersistenceConfiguration("processes-request-dao-test")
                                                                                              .managedClass(RequestEntity.class)
                                                                                              .managedClass(TestTaskEntity.class)
                                                                                              .property("jakarta.persistence.jdbc.url",
                                                                                                        "jdbc:hsqldb:mem:processes-request-dao;shutdown=true")
                                                                                              .property("jakarta.persistence.jdbc.user",
                                                                                                        "SA")
                                                                                              .property("jakarta.persistence.jdbc.password",
                                                                                                        "")
                                                                                              .property("hibernate.hbm2ddl.auto",
                                                                                                        "create-drop")
                                                                                              .createEntityManagerFactory();
    EntityManager em = entityManagerFactory.createEntityManager();
    em.getTransaction().begin();
    // Tasks 1 and 2 on project 10, task 3 moved to project 11, task 4 of
    // another process on project 10, task 5 a plain task on project 10; the
    // request on task 6 lost its task.
    em.persist(new TestTaskEntity(1, 10));
    em.persist(new TestTaskEntity(2, 10));
    em.persist(new TestTaskEntity(3, 11));
    em.persist(new TestTaskEntity(4, 10));
    em.persist(new TestTaskEntity(5, 10));
    em.persist(new RequestEntity(1L, PROCESS, 100L));
    em.persist(new RequestEntity(2L, PROCESS, 100L));
    em.persist(new RequestEntity(3L, PROCESS, 100L));
    em.persist(new RequestEntity(4L, OTHER_PROCESS, 100L));
    em.persist(new RequestEntity(6L, PROCESS, 100L));
    // Requests of a third process on tasks with no task row, beyond one chunk
    LongStream.rangeClosed(1001, 2600).forEach(taskId -> em.persist(new RequestEntity(taskId, 9L, 100L)));
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
   * The restriction keeps only the tasks that are requests of the given
   * process, whatever their project: a plain task, another process's request
   * and a request record whose task is gone are not answered. Mutation: drop
   * the correlation on the task id, or the process restriction, and this test
   * fails.
   */
  @Test
  public void requestOfKeepsOnlyTheTasksOfTheProcessRequests() {
    assertEquals(Set.of(1L, 2L, 3L), tasksOf(List.of(PROCESS)));
    assertEquals(Set.of(4L), tasksOf(List.of(OTHER_PROCESS)));
    assertEquals(Set.of(1L, 2L, 3L, 4L), tasksOf(List.of(PROCESS, OTHER_PROCESS)));
  }

  /**
   * With no process, the restriction answers no task rather than every task.
   * Mutation: return a true restriction for an empty list and this test fails.
   */
  @Test
  public void requestOfWithNoProcessAnswersNoTask() {
    assertEquals(Set.of(), tasksOf(List.of()));
  }

  /**
   * The restriction composes with another one with AND: the requests of the
   * process on project 10 only. It also serves a count, as Task's count query
   * reuses it. Mutation: drop the correlation and the count is wrong.
   */
  @Test
  public void requestOfNarrowsAProjectQueryAndItsCount() {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> query = cb.createQuery(Long.class);
    Root<TestTaskEntity> task = query.from(TestTaskEntity.class);
    query.select(cb.count(task))
         .where(cb.equal(task.get("projectId"), 10L),
                RequestDAO.requestOf(cb, query, task.<Long> get("id"), List.of(PROCESS)));

    assertEquals(Long.valueOf(2), entityManager.createQuery(query).getSingleResult());
  }

  /**
   * The restriction to the requests of any process keeps every task that has a
   * request record, whatever its project or its process, and leaves out a
   * plain task; on project 10 it composes with AND. Mutation: drop the
   * correlation on the task id and the plain task is answered.
   */
  @Test
  public void isRequestKeepsEveryRequestAndNoPlainTask() {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<TestTaskEntity> query = cb.createQuery(TestTaskEntity.class);
    Root<TestTaskEntity> task = query.from(TestTaskEntity.class);
    query.select(task).where(RequestDAO.isRequest(cb, query, task.<Long> get("id")));
    assertEquals(Set.of(1L, 2L, 3L, 4L),
                 entityManager.createQuery(query).getResultList().stream().map(t -> t.id).collect(Collectors.toSet()));

    CriteriaQuery<Long> count = cb.createQuery(Long.class);
    Root<TestTaskEntity> countedTask = count.from(TestTaskEntity.class);
    count.select(cb.count(countedTask))
         .where(cb.equal(countedTask.get("projectId"), 10L), RequestDAO.isRequest(cb, count, countedTask.<Long> get("id")));
    assertEquals(Long.valueOf(3), entityManager.createQuery(count).getSingleResult());
  }

  /**
   * The processes of tasks are read by chunks, so more task ids than one chunk
   * holds are answered in full; a plain task and an unknown id are left out.
   * Mutation: read the first chunk only and this test fails.
   */
  @Test
  public void findWorkFlowIdsByTaskIdsReadsEveryChunk() {
    List<Long> taskIds = LongStream.rangeClosed(1, 2600).boxed().toList();

    Map<Long, Long> workFlowIds = requestDAO.findWorkFlowIdsByTaskIds(taskIds);

    assertEquals(1600 + 5, workFlowIds.size());
    assertEquals(Long.valueOf(PROCESS), workFlowIds.get(3L));
    assertEquals(Long.valueOf(OTHER_PROCESS), workFlowIds.get(4L));
    assertEquals(Long.valueOf(9L), workFlowIds.get(2600L));
    assertEquals(null, workFlowIds.get(5L));
  }

  /**
   * The task ids of a process's request records are read whether the task
   * still exists or not, in ascending order, and only for that process.
   * Mutation: drop the process restriction and this test fails.
   */
  @Test
  public void findTaskIdsByWorkFlowIdReadsEveryRecordOfTheProcess() {
    assertEquals(List.of(1L, 2L, 3L, 6L), requestDAO.findTaskIdsByWorkFlowId(PROCESS));
    assertEquals(List.of(4L), requestDAO.findTaskIdsByWorkFlowId(OTHER_PROCESS));
  }

  private Set<Long> tasksOf(Collection<Long> workFlowIds) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<TestTaskEntity> query = cb.createQuery(TestTaskEntity.class);
    Root<TestTaskEntity> task = query.from(TestTaskEntity.class);
    query.select(task).where(RequestDAO.requestOf(cb, query, task.<Long> get("id"), workFlowIds));
    return entityManager.createQuery(query).getResultList().stream().map(t -> t.id).collect(Collectors.toSet());
  }
}
