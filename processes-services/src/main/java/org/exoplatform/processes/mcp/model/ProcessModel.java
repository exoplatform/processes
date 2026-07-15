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
package org.exoplatform.processes.mcp.model;

// Lean, serialization-safe view of a process type (WorkFlow) returned by the
// list_processes MCP tool. id is the workflow id to pass to submit_work_request;
// projectId is the underlying Task/Kanban project; canManageRequests is true when
// the current user may see/approve the requests of this process (space member/manager).
public record ProcessModel(long id,
                           String title,
                           String description,
                           String summary,
                           boolean enabled,
                           long projectId,
                           String spaceId,
                           boolean canManageRequests) {
}
