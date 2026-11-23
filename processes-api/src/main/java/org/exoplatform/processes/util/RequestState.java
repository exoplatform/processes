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
package org.exoplatform.processes.util;

import org.exoplatform.processes.model.Work;

/**
 * A request's progress, read from its own state and never from the columns of
 * its task project, which may have any names and any number: pending while its
 * task is not completed, cancelled when its requester cancelled it and its
 * task is completed, closed (decided by a manager) when its task is completed
 * otherwise. The column the task sits in is a label only.
 */
public final class RequestState {

  private RequestState() {
  }

  /**
   * Tells whether a request waits for a decision: its task is not completed,
   * whatever its column; a request reopened after a cancel is pending again.
   *
   * @param work the request
   * @return true when pending
   */
  public static boolean isPending(Work work) {
    return work != null && !work.isCompleted();
  }

  /**
   * Tells whether the requester cancelled a request still closed.
   *
   * @param work the request
   * @return true when its task is completed and its record says cancelled
   */
  public static boolean isCanceled(Work work) {
    return work != null && work.isCompleted() && work.isCanceled();
  }

  /**
   * Tells whether a manager closed a request: its task is completed and its
   * requester did not cancel it. The column it sits in words the outcome.
   *
   * @param work the request
   * @return true when decided
   */
  public static boolean isDecided(Work work) {
    return work != null && work.isCompleted() && !work.isCanceled();
  }

  /**
   * Tells whether a request changes no more unless reopened: decided or
   * cancelled, which « Archive all » archives.
   *
   * @param work the request
   * @return true when its task is completed
   */
  public static boolean isFinished(Work work) {
    return work != null && work.isCompleted();
  }
}
