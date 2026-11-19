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
import java.util.List;

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
import org.exoplatform.processes.entity.WorkFlowEntity;

/**
 * Runs the query that names the processes feeding some task projects through
 * Hibernate over an in-memory HSQLDB database, so the engine parses and
 * executes it. Index usage and the production databases' dialects are not
 * covered here.
 */
public class WorkFlowDAOProcessesOfProjectsTest {

  private static EntityManagerFactory       entityManagerFactory;

  private static long                       expenseReport;

  private static long                       trainingRequest;

  private static long                       laptopRequest;

  private EntityManager                     entityManager;

  private MockedStatic<EntityManagerHolder> entityManagerHolder;

  private final WorkFlowDAO                 workFlowDAO = new WorkFlowDAO();

  /**
   * Creates two processes of space 3 sharing project 10, one of space 3 on
   * project 11, and one of another space on project 10.
   */
  @BeforeClass
  public static void createProcesses() {
    entityManagerFactory = new HibernatePersistenceConfiguration("processes-of-projects-test")
                                                                                              .managedClass(WorkFlowEntity.class)
                                                                                              .property("jakarta.persistence.jdbc.url",
                                                                                                        "jdbc:hsqldb:mem:processes-of-projects;shutdown=true")
                                                                                              .property("jakarta.persistence.jdbc.user",
                                                                                                        "SA")
                                                                                              .property("jakarta.persistence.jdbc.password",
                                                                                                        "")
                                                                                              .property("hibernate.hbm2ddl.auto",
                                                                                                        "create-drop")
                                                                                              .createEntityManagerFactory();
    EntityManager em = entityManagerFactory.createEntityManager();
    em.getTransaction().begin();
    trainingRequest = createProcess(em, "Training request", 10L, 3L);
    expenseReport = createProcess(em, "Expense report", 10L, 3L);
    laptopRequest = createProcess(em, "Laptop request", 11L, 3L);
    createProcess(em, "Quote approval", 10L, 4L);
    em.getTransaction().commit();
    em.close();
  }

  /**
   * Drops the database.
   */
  @AfterClass
  public static void dropProcesses() {
    entityManagerFactory.close();
  }

  /**
   * Opens an entity manager the DAO reads through.
   */
  @Before
  public void openEntityManager() {
    entityManager = entityManagerFactory.createEntityManager();
    entityManagerHolder = mockStatic(EntityManagerHolder.class);
    entityManagerHolder.when(EntityManagerHolder::get).thenReturn(entityManager);
  }

  /**
   * Closes the entity manager.
   */
  @After
  public void closeEntityManager() {
    entityManagerHolder.close();
    entityManager.close();
  }

  /**
   * The processes of the space that feed the listed projects come back by
   * title, with their project; a process of another space on the same project
   * is left out. Mutation: drop the space condition and Quote approval comes
   * back; drop the project condition and Laptop request comes back for
   * project 10 alone.
   */
  @Test
  public void theProcessesOfTheSpaceFeedingTheProjectsComeBackByTitle() {
    List<String> rows = workFlowDAO.findProcessesOfProjects(List.of(10L), 3L)
                                   .stream()
                                   .map(row -> row[0] + "/" + row[1] + "/" + row[2])
                                   .toList();

    assertEquals(List.of("10/" + expenseReport + "/Expense report", "10/" + trainingRequest + "/Training request"), rows);
    assertEquals(3, workFlowDAO.findProcessesOfProjects(List.of(10L, 11L), 3L).size());
    assertEquals(laptopRequest, workFlowDAO.findProcessesOfProjects(List.of(11L), 3L).get(0)[1]);
  }

  /**
   * The deprecated lookup of a process by its project answers the process of
   * lowest id once several share the project, where a single-result read would
   * throw. Mutation: go back to getSingleResult and this test fails.
   */
  @Test
  @SuppressWarnings("removal")
  public void theLookupByProjectAnswersTheFirstProcessOfASharedProject() {
    assertEquals(Long.valueOf(trainingRequest), workFlowDAO.getWorkFlowByProjectId(10L).getId());
    assertEquals(null, workFlowDAO.getWorkFlowByProjectId(12L));
  }

  /**
   * Every process feeding a project comes back, of any space, by ascending
   * id; a project no process feeds answers none. Mutation: drop the project
   * condition, or the order, and this test fails.
   */
  @Test
  public void everyProcessFeedingAProjectComesBackById() {
    List<Long> ids = workFlowDAO.findWorkFlowIdsByProjectId(10L);
    assertEquals(3, ids.size());
    assertEquals(ids.stream().sorted().toList(), ids);
    assertEquals(List.of(trainingRequest, expenseReport), ids.subList(0, 2));
    assertEquals(List.of(laptopRequest), workFlowDAO.findWorkFlowIdsByProjectId(11L));
    assertEquals(List.of(), workFlowDAO.findWorkFlowIdsByProjectId(12L));
  }

  /**
   * No project id reads nothing and sends no query with an empty list.
   */
  @Test
  public void noProjectReadsNothing() {
    assertEquals(List.of(), workFlowDAO.findProcessesOfProjects(List.of(), 3L));
  }

  /**
   * Persists a process.
   *
   * @param em the entity manager
   * @param title its title
   * @param projectId its task project id
   * @param spaceId its managing space id
   * @return its id
   */
  private static long createProcess(EntityManager em, String title, long projectId, long spaceId) {
    WorkFlowEntity workFlow = new WorkFlowEntity();
    workFlow.setTitle(title);
    workFlow.setDescription("description");
    workFlow.setSummary("summary");
    workFlow.setEnabled(true);
    workFlow.setCreatedDate(new Date());
    workFlow.setModifiedDate(new Date());
    workFlow.setProjectId(projectId);
    workFlow.setSpaceId(spaceId);
    em.persist(workFlow);
    return workFlow.getId();
  }
}
