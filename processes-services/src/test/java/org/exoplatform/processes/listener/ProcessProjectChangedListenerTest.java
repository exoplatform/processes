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
package org.exoplatform.processes.listener;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import org.exoplatform.processes.service.ProcessesProjectMoveService;
import org.exoplatform.services.listener.Event;

/**
 * The glue that hands a process's move to the move thread once its project
 * change is saved.
 */
class ProcessProjectChangedListenerTest {

  /**
   * The process the event names is handed over; an event naming none hands
   * nothing. Mutation: drop the hand-over and the move waits for the sweep.
   */
  @Test
  void theChangedProcessIsHandedToTheMoveThread() {
    ProcessesProjectMoveService moveService = mock(ProcessesProjectMoveService.class);
    ProcessProjectChangedListener listener = new ProcessProjectChangedListener(moveService);

    listener.onEvent(new Event<>("exo.process.projectChanged", null, "john"));
    verify(moveService, never()).submitMove(anyLong());
    listener.onEvent(new Event<>("exo.process.projectChanged", 7L, "john"));
    verify(moveService).submitMove(7L);
  }
}
