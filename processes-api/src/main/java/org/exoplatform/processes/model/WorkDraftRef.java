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

import java.util.Date;

/**
 * What the platform reads of a request draft to describe it: its owner, its
 * process, its title and its dates.
 *
 * @param id the draft id
 * @param ownerId the identity id of the draft's owner
 * @param workFlowId the id of the draft's process
 * @param title the draft's title
 * @param createdDate when the draft was created
 * @param modifiedDate when the draft was last saved
 */
public record WorkDraftRef(long id, long ownerId, long workFlowId, String title, Date createdDate, Date modifiedDate) {
}
