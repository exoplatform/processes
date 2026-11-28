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
 * Hands Task's task updates that reopen or complete a task to the processes
 * service, whether the task changed in Tasks or in processes: a reopening to
 * {@link ProcessesService#requestTaskReopened(long)}, which makes a cancelled
 * or archived request pending again, and a completion to
 * {@link ProcessesService#requestTaskCompleted(long)}, which clears the
 * cancelled mark an interrupted cancel left on the request, so a manager's
 * decision never reads as a cancel (EXO-94985). Glue only: an update that
 * neither reopens nor completes a task is ignored here. Task broadcasts its
 * update synchronously, on the thread that wrote the task.
 */
public class RequestTaskStateListener extends Listener<Object, TaskPayload> {

  private final ProcessesService processesService;

  /**
   * Builds the listener.
   *
   * @param processesService updates the reopened or completed request's state
   */
  public RequestTaskStateListener(ProcessesService processesService) {
    this.processesService = processesService;
  }

  /**
   * Hands a task reopened or completed by the update to the processes service.
   *
   * @param event Task's update, its data the task before and after
   */
  @Override
  public void onEvent(Event<Object, TaskPayload> event) {
    TaskPayload payload = event.getData();
    TaskDto before = payload == null ? null : payload.before();
    TaskDto after = payload == null ? null : payload.after();
    if (after == null || before == null || before.isCompleted() == after.isCompleted()) {
      return;
    }
    if (after.isCompleted()) {
      processesService.requestTaskCompleted(after.getId());
    } else {
      processesService.requestTaskReopened(after.getId());
    }
  }
}
