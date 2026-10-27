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
import java.util.Set;
import java.util.stream.Collectors;

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
import org.exoplatform.processes.model.ProcessesFilter;

/**
 * Runs the listing query {@link WorkFlowDAO} builds through Hibernate over an
 * in-memory HSQLDB database, so the engine parses and executes it. Index usage
 * and the production databases' dialects are not covered here.
 */
public class WorkFlowDAOTest {

  private static EntityManagerFactory       entityManagerFactory;

  private static long                       hrProcess;

  private static long                       disabledHrProcess;

  private static long                       itProcess;

  private static long                       salesProcess;

  private EntityManager                     entityManager;

  private MockedStatic<EntityManagerHolder> entityManagerHolder;

  private final WorkFlowDAO                 workFlowDAO = new WorkFlowDAO();

  @BeforeClass
  public static void createProcesses() {
    entityManagerFactory = new HibernatePersistenceConfiguration("processes-dao-test")
                                                                                       .managedClass(WorkFlowEntity.class)
                                                                                       .property("jakarta.persistence.jdbc.url",
                                                                                                 "jdbc:hsqldb:mem:processes-dao;shutdown=true")
                                                                                       .property("jakarta.persistence.jdbc.user",
                                                                                                 "SA")
                                                                                       .property("jakarta.persistence.jdbc.password",
                                                                                                 "")
                                                                                       .property("hibernate.hbm2ddl.auto",
                                                                                                 "create-drop")
                                                                                       .createEntityManagerFactory();
    EntityManager em = entityManagerFactory.createEntityManager();
    em.getTransaction().begin();
    hrProcess = createProcess(em, "Expense report", true, "/spaces/hr", Set.of("/spaces/hr"));
    disabledHrProcess = createProcess(em, "Holiday request", false, "/spaces/hr", Set.of("/spaces/hr"));
    itProcess = createProcess(em, "Laptop request", true, "/spaces/it", Set.of("/spaces/it"));
    salesProcess = createProcess(em, "Quote approval", true, "/spaces/sales", Set.of("mary", "/spaces/marketing"));
    em.getTransaction().commit();
    em.close();
  }

  @AfterClass
  public static void dropProcesses() {
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
   * Null memberships, a processes administrator's, list every process, the
   * disabled ones included. Mutation: restrict the listing when memberships are
   * null, or unbox the unset manager filter into a primitive, and this test
   * fails.
   */
  @Test
  public void nullMembershipsListEveryProcess() {
    assertEquals(Set.of(hrProcess, disabledHrProcess, itProcess, salesProcess), findWorkFlows(new ProcessesFilter(), null));
  }

  /**
   * A user's memberships restrict the listing to the processes their spaces
   * participate in. Mutation: drop the participator predicate and this test
   * fails.
   */
  @Test
  public void membershipsRestrictTheListingToTheirSpacesProcesses() {
    assertEquals(Set.of(hrProcess, disabledHrProcess), findWorkFlows(new ProcessesFilter(), List.of("john", "member:/spaces/hr")));
  }

  /**
   * The deprecated process manager flag no longer widens a user's listing.
   * Mutation: list every process when the flag is set and this test fails.
   */
  @Test
  @SuppressWarnings("deprecation")
  public void membershipsRestrictTheListingWhateverTheProcessManagerFlag() {
    ProcessesFilter filter = new ProcessesFilter();
    filter.setIsProcessManager(true);

    assertEquals(Set.of(hrProcess, disabledHrProcess), findWorkFlows(filter, List.of("john", "member:/spaces/hr")));
  }

  /**
   * The "I manage" filter lists the processes of the spaces whose manager
   * memberships it receives.
   */
  @Test
  public void managerFilterListsTheProcessesOfTheManagedSpaces() {
    ProcessesFilter filter = new ProcessesFilter();
    filter.setManager(true);

    assertEquals(Set.of(itProcess), findWorkFlows(filter, List.of("manager:/spaces/it")));
  }

  /**
   * With the manager filter set to false, a process is also listed to the
   * users and the members of the spaces its requests creators name. Mutation:
   * join the two predicates with AND, or match the managers against the
   * memberships with their type, and this test fails.
   */
  @Test
  public void requestsCreatorsListTheProcessWhenTheManagerFilterIsFalse() {
    ProcessesFilter filter = new ProcessesFilter();
    filter.setManager(false);

    assertEquals(Set.of(salesProcess), findWorkFlows(filter, List.of("mary")));
    assertEquals(Set.of(salesProcess), findWorkFlows(filter, List.of("jack", "member:/spaces/marketing")));
    assertEquals(Set.of(itProcess), findWorkFlows(filter, List.of("jack", "member:/spaces/it")));
  }

  /**
   * The text and the enabled filters narrow a user's listing.
   */
  @Test
  public void queryAndEnabledFiltersNarrowTheListing() {
    List<String> memberships = List.of("john", "member:/spaces/hr");
    ProcessesFilter textFilter = new ProcessesFilter();
    textFilter.setQuery("Holiday");
    ProcessesFilter enabledFilter = new ProcessesFilter();
    enabledFilter.setEnabled(true);

    assertEquals(Set.of(disabledHrProcess), findWorkFlows(textFilter, memberships));
    assertEquals(Set.of(hrProcess), findWorkFlows(enabledFilter, memberships));
  }

  /**
   * A process that matches several of a user's memberships is listed once.
   * Mutation: drop DISTINCT from the query and this test fails.
   */
  @Test
  public void aProcessMatchingSeveralMembershipsIsListedOnce() {
    ProcessesFilter filter = new ProcessesFilter();
    filter.setManager(false);

    List<WorkFlowEntity> workFlows = workFlowDAO.findWorkFlows(filter,
                                                               List.of("manager:/spaces/hr", "member:/spaces/hr"),
                                                               0,
                                                               0);

    assertEquals(2, workFlows.size());
  }

  private Set<Long> findWorkFlows(ProcessesFilter filter, List<String> memberships) {
    return workFlowDAO.findWorkFlows(filter, memberships, 0, 0)
                      .stream()
                      .map(WorkFlowEntity::getId)
                      .collect(Collectors.toSet());
  }

  private static long createProcess(EntityManager em, String title, boolean enabled, String spaceGroupId, Set<String> managers) {
    WorkFlowEntity workFlow = new WorkFlowEntity();
    workFlow.setTitle(title);
    workFlow.setEnabled(enabled);
    workFlow.setParticipator(Set.of("manager:" + spaceGroupId, "member:" + spaceGroupId));
    workFlow.setManager(managers);
    workFlow.setCreatedDate(new Date());
    workFlow.setModifiedDate(new Date());
    em.persist(workFlow);
    return workFlow.getId();
  }
}
