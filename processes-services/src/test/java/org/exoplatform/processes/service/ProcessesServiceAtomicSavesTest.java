/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.processes.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.dao.RequestDAO;
import org.exoplatform.processes.dao.WorkDraftDAO;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.RequestEntity;
import org.exoplatform.processes.entity.WorkEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.plugin.RequestValuesPlugin;
import org.exoplatform.processes.storage.ProcessesStorageImpl;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.organization.GroupHandler;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.service.LabelService;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.StatusService;
import org.exoplatform.task.service.TaskService;

/**
 * Runs the two saves the Service makes atomic with another add-on's writes,
 * a request draft with its form values and a process with its form, through
 * the real storage and DAOs, on the schema the processes changelog creates on
 * HSQLDB, inside one transaction begun here as the atomic methods' aspect
 * begins it, or through the atomic methods themselves, whose woven aspect
 * begins and rolls back its own transaction on the entity manager the current
 * container gives. The plugin stands for Forms: where a test needs Forms'
 * rows, it writes a marker row through the same entity manager. Forms' own
 * writes joining the platform's transaction are checked on an acceptance
 * server.
 */
public class ProcessesServiceAtomicSavesTest {

  private static final String                               CHANGELOG = "db/changelog/processes-rdbms.db.changelog-master.xml";

  private static final long                                 USER_ID   = 5L;

  private static final Map<Long, Map<String, List<Object>>> VALUES    = Map.of(12L, Map.of("reason", List.of("Holiday")));

  private Connection                                        connection;

  private EntityManagerFactory                              entityManagerFactory;

  private EntityManager                                     entityManager;

  private MockedStatic<EntityManagerHolder>                 entityManagerHolder;

  private MockedStatic<RequestLifeCycle>                    requestLifeCycle;

  private WorkFlowDAO                                       workFlowDAO;

  private WorkDraftDAO                                      workDraftDAO;

  private RequestValuesPlugin                               plugin;

  private ProcessesStorageImpl                              storage;

  private ProcessesServiceImpl                              processesService;

  /**
   * Builds the schema with the changelog, process 1 in project 10 and draft
   * 50 of user 5 on it, and the Service on the real storage with a mocked
   * plugin.
   *
   * @throws Exception when Liquibase or the engine fails
   */
  @Before
  public void createSchema() throws Exception {
    String url = "jdbc:hsqldb:mem:processes-atomic-" + System.nanoTime();
    connection = DriverManager.getConnection(url, "SA", "");
    new Liquibase(CHANGELOG,
                  new ClassLoaderResourceAccessor(),
                  DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)))
                                                                                                                  .update(new Contexts(),
                                                                                                                          new LabelExpression());
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO PROCESSES_WORK_FLOW (WORK_FLOW_ID, TITLE, DESCRIPTION, SUMMARY, ENABLED, CREATOR_ID, MODIFIER_ID, CREATED_DATE, MODIFIED_DATE, PROJECT_ID) VALUES (1, 'Leave', 'd', 's', TRUE, 5, 5, CURRENT_DATE, CURRENT_DATE, 10)");
      statement.executeUpdate("INSERT INTO PROCESSES_WORK (WORK_ID, TITLE, DESCRIPTION, CREATOR_ID, WORKFLOW_ID, IS_DRAFT, CREATED_DATE, MODIFIED_DATE) VALUES (50, 'draft', 'draft', 5, 1, TRUE, CURRENT_DATE, CURRENT_DATE)");
    }
    connection.commit();
    entityManagerFactory = new HibernatePersistenceConfiguration("processes-atomic-test").managedClass(WorkFlowEntity.class)
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
    IdentityManager identityManager = mock(IdentityManager.class);
    org.exoplatform.social.core.identity.model.Identity john =
                                                             new org.exoplatform.social.core.identity.model.Identity(OrganizationIdentityProvider.NAME,
                                                                                                                     "john");
    john.setId(String.valueOf(USER_ID));
    john.setEnable(true);
    when(identityManager.getIdentity(USER_ID)).thenReturn(john);
    UserACL userACL = mock(UserACL.class);
    when(userACL.getUserIdentity("john")).thenReturn(new Identity("john"));
    workFlowDAO = spy(new WorkFlowDAO());
    workDraftDAO = spy(new WorkDraftDAO());
    storage = new ProcessesStorageImpl(workFlowDAO,
                                       workDraftDAO,
                                       new RequestDAO(),
                                       mock(TaskService.class),
                                       mock(ProjectService.class),
                                       mock(StatusService.class),
                                       identityManager,
                                       mock(SpaceService.class),
                                       mock(ListenerService.class),
                                       mock(ProcessesAttachmentService.class),
                                       mock(FileService.class),
                                       userACL,
                                       mock(LabelService.class));
    processesService = new ProcessesServiceImpl(storage, null, identityManager, userACL, null, null, null, null, null, null);
    plugin = mock(RequestValuesPlugin.class);
    processesService.addRequestValuesPlugin(plugin);
    // The woven transaction aspect finds the entity manager through the
    // current container, as on the platform
    EntityManagerService entityManagerService = mock(EntityManagerService.class);
    when(entityManagerService.getEntityManager()).thenReturn(entityManager);
    when(entityManagerService.getEntityManagerFactory()).thenReturn(entityManagerFactory);
    ExoContainer container = mock(ExoContainer.class);
    when(container.getComponentInstanceOfType(EntityManagerService.class)).thenReturn(entityManagerService);
    // A process read maps its managers through the services of the current
    // container
    OrganizationService organizationService = mock(OrganizationService.class);
    when(organizationService.getGroupHandler()).thenReturn(mock(GroupHandler.class));
    when(container.getComponentInstanceOfType(OrganizationService.class)).thenReturn(organizationService);
    when(container.getComponentInstanceOfType(SpaceService.class)).thenReturn(mock(SpaceService.class));
    ExoContainerContext.setCurrentContainer(container);
    requestLifeCycle = mockStatic(RequestLifeCycle.class);
  }

  /**
   * Drops the database.
   *
   * @throws SQLException when it cannot be shut down
   */
  @After
  public void dropSchema() throws SQLException {
    requestLifeCycle.close();
    ExoContainerContext.setCurrentContainer(null);
    entityManagerHolder.close();
    entityManager.close();
    entityManagerFactory.close();
    try (Statement statement = connection.createStatement()) {
      statement.execute("SHUTDOWN");
    }
    connection.close();
  }

  /**
   * A new draft whose values are refused after its row was written leaves no
   * draft: the row's write joins the transaction. Mutation: make the draft's
   * row commit on its own and the draft stays.
   *
   * @throws Exception on a count failure
   */
  @Test
  public void aNewDraftRefusedForItsValuesLeavesNoDraft() throws Exception {
    doAnswer(invocation -> {
      entityManager.flush();
      throw new IllegalArgumentException("forms.value.nullNotAllowed");
    }).when(plugin).writeDraftValues(anyLong(), anyLong(), any(), any());

    Work draft = draft(0L, "new draft");
    entityManager.getTransaction().begin();
    assertThrows(IllegalArgumentException.class, () -> processesService.writeWorkDraftRows(draft, USER_ID, VALUES, true));
    entityManager.getTransaction().rollback();
    entityManager.clear();

    assertEquals(0, count("SELECT COUNT(*) FROM PROCESSES_WORK WHERE TITLE = 'new draft'"));
    assertEquals(1, count("SELECT COUNT(*) FROM PROCESSES_WORK"));
  }

  /**
   * A draft update whose row fails after its values were written leaves
   * neither: the values, written first, roll back with the row. Mutation:
   * commit the values' write on its own before the row's and the values'
   * marker stays.
   *
   * @throws Exception on a count failure
   */
  @Test
  public void aDraftUpdateWhoseRowFailsLeavesNeitherItsValuesNorItsRow() throws Exception {
    doAnswer(invocation -> {
      entityManager.createNativeQuery("INSERT INTO PROCESSES_WORK_FLOW_LABELS (WORK_FLOW_ID, LABEL_ID) VALUES (1, 77)")
                   .executeUpdate();
      return null;
    }).when(plugin).writeDraftValues(eq(1L), eq(50L), any(), any());
    doAnswer(invocation -> {
      invocation.callRealMethod();
      entityManager.flush();
      throw new PersistenceException("the row's write fails after the values");
    }).when(workDraftDAO).update(any(WorkEntity.class));

    Work draft = draft(50L, "draft renamed");
    entityManager.getTransaction().begin();
    assertThrows(PersistenceException.class, () -> processesService.writeWorkDraftRows(draft, USER_ID, VALUES, true));
    entityManager.getTransaction().rollback();
    entityManager.clear();

    assertEquals(0, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW_LABELS"));
    assertEquals(1, count("SELECT COUNT(*) FROM PROCESSES_WORK WHERE WORK_ID = 50 AND TITLE = 'draft'"));
  }

  /**
   * A process save whose form is refused after the process row was written
   * leaves the process as it was: the row's write joins the transaction.
   * Mutation: make the process row commit on its own and the new title stays.
   *
   * @throws Exception on a count failure
   */
  @Test
  public void aProcessSaveRefusedForItsFormLeavesTheProcessUnchanged() throws Exception {
    doAnswer(invocation -> {
      entityManager.flush();
      throw new IllegalArgumentException("forms.form.notApplicable");
    }).when(plugin).setProcessForm(eq(1L), eq(38L), any());

    entityManager.getTransaction().begin();
    assertThrows(IllegalArgumentException.class, () -> processesService.writeWorkFlowRows(process("Leave request", 38L), USER_ID));
    entityManager.getTransaction().rollback();
    entityManager.clear();

    assertEquals(1, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW WHERE WORK_FLOW_ID = 1 AND TITLE = 'Leave'"));
  }

  /**
   * The draft save's atomic method, called with no transaction open, begins
   * one: values refused after the row was written leave no draft. Mutation:
   * drop the annotation's atomic flag and the row commits on its own, or its
   * write fails for want of a transaction.
   *
   * @throws Exception on a count failure
   */
  @Test
  public void theAtomicDraftSaveBeginsTheTransactionItRollsBack() throws Exception {
    doAnswer(invocation -> {
      entityManager.flush();
      throw new IllegalArgumentException("forms.value.nullNotAllowed");
    }).when(plugin).writeDraftValues(anyLong(), anyLong(), any(), any());
    Work draft = draft(0L, "new draft");

    assertThrows(IllegalArgumentException.class, () -> processesService.saveWorkDraftRows(draft, USER_ID, VALUES, true));
    entityManager.clear();

    assertEquals(0, count("SELECT COUNT(*) FROM PROCESSES_WORK WHERE TITLE = 'new draft'"));
  }

  /**
   * The process save's atomic method, called with no transaction open, begins
   * one: a form refused after the process row was written leaves the process
   * as it was. Mutation: drop the annotation's atomic flag and the new title
   * commits on its own, or its write fails for want of a transaction.
   *
   * @throws Exception on a count failure
   */
  @Test
  public void theAtomicProcessSaveBeginsTheTransactionItRollsBack() throws Exception {
    doAnswer(invocation -> {
      entityManager.flush();
      throw new IllegalArgumentException("forms.form.notApplicable");
    }).when(plugin).setProcessForm(eq(1L), eq(38L), any());

    assertThrows(IllegalArgumentException.class, () -> processesService.saveWorkFlowRows(process("Leave request", 38L), USER_ID));
    entityManager.clear();

    assertEquals(1, count("SELECT COUNT(*) FROM PROCESSES_WORK_FLOW WHERE WORK_FLOW_ID = 1 AND TITLE = 'Leave'"));
  }

  /**
   * Builds a draft of user 5 on process 1, as the Service sets it before the
   * write: the stored process, the server's draft state.
   *
   * @param id the draft id, 0 for a new one
   * @param title its title
   * @return the draft
   */
  private Work draft(long id, String title) {
    WorkFlow storedProcess = storage.getWorkFlowById(1L, null);
    return new Work(id, title, "draft", USER_ID, new Date(), new Date(), null, true, storedProcess);
  }

  /**
   * Builds the update of process 1, in project 10, with a form.
   *
   * @param title its title
   * @param requestFormId its form id
   * @return the process
   */
  private static WorkFlow process(String title, Long requestFormId) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(1L);
    workFlow.setTitle(title);
    workFlow.setDescription("d");
    workFlow.setSummary("s");
    workFlow.setEnabled(true);
    workFlow.setProjectId(10L);
    workFlow.setCreatorId(USER_ID);
    workFlow.setCreatedDate(new Date());
    workFlow.setRequestsCreators(new ArrayList<>());
    workFlow.setRequestFormId(requestFormId);
    return workFlow;
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
