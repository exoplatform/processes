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

import java.io.IOException;
import java.util.List;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.processes.model.*;
import org.exoplatform.social.core.identity.model.Identity;

import jakarta.persistence.EntityNotFoundException;

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

  /**
   * Retrieves list of filtered works
   *
   * @param userIdentityId user identity ide
   * @param workFilter works filter
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @return {@link List} of {@link Work}
   * @throws Exception
   */
  List<Work> getWorks(long userIdentityId, WorkFilter workFilter, int offset, int limit) throws Exception;

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
   * Retrieves a Work by its given id
   *
   * @param userIdentityId user identity id
   * @param workId Work id
   * @return {@link Work}
   * @throws EntityNotFoundException
   */
  Work getWorkById(long userIdentityId, long workId) throws EntityNotFoundException;

    /**
   * @param projectId:   Tasks project id
   * @param isCompleted: filter by completed and uncompleted tasks
   * @throws Exception
   * @return Filtered tasks count
   */
  int countWorksByWorkflow(long projectId, boolean isCompleted) throws Exception;

  /**
   * Delete a work by its given id
   *
   * @param workId: Work id
   */
  void deleteWorkById(Long workId);

  /**
   * update the completed property of the task of a work to completed or uncompleted
   *
   * @param workId work id
   * @param completed work completed property, can be true or false
   * @return {@link Work}
   * */
  Work updateWorkCompleted(Long workId, boolean completed);

  /**
   * Retrieves a list of accessible WorkDraft, for a selected user.
   *
   * @param workFilter work filter
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @param userIdentityId {@link Identity} technical identifier of the user
   * @return {@link List} of {@link Work}
   */
  List<Work> findAllWorkDraftsByUser(WorkFilter workFilter, int offset, int limit, long userIdentityId);

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

  /**
   * Retrieves the list of available statuses in all workflows
   *
   * @return {@link List} of {@link WorkStatus}
   */
  List<WorkStatus> getAvailableWorkStatuses();

  /**
   * Retrieves list fo filtered workflows
   * 
   * @param processesFilter processes filter
   * @param offset Offset of result list
   * @param limit limit of result list
   * @return {@link List} of {@link WorkFlow}
   */
  List<WorkFlow> findWorkFlows(ProcessesFilter processesFilter, long userIdentityId, int offset, int limit);

  /**
   * Retrieves an illustration image by its given id
   *
   * @param illustrationId illustration file id
   * @return {@link IllustrativeAttachment}
   * @throws FileStorageException
   * @throws ObjectNotFoundException
   */
  IllustrativeAttachment getIllustrationImageById(Long illustrationId) throws FileStorageException,
                                                                       ObjectNotFoundException,
                                                                       IOException;

  int countWorkFlows(ProcessesFilter processesFilter);
}
