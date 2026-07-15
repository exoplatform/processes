/*
 * Copyright (C) 2026 eXo Platform SAS
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
package org.exoplatform.processes.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.mcp.model.PendingApprovalModel;
import org.exoplatform.processes.mcp.model.ProcessModel;
import org.exoplatform.processes.mcp.model.RequestModel;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFilter;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.task.dao.OrderBy;
import org.exoplatform.task.dao.TaskQuery;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.service.TaskService;

import io.meeds.mcp.server.plugin.McpToolPlugin;

/**
 * MCP tools exposing the eXo Processes add-on to the AI agent (EVA).
 * <p>
 * A <b>process type</b> ({@link WorkFlow}) is backed by a Task/Kanban project
 * ({@code WorkFlow.projectId}); a <b>request</b> ({@link Work}) IS a task
 * ({@code Work.taskId}) whose status is the task's Kanban column (default
 * columns: {@code Request, RequestInProgress, Validated, Refused, Canceled}).
 * <p>
 * Every method acts as the current user: the current user's social identity id
 * is resolved and passed to the self-scoping {@link ProcessesService} calls
 * ({@code getWorks} / {@code getWorkById} filter by {@code createdBy}), so
 * platform ACLs are enforced.
 * <p>
 * <b>Approver actions ride the existing Task MCP tools by design:</b> to respond
 * to a pending request, the agent calls the eXo Task tools on the request's task
 * id ({@code update_task_status} to Validated/Refused, {@code add_task_comment}).
 * This class deliberately does NOT duplicate that task-write path; it only
 * surfaces the pending requests to act on via {@link #getPendingApprovals()}.
 */
@Service
@Profile("mcp-server")
public class ProcessesMcpTool implements McpToolPlugin {

  private static final String    STATUS_CANCELED        = "Canceled";

  // The two open (not-yet-decided) statuses a request goes through before an
  // approver validates/refuses it; these are what get_pending_approvals surfaces.
  private static final String[]  PENDING_STATUSES       = { "Request", "RequestInProgress" };

  private static final int       MAX_WORKFLOWS          = 200;

  private static final int       MAX_REQUESTS           = 50;

  private static final int       MAX_PENDING            = 100;

  private final ProcessesService processesService;

  private final TaskService      taskService;

  private final IdentityManager  identityManager;

  public ProcessesMcpTool(ProcessesService processesService, TaskService taskService, IdentityManager identityManager) {
    this.processesService = processesService;
    this.taskService = taskService;
    this.identityManager = identityManager;
  }

  // Lists the enabled process types (request types) the current user can see, so
  // the agent can discover what a user may request and get the process id to pass
  // to submit_work_request. READ.
  public List<ProcessModel> listProcesses() throws IllegalAccessException {
    long userIdentityId = getCurrentUserIdentityId();
    ProcessesFilter filter = buildEnabledProcessesFilter();
    List<WorkFlow> workFlows = processesService.getWorkFlows(filter, 0, MAX_WORKFLOWS, userIdentityId);
    return workFlows.stream().filter(wf -> wf != null).map(this::toProcessModel).collect(Collectors.toList());
  }

  // Lists the current user's OWN work requests (the requests they submitted),
  // optionally filtered by status (Request, RequestInProgress, Validated, Refused,
  // Canceled). Self-scoped to the current user. READ.
  public List<RequestModel> getMyRequests(String status) throws Exception {
    long userIdentityId = getCurrentUserIdentityId();
    WorkFilter workFilter = new WorkFilter();
    if (StringUtils.isNotBlank(status)) {
      workFilter.setStatus(status);
    }
    List<Work> works = processesService.getWorks(userIdentityId, workFilter, 0, MAX_REQUESTS);
    return works.stream().filter(w -> w != null).map(this::toRequestModel).collect(Collectors.toList());
  }

  // Retrieves one of the current user's own work requests by its id (the request /
  // task id). Self-scoped to the current user. READ.
  public RequestModel getRequestDetails(long requestId) throws ObjectNotFoundException {
    if (requestId <= 0) {
      throw new IllegalArgumentException("The 'request_id' parameter is mandatory (the id of one of your requests).");
    }
    long userIdentityId = getCurrentUserIdentityId();
    Work work = processesService.getWorkById(userIdentityId, requestId);
    if (work == null) {
      throw new ObjectNotFoundException(("No request found with id %d among your requests. Call get_my_requests to list your "
          + "requests and their ids.").formatted(requestId));
    }
    return toRequestModel(work);
  }

  // Submits a new work request under a given process type. The process_id is a
  // WorkFlow id from list_processes; the request is created as a task in the
  // process project, in the initial 'Request' status, on behalf of the current
  // user. There is no structured field map: put any request data in the
  // description (structured data belongs to the process's document form / UI).
  // WRITE (approval-gated).
  public RequestModel submitWorkRequest(long processId,
                                        String title,
                                        String description) throws IllegalAccessException, ObjectNotFoundException {
    if (processId <= 0) {
      throw new IllegalArgumentException("The 'process_id' parameter is mandatory (a process id from list_processes).");
    }
    if (StringUtils.isBlank(title)) {
      throw new IllegalArgumentException("The 'title' parameter is mandatory (a short title for the request).");
    }
    long userIdentityId = getCurrentUserIdentityId();
    WorkFlow workFlow = processesService.getWorkFlow(processId, userIdentityId);
    if (workFlow == null) {
      throw new ObjectNotFoundException(("No process found with id %d. Call list_processes to list the available process types "
          + "and their ids.").formatted(processId));
    }
    if (!workFlow.isEnabled()) {
      throw new IllegalArgumentException(("The process '%s' is disabled and does not accept new requests.")
          .formatted(workFlow.getTitle()));
    }
    Work work = new Work();
    work.setTitle(title);
    work.setDescription(StringUtils.trimToEmpty(description));
    work.setProjectId(workFlow.getProjectId());
    Work created = processesService.createWork(work, userIdentityId);
    return toRequestModel(created);
  }

  // Cancels one of the current user's own pending requests: sets its status to
  // 'Canceled' and marks it completed (the combination that fires the
  // 'exo.process.request.canceled' event and closes the request). WRITE
  // (approval-gated).
  public RequestModel cancelWorkRequest(long requestId) throws Exception {
    if (requestId <= 0) {
      throw new IllegalArgumentException("The 'request_id' parameter is mandatory (the id of one of your requests to cancel).");
    }
    long userIdentityId = getCurrentUserIdentityId();
    Work work = processesService.getWorkById(userIdentityId, requestId);
    if (work == null) {
      throw new ObjectNotFoundException(("No request found with id %d among your requests. Call get_my_requests to list your "
          + "requests and their ids.").formatted(requestId));
    }
    if (STATUS_CANCELED.equals(work.getStatus()) && work.isCompleted()) {
      // Already canceled: return as-is (updateWork would reject a no-op change).
      return toRequestModel(work);
    }
    work.setStatus(STATUS_CANCELED);
    work.setCompleted(true);
    Work updated = processesService.updateWork(work, userIdentityId);
    return toRequestModel(updated);
  }

  // Lists the requests awaiting the current user's approval: tasks still in the
  // 'Request' / 'RequestInProgress' status (not completed) that belong to the
  // process projects the current user manages (is a space manager/member of). Use
  // the returned task_id with the Task MCP tools (update_task_status to
  // Validated/Refused, add_task_comment) to respond. READ.
  public List<PendingApprovalModel> getPendingApprovals() throws Exception {
    long userIdentityId = getCurrentUserIdentityId();
    ProcessesFilter filter = buildEnabledProcessesFilter();
    List<WorkFlow> workFlows = processesService.getWorkFlows(filter, 0, MAX_WORKFLOWS, userIdentityId);
    // Keep only the processes for which the current user may see/approve requests
    // (space member/manager), and index their project id -> process title.
    Map<Long, String> managedProjects = new LinkedHashMap<>();
    for (WorkFlow workFlow : workFlows) {
      if (workFlow != null && workFlow.isCanShowPending() && workFlow.getProjectId() > 0) {
        managedProjects.put(workFlow.getProjectId(), workFlow.getTitle());
      }
    }
    if (managedProjects.isEmpty()) {
      return new ArrayList<>();
    }
    List<Long> projectIds = new ArrayList<>(managedProjects.keySet());
    List<PendingApprovalModel> pending = new ArrayList<>();
    for (String status : PENDING_STATUSES) {
      TaskQuery taskQuery = new TaskQuery();
      taskQuery.setProjectIds(projectIds);
      taskQuery.setStatusName(status);
      taskQuery.setCompleted(Boolean.FALSE);
      List<OrderBy> orderByList = new ArrayList<>();
      orderByList.add(new OrderBy("id", false));
      taskQuery.setOrderBy(orderByList);
      List<TaskDto> tasks = taskService.findTasks(taskQuery, 0, MAX_PENDING);
      for (TaskDto task : tasks) {
        pending.add(toPendingApprovalModel(task, managedProjects));
      }
    }
    return pending;
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  // Builds the filter used to list enabled process types. isProcessManager MUST be
  // set explicitly: WorkFlowDAO.buildWorkflowQuery unboxes filter.getIsProcessManager()
  // into a primitive boolean, so leaving it null throws a NullPointerException deep in
  // the service (there is no requester-role distinction for this AI discovery view, and
  // since 'enabled' is set the membership-scoped query runs regardless of this flag, so
  // false is the correct, safe value).
  private ProcessesFilter buildEnabledProcessesFilter() {
    ProcessesFilter filter = new ProcessesFilter();
    filter.setEnabled(true);
    filter.setIsProcessManager(false);
    return filter;
  }

  // Resolves the current user's social identity id (the long expected by the
  // self-scoping ProcessesService calls); throws if no user is bound.
  private long getCurrentUserIdentityId() {
    String username = getCurrentUserName();
    if (StringUtils.isBlank(username)) {
      throw new IllegalStateException("Cannot resolve the current user, so no process request can be accessed.");
    }
    Identity identity = identityManager.getOrCreateUserIdentity(username);
    if (identity == null) {
      throw new IllegalStateException("Cannot resolve the identity of user " + username + ".");
    }
    return Long.parseLong(identity.getId());
  }

  private ProcessModel toProcessModel(WorkFlow workFlow) {
    return new ProcessModel(workFlow.getId(),
                            workFlow.getTitle(),
                            workFlow.getDescription(),
                            workFlow.getSummary(),
                            workFlow.isEnabled(),
                            workFlow.getProjectId(),
                            workFlow.getSpaceId(),
                            workFlow.isCanShowPending());
  }

  private RequestModel toRequestModel(Work work) {
    return new RequestModel(work.getId(),
                            work.getTitle(),
                            work.getDescription(),
                            work.getStatus(),
                            work.isCompleted(),
                            work.getCreatedBy(),
                            work.getCreatedDate(),
                            work.getDueDate(),
                            work.getProjectId());
  }

  private PendingApprovalModel toPendingApprovalModel(TaskDto task, Map<Long, String> managedProjects) {
    long projectId = 0;
    String statusName = null;
    if (task.getStatus() != null) {
      statusName = task.getStatus().getName();
      if (task.getStatus().getProject() != null) {
        projectId = task.getStatus().getProject().getId();
      }
    }
    return new PendingApprovalModel(task.getId(),
                                    task.getTitle(),
                                    task.getDescription(),
                                    statusName,
                                    task.getCreatedBy(),
                                    task.getCreatedTime(),
                                    projectId,
                                    managedProjects.get(projectId));
  }

}
