/*
 * Copyright (C) 2025 eXo Platform SAS
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
package org.exoplatform.processes.service;

import java.util.List;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFilter;
import org.exoplatform.processes.model.WorkFlow;

public interface RequestService {

  /**
   * Retrieves list of filtered works
   *
   * @param userName user name
   * @param workFilter works filter
   * @param offset offset of the work lits result
   * @param limit limit of the queried result list
   * @return {@link List} of {@link Work}
   */
  List<Work> getWorks(String userName, WorkFilter workFilter, int offset, int limit);

  /**
   * Creates a work from new work object or from exiting work draft
   *
   * @param work Work Object
   * @param userName user name
   * @return {@link Work}
   * @throws IllegalAccessException if the user is not allowed to create the work
   */
  Work createWork(Work work, String userName) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Update an existing request
   *
   * @param work request Object
   * @param userName user name
   * @return {@link Work}
   * @throws IllegalAccessException if the user is not allowed to update the
   *           request
   * @throws IllegalArgumentException if the request object is null or has invalid
   *           properties
   * @throws ObjectNotFoundException if the request does not exist
   */
  Work updateWork(Work work, String userName) throws IllegalArgumentException, ObjectNotFoundException, IllegalAccessException;

  /**
   * Delete a work by its given id.
   *
   * @param workId: Work id
   * @param userName user name
   * @throws ObjectNotFoundException if the work with the given id does not exist
   * @throws IllegalAccessException if the user is not allowed to delete the work
   */
  void deleteWorkById(Long workId, String userName) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * update the completed property of the task of a work to completed or
   * uncompleted
   *
   * @param workId work id
   * @param userName user name
   * @param completed request completed property, can be true or false
   * @throws ObjectNotFoundException if the request with the given id does not
   *           exist
   * @throws IllegalAccessException if the user is not allowed to edit the request
   * @return {@link Work}
   */
  Work updateWorkCompleted(Long workId, String userName, boolean completed) throws ObjectNotFoundException,
                                                                            IllegalAccessException;

  /**
   * Creates a work draft
   *
   * @param work Work draft object
   * @param userName user name
   * @return {@link Work}
   * @throws IllegalArgumentException if the draft object is null or has invalid
   *           properties
   * @throws IllegalAccessException if the user is not allowed to create a draft
   */
  Work createWorkDraft(Work work, String userName) throws IllegalArgumentException, IllegalAccessException;

  /**
   * Updates a work draft
   *
   * @param work Work draft object
   * @param userName user name
   * @return {@link Work}
   * @throws IllegalArgumentException if the draft object is null or has invalid
   *           properties
   * @throws ObjectNotFoundException if the draft with the given id does not exist
   * @throws IllegalAccessException if the user is not allowed to update the draft
   */
  Work updateWorkDraft(Work work,
                       String userName) throws IllegalArgumentException, ObjectNotFoundException, IllegalAccessException;

  /**
   * Retrieves a list of accessible WorkDraft, for a selected user
   *
   * @param userName user name
   * @param workFilter work filter
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @return {@link List} of {@link Work}
   */
  List<Work> getWorkDrafts(String userName, WorkFilter workFilter, int offset, int limit);

  /**
   * Deletes a work draft by its given id
   *
   * @param userName user name id
   * @param draftId Work draft id
   * @throws IllegalAccessException if the user is not allowed to delete the draft
   * @throws ObjectNotFoundException if the draft with the given id does not exist
   */
  void deleteWorkDraftById(Long draftId, String userName) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Retrieves a Work by its given id
   *
   * @param userName user name id
   * @param workId Work id
   * @return {@link Work}
   * @throws IllegalAccessException if the user is not allowed to access the work
   * @throws ObjectNotFoundException if the work with the given id does not exist
   */
  Work getWorkById(String userName, Long workId) throws IllegalAccessException, ObjectNotFoundException;

  boolean canAddRequest(WorkFlow workFlow, org.exoplatform.services.security.Identity identity);

  boolean canEditRequest(WorkFlow workFlow, org.exoplatform.services.security.Identity identity);

  boolean canDeleteRequest(WorkFlow workFlow, org.exoplatform.services.security.Identity identity);

  boolean canCompleteRequest(Work work, org.exoplatform.services.security.Identity identity) throws ObjectNotFoundException;
}
