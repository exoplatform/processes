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
package org.exoplatform.processes.Utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;

/**
 * The space of a task project, read from the space's manager membership its
 * managers hold (EXO-91987).
 */
public class ProcessesUtilsTest {

  /**
   * A project shared by several processes may have no participant, or a user
   * first among them: its space is still the one of its managers' space
   * membership. A project attached to no space, or deleted, has none.
   * Mutation: read the space from the first participant again and the first
   * two assertions fail, the first one with an exception.
   *
   * @throws Exception never, the mocks throw what they are told
   */
  @Test
  public void theSpaceOfAProjectIsReadFromItsManagers() throws Exception {
    ProjectService projectService = mock(ProjectService.class);
    SpaceService spaceService = mock(SpaceService.class);
    Space space = new Space();
    when(spaceService.getSpaceByGroupId("/spaces/hr")).thenReturn(space);
    when(projectService.getProject(1L)).thenReturn(project(Set.of("manager:/spaces/hr"), Set.of()));
    when(projectService.getProject(2L)).thenReturn(project(Set.of("john", "manager:/spaces/hr"), Set.of("mary")));
    when(projectService.getProject(3L)).thenReturn(project(Set.of("john"), Set.of("member:/spaces/hr")));
    when(projectService.getProject(4L)).thenThrow(new EntityNotFoundException(4L, ProjectDto.class));

    assertSame(space, ProcessesUtils.getProjectParentSpace(projectService, spaceService, 1L));
    assertSame(space, ProcessesUtils.getProjectParentSpace(projectService, spaceService, 2L));
    assertNull("a Tasks app project with the space among its participants",
               ProcessesUtils.getProjectParentSpace(projectService, spaceService, 3L));
    assertNull(ProcessesUtils.getProjectParentSpace(projectService, spaceService, 4L));
  }

  /**
   * Builds a task project.
   *
   * @param managers its manager entries
   * @param participators its participant entries
   * @return the project
   */
  private static ProjectDto project(Set<String> managers, Set<String> participators) {
    ProjectDto project = new ProjectDto();
    project.setManager(new HashSet<>(managers));
    project.setParticipator(new HashSet<>(participators));
    return project;
  }

  /**
   * Only an icon name the icon picker gives, of at most 100 characters, is a
   * process icon. Mutation: drop the length check and the long-name assertion
   * fails.
   */
  @Test
  public void isProcessIcon() {
    assertTrue(ProcessesUtils.isProcessIcon("fas fa-cog"));
    assertTrue(ProcessesUtils.isProcessIcon("fa-question-circle"));
    assertFalse(ProcessesUtils.isProcessIcon(null));
    assertFalse(ProcessesUtils.isProcessIcon("https://example.org/logo.png"));
    assertFalse(ProcessesUtils.isProcessIcon("fa-cog\" onclick=\"x"));
    assertFalse(ProcessesUtils.isProcessIcon("fa-" + "a".repeat(98)));
    assertTrue(ProcessesUtils.isProcessIcon("fa-" + "a".repeat(97)));
  }
}
