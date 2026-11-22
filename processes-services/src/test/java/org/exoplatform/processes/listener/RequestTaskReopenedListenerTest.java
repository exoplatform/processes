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

import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.service.TaskPayload;

/**
 * The listener that hands Task's task updates reopening a task to the
 * processes service, which makes its request pending again (EXO-94923).
 */
class RequestTaskReopenedListenerTest {

  private final ProcessesService           processesService = mock(ProcessesService.class);

  private final RequestTaskReopenedListener listener        = new RequestTaskReopenedListener(processesService);

  /**
   * An update from completed to not completed is a reopening, handed over
   * with the task's id; an update that completes a task, keeps it completed
   * or keeps it open, and an event without a payload, hand nothing. Mutation:
   * hand every update over, or invert the edge, and this test fails.
   */
  @Test
  void onlyAReopeningIsHandedOver() {
    listener.onEvent(event(task(5L, true), task(5L, false)));
    verify(processesService).requestTaskReopened(5L);

    listener.onEvent(event(task(6L, false), task(6L, true)));
    listener.onEvent(event(task(7L, true), task(7L, true)));
    listener.onEvent(event(task(8L, false), task(8L, false)));
    listener.onEvent(event(null, task(9L, false)));
    listener.onEvent(new Event<>("exo.task.taskUpdate", null, null));
    verify(processesService, never()).requestTaskReopened(6L);
    verify(processesService, never()).requestTaskReopened(7L);
    verify(processesService, never()).requestTaskReopened(8L);
    verify(processesService, never()).requestTaskReopened(9L);
    verify(processesService, org.mockito.Mockito.times(1)).requestTaskReopened(anyLong());
  }

  /**
   * Builds Task's update event.
   *
   * @param before the task before the update, or null
   * @param after the task after it
   * @return the event
   */
  private static Event<Object, TaskPayload> event(TaskDto before, TaskDto after) {
    return new Event<>("exo.task.taskUpdate", null, new TaskPayload(before, after));
  }

  /**
   * Builds a task.
   *
   * @param id its id
   * @param completed its completion
   * @return the task
   */
  private static TaskDto task(long id, boolean completed) {
    TaskDto task = new TaskDto();
    task.setId(id);
    task.setCompleted(completed);
    return task;
  }
}
