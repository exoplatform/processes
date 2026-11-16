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
package org.exoplatform.processes.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.commons.persistence.impl.EntityManagerHolder;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.dao.RequestDAO;
import org.exoplatform.processes.dao.WorkDraftDAO;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.RequestEntity;
import org.exoplatform.processes.entity.WorkEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.service.ProcessesAttachmentService;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.StatusService;
import org.exoplatform.task.service.TaskService;

/**
 * Runs the deletion of a process through the real DAOs, on the schema the
 * processes changelog creates on HSQLDB, inside one transaction, as the
 * atomic method that wraps it does (EXO-93633). The wrapping annotation
 * itself is pinned by ProcessesStorageImplTest#deletingAProcessIsAtomic; its
 * rollback with the platform's aspect is checked on an acceptance server.
 */
public class ProcessesStorageDeletionTest {

  private static final String       CHANGELOG = "db/changelog/processes-rdbms.db.changelog-master.xml";

  private Connection                connection;

  private EntityManagerFactory      entityManagerFactory;

  private EntityManager             entityManager;

  private MockedStatic<EntityManagerHolder> entityManagerHolder;

  private ProjectService            projectService;

  private WorkFlowDAO               workFlowDAO;

  private ProcessesStorageImpl      storage;

  /**
   * Builds the schema with the changelog, two processes sharing project 10,
   * a draft and two request records of process 1, one of process 2.
   *
   * @throws Exception when Liquibase or the engine fails
   */
  @Before
  public void createSchema() throws Exception {
    String url = "jdbc:hsqldb:mem:processes-deletion-" + System.nanoTime();
    connection = DriverManager.getConnection(url, "SA", "");
    new Liquibase(CHANGELOG,
                  new ClassLoaderResourceAccessor(),
                  DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)))
                                                                                                                  .update(new Contexts(),
                                                                                                                          new LabelExpression());
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO PROCESSES_WORK_FLOW (WORK_FLOW_ID, TITLE, DESCRIPTION, SUMMARY, ENABLED, CREATED_DATE, MODIFIED_DATE, PROJECT_ID) VALUES (1, 'Leave', 'd', 's', TRUE, CURRENT_DATE, CURRENT_DATE, 10)");
      statement.executeUpdate("INSERT INTO PROCESSES_WORK_FLOW (WORK_FLOW_ID, TITLE, DESCRIPTION, SUMMARY, ENABLED, CREATED_DATE, MODIFIED_DATE, PROJECT_ID) VALUES (2, 'Training', 'd', 's', TRUE, CURRENT_DATE, CURRENT_DATE, 10)");
      statement.executeUpdate("INSERT INTO PROCESSES_WORK (WORK_ID, TITLE, DESCRIPTION, WORKFLOW_ID, IS_DRAFT, CREATED_DATE, MODIFIED_DATE) VALUES (50, 'draft', 'draft', 1, TRUE, CURRENT_DATE, CURRENT_DATE)");
      statement.executeUpdate("INSERT INTO PROCESSES_REQUEST (TASK_ID, WORK_FLOW_ID, CREATOR_ID) VALUES (100, 1, 5)");
      statement.executeUpdate("INSERT INTO PROCESSES_REQUEST (TASK_ID, WORK_FLOW_ID, CREATOR_ID) VALUES (101, 1, 6)");
      statement.executeUpdate("INSERT INTO PROCESSES_REQUEST (TASK_ID, WORK_FLOW_ID, CREATOR_ID) VALUES (200, 2, 5)");
    }
    connection.commit();
    entityManagerFactory = new HibernatePersistenceConfiguration("processes-deletion-test").managedClass(WorkFlowEntity.class)
                                                                                           .managedClass(WorkEntity.class)
                                                                                           .managedClass(RequestEntity.class)
                                                                                           .property("jakarta.persistence.jdbc.url",
                                                                                                     url)
                                                                                           .property("jakarta.persistence.jdbc.user",
                                                                                                     "SA")
                                                                                           .property("jakarta.persistence.jdbc.password",
                                                                                                     "")
                                                                                           .property("hibernate.hbm2ddl.auto",
                                                                                                     "none")
                                                                                           .createEntityManagerFactory();
    entityManager = entityManagerFactory.createEntityManager();
    entityManagerHolder = mockStatic(EntityManagerHolder.class);
    entityManagerHolder.when(EntityManagerHolder::get).thenReturn(entityManager);
    projectService = mock(ProjectService.class);
    workFlowDAO = spy(new WorkFlowDAO());
    storage = new ProcessesStorageImpl(workFlowDAO,
                                       new WorkDraftDAO(),
                                       new RequestDAO(),
                                       mock(TaskService.class),
                                       projectService,
                                       mock(StatusService.class),
                                       mock(IdentityManager.class),
                                       mock(SpaceService.class),
                                       mock(ListenerService.class),
                                       mock(ProcessesAttachmentService.class),
                                       mock(FileService.class),
                                       mock(UserACL.class));
  }

  /**
   * Drops the database.
   *
   * @throws SQLException when it cannot be shut down
   */
  @After
  public void dropSchema() throws SQLException {
    entityManagerHolder.close();
    entityManager.close();
    entityManagerFactory.close();
    try (Statement statement = connection.createStatement()) {
      statement.execute("SHUTDOWN");
    }
    connection.close();
  }

  /**
   * In one transaction, the deletion removes the process's draft, its request
   * records and the process, in an order the request records' foreign key
   * accepts, and leaves the other process sharing the project with its own
   * record; the task project is not touched. Mutation: delete the process
   * before its request records, or not at all, and the foreign key or the
   * counts fail this test.
   *
   * @throws SQLException on a count failure
   */
  @Test
  public void aDeletionRemovesTheProcessRowsAndKeepsTheOtherProcess() throws SQLException {
    entityManager.getTransaction().begin();
    storage.deleteWorkflowRows(1L);
    entityManager.getTransaction().commit();

    assertEquals(0, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW WHERE WORK_FLOW_ID = 1"));
    assertEquals(0, count("SELECT COUNT(*) FROM PROCESSES_WORK WHERE WORKFLOW_ID = 1"));
    assertEquals(0, count("SELECT COUNT(*) FROM PROCESSES_REQUEST WHERE WORK_FLOW_ID = 1"));
    assertEquals(1, count("SELECT COUNT(*) FROM PROCESSES_REQUEST WHERE WORK_FLOW_ID = 2"));
    verifyNoInteractions(projectService);
  }

  /**
   * When the deletion of the process itself fails, after its draft and its
   * request records were deleted in the same transaction, the rollback leaves
   * the process, its draft and its request records as they were: no process
   * stays whose requests no longer count as requests. Mutation: make any of
   * those writes commit on its own and the rows are lost.
   *
   * @throws SQLException on a count failure
   */
  @Test
  public void aFailingDeletionLeavesTheProcessAndItsRequestRecords() throws SQLException {
    doThrow(new PersistenceException("the delete fails")).when(workFlowDAO).delete(any(WorkFlowEntity.class));

    entityManager.getTransaction().begin();
    assertThrows(PersistenceException.class, () -> storage.deleteWorkflowRows(1L));
    entityManager.getTransaction().rollback();

    assertEquals(1, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW WHERE WORK_FLOW_ID = 1"));
    assertEquals(1, count("SELECT COUNT(*) FROM PROCESSES_WORK WHERE WORKFLOW_ID = 1"));
    assertEquals(2, count("SELECT COUNT(*) FROM PROCESSES_REQUEST WHERE WORK_FLOW_ID = 1"));
  }

  /**
   * Runs a count query on a fresh read of the database.
   *
   * @param query the query
   * @return its single value
   * @throws SQLException on a query failure
   */
  private int count(String query) throws SQLException {
    connection.commit();
    try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(query)) {
      rows.next();
      return rows.getInt(1);
    }
  }
}
