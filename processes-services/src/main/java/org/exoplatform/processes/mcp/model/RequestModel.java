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

// Lean, serialization-safe view of a work request (a Work / Task). id is the
// request/work id (the underlying task id); status is the Kanban column name
// (Request, RequestInProgress, Validated, Refused, Canceled). Returned by
// get_my_requests, get_request_details, submit_work_request and cancel_work_request.
public record RequestModel(long id,
                           String title,
                           String description,
                           String status,
                           boolean completed,
                           String createdBy,
                           Date createdDate,
                           Date dueDate,
                           long projectId) {
}
