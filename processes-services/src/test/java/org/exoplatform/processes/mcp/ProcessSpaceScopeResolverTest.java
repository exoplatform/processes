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

import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.test.util.ReflectionTestUtils;

import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;

/**
 * Pins which space a process belongs to for the space scope of the processes
 * tools: the one space all its project's managers are roles of, or none.
 */
public class ProcessSpaceScopeResolverTest {

  private ProcessesService          processesService;

  private ProjectService            projectService;

  private SpaceService              spaceService;

  private ProcessSpaceScopeResolver resolver;

  /**
   * Builds the resolver on mocks, with space 12 for group /spaces/alpha and
   * space 13 for /spaces/beta.
   */
  @Before
  public void setUp() {
    processesService = mock(ProcessesService.class);
    projectService = mock(ProjectService.class);
    spaceService = mock(SpaceService.class);
    resolver = new ProcessSpaceScopeResolver();
    ReflectionTestUtils.setField(resolver, "processesService", processesService);
    ReflectionTestUtils.setField(resolver, "projectService", projectService);
    ReflectionTestUtils.setField(resolver, "spaceService", spaceService);
    Space alpha = mock(Space.class);
    when(alpha.getId()).thenReturn("12");
    Space beta = mock(Space.class);
    when(beta.getId()).thenReturn("13");
    when(spaceService.getSpaceByGroupId("/spaces/alpha")).thenReturn(alpha);
    when(spaceService.getSpaceByGroupId("/spaces/beta")).thenReturn(beta);
  }

  /**
   * The resolver is a bean the mcp-server profile exports under its own name,
   * and the processes tools' declarations name it.
   * Mutants: the name changed, or the annotations dropped.
   */
  @Test
  public void itIsTheProcessResolverOfTheMcpServerProfile() {
    assertEquals("process", resolver.getName());
    assertEquals("processSpaceScopeResolver", ProcessSpaceScopeResolver.class.getAnnotation(Service.class).value());
    assertEquals("mcp-server", ProcessSpaceScopeResolver.class.getAnnotation(Profile.class).value()[0]);
  }

  /**
   * A process whose project is managed through one space, by one or several
   * of its roles, belongs to that space.
   * Mutants: the project id or the space id not read, or the rule inverted.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  public void aProcessBelongsToTheOneSpaceOfItsProjectManagers() throws Exception {// NOSONAR
    process(5L, 7L);
    when(projectService.getProject(7L)).thenReturn(project("manager:/spaces/alpha", "*:/spaces/alpha"));

    assertEquals(Long.valueOf(12L), resolver.spaceOf("5"));
  }

  /**
   * A process belongs to no space when its project has a user manager, a
   * group outside the spaces, two spaces among its managers or no manager,
   * when the project, the process or the space is missing, when the id isn't
   * one, and when a lookup fails.
   * Mutants: the one-group rule, the spaces prefix, the missing-object or the
   * failure guards removed.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  public void aProcessBelongsToNoSpaceOtherwise() throws Exception {// NOSONAR
    process(1L, 21L);
    when(projectService.getProject(21L)).thenReturn(project("manager:/spaces/alpha", "mary"));
    process(2L, 22L);
    when(projectService.getProject(22L)).thenReturn(project("manager:/platform/users"));
    process(3L, 23L);
    when(projectService.getProject(23L)).thenReturn(project("manager:/spaces/alpha", "manager:/spaces/beta"));
    process(4L, 24L);
    when(projectService.getProject(24L)).thenReturn(project());
    process(6L, 26L);
    when(projectService.getProject(26L)).thenThrow(new EntityNotFoundException(26L, ProjectDto.class));
    process(8L, 28L);
    when(projectService.getProject(28L)).thenReturn(project("manager:/spaces/gone"));
    process(9L, 0L);
    when(processesService.getWorkFlow(10L, null)).thenReturn(null);
    when(processesService.getWorkFlow(11L, null)).thenThrow(new IllegalAccessException("refused"));
    when(processesService.getWorkFlow(14L, null)).thenThrow(new IllegalStateException("broken"));

    for (String id : new String[] { "1", "2", "3", "4", "6", "8", "9", "10", "11", "14", "abc", "", null, "-5" }) {
      assertNull(id, resolver.spaceOf(id));
    }
  }

  /**
   * The project rule, which the request resolver calls outside any guard of
   * this resolver, answers no space itself, without failing, for a project
   * with no manager, a missing project, a project id that isn't one and a
   * group outside the spaces, even when that group has a space-like lookup.
   * Mutants: the one-group rule loosened, the spaces prefix dropped, or the
   * missing project or the id guard left to throw.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  public void theProjectRuleAnswersNoSpaceWithoutFailing() throws Exception {// NOSONAR
    Space platform = mock(Space.class);
    when(platform.getId()).thenReturn("99");
    when(spaceService.getSpaceByGroupId("/platform/users")).thenReturn(platform);
    when(projectService.getProject(31L)).thenReturn(project());
    when(projectService.getProject(32L)).thenThrow(new EntityNotFoundException(32L, ProjectDto.class));
    when(projectService.getProject(33L)).thenReturn(project("manager:/platform/users"));
    when(projectService.getProject(34L)).thenReturn(project("manager:/spaces/beta"));

    assertNull(resolver.spaceOfProject(31L));
    assertNull(resolver.spaceOfProject(32L));
    assertNull(resolver.spaceOfProject(33L));
    assertNull(resolver.spaceOfProject(0L));
    assertEquals(Long.valueOf(13L), resolver.spaceOfProject(34L));
  }

  /**
   * @param id a process id
   * @param projectId its project id
   * @throws IllegalAccessException never, the service is a mock
   */
  private void process(long id, long projectId) throws IllegalAccessException {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(id);
    workFlow.setProjectId(projectId);
    when(processesService.getWorkFlow(id, null)).thenReturn(workFlow);
  }

  /**
   * @param managers the project's managers
   * @return a project managed by those
   */
  private static ProjectDto project(String... managers) {
    ProjectDto project = new ProjectDto();
    project.setManager(Set.of(managers));
    return project;
  }

}
