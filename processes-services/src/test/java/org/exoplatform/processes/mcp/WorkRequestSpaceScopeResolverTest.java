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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.service.ProcessesService;

/**
 * Pins which space a work request belongs to for the space scope of the
 * processes tools: the space of its process's project, and none for a task
 * that is no request.
 */
public class WorkRequestSpaceScopeResolverTest {

  private ProcessesService              processesService;

  private ProcessSpaceScopeResolver     processResolver;

  private WorkRequestSpaceScopeResolver resolver;

  /**
   * Builds the resolver on mocks: project 7 is the project of a process, in
   * space 12; project 8 is a plain task project, though in space 12 too.
   */
  @Before
  public void setUp() {
    processesService = mock(ProcessesService.class);
    processResolver = mock(ProcessSpaceScopeResolver.class);
    resolver = new WorkRequestSpaceScopeResolver(processesService, processResolver);
    when(processesService.getWorkFlowByProjectId(7L)).thenReturn(new WorkFlow());
    when(processResolver.spaceOfProject(7L)).thenReturn(12L);
    when(processResolver.spaceOfProject(8L)).thenReturn(12L);
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
   * A request belongs to the space of its process's project; a task of a
   * project that is no process's project is no request and belongs to no
   * space, nor do a missing request, a request with no project, an id that
   * isn't one and a lookup that fails.
   * Mutants: the process check removed, the project id not read, or a guard
   * removed.
   */
  @Test
  public void aRequestBelongsToTheSpaceOfItsProcessOnly() {
    when(processesService.getWorkById(5L)).thenReturn(work(7L));
    when(processesService.getWorkById(6L)).thenReturn(work(8L));
    when(processesService.getWorkById(4L)).thenReturn(work(0L));
    when(processesService.getWorkById(3L)).thenReturn(null);
    when(processesService.getWorkById(2L)).thenThrow(new IllegalStateException("broken"));

    assertEquals(Long.valueOf(12L), resolver.spaceOf("5"));
    for (String id : new String[] { "6", "4", "3", "2", "abc", "", null }) {
      assertNull(id, resolver.spaceOf(id));
    }
  }

  /**
   * @param projectId the request's project id
   * @return a request of that project
   */
  private static Work work(long projectId) {
    Work work = new Work();
    work.setProjectId(projectId);
    return work;
  }

}
