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
import java.util.Map;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.processes.model.*;
import org.exoplatform.services.attachments.model.Attachment;
import org.exoplatform.social.core.identity.model.Identity;

import jakarta.persistence.EntityNotFoundException;
import java.util.Collection;
import java.util.Set;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

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

  WorkFlow getWorkFlowById(long id, Long userIdentityId);

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
   * Reads the processes of a space that feed some task projects, by title, the
   * ids being read in chunks the database accepts as parameters.
   *
   * @param projectIds the task project ids
   * @param spaceId the managing space id the processes must have
   * @return the processes of each project that has some
   */
  Map<Long, List<ProcessReference>> findProcessesOfProjects(List<Long> projectIds, long spaceId);

  /**
   * Saves a process's own rows: the process, its illustration and its label
   * ids. It writes nothing through Task, whose labels it only reads back, so
   * it may run inside one transaction; a new
   * process's project is created before it ({@link #createProject}), and its
   * attachments are linked after it ({@link #linkWorkFlowAttachments}).
   *
   * @param workFlow the process, whose labels the Service resolved to labels
   *          of its project; null labels keep the stored ones of an update
   * @param userId the saving user's identity id
   * @return the saved process
   * @throws IllegalArgumentException when the process or the user is missing,
   *           a new process has no project yet, or a label has no id
   */
  WorkFlow saveWorkFlow(WorkFlow workFlow, long userId) throws IllegalArgumentException;

  /**
   * Creates the task project of a new process in its managing space, with the
   * process statuses, and gives the process that project and the space's
   * memberships. Task commits each write on its own.
   *
   * @param workFlow the new process
   * @return the process, with its project id
   * @throws IllegalArgumentException when the managing space does not exist
   */
  WorkFlow createProject(WorkFlow workFlow);

  /**
   * Links attachments to a saved process, in the process's project.
   *
   * @param savedWorkFlow the saved process
   * @param attachments the attachments the Service selected
   * @param userId the saving user's identity id
   */
  void linkWorkFlowAttachments(WorkFlow savedWorkFlow, Attachment[] attachments, long userId);

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
   * Retrieves list of filtered workflows
   * 
   * @param processesFilter processes filter
   * @param userIdentityId {@link Identity} technical identifier of the user
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

  /**
   * Reads the processes of the tasks that are requests, one query per page of
   * {@code 1000} task ids. It is the platform's own read, with no user's
   * permission.
   *
   * @param taskIds the task ids
   * @return task id → process id, for the tasks that have a request record
   */
  Map<Long, Long> getRequestWorkFlowIds(Collection<Long> taskIds);

  /**
   * Reads one page of the tasks of a process's requests that still exist, by
   * ascending id. It is the platform's own read, with no user's permission.
   *
   * @param workFlowId the process id
   * @param afterTaskId the last task id of the previous page, or null for the
   *          first page
   * @param limit the page size, strictly positive
   * @return the task ids of the page, ascending
   */
  List<Long> getRequestTaskIds(long workFlowId, Long afterTaskId, int limit);

  /**
   * Reads the task projects that hold the requests of a process, in one query:
   * the current projects of its request tasks, which may differ from the
   * process's stored project. It is the platform's own read, with no user's
   * permission.
   *
   * @param workFlowId the process id
   * @return the distinct project ids
   */
  Set<Long> getRequestProjectIds(long workFlowId);

  /**
   * Counts the requests of a process whose task still exists. It is the
   * platform's own read, with no user's permission.
   *
   * @param workFlowId the process id
   * @return the number of request tasks of the process
   */
  long countRequests(long workFlowId);

  /**
   * Builds the restriction of a query to the tasks that are requests of some
   * processes. See
   * {@link org.exoplatform.processes.service.ProcessesService#requestOf}.
   *
   * @param cb the criteria builder of the query
   * @param query the query, to create the subquery from
   * @param taskId the task id expression of the query
   * @param workFlowIds the process ids
   * @return the restriction; one that no task meets when no process is given
   */
  Predicate requestOf(CriteriaBuilder cb, CommonAbstractCriteria query, Expression<Long> taskId, Collection<Long> workFlowIds);
}
