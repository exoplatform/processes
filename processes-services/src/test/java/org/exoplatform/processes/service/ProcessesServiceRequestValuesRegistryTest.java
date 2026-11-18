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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import org.exoplatform.processes.plugin.RequestValuesPlugin;

/**
 * The registry of the request values plugin: at most one plugin, a second
 * registration replaces the first, and only the registered plugin unregisters.
 */
public class ProcessesServiceRequestValuesRegistryTest {

  private final ProcessesServiceImpl processesService = new ProcessesServiceImpl(null,
                                                                                 null,
                                                                                 null,
                                                                                 null,
                                                                                 null,
                                                                                 null,
                                                                                 null,
                                                                                 null,
                                                                                 null,
                                                                                 null);

  /**
   * No add-on registered a plugin: none is read.
   */
  @Test
  public void noPluginIsRegisteredByDefault() {
    assertNull(processesService.getRequestValuesPlugin());
  }

  /**
   * A second registration replaces the first. Mutation: keep the first
   * registered plugin and this test fails.
   */
  @Test
  public void aSecondRegistrationReplacesTheFirst() {
    RequestValuesPlugin first = mock(RequestValuesPlugin.class);
    RequestValuesPlugin second = mock(RequestValuesPlugin.class);

    processesService.addRequestValuesPlugin(first);
    processesService.addRequestValuesPlugin(second);

    assertSame(second, processesService.getRequestValuesPlugin());
  }

  /**
   * Unregistering a plugin that is not the registered one keeps the
   * registration; unregistering the registered one removes it. Mutation:
   * unregister whatever plugin is given and the first assertion fails.
   */
  @Test
  public void onlyTheRegisteredPluginUnregisters() {
    RequestValuesPlugin registered = mock(RequestValuesPlugin.class);
    processesService.addRequestValuesPlugin(registered);

    processesService.removeRequestValuesPlugin(mock(RequestValuesPlugin.class));
    assertSame(registered, processesService.getRequestValuesPlugin());

    processesService.removeRequestValuesPlugin(registered);
    assertNull(processesService.getRequestValuesPlugin());
  }

  /**
   * A null plugin is refused.
   */
  @Test
  public void aNullPluginIsRefused() {
    assertThrows(IllegalArgumentException.class, () -> processesService.addRequestValuesPlugin(null));
  }
}
