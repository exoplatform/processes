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
package org.exoplatform.processes.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.model.ProcessPermission;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

@ExtendWith(MockitoExtension.class)
class ProcessTranslationPluginTest {

  private static final long        EDITOR_ID   = 11L;

  private static final long        READER_ID   = 12L;

  private static final long        OUTSIDER_ID = 13L;

  @Mock
  private ProcessesService         processesService;

  @Mock
  private IdentityManager          identityManager;

  private ProcessTranslationPlugin plugin;

  @BeforeEach
  void setUp() throws Exception {
    plugin = new ProcessTranslationPlugin(() -> processesService, () -> identityManager);
    lenient().when(identityManager.getOrCreateUserIdentity("editor")).thenReturn(identity(EDITOR_ID));
    lenient().when(identityManager.getOrCreateUserIdentity("reader")).thenReturn(identity(READER_ID));
    lenient().when(identityManager.getOrCreateUserIdentity("outsider")).thenReturn(identity(OUTSIDER_ID));
    lenient().when(processesService.getWorkFlow(9L, EDITOR_ID)).thenReturn(workFlow(true));
    lenient().when(processesService.getWorkFlow(9L, READER_ID)).thenReturn(workFlow(false));
    lenient().when(processesService.getWorkFlow(9L, OUTSIDER_ID)).thenThrow(new IllegalAccessException("can't see"));
  }

  /**
   * The plugin is the one of the "process" translations.
   */
  @Test
  void itGrantsTheProcessTranslations() {
    assertEquals("process", plugin.getObjectType());
  }

  /**
   * A user who may see the process reads its translations; one who may not,
   * or no user, doesn't.
   * Mutants: the access granted to everyone, or the refusal of the process
   * read ignored.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  void theUsersWhoSeeTheProcessReadItsTranslations() throws Exception {
    assertTrue(plugin.hasAccessPermission("9", "editor"));
    assertTrue(plugin.hasAccessPermission("9", "reader"));
    assertFalse(plugin.hasAccessPermission("9", "outsider"));
    assertFalse(plugin.hasAccessPermission("9", null));
  }

  /**
   * Only a user who may edit the process, as its update requires, writes its
   * translations; a reader or an outsider doesn't, and no user is never
   * resolved to the platform's own read.
   * Mutants: the edit flag ignored or inverted, the access right used for the
   * edit, or a blank user read as the platform.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  void onlyTheUsersWhoEditTheProcessWriteItsTranslations() throws Exception {
    assertTrue(plugin.hasEditPermission("9", "editor"));
    assertFalse(plugin.hasEditPermission("9", "reader"));
    assertFalse(plugin.hasEditPermission("9", "outsider"));
    assertFalse(plugin.hasEditPermission("9", ""));
    verify(processesService, never()).getWorkFlow(anyLong(), isNull());
  }

  /**
   * A process that doesn't exist, or an id that names none, is answered as
   * not found.
   * Mutant: a missing process read as a refusal.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  void anUnknownProcessIsNotFound() throws Exception {
    when(processesService.getWorkFlow(404L, EDITOR_ID)).thenReturn(null);

    assertThrows(ObjectNotFoundException.class, () -> plugin.hasEditPermission("404", "editor"));
    assertThrows(ObjectNotFoundException.class, () -> plugin.hasAccessPermission("abc", "editor"));
    assertThrows(ObjectNotFoundException.class, () -> plugin.hasAccessPermission("0", "editor"));
  }

  /**
   * A process's translations are bound to no space and no metadata audience.
   */
  @Test
  void theTranslationsBelongToNoSpace() {
    assertEquals(0, plugin.getSpaceId("9"));
    assertEquals(0, plugin.getAudienceId("9"));
  }

  /**
   * @param canEdit whether the user may edit the process
   * @return the process as that user sees it
   */
  private static WorkFlow workFlow(boolean canEdit) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(9L);
    workFlow.setAcl(new ProcessPermission(true, canEdit, canEdit, true));
    return workFlow;
  }

  /**
   * @param id the identity id
   * @return a user identity
   */
  private static Identity identity(long id) {
    Identity identity = new Identity(OrganizationIdentityProvider.NAME, "user" + id);
    identity.setId(String.valueOf(id));
    return identity;
  }
}
