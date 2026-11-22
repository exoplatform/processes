/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.processes.rest.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A document a process asks its requesters to fill in, as the request drawer
 * needs it to tell the draft's copy of it and whether that copy was filled:
 * no path and no download link, since a requester may not read the process's
 * own file.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentToFillEntity {

  /** The id of the process's document */
  private String  id;

  /** Its title, the name the drawer shows for the request's copy */
  private String  title;

  /** The name it is stored under, which the draft's copy keeps */
  private String  fileName;

  private String  mimetype;

  private long    size;

  /** When it was last updated: a copy updated at another time was filled */
  private String  updated;

  /** Whether the user may open the process's own document */
  private boolean canAccess;
}
