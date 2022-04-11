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

import java.io.IOException;
import java.util.List;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.services.FileStorageException;
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

  /**
   * Retrieves list of filtered works
   *
   * @param userIdentityId user identity id
   * @param workFilter works filter
   * @param offset offset of the work lits result
   * @param limit limit of the queried result list
   * @return {@link List} of {@link Work}
   * @throws Exception
   */
  List<Work> getWorks(long userIdentityId, WorkFilter workFilter, int offset, int limit) throws Exception;

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
  void deleteWorkById(Long workId);

  /**
   * update the completed property of the task of a work to completed or uncompleted
   *
   * @param workId work id
   * @param completed work completed property, can be true or false
   * @return {@link Work}
   */
  Work updateWorkCompleted(Long workId, boolean completed);

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
   * @param workFilter work filter
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @return {@link List} of {@link Work}
   */
  List<Work> getWorkDrafts(long userIdentityId, WorkFilter workFilter, int offset, int limit);

  /**
   * Deletes a work draft by its given id
   *
   * @param id Work draft id
   */
  void deleteWorkDraftById(Long id);

  /**
   * Retrieves the list of available statuses in all workflows
   *
   * @return {@link List} of {@link WorkStatus}
   */
  List<WorkStatus> getAvailableWorkStatuses();

  /**
   * Retrieves a Work by its given id
   *
   * @param userIdentityId user identity id
   * @param workId Work id
   * @return {@link Work}
   */
  Work getWorkById(long userIdentityId, Long workId);

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
}
