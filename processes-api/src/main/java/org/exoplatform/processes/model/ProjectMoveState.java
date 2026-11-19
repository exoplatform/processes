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

/**
 * The state a pass of a project move starts from, read under its claim.
 *
 * @param seq the move sequence, which each save changing the project
 *          increments: the pass's conditional writes compare it
 * @param requestedBy the user id of the editor who changed the project, the
 *          user the pass acts as
 * @param projectId the process's stored project, which the requests move to
 * @param attempts the failed attempts since the last save that changed the
 *          project
 */
public record ProjectMoveState(long seq, String requestedBy, long projectId, int attempts) {
}
