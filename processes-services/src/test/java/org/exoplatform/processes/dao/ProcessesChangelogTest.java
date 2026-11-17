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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.lang.reflect.Field;
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

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import org.exoplatform.commons.persistence.impl.EntityManagerHolder;
import org.exoplatform.processes.entity.RequestEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;

/**
 * Runs the processes changelog on HSQLDB and checks what EXO-91987 adds to it:
 * several processes may share a task project, the request record table and the
 * process's space and move columns exist with the entities' columns, and the
 * added changesets roll back and apply again. MySQL and PostgreSQL are not
 * covered here.
 */
public class ProcessesChangelogTest {

  /** The master changelog the Kernel changelogs plugin runs. */
  private static final String CHANGELOG              = "db/changelog/processes-rdbms.db.changelog-master.xml";

  /** The first changeset EXO-91987 adds. */
  private static final String FIRST_CHANGESET        = "1.0.0-exo-91987-01";

  /** How many changesets the changelog held before EXO-91987. */
  private static final int    CHANGESETS_BEFORE      = 9;

  private static final String WORK_FLOW_TABLE        = "PROCESSES_WORK_FLOW";

  private static final String REQUEST_TABLE          = "PROCESSES_REQUEST";

  private static final String LABELS_TABLE           = "PROCESSES_WORK_FLOW_LABELS";

  private String              url;

  private Connection          connection;

  /**
   * Opens a fresh in-memory database for each test.
   *
   * @throws SQLException when the database cannot be opened
   */
  @Before
  public void openDatabase() throws SQLException {
    url = "jdbc:hsqldb:mem:processes-changelog-" + System.nanoTime();
    connection = DriverManager.getConnection(url, "SA", "");
  }

  /**
   * Drops the database.
   *
   * @throws SQLException when it cannot be shut down
   */
  @After
  public void dropDatabase() throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("SHUTDOWN");
    }
    connection.close();
  }

  /**
   * Two processes may feed the same task project once the changelog ran, where
   * the former unique constraint refused it. Mutation: remove changeset
   * 1.0.0-exo-91987-01 and the second insert fails.
   *
   * @throws Exception when Liquibase or the engine fails
   */
  @Test
  public void twoProcessesMayShareATaskProject() throws Exception {
    update(connection);

    insertWorkFlow(1, 10);
    insertWorkFlow(2, 10);
    connection.commit();

    assertEquals(2, count("SELECT COUNT(*) FROM " + WORK_FLOW_TABLE + " WHERE PROJECT_ID = 10"));
  }

  /**
   * Before EXO-91987 the engine refuses a second process of one project: the
   * test above passes because of the changeset, not because HSQLDB ignores the
   * constraint.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  public void theFormerSchemaRefusesASharedTaskProject() throws Exception {
    liquibase(connection).update(CHANGESETS_BEFORE, new Contexts(), new LabelExpression());
    insertWorkFlow(1, 10);

    assertThrows(SQLException.class, () -> insertWorkFlow(2, 10));
  }

  /**
   * A process that existed before the upgrade gets no space and an idle move
   * state: no move pending, sequence and attempts at 0, no next attempt.
   * Mutation: drop a default from changeset 1.0.0-exo-91987-03 and this test
   * fails.
   *
   * @throws Exception when Liquibase or the engine fails
   */
  @Test
  public void anExistingProcessGetsAnIdleMoveState() throws Exception {
    liquibase(connection).update(CHANGESETS_BEFORE, new Contexts(), new LabelExpression());
    insertWorkFlow(1, 10);
    connection.commit();
    assertEquals("the process must exist before the upgrade", 1, count("SELECT COUNT(*) FROM " + WORK_FLOW_TABLE));

    update(connection);

    try (Statement statement = connection.createStatement();
        ResultSet row = statement.executeQuery("SELECT SPACE_ID, PROJECT_MOVE_PENDING, MOVE_SEQ, MOVE_ATTEMPTS, "
            + "MOVE_NEXT_ATTEMPT, MOVE_OWNER FROM " + WORK_FLOW_TABLE + " WHERE WORK_FLOW_ID = 1")) {
      assertTrue(row.next());
      row.getLong("SPACE_ID");
      assertTrue("no space until the upgrade plugin sets it", row.wasNull());
      assertFalse(row.getBoolean("PROJECT_MOVE_PENDING"));
      assertEquals(0, row.getLong("MOVE_SEQ"));
      assertEquals(0, row.getInt("MOVE_ATTEMPTS"));
      row.getLong("MOVE_NEXT_ATTEMPT");
      assertTrue(row.wasNull());
      assertEquals(null, row.getString("MOVE_OWNER"));
    }
  }

  /**
   * A request record needs an existing process, and one task is the request of
   * one process only. Mutation: drop the foreign key or the primary key from
   * changeset 1.0.0-exo-91987-02 and this test fails.
   *
   * @throws Exception when Liquibase or the engine fails
   */
  @Test
  public void aRequestRecordNeedsAnExistingProcessAndOneRowPerTask() throws Exception {
    update(connection);
    insertWorkFlow(1, 10);
    insertRequest(100, 1, 5);

    assertThrows("a request of a missing process", SQLException.class, () -> insertRequest(101, 2, 5));
    assertThrows("a second record for the same task", SQLException.class, () -> insertRequest(100, 1, 6));
  }

  /**
   * Every column the request record and the workflow entities map is one the
   * changelog creates, so the entities match the schema rather than one
   * Hibernate would generate.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  public void everyColumnTheEntitiesMapExists() throws Exception {
    update(connection);

    assertEntityColumnsExist(RequestEntity.class);
    assertEntityColumnsExist(WorkFlowEntity.class);
  }

  /**
   * The requests of a process are indexed by process then task, in that order,
   * so a keyset read of one process's requests by task id needs no sort, and
   * by requester. Mutation: drop TASK_ID from IDX_PROCESSES_REQUEST_01, or
   * swap its columns, and this test fails.
   *
   * @throws Exception when Liquibase or the metadata read fails
   */
  @Test
  public void theRequestRecordsAreIndexedByProcessThenTask() throws Exception {
    update(connection);

    assertEquals(java.util.List.of("WORK_FLOW_ID", "TASK_ID"), indexColumns(REQUEST_TABLE, "IDX_PROCESSES_REQUEST_01"));
    assertEquals(java.util.List.of("CREATOR_ID"), indexColumns(REQUEST_TABLE, "IDX_PROCESSES_REQUEST_02"));
  }

  /**
   * Reads the columns of an index, in their order.
   *
   * @param table the table name
   * @param index the index name
   * @return the column names
   * @throws SQLException on a metadata read failure
   */
  private java.util.List<String> indexColumns(String table, String index) throws SQLException {
    java.util.SortedMap<Short, String> columns = new java.util.TreeMap<>();
    try (ResultSet rows = connection.getMetaData().getIndexInfo(null, null, table, false, false)) {
      while (rows.next()) {
        if (index.equals(rows.getString("INDEX_NAME"))) {
          columns.put(rows.getShort("ORDINAL_POSITION"), rows.getString("COLUMN_NAME"));
        }
      }
    }
    return new java.util.ArrayList<>(columns.values());
  }

  /**
   * A request record goes through {@link RequestDAO} into the table the
   * changelog creates and is found by its task id, and the deletion of a
   * process's records, run by the engine, removes those of that process only.
   * Mutation: drop the process condition of the deletion and the other
   * process's record goes too.
   *
   * @throws Exception when Liquibase or Hibernate fails
   */
  @Test
  public void aRequestRecordRoundTripsThroughItsDao() throws Exception {
    update(connection);
    insertWorkFlow(1, 10);
    insertWorkFlow(2, 10);
    insertRequest(200, 2, 6);
    connection.commit();

    EntityManagerFactory entityManagerFactory = new HibernatePersistenceConfiguration("processes-changelog-test")
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
    EntityManager entityManager = entityManagerFactory.createEntityManager();
    try (MockedStatic<EntityManagerHolder> holder = mockStatic(EntityManagerHolder.class)) {
      holder.when(EntityManagerHolder::get).thenReturn(entityManager);
      RequestDAO requestDAO = new RequestDAO();
      entityManager.getTransaction().begin();
      requestDAO.create(new RequestEntity(100L, 1L, 5L));
      entityManager.getTransaction().commit();
      entityManager.clear();

      assertEquals(new RequestEntity(100L, 1L, 5L), requestDAO.find(100L));

      // Deleting a process's records leaves the other processes' ones
      entityManager.getTransaction().begin();
      requestDAO.create(new RequestEntity(101L, 1L, 5L));
      entityManager.getTransaction().commit();
      entityManager.getTransaction().begin();
      assertEquals(2, requestDAO.deleteByWorkFlowId(1L));
      entityManager.getTransaction().commit();
    } finally {
      entityManager.close();
      entityManagerFactory.close();
    }
    assertEquals(0, count("SELECT COUNT(*) FROM " + REQUEST_TABLE + " WHERE WORK_FLOW_ID = 1"));
    assertEquals(1, count("SELECT COUNT(*) FROM " + REQUEST_TABLE + " WHERE WORK_FLOW_ID = 2"));
  }

  /**
   * Rolling back the changesets EXO-91987 adds removes the request table and the
   * new columns and puts back the uniqueness of a process's project, and the
   * changelog applies again from there.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  public void theAddedChangesetsRollBackAndReapply() throws Exception {
    update(connection);

    liquibase(connection).rollback(changesetsSince(FIRST_CHANGESET), new Contexts(), new LabelExpression());

    assertFalse("the request table must be dropped", tableExists(REQUEST_TABLE));
    assertFalse("and the space column", columnExists(WORK_FLOW_TABLE, "SPACE_ID"));
    assertFalse("and the move columns", columnExists(WORK_FLOW_TABLE, "MOVE_SEQ"));
    assertFalse("and the label table", tableExists(LABELS_TABLE));
    insertWorkFlow(1, 10);
    assertThrows("the project is unique again", SQLException.class, () -> insertWorkFlow(2, 10));
    connection.rollback();

    update(connection);

    assertTrue(tableExists(REQUEST_TABLE));
    assertTrue(columnExists(WORK_FLOW_TABLE, "SPACE_ID"));
    assertTrue(tableExists(LABELS_TABLE));
  }

  /**
   * A process keeps a label once, and only a process that exists keeps
   * labels; a label row needs no Task label, which Tasks may delete. Mutation:
   * drop the primary key or the foreign key and an insert passes.
   *
   * @throws Exception when Liquibase or the engine fails
   */
  @Test
  public void aProcessKeepsEachLabelOnce() throws Exception {
    update(connection);
    insertWorkFlow(1, 10);
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO " + LABELS_TABLE + " (WORK_FLOW_ID, LABEL_ID) VALUES (1, 7)");
    }
    connection.commit();

    assertThrows("a label twice", SQLException.class, () -> {
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate("INSERT INTO " + LABELS_TABLE + " (WORK_FLOW_ID, LABEL_ID) VALUES (1, 7)");
      }
    });
    connection.rollback();
    assertThrows("a label of no process", SQLException.class, () -> {
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate("INSERT INTO " + LABELS_TABLE + " (WORK_FLOW_ID, LABEL_ID) VALUES (99, 7)");
      }
    });
    connection.rollback();
    assertEquals(1, count("SELECT COUNT(*) FROM " + LABELS_TABLE));
  }

  /**
   * The rollback of the dropped uniqueness fails once two processes share a
   * project, as its changeset says: the data must be unshared first.
   *
   * @throws Exception when Liquibase fails
   */
  @Test
  public void theUniquenessCannotComeBackOverASharedProject() throws Exception {
    update(connection);
    insertWorkFlow(1, 10);
    insertWorkFlow(2, 10);
    connection.commit();
    assertEquals("both processes must be committed before the rollback",
                 2,
                 count("SELECT COUNT(*) FROM " + WORK_FLOW_TABLE + " WHERE PROJECT_ID = 10"));

    assertThrows(Exception.class,
                 () -> liquibase(connection).rollback(changesetsSince(FIRST_CHANGESET),
                                                      new Contexts(),
                                                      new LabelExpression()));
  }

  /**
   * Checks that each column an entity maps exists in its table.
   *
   * @param entityClass the entity
   * @throws SQLException on a metadata read failure
   */
  private void assertEntityColumnsExist(Class<?> entityClass) throws SQLException {
    String table = entityClass.getAnnotation(Table.class).name();
    int mapped = 0;
    for (Field field : entityClass.getDeclaredFields()) {
      Column column = field.getAnnotation(Column.class);
      if (column != null) {
        mapped++;
        // An element collection's column is in its own table
        CollectionTable collectionTable = field.getAnnotation(CollectionTable.class);
        String columnTable = collectionTable == null ? table : collectionTable.name();
        assertTrue(columnTable + "." + column.name() + " must exist", columnExists(columnTable, column.name()));
      }
    }
    assertTrue(table + " must map columns", mapped > 0);
  }

  /**
   * Inserts a process row with the columns every changeset requires.
   *
   * @param id the process id
   * @param projectId its task project id
   * @throws SQLException when the engine refuses the row
   */
  private void insertWorkFlow(long id, long projectId) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO " + WORK_FLOW_TABLE + " (WORK_FLOW_ID, TITLE, DESCRIPTION, SUMMARY, PROJECT_ID) VALUES ("
          + id + ", 'Process " + id + "', 'description', 'summary', " + projectId + ")");
    }
  }

  /**
   * Inserts a request record.
   *
   * @param taskId the request's task id
   * @param workFlowId its process id
   * @param creatorId its requester's identity id
   * @throws SQLException when the engine refuses the row
   */
  private void insertRequest(long taskId, long workFlowId, long creatorId) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO " + REQUEST_TABLE + " (TASK_ID, WORK_FLOW_ID, CREATOR_ID) VALUES (" + taskId + ", "
          + workFlowId + ", " + creatorId + ")");
    }
  }

  /**
   * Counts the changesets that ran from a given one to the end of the changelog,
   * that one included.
   *
   * @param firstId the first changeset to count
   * @return the count
   * @throws SQLException on a query failure
   */
  private int changesetsSince(String firstId) throws SQLException {
    return count("SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ORDEREXECUTED >= "
        + "(SELECT ORDEREXECUTED FROM DATABASECHANGELOG WHERE ID = '" + firstId + "')");
  }

  /**
   * Runs a count query.
   *
   * @param query the query
   * @return its single value
   * @throws SQLException on a query failure
   */
  private int count(String query) throws SQLException {
    try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(query)) {
      rows.next();
      return rows.getInt(1);
    }
  }

  /**
   * Whether a table exists.
   *
   * @param table the table name
   * @return true when it exists
   * @throws SQLException on a metadata read failure
   */
  private boolean tableExists(String table) throws SQLException {
    try (ResultSet tables = connection.getMetaData().getTables(null, null, table, null)) {
      return tables.next();
    }
  }

  /**
   * Whether a column exists.
   *
   * @param table the table name
   * @param column the column name
   * @return true when it exists
   * @throws SQLException on a metadata read failure
   */
  private boolean columnExists(String table, String column) throws SQLException {
    try (ResultSet columns = connection.getMetaData().getColumns(null, null, table, column)) {
      return columns.next();
    }
  }

  /**
   * Applies the whole changelog. Liquibase leaves the connection out of
   * auto-commit, so the tests commit the rows they write before a rollback.
   *
   * @param connection the database
   * @throws Exception when Liquibase fails
   */
  private static void update(Connection connection) throws Exception {
    liquibase(connection).update(new Contexts(), new LabelExpression());
  }

  /**
   * A Liquibase instance over the processes changelog.
   *
   * @param connection the database
   * @return the instance
   * @throws Exception when the database cannot be wrapped
   */
  private static Liquibase liquibase(Connection connection) throws Exception {
    Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
    return new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
  }
}
