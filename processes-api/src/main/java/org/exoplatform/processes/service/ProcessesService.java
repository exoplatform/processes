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
   */
  List<WorkFlow> getWorkFlows(ProcessesFilter filter,
                                    int offset,
                                    int limit,
                                    long userIdentityId) throws IllegalAccessException;


  int countWorkFlows(ProcessesFilter filter,
                     long userIdentityId) throws IllegalAccessException;

  WorkFlow getWorkFlow(long id, Long userIdentityId) throws IllegalAccessException;

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
   * @throws IllegalAccessException when the user can't add a request to the
   *           work's process
   * @throws ObjectNotFoundException when the work's project isn't a process
   *           project
   */
  Work createWork(Work work, long userId) throws IllegalAccessException, ObjectNotFoundException;

  Work updateWork(Work work, long userId) throws IllegalArgumentException,
            ObjectNotFoundException,
            IllegalAccessException;
  /**
   * Delete a workflow by its given Id, without any permission check.
   *
   * @param workflowId : workflow id
   * @deprecated use {@link #deleteWorkflowById(Long, long)} instead, which
   *             checks the user's permission. Since 7.3.0, for removal.
   */
  @Deprecated(forRemoval = true)
  void deleteWorkflowById(Long workflowId);

  /**
   * Delete a workflow by its given Id, when the user is allowed to delete it
   *
   * @param workflowId workflow id
   * @param userIdentityId user identity id
   * @throws ObjectNotFoundException when the workflow doesn't exist
   * @throws IllegalAccessException when the user isn't allowed to delete it
   */
  void deleteWorkflowById(Long workflowId, long userIdentityId) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * @param projectId: Tasks project id
   * @param isCompleted: filter by completed and uncompleted tasks
   * @return Filtered tasks count
   * @throws Exception
   */
  int countWorksByWorkflow(Long projectId, Boolean isCompleted) throws Exception;

  /**
   * Delete a work by its given id, without any permission check.
   *
   * @param workId: Work id
   * @deprecated use {@link #deleteWorkById(Long, long)} instead, which checks
   *             the user's permission. Since 7.3.0, for removal.
   */
  @Deprecated(forRemoval = true)
  void deleteWorkById(Long workId);

  /**
   * Delete a work by its given id, when the user is its creator or a manager
   * of its process
   *
   * @param workId Work id
   * @param userIdentityId user identity id
   * @throws ObjectNotFoundException when the work doesn't exist
   * @throws IllegalAccessException when the task isn't a request of a process,
   *           or when the user isn't allowed to manage it
   */
  void deleteWorkById(Long workId, long userIdentityId) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * update the completed property of the task of a work to completed or
   * uncompleted, without any permission check.
   *
   * @param workId work id
   * @param completed work completed property, can be true or false
   * @return {@link Work}
   * @deprecated use {@link #updateWorkCompleted(Long, boolean, long)} instead,
   *             which checks the user's permission. Since 7.3.0, for removal.
   */
  @Deprecated(forRemoval = true)
  Work updateWorkCompleted(Long workId, boolean completed);

  /**
   * update the completed property of the task of a work to completed or
   * uncompleted, when the user is its creator or a manager of its process
   *
   * @param workId work id
   * @param completed work completed property, can be true or false
   * @param userIdentityId user identity id
   * @return {@link Work}
   * @throws ObjectNotFoundException when the work doesn't exist
   * @throws IllegalAccessException when the task isn't a request of a process,
   *           or when the user isn't allowed to manage it
   */
  Work updateWorkCompleted(Long workId, boolean completed, long userIdentityId) throws ObjectNotFoundException,
                                                                                IllegalAccessException;

  /**
   * Creates a work draft
   *
   * @param work Work draft object
   * @param userId user identity
   * @return {@link Work}
   * @throws IllegalArgumentException
   * @throws ObjectNotFoundException when the draft's process doesn't exist
   * @throws IllegalAccessException when the user can't add a request to the
   *           draft's process
   */
  Work createWorkDraft(Work work, long userId) throws IllegalArgumentException, ObjectNotFoundException, IllegalAccessException;

  /**
   * Updates a work draft
   *
   * @param work Work draft object
   * @param userId user identity
   * @return {@link Work}
   * @throws IllegalArgumentException
   * @throws ObjectNotFoundException
   * @throws IllegalAccessException when the user isn't the draft's creator
   */
  Work updateWorkDraft(Work work, long userId) throws IllegalArgumentException, ObjectNotFoundException, IllegalAccessException;

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
   * Deletes a work draft by its given id, without any permission check.
   *
   * @param id Work draft id
   * @deprecated use {@link #deleteWorkDraftById(Long, long)} instead, which
   *             checks the user's permission. Since 7.3.0, for removal.
   */
  @Deprecated(forRemoval = true)
  void deleteWorkDraftById(Long id);

  /**
   * Deletes a work draft by its given id, when the user is its creator
   *
   * @param id Work draft id
   * @param userIdentityId user identity id
   * @throws ObjectNotFoundException when the draft doesn't exist
   * @throws IllegalAccessException when the user isn't the draft's creator
   */
  void deleteWorkDraftById(Long id, long userIdentityId) throws ObjectNotFoundException, IllegalAccessException;

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
   * Retrieves a request by its id, whoever asks: for the platform itself, for
   * example the digest emails, never for a user request
   *
   * @param workId the request (work) id, the same as its task id
   * @return the request, or null when it doesn't exist any more
   */
  Work getWorkById(long workId);

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

  /**
   * Retrieves the illustration image of a workflow, when the user can see the
   * workflow: a member of its participators or of its request creators, or a
   * processes manager
   *
   * @param workflowId workflow id
   * @param userIdentityId user identity id
   * @return {@link IllustrativeAttachment}
   * @throws ObjectNotFoundException when the workflow doesn't exist or has no
   *           illustration
   * @throws IllegalAccessException when the user can't see the workflow
   * @throws FileStorageException
   * @throws IOException
   */
  IllustrativeAttachment getWorkFlowIllustration(long workflowId, long userIdentityId) throws ObjectNotFoundException,
                                                                                       IllegalAccessException,
                                                                                       FileStorageException,
                                                                                       IOException;
}
