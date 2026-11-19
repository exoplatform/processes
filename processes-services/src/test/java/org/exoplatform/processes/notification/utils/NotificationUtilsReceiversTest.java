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
package org.exoplatform.processes.notification.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

@ExtendWith(MockitoExtension.class)
class NotificationUtilsReceiversTest {

  @Mock
  private ProcessesService processesService;

  @Mock
  private SpaceService     spaceService;

  /**
   * A request's notification reaches the members of the managing space of the
   * process it names, not of the first process of its project; a notification
   * that names no process, or one that can't be read, reaches those of its
   * project's process. The requester is left out.
   * Mutants: the process read from the project whatever the id, or the
   * requester kept.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  @SuppressWarnings("removal")
  void theRecipientsAreTheMembersOfTheSpaceOfTheNamedProcess() throws Exception {
    when(processesService.getWorkFlow(5L, null)).thenReturn(process(Set.of("manager:/spaces/hr")));
    when(processesService.getWorkFlow(6L, null)).thenReturn(null);
    when(processesService.getWorkFlowByProjectId(50L)).thenReturn(process(Set.of("manager:/spaces/legal")));
    when(spaceService.getSpaceByGroupId("/spaces/hr")).thenReturn(space("mary", "root"));
    when(spaceService.getSpaceByGroupId("/spaces/legal")).thenReturn(space("paul"));

    try (MockedStatic<CommonsUtils> commonsUtils = mockStatic(CommonsUtils.class)) {
      commonsUtils.when(() -> CommonsUtils.getService(ProcessesService.class)).thenReturn(processesService);
      commonsUtils.when(() -> CommonsUtils.getService(SpaceService.class)).thenReturn(spaceService);

      assertEquals(List.of("mary"), NotificationUtils.getReceivers("5", 50L, "root", false));
      assertEquals(List.of("paul"), NotificationUtils.getReceivers(null, 50L, "root", false));
      assertEquals(List.of("paul"), NotificationUtils.getReceivers("6", 50L, "root", false));
    }
  }

  private static WorkFlow process(Set<String> participators) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setParticipator(participators);
    return workFlow;
  }

  private static Space space(String... members) {
    Space space = new Space();
    space.setMembers(members);
    return space;
  }
}
