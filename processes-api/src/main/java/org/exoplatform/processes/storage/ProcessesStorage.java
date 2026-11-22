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
import org.exoplatform.processes.constant.TaskMoveResult;
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
   * Reads the stored ids of the forms of a process's task project that its
   * requesters fill, with no other read of the process.
   *
   * @param workFlowId the process id
   * @return the form ids, in id order; empty when the process does not exist
   */
  List<Long> getRequesterFormIds(long workFlowId);

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
   * Creates the task project of a new process in its managing space, with
   * Task's initial columns, and gives the process that project and the space's
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

  /**
   * Counts the requests a user created that match a filter, as
   * {@link #getWorks(long, WorkFilter, int, int)} lists them.
   *
   * @param userIdentityId the creator's identity id
   * @param workFilter the filter: status, text and completion
   * @return the count
   * @throws Exception when the Task add-on fails to count
   */
  int countWorks(long userIdentityId, WorkFilter workFilter) throws Exception;

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
   * Creates a new request's task, its request record and its process's
   * labels: the first steps of {@link #saveWork(Work, long)} for a new
   * request, before its values are written.
   *
   * @param work the new request, its project set
   * @param userId the requester's identity id
   * @return the request, its id being its task's
   * @throws IllegalStateException when the record cannot be saved, the task
   *           being removed
   */
  Work createRequest(Work work, long userId);

  /**
   * Ends a new request's creation once its values are written: moves its
   * draft's attachments, deletes its draft and announces it, then announces
   * the request.
   *
   * @param request the request {@link #createRequest(Work, long)} answered
   * @param work the new request as submitted, with its draft id
   * @param userId the requester's identity id
   * @return the created request
   */
  Work completeRequest(Work request, Work work, long userId);

  /**
   * Removes a request whose values were refused after its task was created:
   * its task, then its record. A failure of either is logged, so the caller
   * answers the refusal that made it remove the request.
   *
   * @param taskId the request's task id
   */
  void removeRequest(long taskId);

  /**
   * Deletes a process, its drafts and its request records, in one
   * transaction. The request tasks stay as plain tasks.
   *
   * @param workflowId the process id
   * @return the task ids its request records held and the ids of its drafts
   * @throws EntityNotFoundException when the process does not exist
   */
  ProcessDeletion deleteWorkflowById(Long workflowId) throws EntityNotFoundException;

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
   * Writes a request's own state, which no column of its task project
   * carries: the requester's cancel and archive flags on its request record,
   * and its task's completion; when the completion fails, the flags are
   * written back as they were, unless that write fails too, and the
   * completion's failure is thrown. It is the platform's own
   * write, with no user's permission: the caller checks the user's right.
   *
   * @param workId the request's task id
   * @param canceled the cancelled flag to write, null to keep it
   * @param archived the archived flag to write, null to keep it
   * @param completed the task's completion to write, null to keep it
   * @return the request as stored after the write
   */
  Work saveRequestState(long workId, Boolean canceled, Boolean archived, Boolean completed);

  /**
   * Broadcasts a committed cancel of a request as
   * {@link org.exoplatform.processes.constant.ProcessesEvents#REQUEST_CANCELED},
   * with the request's task as source and its project as data.
   *
   * @param workId the cancelled request's task id
   */
  void broadcastRequestCanceled(long workId);

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
   * Saves a request draft's row only: what {@link #saveWorkDraft(Work, long)}
   * does but the copy of its process's attachments into a new draft. The
   * server sets the row's draft state.
   *
   * @param work the draft
   * @param userId the owner's identity id
   * @return the saved draft
   */
  Work saveWorkDraftRow(Work work, long userId);

  /**
   * Copies a process's attachments into a new request draft, as a draft's
   * creation does.
   *
   * @param draftId the new draft's id
   * @param workFlow the draft's stored process
   * @param userId the owner's identity id
   */
  void copyProcessAttachmentsToDraft(Long draftId, WorkFlow workFlow, long userId);

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
   * Lists every process the filter lists to a user, in one unpaged listing
   * query, with at least its id, its enabled flag and the user's rights on it.
   * The rights read each process's managers and participators. An
   * implementation should map nothing else (no request makers, illustration,
   * managing space or pending-requests flag); this default falls back to the
   * full, unpaged listing.
   *
   * @param processesFilter processes filter
   * @param userIdentityId {@link Identity} technical identifier of the user
   * @return every process listed, its {@link WorkFlow#getAcl()} set
   */
  default List<WorkFlow> findWorkFlowRights(ProcessesFilter processesFilter, long userIdentityId) {
    return findWorkFlows(processesFilter, userIdentityId, 0, 0);
  }

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
   * Counts the requests of a process whose task still exists and is, or is
   * not, completed, whatever project the task is in. It is the platform's own
   * read, with no user's permission.
   *
   * @param workFlowId the process id
   * @param completed true to count the completed requests, false the open ones
   * @return the number of request tasks of the process in that state
   */
  long countRequests(long workFlowId, boolean completed);

  /**
   * Counts the requests of several processes whose task still exists, open,
   * completed or all, whatever project the task is in. It is the platform's
   * own read, with no user's permission.
   *
   * @param workFlowIds the process ids
   * @param completed true to count the completed requests, false the open
   *          ones, null for all
   * @return the number of request tasks of those processes in that state
   */
  long countRequests(Collection<Long> workFlowIds, Boolean completed);

  /**
   * Reads the ids of every process feeding a task project.
   *
   * @param projectId the task project id
   * @return the process ids, ascending
   */
  List<Long> getWorkFlowIdsByProjectId(long projectId);

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

  /**
   * Reads what describes some request drafts, one query per page of 1,000 ids.
   * The platform's own read, with no user's permission.
   *
   * @param draftIds the draft ids
   * @return draft id → its owner, process, title and dates, for the ids that
   *         are drafts
   */
  Map<Long, WorkDraftRef> getWorkDraftRefs(Collection<Long> draftIds);

  /**
   * Reads one page of a process's draft ids, by ascending id. The platform's
   * own read, with no user's permission.
   *
   * @param workFlowId the process id
   * @param afterDraftId the last id of the previous page, or null
   * @param limit the page size, strictly positive
   * @return the draft ids of the page
   */
  List<Long> getWorkDraftIds(long workFlowId, Long afterDraftId, int limit);

  /**
   * Counts a process's drafts. The platform's own read, with no user's
   * permission.
   *
   * @param workFlowId the process id
   * @return the number of drafts
   */
  long countWorkDrafts(long workFlowId);

  /**
   * Flags a process whose project the save changed, in the save's
   * transaction: its requests may be outside its project until a pass moves
   * them. The move sequence is incremented and the next attempt is due at
   * once.
   *
   * @param workFlowId the process id
   * @param requestedBy the user id of the editor who changed the project
   * @param now the current time, in epoch milliseconds
   */
  void requestProjectMove(long workFlowId, String requestedBy, long now);

  /**
   * Claims the move of a process for one pass, when a move is pending, its
   * next attempt is due and no other pass holds an unexpired claim.
   *
   * @param workFlowId the process id
   * @param token the pass's own random token
   * @param now the current time, in epoch milliseconds
   * @param until when the claim expires, in epoch milliseconds
   * @return the state the pass starts from, or null when it holds no claim
   */
  ProjectMoveState claimProjectMove(long workFlowId, String token, long now, long until);

  /**
   * Extends the claim a pass holds, while it has not expired.
   *
   * @param workFlowId the process id
   * @param token the pass's token
   * @param now the current time, in epoch milliseconds
   * @param until the new expiry, in epoch milliseconds
   * @return the state the move is in, or null when the pass no longer holds
   *         the claim
   */
  ProjectMoveState renewProjectMove(long workFlowId, String token, long now, long until);

  /**
   * Releases the claim a pass holds, if it still holds it.
   *
   * @param workFlowId the process id
   * @param token the pass's token
   */
  void releaseProjectMove(long workFlowId, String token);

  /**
   * Stops the move of a process whose project no longer exists, until a save
   * sets another project; written only when no save changed the project since
   * the pass started and the pass still holds the claim.
   *
   * @param workFlowId the process id
   * @param seq the move sequence the pass started with
   * @param token the pass's token
   * @return true when the move was stopped
   */
  boolean stopProjectMove(long workFlowId, long seq, String token);

  /**
   * Clears the move flag of a process; written only when no save changed the
   * project since the pass started and the pass still holds an unexpired
   * claim.
   *
   * @param workFlowId the process id
   * @param seq the move sequence the pass started with
   * @param token the pass's token
   * @param now the current time, in epoch milliseconds
   * @return true when the flag was cleared
   */
  boolean clearProjectMove(long workFlowId, long seq, String token, long now);

  /**
   * Schedules the next pass of a process's move, conditioned as
   * {@link #clearProjectMove(long, long, String, long)}.
   *
   * @param workFlowId the process id
   * @param seq the move sequence the pass started with
   * @param token the pass's token
   * @param now the current time, in epoch milliseconds
   * @param nextAttempt when the next pass is due, in epoch milliseconds
   * @param failed true to count a failed attempt, false for the verification
   *          pass after a pass that moved requests
   * @return true when the next pass was scheduled
   */
  boolean rescheduleProjectMove(long workFlowId, long seq, String token, long now, long nextAttempt, boolean failed);

  /**
   * Reads the processes whose move a pass may claim now.
   *
   * @param now the current time, in epoch milliseconds
   * @return their ids, the most overdue first
   */
  List<Long> getDueProjectMoves(long now);

  /**
   * Reads the stored project of a process from the database, never from a
   * copy loaded earlier.
   *
   * @param workFlowId the process id
   * @return its project id, or null when the process does not exist
   */
  Long getStoredProjectId(long workFlowId);

  /**
   * Reads a page of the task ids of a process's request records, by task id,
   * whether the task still exists or not.
   *
   * @param workFlowId the process id
   * @param afterTaskId the last task id of the previous page, 0 for the first
   * @param limit the page size, strictly positive
   * @return the task ids, ascending
   */
  List<Long> getRequestRecordTaskIds(long workFlowId, long afterTaskId, int limit);

  /**
   * Deletes the request record of a task that no longer exists.
   *
   * @param taskId the task id
   */
  void deleteRequestRecord(long taskId);

  /**
   * Counts the requests of a process whose task still exists and is not in
   * the given project.
   *
   * @param workFlowId the process id
   * @param projectId the project the requests should be in
   * @return the number of those requests
   */
  long countRequestsOutsideProject(long workFlowId, long projectId);

  /**
   * Moves a request's task to a project, unless it is already there or no
   * longer exists: first its labels of its former project are removed, so
   * that a task found in the target has nothing left to do; then it takes the
   * target's column of the same name, read as a label only, else the one
   * whose name equals it trimmed, case aside, of lowest id, else the target's
   * first column, never creating one; then the task is saved. A target with
   * no column refuses the move with an IllegalArgumentException
   * processes.project.noStatus. Each Task call commits on its own.
   * The task is saved as read: a copy of it saved in Tasks after this read and
   * before this save is overwritten, and the description goes through Task's
   * sanitizer again, as on every task edit. As on every task edit, Task then
   * queues on its shared asynchronous thread a rewrite of the whole target
   * project from the project as read at this save, which undoes an edit of
   * that project made in Tasks while the rewrite waits.
   *
   * @param taskId the task id, read again from Task
   * @param projectId the target project id
   * @return what the move did
   */
  TaskMoveResult moveTaskToProject(long taskId, long projectId);
}
