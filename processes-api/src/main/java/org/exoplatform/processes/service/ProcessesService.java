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
package org.exoplatform.processes.service;

import java.util.List;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.model.*;
import org.exoplatform.social.core.identity.model.Identity;

public interface ProcessesService {

  /**
   * Retrieves a list of accessible WorkFlows, for a selected user, by applying
   * the designated filter. The returned results will be of type
   * {@link WorkFlow} only. The ownerId of filter object will be used to select
   * the list of accessible WorkFlows to retrieve.
   * 
   * @param filter {@link ProcessesFilter} that contains filtering criteria
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @param userIdentityId {@link Identity} technical identifier of the user
   *          acessing files
   * @return {@link List} of {@link WorkFlow}
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated ownerId
   * @throws ObjectNotFoundException when ownerId doesn't exisits
   */
  List<WorkFlow> getWorkFlows(ProcessesFilter filter,
                                    int offset,
                                    int limit,
                                    long userIdentityId) throws IllegalAccessException;

  WorkFlow getWorkFlow(long id) throws IllegalAccessException;

  WorkFlow createWorkFlow(WorkFlow workFlow, long userId) throws IllegalAccessException;

  WorkFlow updateWorkFlow(WorkFlow workFlow,
                                long userId) throws IllegalArgumentException, ObjectNotFoundException, IllegalAccessException;

  List<Work> getWorks(long userIdentityId, int offset, int limit) throws Exception;

  WorkFlow getWorkFlowByProjectId(long projectId);

  /**
   * Creates a work from new work object or from exiting work draft
   *
   * @param work Work Object
   * @param userId user id
   * @return {@link Work}
   * @throws IllegalAccessException
   */
  Work createWork(Work work, long userId) throws IllegalAccessException;

  Work updateWork(Work work, long userId) throws IllegalArgumentException,
            ObjectNotFoundException,
            IllegalAccessException;
  /**
   * Delete a workflow by its given Id.
   *
   * @param workflowId : workflow id
   */
  void deleteWorkflowById(Long workflowId);

  /**
   * @param projectId: Tasks project id
   * @param isCompleted: filter by completed and uncompleted tasks
   * @return Filtered tasks count
   * @throws Exception
   */
  int countWorksByWorkflow(Long projectId, Boolean isCompleted) throws Exception;

  /**
   * Delete a work by its given id.
   *
   * @param workId: Work id
   */
  public void deleteWorkById(Long workId);

  /**
   * Creates a work draft
   *
   * @param work Work draft object
   * @param userId user identity
   * @return {@link Work}
   * @throws IllegalArgumentException
   */
  Work createWorkDraft(Work work, long userId) throws IllegalArgumentException;

  /**
   * Updates a work draft
   *
   * @param work Work draft object
   * @param userId user identity
   * @return {@link Work}
   * @throws IllegalArgumentException
   * @throws ObjectNotFoundException
   */
  Work updateWorkDraft(Work work, long userId) throws IllegalArgumentException, ObjectNotFoundException;

  /**
   * Retrieves a list of accessible WorkDraft, for a selected user
   *
   * @param userIdentityId user identity
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @return {@link List} of {@link Work}
   */
  List<Work> getWorkDrafts(long userIdentityId, int offset, int limit);

  /**
   * Deletes a work draft by its given id
   *
   * @param id Work draft id
   */
  void deleteWorkDraftById(Long id);
}
