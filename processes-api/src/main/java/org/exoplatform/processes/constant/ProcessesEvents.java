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

  /**
   * A request draft was deleted, its deletion committed: by its owner, at the
   * submit of the request made from it, or with its process. The source is the
   * draft id, the data the id of its process.
   */
  public static final String REQUEST_DRAFT_DELETED = "exo.process.requestDraft.deleted";

  /**
   * A process's task project was changed, its save committed with the move of
   * its requests pending. The source is the process id, the data the user id
   * of the editor. The move runs in the background: a listener only hands it
   * over.
   */
  public static final String PROCESS_PROJECT_CHANGED = "exo.process.projectChanged";

  /**
   * A request was cancelled by its requester, its cancel committed. The
   * source is the request's task, the data the task's project, as the
   * cancel's notification reads them.
   */
  public static final String REQUEST_CANCELED = "exo.process.request.canceled";

  private ProcessesEvents() {
  }
}
