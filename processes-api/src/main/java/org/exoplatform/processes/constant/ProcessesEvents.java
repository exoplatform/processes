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
package org.exoplatform.processes.constant;

/**
 * The events the Process add-on broadcasts for other add-ons to react to.
 */
public final class ProcessesEvents {

  /**
   * A process was deleted, its deletion committed. The source is the process
   * id, the data the ids of the tasks its requests had, which stay as plain
   * tasks.
   */
  public static final String PROCESS_DELETED = "exo.process.deleted";

  private ProcessesEvents() {
  }
}
