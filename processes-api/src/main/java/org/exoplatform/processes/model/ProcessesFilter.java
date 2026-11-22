/*
 * Copyright (C) 2021 eXo Platform SAS
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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessesFilter {

  private String query;
  private Boolean enabled;
  private Boolean manager;
  /**
   * @deprecated not read: whether a user lists every process is derived from
   *             the user. Since 7.3.0, for removal.
   */
  @Deprecated(forRemoval = true)
  private Boolean isProcessManager;

  /**
   * The category the listed processes are filed under, itself or one of its
   * subcategories; null for any category.
   */
  private Long categoryId;

  /**
   * The only processes to list, by id: set by the processes service once it
   * resolved {@link #categoryId}, never read from a client; null for no
   * restriction.
   */
  private List<Long> ids;

  /**
   * Builds a filter on the text, the state and the managed processes, for any
   * category.
   *
   * @param query a search on the title, the description and the summary
   * @param enabled whether to list the enabled or the disabled processes, null
   *          for both
   * @param manager whether to list the processes the user manages
   * @param isProcessManager not read
   */
  public ProcessesFilter(String query, Boolean enabled, Boolean manager, Boolean isProcessManager) {
    this(query, enabled, manager, isProcessManager, null, null);
  }
}
