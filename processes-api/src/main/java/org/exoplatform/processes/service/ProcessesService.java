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
import java.util.Collections;
import java.util.List;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.processes.model.*;
import org.exoplatform.processes.plugin.RequestValuesPlugin;
import org.exoplatform.social.core.identity.model.Identity;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

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
   * Reads the categories a user may filter the processes by: those the
   * processes the user sees are filed under. A user sees a process they
   * participate in or may add a request to, and a disabled one only when they
   * manage it.
   *
   * @param userIdentityId the user's identity technical identifier
   * @return the category ids, without duplicates
   * @throws IllegalAccessException when the identity isn't an active user
   */
  default List<Long> getCategoryIds(long userIdentityId) throws IllegalAccessException {
    return Collections.emptyList();
  }

  /**
   * Reads the categories each process is filed under, in one read. It checks
   * no right: the caller reads processes the user already sees.
   *
   * @param workflowIds the process ids
   * @return the category ids by process id; a process filed under none is
   *         absent
   */
  default Map<Long, List<Long>> getCategoryIds(List<Long> workflowIds) {
    return Collections.emptyMap();
  }

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
   * Lists the labels of a task project a process of a space may feed, for the
   * process editor, by id order.
   *
   * @param spaceId the managing space id
   * @param projectId the project id
   * @param userIdentityId the reading user's identity id
   * @return the project's labels
   * @throws ObjectNotFoundException when the space does not exist
   * @throws IllegalAccessException when the user is neither a processes
   *           administrator nor a manager of the space
   * @throws IllegalArgumentException processes.project.notEligible or
   *           processes.project.statusNameConflict when a process of the space
   *           may not feed the project
   */
  List<WorkFlowLabel> listProjectLabels(String spaceId, long projectId, long userIdentityId) throws ObjectNotFoundException,
                                                                                            IllegalAccessException;

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
   * Counts the requests a user created that match a filter, as
   * {@link #getWorks(long, WorkFilter, int, int)} lists them.
   *
   * @param userIdentityId the creator's identity id
   * @param workFilter the filter: status, text and completion
   * @return the count
   * @throws Exception when the Task add-on fails to count
   */
  int countWorks(long userIdentityId, WorkFilter workFilter) throws Exception;

  /**
   * Archives every request the user created that is not archived yet and whose
   * status is final: Validated, Refused or Canceled. Each request is archived
   * through {@link #updateWorkCompleted(Long, boolean, long)} as that user; one
   * refused, gone or no longer finished at its turn is skipped.
   *
   * @param userIdentityId the creator's identity id
   * @return how many requests were archived
   * @throws Exception when the requests cannot be listed
   */
  int archiveFinishedWorks(long userIdentityId) throws Exception;

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
   * the stored task project of the process its {@code workFlowId}
   * names, whatever project id it carries, and records it as a request of
   * that process. Its task is named by the fixed rule « Process name -
   * yyyy/MM/dd - Requester name », whatever title the work carries.
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
  default Work createWork(Work work, long userId) throws IllegalAccessException, ObjectNotFoundException {
    return createWork(work, userId, null);
  }

  /**
   * Creates a request, its task named by the fixed rule « Process name -
   * yyyy/MM/dd - Requester name », followed by « - agent title » when one is
   * given; the title of the given work is ignored. Only the AI agent's tool
   * gives an agent title: every other channel calls
   * {@link #createWork(Work, long)}.
   *
   * @param work the request
   * @param userId the requester's identity id
   * @param agentTitle the title the AI agent sent, or null
   * @return the created request
   * @throws IllegalAccessException when the user can't add a request to the
   *           process, or the draft is another user's
   * @throws ObjectNotFoundException when the process or the draft does not
   *           exist
   * @throws IllegalArgumentException processes.request.workflowRequired when no
   *           process is named, processes.request.draftMismatch when the draft
   *           is of another process
   */
  Work createWork(Work work, long userId, String agentTitle) throws IllegalAccessException, ObjectNotFoundException;

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
   * Counts every task of a task project, without any permission check.
   *
   * @param projectId Tasks project id
   * @param isCompleted filter by completed and uncompleted tasks
   * @return Filtered tasks count
   * @throws Exception when the tasks can't be counted
   * @deprecated a task project may be fed by several processes and hold tasks
   *             created in Tasks: count the requests of one process with
   *             {@link #countRequests(long, Boolean, long)}, which checks the
   *             user's permission. Since 7.3.0.50, for removal.
   */
  @Deprecated(since = "7.3.0.50", forRemoval = true)
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

  /**
   * Reads the processes of the tasks that are requests, one query per page of
   * {@code 1000} task ids. It is the platform's own read, with no user's permission: a caller that serves a user checks first that
   * user's right on the process with {@link #getWorkFlow(long, Long)} given the user's identity id, and never
   * returns its answer to a client as it is.
   *
   * @param taskIds the task ids
   * @return task id → process id, for the tasks that are requests
   */
  Map<Long, Long> getRequestWorkFlowIds(Collection<Long> taskIds);

  /**
   * Reads one page of the tasks of a process's requests that still exist, by
   * ascending id. It is the platform's own read, with no user's permission: a caller that serves a user checks first that
   * user's right on the process with {@link #getWorkFlow(long, Long)} given the user's identity id, and never
   * returns its answer to a client as it is.
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
   * process's stored project when a request was moved. It is the platform's own read, with no user's permission: a caller that serves a user checks first that
   * user's right on the process with {@link #getWorkFlow(long, Long)} given the user's identity id, and never
   * returns its answer to a client as it is.
   *
   * @param workFlowId the process id
   * @return the distinct project ids
   */
  Set<Long> getRequestProjectIds(long workFlowId);

  /**
   * Counts the requests of a process whose task still exists. It is the platform's own read, with no user's permission: a caller that serves a user checks first that
   * user's right on the process with {@link #getWorkFlow(long, Long)} given the user's identity id, and never
   * returns its answer to a client as it is.
   *
   * @param workFlowId the process id
   * @return the number of request tasks of the process
   */
  long countRequests(long workFlowId);

  /**
   * Counts the requests of a process, open, completed or all, whatever project
   * their task is in, for a user who may see them: a processes administrator,
   * a user who can edit the process, or a member of its managing space (its
   * stored space, else its task project's) or a super manager of spaces, who
   * see its pending requests.
   *
   * @param workFlowId the process id
   * @param completed true to count the completed requests, false the open
   *          ones, null to count them all
   * @param userIdentityId the identity id of the user asking
   * @return the number of request tasks of the process in that state
   * @throws ObjectNotFoundException when the process does not exist
   * @throws IllegalAccessException when the user may not see the count
   */
  long countRequests(long workFlowId, Boolean completed, long userIdentityId) throws ObjectNotFoundException,
                                                                             IllegalAccessException;

  /**
   * Lists the documents a process asks its requesters to fill in: its own
   * documents, which each request's draft receives a copy of. A user who may
   * make a request on the process, or see it, gets them whatever their right
   * on the process's own files, which a requester outside the managing space
   * may not read: the request drawer tells the draft's copies by them.
   *
   * @param workFlowId the process id
   * @param userIdentityId the identity id of the user asking
   * @return the process's documents
   * @throws ObjectNotFoundException when the process does not exist, or the
   *           user may neither make a request on it nor see it
   * @throws IllegalAccessException when the attachment service refuses the
   *           user the process's documents, which it declares and does not
   *           throw at this version
   */
  List<org.exoplatform.services.attachments.model.Attachment> getDocumentsToFill(long workFlowId,
                                                                               long userIdentityId) throws ObjectNotFoundException,
                                                                                                    IllegalAccessException;

  /**
   * Counts the requests of every process feeding a task project, open,
   * completed or all, for a user who may read the request counts of at least
   * one of them (see {@link #countRequests(long, Boolean, long)}).
   *
   * @param projectId the task project id
   * @param completed true to count the completed requests, false the open
   *          ones, null to count them all
   * @param userIdentityId the identity id of the user asking
   * @return the number of request tasks of those processes in that state
   * @throws ObjectNotFoundException when no process feeds the project
   * @throws IllegalAccessException when the user may read the counts of none
   */
  long countProjectRequests(long projectId, Boolean completed, long userIdentityId) throws ObjectNotFoundException,
                                                                                    IllegalAccessException;

  /**
   * Reads the space that manages a process: its stored space when it still
   * exists, else the first space that manages its task project. It is the platform's own read, with no user's permission: a caller that serves a user checks first that
   * user's right on the process with {@link #getWorkFlow(long, Long)} given the user's identity id, and never
   * returns its answer to a client as it is.
   *
   * @param workFlowId the process id
   * @return the space id, or null when the process does not exist or no space
   *         manages it
   */
  Long getManagingSpaceId(long workFlowId);

  /**
   * Reads the forms of a process's task project that its requesters fill when
   * they create a request, as the process's editor chose them. It is the
   * platform's own read, with no user's permission: a caller that serves a
   * user checks first that user's right on the process with
   * {@link #getWorkFlow(long, Long)} given the user's identity id, and never
   * returns its answer to a client as it is.
   *
   * @param workFlowId the process id
   * @return the form ids, in id order; empty when the process does not exist
   *         or its requesters fill none of its project's forms
   */
  List<Long> getRequesterFormIds(long workFlowId);

  /**
   * Builds the restriction of a task query to the tasks that are requests of
   * some processes: an EXISTS on their request records, correlated by the task
   * id. It only narrows a query, and must stay out of any OR that would widen
   * it. It is the platform's own read, with no user's permission: a caller that serves a user checks first that
   * user's right on the process with {@link #getWorkFlow(long, Long)} given the user's identity id, and never
   * returns its answer to a client as it is.
   *
   * @param cb the criteria builder of the query
   * @param query the query, to create the subquery from
   * @param taskId the task id expression of the query
   * @param workFlowIds the process ids
   * @return the restriction; one that no task meets when no process is given
   */
  Predicate requestOf(CriteriaBuilder cb, CommonAbstractCriteria query, Expression<Long> taskId, Collection<Long> workFlowIds);

  /**
   * Registers the plugin that checks and writes the values a request carries
   * and the form of a process. At most one is registered: a second
   * registration replaces the first.
   *
   * @param plugin the plugin to register
   */
  void addRequestValuesPlugin(RequestValuesPlugin plugin);

  /**
   * Reads one of the user's own request drafts.
   *
   * @param draftId the draft id
   * @param userIdentityId the user's identity id
   * @return the draft
   * @throws ObjectNotFoundException when no such draft exists
   * @throws IllegalAccessException when the draft is not the user's
   */
  Work getWorkDraft(long draftId, long userIdentityId) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Reads the process of a request draft. It is the platform's own read, with no user's permission: a caller that serves a user checks that the
   * draft's owner is that user ({@link #getWorkDraft(long, long)}, or the owner id against the viewer), never only
   * the user's right on the process, which would show one requester's drafts to every user who may add a request
   * to it.
   *
   * @param draftId the draft id
   * @return the process id, or null when no such draft exists
   */
  Long getWorkDraftWorkFlowId(long draftId);

  /**
   * Reads the owner of a request draft. It is the platform's own read, with no user's permission: a caller that serves a user checks that the
   * draft's owner is that user ({@link #getWorkDraft(long, long)}, or the owner id against the viewer), never only
   * the user's right on the process, which would show one requester's drafts to every user who may add a request
   * to it.
   *
   * @param draftId the draft id
   * @return the owner's identity id, or null when no such draft exists
   */
  Long getWorkDraftOwnerId(long draftId);

  /**
   * Reads what describes some request drafts, one query per page of
   * {@code 1000} ids. It is the platform's own read, with no user's permission: a caller that serves a user checks that the
   * draft's owner is that user ({@link #getWorkDraft(long, long)}, or the owner id against the viewer), never only
   * the user's right on the process, which would show one requester's drafts to every user who may add a request
   * to it.
   *
   * @param draftIds the draft ids
   * @return draft id → its owner, process, title and dates, for the ids that
   *         are drafts
   */
  Map<Long, WorkDraftRef> getWorkDraftRefs(Collection<Long> draftIds);

  /**
   * Reads one page of a process's draft ids, by ascending id. It is the platform's own read, with no user's permission: a caller that serves a user checks that the
   * draft's owner is that user ({@link #getWorkDraft(long, long)}, or the owner id against the viewer), never only
   * the user's right on the process, which would show one requester's drafts to every user who may add a request
   * to it.
   *
   * @param workFlowId the process id
   * @param afterDraftId the last id of the previous page, or null for the
   *          first page
   * @param limit the page size, strictly positive
   * @return the draft ids of the page
   */
  List<Long> getWorkDraftIds(long workFlowId, Long afterDraftId, int limit);

  /**
   * Counts a process's drafts. It is the platform's own read, with no user's permission: a caller that serves a user checks that the
   * draft's owner is that user ({@link #getWorkDraft(long, long)}, or the owner id against the viewer), never only
   * the user's right on the process, which would show one requester's drafts to every user who may add a request
   * to it.
   *
   * @param workFlowId the process id
   * @return the number of drafts
   */
  long countWorkDrafts(long workFlowId);

  /**
   * Reads the forms bound to a process itself, for a user who can edit it.
   *
   * @param workFlowId the process id
   * @param userIdentityId the user's identity id
   * @return the ids of the process's own forms, empty when it has none or when
   *         no add-on registered a request values plugin
   * @throws ObjectNotFoundException when the process does not exist
   * @throws IllegalAccessException when the user may not edit the process
   */
  List<Long> getProcessFormIds(long workFlowId, long userIdentityId) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Unregisters a plugin registered by
   * {@link #addRequestValuesPlugin(RequestValuesPlugin)}. A plugin that is not
   * the registered one leaves the registration as it is.
   *
   * @param plugin the plugin to unregister
   */
  void removeRequestValuesPlugin(RequestValuesPlugin plugin);
}
