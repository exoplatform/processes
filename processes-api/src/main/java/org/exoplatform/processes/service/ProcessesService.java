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
   * @throws IllegalAccessException when the identity isn't an active user
   */
  List<WorkFlow> getWorkFlows(ProcessesFilter filter,
                                    int offset,
                                    int limit,
                                    long userIdentityId) throws IllegalAccessException;


  int countWorkFlows(ProcessesFilter filter,
                     long userIdentityId) throws IllegalAccessException;

  /**
   * Retrieves a workflow by its id, with the user's permissions on it
   *
   * @param id workflow technical identifier
   * @param userIdentityId the user's identity technical identifier, or null for
   *          a read made by the platform itself, whose permissions are all
   *          false
   * @return the {@link WorkFlow}, or null when it doesn't exist
   * @throws IllegalAccessException when the identity isn't an active user, or
   *           neither participates in the workflow nor may add a request to it
   */
  WorkFlow getWorkFlow(long id, Long userIdentityId) throws IllegalAccessException;

  /**
   * Checks whether a user administers every process: a member of
   * /platform/processes or a platform administrator creates, edits and deletes
   * any process, and manages any request
   *
   * @param userIdentityId {@link Identity} technical identifier of the user
   * @return true when the user administers every process
   * @throws IllegalAccessException when the identity isn't an active user
   */
  boolean isProcessesAdministrator(long userIdentityId) throws IllegalAccessException;

  /**
   * Creates a process. Its task project is a new one named after it
   * ({@link org.exoplatform.processes.constant.ProjectMode#NEW}, or no mode
   * stated), or the existing one its project id names
   * ({@link org.exoplatform.processes.constant.ProjectMode#EXISTING}), which
   * must be one {@link #listEligibleProjects} returns for its managing space;
   * the process statuses missing from that project are added to it.
   *
   * @param workFlow the process to create
   * @param userId the creator's identity id
   * @return the created process
   * @throws IllegalAccessException when the user isn't a processes administrator
   * @throws IllegalArgumentException with the message code
   *           processes.project.notEligible or
   *           processes.project.statusNameConflict when the existing project
   *           cannot be used
   */
  WorkFlow createWorkFlow(WorkFlow workFlow, long userId) throws IllegalAccessException;

  /**
   * Lists the task projects a process of a space may feed: the projects
   * attached to the space, whose managers hold the space's manager membership
   * and whose managers and participants hold no group membership but the
   * space's, users being allowed, less the projects with a column named like a
   * process status in another case. Each comes with the processes of that
   * space that already feed it.
   *
   * @param spaceId the managing space id
   * @param offset the index of the first project of the page
   * @param limit the page size, 0 for every project
   * @param userIdentityId the reading user's identity id
   * @return the page and the number of eligible projects
   * @throws ObjectNotFoundException when the space does not exist
   * @throws IllegalAccessException when the user is neither a processes
   *           administrator nor a manager of the space
   * @throws IllegalArgumentException processes.projects.invalidPage for a
   *           negative offset or limit
   */
  EligibleProjects listEligibleProjects(String spaceId,
                                        int offset,
                                        int limit,
                                        long userIdentityId) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Updates a process, which keeps its task project. Moving it to another space
   * is refused while it keeps that project, since the project may be fed by
   * other processes.
   *
   * @param workFlow the process as the client sent it
   * @param userId the editor's identity id
   * @return the updated process
   * @throws IllegalArgumentException processes.project.notEligible for a space
   *           change, processes.space.notFound for a space that does not
   *           exist, or when nothing changed
   * @throws ObjectNotFoundException when the process does not exist
   * @throws IllegalAccessException when the user may not edit it, or may not
   *           move it to the space sent
   */
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

  /**
   * Reads the first process feeding a task project.
   *
   * @param projectId the task project id
   * @return the process of lowest id feeding it, or null when none does
   * @deprecated a task project may now be fed by several processes: read the
   *             process of a request from its request record instead. Since
   *             7.3.0.50, for removal.
   */
  @Deprecated(since = "7.3.0.50", forRemoval = true)
  WorkFlow getWorkFlowByProjectId(long projectId);

  /**
   * Creates a request, from a new work object or from an existing draft, in
   * the stored task project of the process its {@link Work#getWorkFlowId()}
   * names, whatever project id it carries, and records it as a request of
   * that process.
   *
   * @param work the request
   * @param userId the requester's identity id
   * @return the created request
   * @throws IllegalAccessException when the user can't add a request to the
   *           process, or the draft is another user's
   * @throws ObjectNotFoundException when the process or the draft does not
   *           exist
   * @throws IllegalArgumentException processes.request.workflowRequired when no
   *           process is named, processes.request.draftMismatch when the draft
   *           is of another process
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
   * @throws IllegalAccessException when the identity isn't an active user, or
   *           can't see the workflow
   * @throws FileStorageException
   * @throws IOException
   */
  IllustrativeAttachment getWorkFlowIllustration(long workflowId, long userIdentityId) throws ObjectNotFoundException,
                                                                                       IllegalAccessException,
                                                                                       FileStorageException,
                                                                                       IOException;

  /**
   * Reads the process a task was created for, from its request record. It is
   * the platform's own read, with no user's permission: a caller that serves
   * a user checks that user's rights on what it returns.
   *
   * @param taskId the task id
   * @return the id of the process whose request the task is, or null when the
   *         task is no request
   */
  Long getRequestWorkFlowId(long taskId);
}
