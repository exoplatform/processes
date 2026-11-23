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

import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.service.TaskPayload;

/**
 * Hands Task's task updates that reopen a task to
 * {@link ProcessesService#requestTaskReopened(long)}, which makes a cancelled
 * or archived request pending again, whether it was reopened in Tasks or in
 * processes. Glue only: an update that does not reopen a task is ignored here.
 */
public class RequestTaskReopenedListener extends Listener<Object, TaskPayload> {

  private final ProcessesService processesService;

  /**
   * Builds the listener.
   *
   * @param processesService clears the reopened request's state
   */
  public RequestTaskReopenedListener(ProcessesService processesService) {
    this.processesService = processesService;
  }

  /**
   * Hands a task reopened by the update to the processes service.
   *
   * @param event Task's update, its data the task before and after
   */
  @Override
  public void onEvent(Event<Object, TaskPayload> event) {
    TaskPayload payload = event.getData();
    TaskDto before = payload == null ? null : payload.before();
    TaskDto after = payload == null ? null : payload.after();
    if (after != null && !after.isCompleted() && before != null && before.isCompleted()) {
      processesService.requestTaskReopened(after.getId());
    }
  }
}
