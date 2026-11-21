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
package org.exoplatform.processes.category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.processes.model.ProcessPermission;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.social.category.model.CategoryObject;
import io.meeds.social.category.service.CategoryPluginService;

import jakarta.annotation.PostConstruct;

@RunWith(MockitoJUnitRunner.class)
public class ProcessCategoryPluginTest {

  private static final long     JOHN_ID = 11L;

  @Mock
  private ProcessesService      processesService;

  @Mock
  private IdentityManager       identityManager;

  @Mock
  private CategoryPluginService categoryPluginService;

  /**
   * The plug-in answers for processes, the type the Processes page links and
   * filters.
   */
  @Test
  public void itAnswersForProcesses() {
    assertEquals("process", plugin().getType());
  }

  /**
   * The plug-in registers itself once built, the framework calling its
   * {@code init}: the categories otherwise answer for processes with their
   * default rules. Mutations: drop the {@code addPlugin} call, or the
   * {@code @PostConstruct} annotation, and this test fails.
   *
   * @throws Exception never
   */
  @Test
  public void itRegistersWithTheCategoriesOnceBuilt() throws Exception {
    assertTrue(ProcessCategoryPlugin.class.getMethod("init").isAnnotationPresent(PostConstruct.class));
    ProcessCategoryPlugin plugin = plugin();

    plugin.init();

    verify(categoryPluginService).addPlugin(plugin);
  }

  /**
   * Who participates in a process, or may add a request to it, sees its
   * categories; who has neither does not. Mutation: read only one of the two
   * flags and this test fails.
   *
   * @throws Exception never
   */
  @Test
  public void whoSeesAProcessSeesItsCategories() throws Exception {
    john();
    when(processesService.getWorkFlow(5L, JOHN_ID)).thenReturn(workFlow(true, false, false));
    when(processesService.getWorkFlow(6L, JOHN_ID)).thenReturn(workFlow(false, true, false));
    when(processesService.getWorkFlow(7L, JOHN_ID)).thenReturn(workFlow(false, false, true));

    assertTrue(plugin().canAccess("5", "john"));
    assertTrue(plugin().canAccess("6", "john"));
    assertFalse(plugin().canAccess("7", "john"));
  }

  /**
   * A process the user may not see, or that doesn't exist, hides its
   * categories, and so does every process for an unknown or blank user.
   * Mutation: answer true on the refusal and this test fails.
   *
   * @throws Exception never
   */
  @Test
  public void aHiddenOrUnknownProcessHidesItsCategories() throws Exception {
    john();
    when(processesService.getWorkFlow(5L, JOHN_ID)).thenThrow(new IllegalAccessException("hidden"));
    when(processesService.getWorkFlow(6L, JOHN_ID)).thenReturn(null);

    assertFalse(plugin().canAccess("5", "john"));
    assertFalse(plugin().canAccess("6", "john"));
    assertFalse(plugin().canAccess("not-an-id", "john"));
    assertFalse(plugin().canAccess("5", "ghost"));
    assertFalse(plugin().canAccess("5", " "));
    assertFalse(plugin().canEdit("5", "john"));
    assertFalse(plugin().canEdit("5", null));
  }

  /**
   * Only a manager of the process changes its categories: one who sees it, or
   * may add a request to it, does not. Mutation: grant canEdit on canAccess or
   * on canAddRequest, or refuse the manager, and this test fails.
   *
   * @throws Exception never
   */
  @Test
  public void onlyAProcessManagerChangesItsCategories() throws Exception {
    john();
    when(processesService.getWorkFlow(5L, JOHN_ID)).thenReturn(workFlow(true, true, true));
    when(processesService.getWorkFlow(6L, JOHN_ID)).thenReturn(workFlow(true, true, false));

    assertTrue(plugin().canEdit("5", "john"));
    assertFalse(plugin().canEdit("6", "john"));
  }

  /**
   * The categories offered to filter by are those of the processes the user
   * sees, whatever the space; an unknown user is offered none, and the
   * user-less reads read no process. Mutation: answer the whole type's
   * categories and this test fails.
   *
   * @throws Exception never
   */
  @Test
  public void theCategoriesToFilterByAreThoseOfTheProcessesTheUserSees() throws Exception {
    john();
    when(processesService.getCategoryIds(JOHN_ID)).thenReturn(List.of(31L, 32L));

    assertEquals(List.of(31L, 32L), plugin().getCategoryIds(0, "john"));
    assertEquals(List.of(31L, 32L), plugin().getCategoryIds(4, "john"));
    assertEquals(List.of(), plugin().getCategoryIds(0, "ghost"));
    assertEquals(List.of(), plugin().getCategoryIds());
    assertEquals(List.of(), plugin().getCategoryIds(4));
    verify(processesService, never()).getCategoryIds(org.mockito.ArgumentMatchers.<List<Long>> any());
  }

  /**
   * A user the processes service refuses, an inactive one, is offered no
   * category.
   *
   * @throws Exception never
   */
  @Test
  public void aRefusedUserIsOfferedNoCategory() throws Exception {
    john();
    when(processesService.getCategoryIds(JOHN_ID)).thenThrow(new IllegalAccessException("inactive"));

    assertEquals(List.of(), plugin().getCategoryIds(0, "john"));
  }

  /**
   * A link is stored with the process id in the form the bulk reads match:
   * leading zeros and spaces go; an id already canonical, a non-numeric one
   * and another type's object are left as sent. Mutation: return the object
   * unchanged and this test fails.
   */
  @Test
  public void aLinkIsStoredWithTheCanonicalProcessId() {
    CategoryObject padded = plugin().getObject(new CategoryObject("process", "057", null, 3L));
    assertEquals("57", padded.getId());
    assertEquals("process", padded.getType());
    assertEquals(3L, padded.getSpaceId());
    assertEquals("57", plugin().getObject(new CategoryObject("process", " 57 ", 0L)).getId());

    CategoryObject canonical = new CategoryObject("process", "57", 0L);
    CategoryObject notAnId = new CategoryObject("process", "x", 0L);
    CategoryObject other = new CategoryObject("formsList", "057", 0L);
    assertSame(canonical, plugin().getObject(canonical));
    assertSame(notAnId, plugin().getObject(notAnId));
    assertSame(other, plugin().getObject(other));
    verify(processesService, never()).getCategoryIds(anyLong());
  }

  private ProcessCategoryPlugin plugin() {
    return new ProcessCategoryPlugin(processesService, identityManager, categoryPluginService);
  }

  private void john() {
    Identity john = new Identity(String.valueOf(JOHN_ID));
    when(identityManager.getOrCreateUserIdentity("john")).thenReturn(john);
  }

  private static WorkFlow workFlow(boolean canAccess, boolean canAddRequest, boolean canEdit) {
    WorkFlow workFlow = new WorkFlow();
    ProcessPermission acl = new ProcessPermission();
    acl.setCanAccess(canAccess);
    acl.setCanAddRequest(canAddRequest);
    acl.setCanEdit(canEdit);
    workFlow.setAcl(acl);
    workFlow.setEnabled(true);
    return workFlow;
  }
}
