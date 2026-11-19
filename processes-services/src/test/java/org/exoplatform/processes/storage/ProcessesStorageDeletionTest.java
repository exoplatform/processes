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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

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
import org.exoplatform.task.service.LabelService;
import org.exoplatform.commons.persistence.impl.EntityManagerHolder;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.dao.RequestDAO;
import org.exoplatform.processes.dao.WorkDraftDAO;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.RequestEntity;
import org.exoplatform.processes.entity.WorkEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.model.WorkFlowLabel;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.task.dto.LabelDto;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.processes.service.ProcessesAttachmentService;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.StatusService;
import org.exoplatform.task.service.CommentService;
import org.exoplatform.task.service.TaskService;

/**
 * Runs the deletion of a process (EXO-93633) and the save of its rows with
 * its labels (EXO-91988) through the real DAOs, on the schema the processes
 * changelog creates on HSQLDB, inside one transaction, as the atomic methods
 * that wrap them do. The wrapping annotation
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

  private IdentityManager           identityManager;

  private LabelService              labelService;

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
      statement.executeUpdate("INSERT INTO PROCESSES_WORK_FLOW_LABELS (WORK_FLOW_ID, LABEL_ID) VALUES (2, 9)");
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
    identityManager = mock(IdentityManager.class);
    when(identityManager.getIdentity(5L)).thenReturn(new Identity(OrganizationIdentityProvider.NAME, "john"));
    labelService = mock(LabelService.class);
    when(labelService.getLabel(7L)).thenReturn(label(7L, "Holiday", 10L));
    when(labelService.getLabel(8L)).thenReturn(label(8L, "Other project", 11L));
    workFlowDAO = spy(new WorkFlowDAO());
    storage = new ProcessesStorageImpl(workFlowDAO,
                                       new WorkDraftDAO(),
                                       new RequestDAO(),
                                       mock(TaskService.class),
                                       projectService,
                                       mock(StatusService.class),
                                       identityManager,
                                       mock(SpaceService.class),
                                       mock(ListenerService.class),
                                       mock(ProcessesAttachmentService.class),
                                       mock(FileService.class),
                                       mock(UserACL.class),
                                       labelService,
                                       mock(CommentService.class));
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
   * Deleting a process deletes its label rows with it, and leaves the other
   * process's labels. Mutation: map the labels with no collection table and
   * the foreign key refuses the deletion.
   *
   * @throws SQLException on a count failure
   */
  @Test
  public void aDeletionRemovesTheProcessLabels() throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO PROCESSES_WORK_FLOW_LABELS (WORK_FLOW_ID, LABEL_ID) VALUES (1, 7)");
    }
    connection.commit();

    entityManager.getTransaction().begin();
    storage.deleteWorkflowRows(1L);
    entityManager.getTransaction().commit();

    assertEquals(0, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW_LABELS WHERE WORK_FLOW_ID = 1"));
    assertEquals(1, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW_LABELS WHERE WORK_FLOW_ID = 2"));
  }

  /**
   * Deleting a process deletes its requester form rows with it, and leaves
   * the other process's rows. Mutation: map the requester forms with no
   * collection table and the foreign key refuses the deletion.
   *
   * @throws SQLException on a count failure
   */
  @Test
  public void aDeletionRemovesTheProcessRequesterForms() throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO PROCESSES_WORK_FLOW_REQ_FORMS (WORK_FLOW_ID, FORM_ID) VALUES (1, 63)");
      statement.executeUpdate("INSERT INTO PROCESSES_WORK_FLOW_REQ_FORMS (WORK_FLOW_ID, FORM_ID) VALUES (2, 63)");
    }
    connection.commit();

    entityManager.getTransaction().begin();
    storage.deleteWorkflowRows(1L);
    entityManager.getTransaction().commit();

    assertEquals(0, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW_REQ_FORMS WHERE WORK_FLOW_ID = 1"));
    assertEquals(1, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW_REQ_FORMS WHERE WORK_FLOW_ID = 2"));
  }

  /**
   * A save writes the requester form ids it is given on the schema the
   * changelog builds, keeps the stored ones when given none, and replaces
   * them when given others; a save rolled back leaves none; the narrow read
   * gives them in id order. Mutation: keep them out of the saved entity, let
   * a null list clear them, or map another table or column, and a count
   * fails.
   *
   * @throws SQLException on a count failure
   */
  @Test
  public void aSaveWritesTheRequesterFormsItIsGivenInTheTransaction() throws SQLException {
    WorkFlow chosen = process("Leave", null);
    chosen.setRequesterFormIds(List.of(64L, 63L));
    entityManager.getTransaction().begin();
    storage.saveWorkFlow(chosen, 5L);
    entityManager.getTransaction().rollback();
    entityManager.clear();
    assertEquals(0, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW_REQ_FORMS WHERE WORK_FLOW_ID = 1"));

    entityManager.getTransaction().begin();
    storage.saveWorkFlow(chosen, 5L);
    entityManager.getTransaction().commit();
    entityManager.clear();
    assertEquals(2, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW_REQ_FORMS WHERE WORK_FLOW_ID = 1"));
    assertEquals(List.of(63L, 64L), storage.getRequesterFormIds(1L));

    entityManager.getTransaction().begin();
    storage.saveWorkFlow(process("Leave requests", null), 5L);
    entityManager.getTransaction().commit();
    entityManager.clear();
    assertEquals(2, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW_REQ_FORMS WHERE WORK_FLOW_ID = 1"));

    WorkFlow replaced = process("Leave requests", null);
    replaced.setRequesterFormIds(List.of(64L));
    entityManager.getTransaction().begin();
    storage.saveWorkFlow(replaced, 5L);
    entityManager.getTransaction().commit();
    entityManager.clear();
    assertEquals(1, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW_REQ_FORMS WHERE WORK_FLOW_ID = 1 AND FORM_ID = 64"));
    assertEquals(1, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW_REQ_FORMS WHERE WORK_FLOW_ID = 1"));
    assertEquals(List.of(), storage.getRequesterFormIds(99L));
  }

  /**
   * A save writes the label ids it is given, keeps the stored ones when given
   * none, and replaces them when given others; a save rolled back after its
   * writes leaves neither the process's new title nor its labels, so the
   * storage commits nothing on its own inside the transaction the Service's
   * atomic method begins. Mutation: keep the labels out of the saved entity,
   * or let a null list clear them, and a count fails.
   *
   * @throws SQLException on a count failure
   */
  @Test
  public void aSaveWritesTheLabelsItIsGivenInTheTransaction() throws SQLException {
    entityManager.getTransaction().begin();
    storage.saveWorkFlow(process("Leave", List.of(new WorkFlowLabel(7L, "Holiday"), new WorkFlowLabel(8L, "x"))), 5L);
    entityManager.getTransaction().rollback();
    entityManager.clear();
    assertEquals(0, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW_LABELS WHERE WORK_FLOW_ID = 1"));
    assertEquals(1, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW WHERE WORK_FLOW_ID = 1 AND TITLE = 'Leave'"));

    entityManager.getTransaction().begin();
    storage.saveWorkFlow(process("Leave request", List.of(new WorkFlowLabel(7L, "Holiday"), new WorkFlowLabel(8L, "x"))), 5L);
    entityManager.getTransaction().commit();
    entityManager.clear();
    assertEquals(2, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW_LABELS WHERE WORK_FLOW_ID = 1"));

    entityManager.getTransaction().begin();
    storage.saveWorkFlow(process("Leave requests", null), 5L);
    entityManager.getTransaction().commit();
    entityManager.clear();
    assertEquals(2, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW_LABELS WHERE WORK_FLOW_ID = 1"));

    entityManager.getTransaction().begin();
    storage.saveWorkFlow(process("Leave requests", List.of(new WorkFlowLabel(8L, "x"))), 5L);
    entityManager.getTransaction().commit();
    entityManager.clear();
    assertEquals(1, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW_LABELS WHERE WORK_FLOW_ID = 1 AND LABEL_ID = 8"));
    assertEquals(1, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW_LABELS WHERE WORK_FLOW_ID = 1"));
  }

  /**
   * When the save fails after the process row and its labels were written in
   * the same transaction, the rollback leaves neither: the storage's writes
   * join the transaction rather than commit on their own. Mutation: make the
   * update commit on its own and the new title and labels stay.
   *
   * @throws SQLException on a count failure
   */
  @Test
  public void aFailingSaveLeavesNeitherTheProcessChangeNorItsLabels() throws SQLException {
    doAnswer(invocation -> {
      invocation.callRealMethod();
      entityManager.flush();
      throw new PersistenceException("the save fails after its writes");
    }).when(workFlowDAO).update(any(WorkFlowEntity.class));

    entityManager.getTransaction().begin();
    assertThrows(PersistenceException.class,
                 () -> storage.saveWorkFlow(process("Leave request", List.of(new WorkFlowLabel(7L, "Holiday"))), 5L));
    entityManager.getTransaction().rollback();
    entityManager.clear();

    assertEquals(0, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW_LABELS WHERE WORK_FLOW_ID = 1"));
    assertEquals(1, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW WHERE WORK_FLOW_ID = 1 AND TITLE = 'Leave'"));
  }

  /**
   * The storage never saves a label the Service did not resolve to a label of
   * the project: a label with no id is refused before any write. Mutation:
   * skip the check and the save writes a label id of 0.
   *
   * @throws SQLException on a count failure
   */
  @Test
  public void aSaveRefusesALabelWithNoId() throws SQLException {
    entityManager.getTransaction().begin();
    IllegalArgumentException refusal =
                                     assertThrows(IllegalArgumentException.class,
                                                  () -> storage.saveWorkFlow(process("Leave request",
                                                                                     List.of(new WorkFlowLabel(0L, "New one"))),
                                                                             5L));
    entityManager.getTransaction().rollback();
    assertEquals("processes.label.notInProject", refusal.getMessage());
    assertEquals(0, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW_LABELS WHERE WORK_FLOW_ID = 1"));
  }

  /**
   * Reading a process gives its labels that still exist and belong to its
   * project, by id: a label deleted in Tasks (9) or moved to another project
   * (8) is left out. Mutation: drop the project check and label 8 is read.
   *
   * @throws SQLException on an insert failure
   */
  @Test
  public void aReadGivesTheLabelsOfTheProcessProjectOnly() throws SQLException {
    try (Statement statement = connection.createStatement()) {
      // A read maps the process's creator and modifier
      statement.executeUpdate("UPDATE PROCESSES_WORK_FLOW SET CREATOR_ID = 5, MODIFIER_ID = 5 WHERE WORK_FLOW_ID = 1");
      statement.executeUpdate("INSERT INTO PROCESSES_WORK_FLOW_LABELS (WORK_FLOW_ID, LABEL_ID) VALUES (1, 9)");
      statement.executeUpdate("INSERT INTO PROCESSES_WORK_FLOW_LABELS (WORK_FLOW_ID, LABEL_ID) VALUES (1, 8)");
      statement.executeUpdate("INSERT INTO PROCESSES_WORK_FLOW_LABELS (WORK_FLOW_ID, LABEL_ID) VALUES (1, 7)");
    }
    connection.commit();

    assertEquals(List.of(new WorkFlowLabel(7L, "Holiday")), storage.getWorkFlowById(1L, null).getLabels());
  }

  /**
   * Builds the update of process 1, in project 10.
   *
   * @param title its title
   * @param labels its labels, or null
   * @return the process
   */
  private static WorkFlow process(String title, List<WorkFlowLabel> labels) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(1L);
    workFlow.setTitle(title);
    workFlow.setDescription("d");
    workFlow.setSummary("s");
    workFlow.setEnabled(true);
    workFlow.setProjectId(10L);
    workFlow.setCreatorId(5L);
    workFlow.setCreatedDate(new java.util.Date());
    workFlow.setRequestsCreators(new ArrayList<>());
    workFlow.setLabels(labels);
    return workFlow;
  }

  /**
   * Builds a Task label of a project.
   *
   * @param id its id
   * @param name its name
   * @param projectId its project id
   * @return the label
   */
  private static LabelDto label(long id, String name, long projectId) {
    ProjectDto project = new ProjectDto();
    project.setId(projectId);
    LabelDto label = new LabelDto();
    label.setId(id);
    label.setName(name);
    label.setProject(project);
    return label;
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
