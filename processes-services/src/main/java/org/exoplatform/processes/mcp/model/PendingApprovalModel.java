/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.processes.mcp.model;

import java.util.Date;

/**
 * Lean, serialization-safe view of a request pending in an enabled process
 * whose space the current user is a member of, or in any enabled process with
 * a space for a platform administrator: its task is not completed, whatever
 * column of the process's task project it sits in. taskId is the underlying
 * task id: pass it to the Task MCP tools to respond (update_task_status to move
 * it to the project's column that words the outcome, complete_task to decide
 * it, add_task_comment). Returned by get_pending_approvals.
 *
 * @param taskId the request's task id
 * @param title its title
 * @param description its description
 * @param status the name of its column
 * @param requestedBy its requester's user name
 * @param createdDate its creation date
 * @param projectId its task project id
 * @param processTitle the title of its process, in the user's language
 */
public record PendingApprovalModel(long taskId,
                                   String title,
                                   String description,
                                   String status,
                                   String requestedBy,
                                   Date createdDate,
                                   long projectId,
                                   String processTitle) {
}
