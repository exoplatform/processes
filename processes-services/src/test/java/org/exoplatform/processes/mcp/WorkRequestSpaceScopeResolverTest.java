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
package org.exoplatform.processes.mcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.test.util.ReflectionTestUtils;

import org.exoplatform.processes.service.ProcessesService;

/**
 * Pins which space a work request belongs to for the space scope of the
 * processes tools: the space of the process its request record names, and
 * none for a task with no record.
 */
public class WorkRequestSpaceScopeResolverTest {

  private ProcessesService              processesService;

  private ProcessSpaceScopeResolver     processResolver;

  private WorkRequestSpaceScopeResolver resolver;

  /**
   * Builds the resolver on mocks: process 70 is in space 12 and process 71 in
   * space 13, both feeding one task project.
   */
  @Before
  public void setUp() {
    processesService = mock(ProcessesService.class);
    processResolver = mock(ProcessSpaceScopeResolver.class);
    resolver = new WorkRequestSpaceScopeResolver();
    ReflectionTestUtils.setField(resolver, "processesService", processesService);
    ReflectionTestUtils.setField(resolver, "processSpaceScopeResolver", processResolver);
    when(processResolver.spaceOf("70")).thenReturn(12L);
    when(processResolver.spaceOf("71")).thenReturn(13L);
  }

  /**
   * The resolver is a bean the mcp-server profile exports under its own name.
   * Mutants: the name changed, or the annotations dropped.
   */
  @Test
  public void itIsTheWorkRequestResolverOfTheMcpServerProfile() {
    assertEquals("work_request", resolver.getName());
    assertEquals("workRequestSpaceScopeResolver", WorkRequestSpaceScopeResolver.class.getAnnotation(Service.class).value());
    assertEquals("mcp-server", WorkRequestSpaceScopeResolver.class.getAnnotation(Profile.class).value()[0]);
  }

  /**
   * A request belongs to the space of the process its request record names:
   * two requests of one shared task project, of two processes, go to their
   * own processes' spaces, and a plain task of that project, which has no
   * record, belongs to no space, nor do an id that isn't one and a lookup
   * that fails. The project of the task is never read. Mutants: the record
   * not read, the project's first process read instead, or a guard removed.
   */
  @Test
  @SuppressWarnings("removal")
  public void aRequestBelongsToTheSpaceOfTheProcessItsRecordNames() {
    when(processesService.getRequestWorkFlowId(5L)).thenReturn(70L);
    when(processesService.getRequestWorkFlowId(6L)).thenReturn(71L);
    when(processesService.getRequestWorkFlowId(7L)).thenReturn(null);
    when(processesService.getRequestWorkFlowId(2L)).thenThrow(new IllegalStateException("broken"));

    assertEquals(Long.valueOf(12L), resolver.spaceOf("5"));
    assertEquals(Long.valueOf(13L), resolver.spaceOf("6"));
    for (String id : new String[] { "7", "2", "abc", "", null }) {
      assertNull(id, resolver.spaceOf(id));
    }
    verify(processesService, never()).getWorkFlowByProjectId(anyLong());
    verify(processesService, never()).getWorkById(anyLong());
  }

}
