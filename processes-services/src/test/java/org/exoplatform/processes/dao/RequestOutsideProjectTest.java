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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import org.exoplatform.processes.entity.RequestEntity;

/**
 * Runs {@link RequestDAO#outsideProject}, with {@link RequestDAO#requestOf},
 * through Hibernate over an in-memory HSQLDB database, as the end of a move
 * pass counts the requests left outside the process's project. Small task,
 * status and project entities stand for the Task add-on's, with the same
 * attribute names. Index usage and the production dialects are not covered.
 */
class RequestOutsideProjectTest {

  private static final long          PROCESS = 7L;

  private static final long          TARGET  = 20L;

  private static EntityManagerFactory entityManagerFactory;

  /**
   * Stands for the Task add-on's project.
   */
  @Entity(name = "OutsideTestProject")
  @Table(name = "OUTSIDE_TEST_PROJECT")
  public static class TestProject {
    @Id
    @Column(name = "ID")
    private Long id;

    /** Builds an empty project, for Hibernate. */
    public TestProject() {
    }

    /**
     * @param id the project id
     */
    TestProject(long id) {
      this.id = id;
    }
  }

  /**
   * Stands for the Task add-on's status: a column of a project, or of none.
   */
  @Entity(name = "OutsideTestStatus")
  @Table(name = "OUTSIDE_TEST_STATUS")
  public static class TestStatus {
    @Id
    @Column(name = "ID")
    private Long        id;

    @ManyToOne
    @JoinColumn(name = "PROJECT_ID")
    private TestProject project;

    /** Builds an empty status, for Hibernate. */
    public TestStatus() {
    }

    /**
     * @param id the status id
     * @param project its project, or null
     */
    TestStatus(long id, TestProject project) {
      this.id = id;
      this.project = project;
    }
  }

  /**
   * Stands for the Task add-on's task: an id and a status, or none.
   */
  @Entity(name = "OutsideTestTask")
  @Table(name = "OUTSIDE_TEST_TASK")
  public static class TestTask {
    @Id
    @Column(name = "ID")
    private Long       id;

    @ManyToOne
    @JoinColumn(name = "STATUS_ID")
    private TestStatus status;

    /** Builds an empty task, for Hibernate. */
    public TestTask() {
    }

    /**
     * @param id the task id
     * @param status its status, or null
     */
    TestTask(long id, TestStatus status) {
      this.id = id;
      this.status = status;
    }
  }

  /**
   * Requests of process 7: task 1 in the target project, task 2 in another
   * project, task 3 with no status, task 4 in a status of no project; task 5,
   * in another project, is a request of another process; task 6, in another
   * project, is no request.
   */
  @BeforeAll
  static void createTasks() {
    entityManagerFactory = new HibernatePersistenceConfiguration("processes-outside-project-test").managedClass(RequestEntity.class)
                                                                                                  .managedClass(TestProject.class)
                                                                                                  .managedClass(TestStatus.class)
                                                                                                  .managedClass(TestTask.class)
                                                                                                  .property("jakarta.persistence.jdbc.url",
                                                                                                            "jdbc:hsqldb:mem:processes-outside-project;shutdown=true")
                                                                                                  .property("jakarta.persistence.jdbc.user",
                                                                                                            "SA")
                                                                                                  .property("jakarta.persistence.jdbc.password",
                                                                                                            "")
                                                                                                  .property("hibernate.hbm2ddl.auto",
                                                                                                            "create-drop")
                                                                                                  .createEntityManagerFactory();
    EntityManager em = entityManagerFactory.createEntityManager();
    em.getTransaction().begin();
    TestProject target = new TestProject(TARGET);
    TestProject former = new TestProject(10L);
    em.persist(target);
    em.persist(former);
    TestStatus inTarget = new TestStatus(2001L, target);
    TestStatus inFormer = new TestStatus(1001L, former);
    TestStatus inNoProject = new TestStatus(3001L, null);
    em.persist(inTarget);
    em.persist(inFormer);
    em.persist(inNoProject);
    em.persist(new TestTask(1L, inTarget));
    em.persist(new TestTask(2L, inFormer));
    em.persist(new TestTask(3L, null));
    em.persist(new TestTask(4L, inNoProject));
    em.persist(new TestTask(5L, inFormer));
    em.persist(new TestTask(6L, inFormer));
    for (long taskId = 1; taskId <= 4; taskId++) {
      em.persist(new RequestEntity(taskId, PROCESS, 100L));
    }
    em.persist(new RequestEntity(5L, 8L, 100L));
    em.getTransaction().commit();
    em.close();
  }

  @AfterAll
  static void dropTasks() {
    entityManagerFactory.close();
  }

  /**
   * The count sees the requests of the process in another project, in no
   * status or in a status of no project, never the one in the target, nor
   * another process's or a plain task. Mutation: navigate the status and its
   * project with implicit joins, which Hibernate makes inner, and the tasks
   * with no status or no project are left out; or compare with equal, and only
   * the one in the target is counted.
   */
  @Test
  void theRequestsOutsideTheProjectAreThoseInAnotherProjectOrInNone() {
    EntityManager em = entityManagerFactory.createEntityManager();
    try {
      CriteriaBuilder cb = em.getCriteriaBuilder();
      CriteriaQuery<Long> count = cb.createQuery(Long.class);
      Root<TestTask> task = count.from(TestTask.class);
      count.select(cb.countDistinct(task))
           .where(RequestDAO.requestOf(cb, count, task.<Long> get("id"), List.of(PROCESS)),
                  RequestDAO.outsideProject(cb, task, TARGET));
      assertEquals(3L, em.createQuery(count).getSingleResult());

      CriteriaQuery<Long> ids = cb.createQuery(Long.class);
      Root<TestTask> idTask = ids.from(TestTask.class);
      ids.select(idTask.<Long> get("id"))
         .where(RequestDAO.requestOf(cb, ids, idTask.<Long> get("id"), List.of(PROCESS)),
                RequestDAO.outsideProject(cb, idTask, TARGET))
         .orderBy(cb.asc(idTask.get("id")));
      assertEquals(List.of(2L, 3L, 4L), em.createQuery(ids).getResultList());
    } finally {
      em.close();
    }
  }
}
