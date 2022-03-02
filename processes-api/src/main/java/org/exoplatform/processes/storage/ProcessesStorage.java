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
package org.exoplatform.processes.storage;

import java.util.List;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.model.*;
import org.exoplatform.social.core.identity.model.Identity;

import javax.persistence.EntityNotFoundException;

public interface ProcessesStorage {

  /**
   * Retrieves a list of accessible WorkFlows, for a selected user, by applying
   * the designated filter. The returned results will be of type
   * {@link WorkFlow} only. The ownerId of filter object will be used to select
   * the list of accessible WorkFlows to retrieve.
   *
   * @param filter         {@link ProcessesFilter} that contains filtering criteria
   * @param offset         Offset of the result list
   * @param limit          Limit of the result list
   * @param userIdentityId {@link Identity} technical identifier of the user
   *                       acessing files
   * @return {@link List} of {@link WorkFlow}
   * @throws IllegalAccessException  when the user isn't allowed to access
   *                                 documents of the designated ownerId
   * @throws ObjectNotFoundException when ownerId doesn't exisits
   */

  List<WorkFlow> findAllWorkFlowsByUser(ProcessesFilter filter, int offset, int limit, long userIdentityId);

  List<WorkFlow> findEnabledWorkFlowsByUser(ProcessesFilter filter, int offset, int limit, long userIdentityId);

  List<WorkFlow> findAllWorkFlows(int offset, int limit);

  List<WorkFlow> findEnabledWorkFlows(int offset, int limit);

  /**
   * Retrieves a list of disabled workflows, The returned results will be of type
   * {@link WorkFlow} only.
   *
   * @param offset Offset of the result list
   * @param limit  Limit of the result list
   * @return {@link List} of {@link WorkFlow}
   */
  List<WorkFlow> findDisabledWorkFlows(int offset, int limit);

  WorkFlow getWorkFlowById(long id);

  WorkFlow getWorkFlowByProjectId(long projectId);

  WorkFlow saveWorkFlow(WorkFlow workFlow, long userId) throws IllegalArgumentException;

  List<Work> getWorks(long userIdentityId, int offset, int limit) throws Exception;

  Work getWorkById(long id);

  /**
   * Saving a work and deletes its related draft if it was created from draft
   *
   * @param work Work Object
   * @param userId user Id
   * @return {@link Work}
   * @throws IllegalArgumentException
   */
  Work saveWork(Work work, long userId) throws IllegalArgumentException;

  /**
   * Delete a workflow by its given Id.
   *
   * @param workflowId : workflow id
   */
  void deleteWorkflowById(Long workflowId) throws EntityNotFoundException;

  /**
   * @param projectId:   Tasks project id
   * @param isCompleted: filter by completed and uncompleted tasks
   * @throws Exception
   * @return: Filtered tasks count
   */
  int countWorksByWorkflow(long projectId, boolean isCompleted) throws Exception;

  /**
   * Delete a work by its given id
   *
   * @param workId: Work id
   */
  void deleteWorkById(Long workId);

  /**
   * Retrieves a list of accessible WorkDraft, for a selected user.
   *
   * @param offset         Offset of the result list
   * @param limit          Limit of the result list
   * @param userIdentityId {@link Identity} technical identifier of the user
   * @return {@link List} of {@link Work}
   */
  List<Work> findAllWorkDraftsByUser(int offset, int limit, long userIdentityId);

  /**
   * Save a draft of a work
   *
   * @param work work draft object
   * @param userId user id of the creator
   * @return {@link Work}
   */
  Work saveWorkDraft(Work work, long userId);

  /**
   * Retrieves a work draft by its given id
   *
   * @param id Work draft id
   * @return {@link Work}
   */
  Work getWorkDraftyId(long id);

  /**
   * Delete a work draft by its given id
   *
   * @param id Work draft id
   */
  void deleteWorkDraftById(long id) throws EntityNotFoundException;
}
