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

import java.util.Date;

// Lean, serialization-safe view of a request awaiting the current user's approval
// (a Task in one of the user's managed process projects, still in Request /
// RequestInProgress and not completed). taskId is the underlying task id: pass it
// to the Task MCP tools (update_task_status to Validated/Refused, add_task_comment)
// to respond. Returned by get_pending_approvals.
public record PendingApprovalModel(long taskId,
                                   String title,
                                   String description,
                                   String status,
                                   String requestedBy,
                                   Date createdDate,
                                   long projectId,
                                   String processTitle) {
}
