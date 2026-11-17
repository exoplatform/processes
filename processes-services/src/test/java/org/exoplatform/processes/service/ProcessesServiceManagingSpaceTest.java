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
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.service.ProjectService;

/**
 * The managing space of a process, as Forms reads it to offer a space's forms:
 * the stored space when it still exists, else the first space managing the
 * process's task project.
 */
@RunWith(MockitoJUnitRunner.class)
public class ProcessesServiceManagingSpaceTest {

  @Mock
  private ProcessesStorage processesStorage;

  @Mock
  private ProjectService   projectService;

  @Mock
  private SpaceService     spaceService;

  /**
   * A process with a stored space that still exists is managed by it, whatever
   * the spaces of its project. Mutation: read the project's space first and
   * this test fails.
   */
  @Test
  public void aProcessIsManagedByItsStoredSpace() {
    when(processesStorage.getWorkFlowById(7L, null)).thenReturn(workFlow("12", 10L));
    when(spaceService.getSpaceById("12")).thenReturn(space("12"));

    try (MockedStatic<ProcessesUtils> utils = mockStatic(ProcessesUtils.class)) {
      assertEquals(Long.valueOf(12), service().getManagingSpaceId(7L));
      utils.verify(() -> ProcessesUtils.getProjectParentSpace(projectService, spaceService, 10L), never());
    }
  }

  /**
   * A process stored before 7.3.0.50, with no space, or whose space was
   * deleted, is managed by the space of its project. Mutation: answer null
   * when no space is stored and this test fails.
   */
  @Test
  public void aProcessWithNoStoredSpaceIsManagedByItsProjectSpace() {
    when(processesStorage.getWorkFlowById(7L, null)).thenReturn(workFlow(null, 10L));
    when(processesStorage.getWorkFlowById(8L, null)).thenReturn(workFlow("99", 11L));

    try (MockedStatic<ProcessesUtils> utils = mockStatic(ProcessesUtils.class)) {
      utils.when(() -> ProcessesUtils.getProjectParentSpace(projectService, spaceService, 10L)).thenReturn(space("13"));
      utils.when(() -> ProcessesUtils.getProjectParentSpace(projectService, spaceService, 11L)).thenReturn(space("14"));

      assertEquals(Long.valueOf(13), service().getManagingSpaceId(7L));
      assertEquals(Long.valueOf(14), service().getManagingSpaceId(8L));
    }
  }

  /**
   * An unknown process, or one no space manages, has no managing space.
   */
  @Test
  public void anUnknownOrSpacelessProcessHasNoManagingSpace() {
    when(processesStorage.getWorkFlowById(7L, null)).thenReturn(null);
    when(processesStorage.getWorkFlowById(8L, null)).thenReturn(workFlow(null, 10L));

    try (MockedStatic<ProcessesUtils> utils = mockStatic(ProcessesUtils.class)) {
      utils.when(() -> ProcessesUtils.getProjectParentSpace(projectService, spaceService, 10L)).thenReturn(null);

      assertNull(service().getManagingSpaceId(7L));
      assertNull(service().getManagingSpaceId(8L));
    }
    // The platform's own read: no user's memberships
    verify(processesStorage).getWorkFlowById(7L, isNull());
  }

  private ProcessesServiceImpl service() {
    return new ProcessesServiceImpl(processesStorage, projectService, null, null, spaceService, null, null, null, null, null);
  }

  private static WorkFlow workFlow(String spaceId, long projectId) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setSpaceId(spaceId);
    workFlow.setProjectId(projectId);
    return workFlow;
  }

  private static Space space(String id) {
    Space space = new Space();
    space.setId(id);
    return space;
  }
}
