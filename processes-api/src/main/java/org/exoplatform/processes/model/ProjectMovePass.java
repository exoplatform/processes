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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What one pass of a project move did, counted as it runs.
 */
public class ProjectMovePass {

  /** The failing task ids a pass names at most */
  public static final int  LOGGED_FAILURES = 20;

  private int              moved;

  private int              failed;

  private final List<Long> failedIds       = new ArrayList<>();

  private long             lastRenewal;

  /**
   * Counts a request moved by the pass.
   */
  public void countMoved() {
    moved++;
  }

  /**
   * Counts a request that failed to move, naming the first
   * {@value #LOGGED_FAILURES} ones.
   *
   * @param taskId the request's task id
   */
  public void countFailed(long taskId) {
    failed++;
    if (failedIds.size() < LOGGED_FAILURES) {
      failedIds.add(taskId);
    }
  }

  /**
   * @return the requests the pass moved
   */
  public int getMoved() {
    return moved;
  }

  /**
   * @return the requests that failed to move
   */
  public int getFailed() {
    return failed;
  }

  /**
   * @return the first ids of the requests that failed to move
   */
  public List<Long> getFailedIds() {
    return Collections.unmodifiableList(failedIds);
  }

  /**
   * @return when the pass last renewed its claim, on a monotonic clock in
   *         nanoseconds
   */
  public long getLastRenewal() {
    return lastRenewal;
  }

  /**
   * @param lastRenewal when the pass last renewed its claim, on a monotonic
   *          clock in nanoseconds
   */
  public void setLastRenewal(long lastRenewal) {
    this.lastRenewal = lastRenewal;
  }
}
