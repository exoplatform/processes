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
package org.exoplatform.processes.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.model.ProcessDeletion;
import org.exoplatform.processes.model.ProcessPermission;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.social.category.model.CategoryObject;
import io.meeds.social.category.service.CategoryLinkService;
import io.meeds.social.category.service.CategoryService;

/**
 * The processes filed under the platform's categories: the filter by category,
 * the categories a user may filter by, the categories of a listed process, and
 * the unlinking of a deleted one.
 */
@RunWith(MockitoJUnitRunner.class)
public class ProcessesServiceCategoriesTest {

  private static final long         USER_ID = 1L;

  @Mock
  private ProcessesStorage          processesStorage;

  @Mock
  private IdentityManager           identityManager;

  @Mock
  private UserACL                   userACL;

  @Mock
  private ListenerService           listenerService;

  @Mock
  private ProcessTranslationService processTranslationService;

  @Mock
  private CategoryLinkService       categoryLinkService;

  @Mock
  private CategoryService           categoryService;

  /**
   * A filter by category lists the processes filed under the category or one
   * of its subcategories, down to the page's depth, with the client's other
   * criteria kept. Mutations: drop the subcategories, the depth, or the ids,
   * and this test fails.
   *
   * @throws Exception never
   */
  @Test
  public void aCategoryFilterListsTheProcessesOfTheCategoryAndItsSubcategories() throws Exception {
    activeUser();
    when(categoryService.getSubcategoryIds(20L, 0, 0, 4)).thenReturn(List.of(21L));
    when(categoryLinkService.getLinkedObjects(20L, List.of("process"), 0, 0)).thenReturn(List.of(link("5"), link("6")));
    when(categoryLinkService.getLinkedObjects(21L, List.of("process"), 0, 0)).thenReturn(List.of(link("6"),
                                                                                                link("7"),
                                                                                                new CategoryObject("formsList",
                                                                                                                   "8",
                                                                                                                   0L)));
    ProcessesFilter filter = new ProcessesFilter("hr", true, false, null);
    filter.setCategoryId(20L);

    service().getWorkFlows(filter, 0, 10, USER_ID);

    ArgumentCaptor<ProcessesFilter> queried = ArgumentCaptor.forClass(ProcessesFilter.class);
    verify(processesStorage).findWorkFlows(queried.capture(), eq(USER_ID), eq(0), eq(10));
    assertEquals(List.of(5L, 6L, 7L), queried.getValue().getIds());
    assertEquals("hr", queried.getValue().getQuery());
    assertEquals(Boolean.TRUE, queried.getValue().getEnabled());
    assertEquals(Boolean.FALSE, queried.getValue().getManager());
  }

  /**
   * A category that files no process lists none, with no query. Mutation:
   * query the storage with an empty id list and this test fails.
   *
   * @throws Exception never
   */
  @Test
  public void aCategoryFilingNoProcessListsNone() throws Exception {
    activeUser();
    when(categoryService.getSubcategoryIds(20L, 0, 0, 4)).thenReturn(List.of());
    when(categoryLinkService.getLinkedObjects(20L, List.of("process"), 0, 0)).thenReturn(List.of());
    ProcessesFilter filter = new ProcessesFilter();
    filter.setCategoryId(20L);

    assertTrue(service().getWorkFlows(filter, 0, 10, USER_ID).isEmpty());

    verify(processesStorage, never()).findWorkFlows(any(), anyLong(), anyInt(), anyInt());
  }

  /**
   * With no category, the listing is not restricted to ids, whatever ids the
   * caller set: only the service sets them. Mutation: pass the caller's ids
   * through and this test fails.
   *
   * @throws Exception never
   */
  @Test
  public void withNoCategoryTheListingIsNotRestricted() throws Exception {
    activeUser();
    ProcessesFilter filter = new ProcessesFilter("hr", null, null, null);
    filter.setIds(List.of(5L));

    service().getWorkFlows(filter, 0, 10, USER_ID);

    ArgumentCaptor<ProcessesFilter> queried = ArgumentCaptor.forClass(ProcessesFilter.class);
    verify(processesStorage).findWorkFlows(queried.capture(), eq(USER_ID), eq(0), eq(10));
    assertNull(queried.getValue().getIds());
    assertEquals("hr", queried.getValue().getQuery());
    verify(categoryLinkService, never()).getLinkedObjects(anyLong(), any(), anyInt(), anyInt());
  }

  /**
   * The categories a user may filter by are those of the processes they see:
   * those they participate in or may request, enabled, or disabled when they
   * manage them; every process they may request is read, in one read of
   * their rights. Mutations: keep a disabled process the user doesn't manage,
   * a process the user can't see, or the participated processes only, and
   * this test fails.
   *
   * @throws Exception never
   */
  @Test
  public void theCategoriesToFilterByAreThoseOfTheProcessesTheUserSees() throws Exception {
    activeUser();
    List<WorkFlow> listed = new ArrayList<>();
    LongStream.rangeClosed(1, 99).forEach(id -> listed.add(workFlow(id + 100, true, true, false, false)));
    // Enabled, may only add a request
    listed.add(workFlow(1L, true, false, true, false));
    // Disabled, managed
    listed.add(workFlow(2L, false, true, false, true));
    // Disabled, not managed
    listed.add(workFlow(3L, false, true, false, false));
    // Enabled, not seen
    listed.add(workFlow(4L, true, false, false, false));
    when(processesStorage.findWorkFlowRights(any(ProcessesFilter.class), eq(USER_ID))).thenReturn(listed);
    when(categoryLinkService.getLinkedIds(eq("process"), any())).thenReturn(Map.of("1", List.of(31L), "2", List.of(31L, 32L)));

    assertEquals(List.of(31L, 32L), service().getCategoryIds(USER_ID).stream().sorted().toList());

    ArgumentCaptor<ProcessesFilter> filter = ArgumentCaptor.forClass(ProcessesFilter.class);
    verify(processesStorage).findWorkFlowRights(filter.capture(), eq(USER_ID));
    verify(processesStorage, never()).findWorkFlows(any(), anyLong(), anyInt(), anyInt());
    assertEquals(Boolean.FALSE, filter.getValue().getManager());
    assertNull(filter.getValue().getEnabled());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> ids = ArgumentCaptor.forClass(List.class);
    verify(categoryLinkService).getLinkedIds(eq("process"), ids.capture());
    assertTrue(ids.getValue().contains("1"));
    assertTrue(ids.getValue().contains("2"));
    assertTrue(ids.getValue().contains("101"));
    assertFalse(ids.getValue().contains("3"));
    assertFalse(ids.getValue().contains("4"));
    assertEquals(101, ids.getValue().size());
  }

  /**
   * A user who sees no process is offered no category, with no category read.
   *
   * @throws Exception never
   */
  @Test
  public void aUserSeeingNoProcessIsOfferedNoCategory() throws Exception {
    activeUser();
    when(processesStorage.findWorkFlowRights(any(ProcessesFilter.class), eq(USER_ID))).thenReturn(List.of());

    assertTrue(service().getCategoryIds(USER_ID).isEmpty());

    verify(categoryLinkService, never()).getLinkedIds(any(String.class), any());
  }

  /**
   * An inactive user is refused, with no process read.
   */
  @Test
  public void anInactiveUserIsRefusedTheCategories() {
    when(identityManager.getIdentity(USER_ID)).thenReturn(null);

    org.junit.Assert.assertThrows(IllegalAccessException.class, () -> service().getCategoryIds(USER_ID));

    verify(processesStorage, never()).findWorkFlowRights(any(), anyLong());
  }

  /**
   * The categories of listed processes are read in one go, by process id.
   * Mutation: drop a process's entry and this test fails.
   */
  @Test
  public void theCategoriesOfListedProcessesAreReadInOneGo() {
    when(categoryLinkService.getLinkedIds("process", List.of("5", "6", "7"))).thenReturn(Map.of("5",
                                                                                                List.of(31L),
                                                                                                "6",
                                                                                                List.of(31L, 32L)));

    Map<Long, List<Long>> categoryIds = service().getCategoryIds(List.of(5L, 6L, 7L));

    assertEquals(Map.of(5L, List.of(31L), 6L, List.of(31L, 32L)), categoryIds);
    assertTrue(service().getCategoryIds(List.<Long> of()).isEmpty());
  }

  /**
   * A deleted process leaves every category it was filed under, after its
   * deletion; a failed unlink leaves the deletion done. Mutations: drop the
   * unlink, or let its failure through, and this test fails.
   *
   * @throws Exception never
   */
  @Test
  public void aDeletedProcessLeavesItsCategories() throws Exception {
    WorkFlow deletable = workFlow(7L, true, true, false, true);
    deletable.getAcl().setCanDelete(true);
    when(processesStorage.getWorkFlowById(7L, USER_ID)).thenReturn(deletable);
    when(processesStorage.deleteWorkflowById(7L)).thenReturn(new ProcessDeletion(List.of(), List.of()));
    CategoryObject process = new CategoryObject("process", "7", 0L);
    when(categoryLinkService.getLinkedIds(process)).thenReturn(List.of(31L, 32L));
    doThrow(new IllegalStateException("unavailable")).when(categoryLinkService).unlink(31L, process);

    service().deleteWorkflowById(7L, USER_ID);

    verify(categoryLinkService).unlink(31L, process);
    verify(categoryLinkService).unlink(32L, process);
  }

  /**
   * The platform's own deletion of a process unlinks it too.
   */
  @Test
  @SuppressWarnings("deprecation")
  public void thePlatformDeletionLeavesTheCategoriesToo() {
    when(processesStorage.deleteWorkflowById(7L)).thenReturn(new ProcessDeletion(List.of(), List.of()));
    CategoryObject process = new CategoryObject("process", "7", 0L);
    when(categoryLinkService.getLinkedIds(process)).thenReturn(List.of(31L));

    service().deleteWorkflowById(7L);

    verify(categoryLinkService).unlink(31L, process);
  }

  /**
   * A refused deletion unlinks nothing.
   */
  @Test
  public void aRefusedDeletionLeavesTheCategories() {
    when(processesStorage.getWorkFlowById(7L, USER_ID)).thenReturn(workFlow(7L, true, true, false, false));

    org.junit.Assert.assertThrows(IllegalAccessException.class, () -> service().deleteWorkflowById(7L, USER_ID));

    verify(categoryLinkService, never()).unlink(anyLong(), any(CategoryObject.class));
  }

  private ProcessesServiceImpl service() {
    ProcessesServiceImpl service = new ProcessesServiceImpl(processesStorage,
                                                            null,
                                                            identityManager,
                                                            userACL,
                                                            null,
                                                            null,
                                                            null,
                                                            null,
                                                            listenerService,
                                                            processTranslationService);
    service.setCategoryLinkService(categoryLinkService);
    service.setCategoryService(categoryService);
    return service;
  }

  private void activeUser() {
    Identity identity = new Identity(OrganizationIdentityProvider.NAME, "john");
    identity.setId(String.valueOf(USER_ID));
    identity.setEnable(true);
    when(identityManager.getIdentity(USER_ID)).thenReturn(identity);
    when(userACL.getUserIdentity("john")).thenReturn(new org.exoplatform.services.security.Identity("john"));
  }

  private static CategoryObject link(String id) {
    return new CategoryObject("process", id, 0L);
  }

  private static WorkFlow workFlow(long id, boolean enabled, boolean canAccess, boolean canAddRequest, boolean canEdit) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(id);
    workFlow.setEnabled(enabled);
    ProcessPermission acl = new ProcessPermission();
    acl.setCanAccess(canAccess);
    acl.setCanAddRequest(canAddRequest);
    acl.setCanEdit(canEdit);
    workFlow.setAcl(acl);
    return workFlow;
  }
}
