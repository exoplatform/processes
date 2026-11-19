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
package org.exoplatform.processes.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.MockedStatic;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import org.exoplatform.commons.persistence.impl.EntityManagerHolder;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.processes.dao.RequestDAO;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.RequestEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dao.TaskQuery;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.TaskService;

/**
 * Sizes the processes side of the request backfill on the processes schema as
 * Liquibase creates it, in HSQLDB: 20,000 tasks over four processes, each fed
 * by its own project, recorded through the DAOs in their real transactions.
 * The Task add-on's page reads and the identity lookups are mocked, so the
 * figure covers the record reads, the record writes and the clears only, not
 * the whole upgrade. It is left out of the
 * default suite; run it with {@code -Dprocesses.upgrade.sizing=true
 * -Dtest=ProcessesRequestUpgradeSizingTest}. It prints the time, the page
 * count and the entity manager clears, and checks a rerun records nothing.
 */
@EnabledIfSystemProperty(named = "processes.upgrade.sizing", matches = "true")
class ProcessesRequestUpgradeSizingTest {

  private static final String CHANGELOG         = "db/changelog/processes-rdbms.db.changelog-master.xml";

  private static final int    PROCESSES         = 4;

  private static final int    TASKS_PER_PROCESS = 5_000;

  /**
   * Runs the backfill on 20,000 tasks, then again, and reports the figures.
   *
   * @throws Exception when the database can't be set up
   */
  @Test
  void theBackfillOfTwentyThousandRequestsIsSized() throws Exception {
    String url = "jdbc:hsqldb:mem:processes-upgrade-sizing-" + System.nanoTime();
    try (Connection connection = DriverManager.getConnection(url, "SA", "")) {
      Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
      new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database).update(new Contexts(), new LabelExpression());
      try (Statement statement = connection.createStatement()) {
        for (int process = 1; process <= PROCESSES; process++) {
          statement.executeUpdate("INSERT INTO PROCESSES_WORK_FLOW (WORK_FLOW_ID, TITLE, DESCRIPTION, SUMMARY, PROJECT_ID, SPACE_ID) VALUES ("
              + process + ", 'Process " + process + "', 'description', 'summary', " + (100 + process) + ", 7)");
        }
      }
      connection.commit();

      EntityManagerFactory entityManagerFactory = new HibernatePersistenceConfiguration("processes-upgrade-sizing")
                                                                                                                  .managedClass(WorkFlowEntity.class)
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
      EntityManager entityManager = spy(entityManagerFactory.createEntityManager());
      EntityManagerService entityManagerService = mock(EntityManagerService.class);
      when(entityManagerService.getEntityManager()).thenReturn(entityManager);
      TaskService taskService = mock(TaskService.class);
      TaskPages taskPages = new TaskPages();
      when(taskService.findTasks(any(TaskQuery.class), anyInt(), anyInt())).thenAnswer(taskPages);
      IdentityManager identityManager = mock(IdentityManager.class);
      Identity mary = new Identity("organization", "mary");
      mary.setId("31");
      when(identityManager.getOrCreateUserIdentity("mary")).thenReturn(mary);

      try (MockedStatic<EntityManagerHolder> holder = mockStatic(EntityManagerHolder.class);
          MockedStatic<ExoContainerContext> container = mockStatic(ExoContainerContext.class, CALLS_REAL_METHODS)) {
        holder.when(EntityManagerHolder::get).thenReturn(entityManager);
        // The transactional aspect woven into the DAOs finds the entity manager here
        container.when(() -> ExoContainerContext.getService(EntityManagerService.class)).thenReturn(entityManagerService);
        ProcessesRequestUpgradePlugin plugin = new ProcessesRequestUpgradePlugin(initParams(),
                                                                                 new WorkFlowDAO(),
                                                                                 new RequestDAO(),
                                                                                 taskService,
                                                                                 mock(ProjectService.class),
                                                                                 identityManager,
                                                                                 mock(SpaceService.class),
                                                                                 entityManagerService);

        long start = System.currentTimeMillis();
        plugin.processUpgrade("7.2.0", "7.3.0");
        long firstRun = System.currentTimeMillis() - start;
        int pages = PROCESSES * (TASKS_PER_PROCESS / ProcessesRequestUpgradePlugin.PAGE_SIZE + 1);
        verify(entityManager, times(pages)).clear();
        assertEquals(PROCESSES * TASKS_PER_PROCESS, count(connection));

        // The rerun reads every task again and records none
        taskPages.reset();
        start = System.currentTimeMillis();
        plugin.processUpgrade("7.2.0", "7.3.0");
        long rerun = System.currentTimeMillis() - start;
        assertEquals(PROCESSES * TASKS_PER_PROCESS, count(connection));

        System.out.println("SIZING ProcessesRequestUpgradePlugin, processes side only (task pages and identities mocked), HSQLDB: "
            + PROCESSES * TASKS_PER_PROCESS + " requests over " + PROCESSES
            + " processes, " + pages + " pages of " + ProcessesRequestUpgradePlugin.PAGE_SIZE
            + ", entity manager cleared after each page, first run " + firstRun + " ms, rerun (nothing to record) " + rerun + " ms");
      } finally {
        entityManager.close();
        entityManagerFactory.close();
        try (Statement statement = connection.createStatement()) {
          statement.execute("SHUTDOWN");
        }
      }
    }
  }

  private static int count(Connection connection) throws Exception {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM PROCESSES_REQUEST")) {
      result.next();
      return result.getInt(1);
    }
  }

  private static InitParams initParams() {
    InitParams params = new InitParams();
    ValueParam groupId = new ValueParam();
    groupId.setName("product.group.id");
    groupId.setValue("org.exoplatform.processes");
    params.addParameter(groupId);
    return params;
  }

  /**
   * Answers each project's tasks page after page, as the Task add-on's keyset
   * read would: each call for a project answers its next page, an empty page
   * at the end.
   */
  private static final class TaskPages implements org.mockito.stubbing.Answer<List<TaskDto>> {

    private final Map<Long, Integer> nextIndex = new HashMap<>();

    /** Starts every project's pages again. */
    void reset() {
      nextIndex.clear();
    }

    @Override
    public List<TaskDto> answer(org.mockito.invocation.InvocationOnMock invocation) {
      TaskQuery query = invocation.getArgument(0);
      int limit = invocation.getArgument(2);
      long projectId = query.getProjectIds().get(0);
      int from = nextIndex.getOrDefault(projectId, 0);
      int to = Math.min(from + limit, TASKS_PER_PROCESS);
      nextIndex.put(projectId, to);
      List<TaskDto> page = new ArrayList<>();
      for (int index = from; index < to; index++) {
        TaskDto task = new TaskDto();
        task.setId(projectId * 1_000_000L + index);
        task.setCreatedBy("mary");
        page.add(task);
      }
      return page;
    }
  }
}
