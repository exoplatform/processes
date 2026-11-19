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

import org.exoplatform.processes.service.ProcessesProjectMoveService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;

/**
 * Hands the move of a process's requests to the move thread once the save
 * that changed its project is committed. Glue only: the move runs in the
 * background, and a move this listener cannot hand over is left to the sweep.
 */
public class ProcessProjectChangedListener extends Listener<Long, String> {

  private final ProcessesProjectMoveService projectMoveService;

  /**
   * Builds the listener.
   *
   * @param projectMoveService runs the moves
   */
  public ProcessProjectChangedListener(ProcessesProjectMoveService projectMoveService) {
    this.projectMoveService = projectMoveService;
  }

  /**
   * Hands the move over.
   *
   * @param event the process id and the user id of the editor
   */
  @Override
  public void onEvent(Event<Long, String> event) {
    if (event.getSource() != null) {
      projectMoveService.submitMove(event.getSource());
    }
  }
}
