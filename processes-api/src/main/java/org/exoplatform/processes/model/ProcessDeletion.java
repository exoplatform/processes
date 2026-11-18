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
package org.exoplatform.processes.model;

import java.util.List;

/**
 * What a process's deletion removed, read in the deletion's own transaction.
 *
 * @param requestTaskIds the task ids its request records held; the tasks stay
 *          as plain tasks
 * @param draftIds the ids of its request drafts, deleted with it
 */
public record ProcessDeletion(List<Long> requestTaskIds, List<Long> draftIds) {

  /**
   * Copies the two lists, so the record never changes after the deletion.
   *
   * @param requestTaskIds the task ids its request records held
   * @param draftIds the ids of its request drafts
   */
  public ProcessDeletion {
    requestTaskIds = requestTaskIds == null ? List.of() : List.copyOf(requestTaskIds);
    draftIds = draftIds == null ? List.of() : List.copyOf(draftIds);
  }
}
