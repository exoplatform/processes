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
 * How processes are filed under the platform's categories.
 */
public final class ProcessCategories {

  /**
   * The object type of a process among the categories, the one its
   * translations use too: links and translations are kept by distinct
   * services, so the shared name binds nothing between them.
   */
  public static final String OBJECT_TYPE = "process";

  /**
   * How deep the category filter reaches under the chosen category: the depth
   * of the category tree the Processes page shows.
   */
  public static final int    FILTER_DEPTH = 4;

  /**
   * Prevents instantiation: a constants class.
   */
  private ProcessCategories() {
  }
}
