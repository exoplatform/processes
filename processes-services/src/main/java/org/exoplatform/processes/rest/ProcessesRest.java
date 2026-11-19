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
package org.exoplatform.processes.rest;

import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.security.RolesAllowed;
import javax.jcr.ItemExistsException;
import jakarta.persistence.EntityNotFoundException;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

import org.exoplatform.services.rest.http.PATCH;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.portal.application.localization.LocalizationFilter;
import org.exoplatform.processes.model.*;
import org.exoplatform.processes.rest.model.WorkEntity;
import org.exoplatform.processes.rest.model.WorkFlowEntity;
import org.exoplatform.processes.rest.util.EntityBuilder;
import org.exoplatform.processes.rest.util.RestUtils;
import org.exoplatform.processes.service.ProcessesAttachmentService;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.attachments.model.Attachment;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.social.core.manager.IdentityManager;


@Path("/v1/processes")
@Tag(name = "/v1/processes", description = "Manages processes")
public class ProcessesRest implements ResourceContainer {

  private static final Log LOG = ExoLogger.getLogger(ProcessesRest.class);

  private ProcessesService processesService;

  private IdentityManager  identityManager;

  private ProcessesAttachmentService processesAttachmentService;

  private static final int           CACHE_DURATION_SECONDS      = 31536000;

  private static final long          CACHE_DURATION_MILLISECONDS = CACHE_DURATION_SECONDS * 1000L;

  private static final CacheControl  ILLUSTRATION_CACHE_CONTROL  = new CacheControl();

  private static final CacheControl  UNVERSIONED_ILLUSTRATION_CACHE_CONTROL = new CacheControl();

  private static final CacheControl  PER_USER_CACHE_CONTROL      = new CacheControl();

  private static final String              ONLY_CURRENT_USER_MESSAGE = "Only the current user can be queried";

  private static final String              WORKFLOW_NOT_FOUND_MESSAGE = "Workflow not found";

  private static final String              WORKFLOW_REQUIRED          = "processes.request.workflowRequired";

  static {
    ILLUSTRATION_CACHE_CONTROL.setMaxAge(CACHE_DURATION_SECONDS);
    // The illustration is served only to the users who see the process
    ILLUSTRATION_CACHE_CONTROL.setPrivate(true);
    UNVERSIONED_ILLUSTRATION_CACHE_CONTROL.setPrivate(true);
    // The workflows and the permissions are answered per user, with their own
    // rights: no cache shares or stores them
    PER_USER_CACHE_CONTROL.setPrivate(true);
    PER_USER_CACHE_CONTROL.setNoStore(true);
  }

  public ProcessesRest(ProcessesService processesService,
                       IdentityManager identityManager,
                       ProcessesAttachmentService processesAttachmentService) {
    this.processesService = processesService;
    this.identityManager = identityManager;
    this.processesAttachmentService = processesAttachmentService;
  }

  /**
   * Lists the processes the current user sees, their texts in the user's
   * language.
   *
   * @param userId the current user's identity id, or null
   * @param enabled whether to list the enabled or the disabled processes
   * @param manager whether to list the processes the user manages
   * @param query a search on the process title
   * @param expand the properties to expand
   * @param offset the offset of the first process
   * @param limit the number of processes
   * @return the processes, or the refusal status
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/workflows")
  @Operation(
          summary = "Retrieves the list of workFlows",
          description = "Retrieves the list of workFlows for an authenticated user",
          method = "GET"
  )
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "403", description = "Forbidden operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getWorkFlows(@Parameter(name = "Identity technical identifier", required = false)
  @QueryParam("userId")
  Long userId,
                                  @Parameter(description = "filter workflow by status", required = false)
                                  @QueryParam("enabled") Boolean enabled,
                                  @Parameter(description = "filter workflow that i manage", required = false)
                                  @QueryParam("manager") Boolean manager,
                                  @Parameter(description = "Search query entered by the user", required = false)
                                  @QueryParam("query")
                                  String query,
                                  @Parameter(description = "Processes properties to expand.", required = false)
                                  @QueryParam("expand")
                                  String expand,
                                  @Parameter(description = "Offset of results to return", required = false)
                                  @Schema(defaultValue = "0")
                                  @QueryParam("offset")
                                  int offset,
                                  @Parameter(description = "Limit of results to return", required = false)
                                  @Schema(defaultValue = "10")
                                  @QueryParam("limit")
                                  int limit) {
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      ProcessesFilter filter = new ProcessesFilter();
      if (enabled != null) {
        filter.setEnabled(enabled);
      }
      if (manager != null) {
        filter.setManager(manager);
      }
      if (query != null) {
        filter.setQuery(query);
      }

      if (userId != null && userId != currentIdentityId) {
        return Response.status(Response.Status.FORBIDDEN).entity(ONLY_CURRENT_USER_MESSAGE).build();
      }
      long userIdentityId = currentIdentityId;
      List<WorkFlow> workFlows = processesService.getWorkFlows(filter, offset, limit, userIdentityId);
      return Response.ok(EntityBuilder.toRestEntities(workFlows, expand, getLocale())).cacheControl(PER_USER_CACHE_CONTROL).build();
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' isn't allowed to list the workflows", currentIdentityId, e);
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving list of workFlows", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  /**
   * Creates a process, answered with its texts in the user's language.
   *
   * @param workFlowEntity the process to create
   * @return the created process, or the refusal status
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/workflows")
  @Operation(
          summary = "Creates a new WorkFlow",
          description = "Creates a new WorkFlow",
          method = "POST")
  @ApiResponses(value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "403", description = "Forbidden operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response createWorkFlow(@RequestBody(description = "WorkFlow object to create", required = true)
  WorkFlowEntity workFlowEntity) {
    if (workFlowEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("WorkFlow object is mandatory").build();
    }
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      WorkFlow newWorkFlow = processesService.createWorkFlow(EntityBuilder.fromEntity(workFlowEntity),
                                                                      currentIdentityId);
      return Response.ok(EntityBuilder.toEntity(newWorkFlow, "", getLocale())).build();
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' isn't allowed to create a workflow", currentIdentityId, e);
      return Response.status(Response.Status.FORBIDDEN).entity("Not allowed to create a workflow").build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error creating a WorkFlow", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  /**
   * Updates a process, answered with its texts in the user's language.
   *
   * @param workFlowEntity the process to update
   * @return the updated process, or the refusal status
   */
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/workflows")
  @Operation(
          summary = "Updates a workFlow",
          description = "Updates a workFlow",
          method = "PUT")
  @ApiResponses(value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "403", description = "Forbidden operation"),
      @ApiResponse(responseCode = "404", description = "Object to update not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response updateWorkFlow(@RequestBody(description = "WorkFlow object to update", required = true)
  WorkFlowEntity workFlowEntity) {
    if (workFlowEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("WorkFlow object is mandatory").build();
    }
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      WorkFlow newWorkFlow = processesService.updateWorkFlow(EntityBuilder.fromEntity(workFlowEntity),
                                                                      currentIdentityId);
      return Response.ok(EntityBuilder.toEntity(newWorkFlow, "", getLocale())).build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' attempts to update a not existing work workFlow '{}'", currentIdentityId, e);
      return Response.status(Response.Status.NOT_FOUND).entity("Work workFlow not found").build();
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' isn't allowed to update the workflow", currentIdentityId, e);
      return Response.status(Response.Status.FORBIDDEN).entity("Not allowed to update this workflow").build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error updating a work workFlow", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  /**
   * Lists the current user's requests, their expanded process in the user's
   * language.
   *
   * @param userId the current user's identity id
   * @param expand the properties to expand
   * @param completed whether to list the completed requests
   * @param status the status of the requests to list
   * @param query a search on the request
   * @param offset the offset of the first request
   * @param limit the number of requests
   * @return the requests, or the refusal status
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/works")
  @Operation(
          summary = "Retrieves the list of works",
          description = "Retrieves the list of works for an authenticated user",
          method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getWorks(@Parameter(description = "Identity technical identifier")
                                  @QueryParam("userId")
                                          Long userId,
                                  @Parameter(description = "Processes properties to expand.")
                                  @QueryParam("expand")
                                          String expand,
                                  @Parameter(description = "work completed property") @QueryParam("completed") Boolean completed,
                                  @Parameter(description = "Works status") @QueryParam("status") String status,
                                  @Parameter(description = "Works query") @QueryParam("query") String query,
                                  @Parameter(description = "Offset of results to return")
                                  @Schema(defaultValue = "0")
                                  @QueryParam("offset")
                                          int offset,
                                  @Parameter(description = "Limit of results to return")
                                  @Schema(defaultValue = "10")
                                  @QueryParam("limit")
                                          int limit) {
    try {
      long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      if (currentIdentityId == 0) {
        return Response.status(Response.Status.UNAUTHORIZED).build();
      }

      if (userId != null && userId != currentIdentityId) {
        return Response.status(Response.Status.FORBIDDEN).entity(ONLY_CURRENT_USER_MESSAGE).build();
      }
      long userIdentityId = currentIdentityId;
      WorkFilter workFilter = new WorkFilter();
      if (status != null) {
        workFilter.setStatus(status);
      }
      if (query != null) {
        workFilter.setQuery(query);
      }
      if (completed != null) {
        workFilter.setCompleted(completed);
      }
      List<Work> works = processesService.getWorks(userIdentityId, workFilter, offset, limit);
      return Response.ok(EntityBuilder.toWorkEntityList(processesService, works, expand, getLocale())).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving list of works", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }


  /**
   * Creates a request, answered with its process in the user's language.
   *
   * @param workEntity the request to create
   * @return the created request, or the refusal status
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/works")
  @Operation(
          summary = "Creates a Work",
          description = "Creates a Work",
          method = "POST")
  @ApiResponses(value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
          @ApiResponse(responseCode = "404", description = "Object not found"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response createWork(@RequestBody(description = "Work object to create", required = true)
                                     WorkEntity workEntity) {
    if (workEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Work object is mandatory").build();
    }
    if (workEntity.getWorkFlow() == null || workEntity.getWorkFlow().getId() <= 0) {
      return Response.status(Response.Status.BAD_REQUEST).entity(WORKFLOW_REQUIRED).build();
    }
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      Work newWork = processesService.createWork(EntityBuilder.toNewWork(processesService, workEntity), currentIdentityId);
      return Response.ok(EntityBuilder.toWorkEntity(processesService, newWork, "workFlow", getLocale())).build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' attempts to create a request on a not existing workflow", currentIdentityId, e);
      return Response.status(Response.Status.NOT_FOUND).entity(WORKFLOW_NOT_FOUND_MESSAGE).build();
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' isn't allowed to create a request", currentIdentityId, e);
      return Response.status(Response.Status.FORBIDDEN).entity("Not allowed to add a request to this workflow").build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error creating a Work", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  /**
   * Updates a request, answered with its process in the user's language.
   *
   * @param workEntity the request to update
   * @return the updated request, or the refusal status
   */
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/works")
  @Operation(
          summary = "Updates a new work",
          description = "Updates a new work",
          method = "PUT")
  @ApiResponses(value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
          @ApiResponse(responseCode = "404", description = "Object not found"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response updateWork(@RequestBody(description = "Work object to update", required = true)
                                         WorkEntity workEntity) {
    if (workEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Work object is mandatory").build();
    }
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      Work newWork = processesService.updateWork(EntityBuilder.toWork(processesService, workEntity), currentIdentityId);
      return Response.ok(EntityBuilder.toWorkEntity(processesService, newWork, "workFlow", getLocale())).build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' attempts to update a not existing work workFlow '{}'", currentIdentityId, e);
      return Response.status(Response.Status.NOT_FOUND).entity("Work workFlow not found").build();
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' isn't allowed to update the request", currentIdentityId, e);
      return Response.status(Response.Status.FORBIDDEN).entity("Not allowed to update this request").build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error updating a work workFlow", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  /**
   * Lists the task projects a process of a space may feed, each with the
   * processes of that space that already feed it.
   *
   * @param spaceId the managing space id
   * @param offset the index of the first project of the page
   * @param limit the page size, 0 for every project
   * @return the page and the number of eligible projects, 404 when the space
   *         does not exist, 403 unless the user is a processes administrator or
   *         a manager of the space
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/projects")
  @Operation(summary = "Lists the task projects a process of a space may feed", description = "Lists the task projects attached to the managing space that a process may feed, each with the processes of that space that already feed it", method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "403", description = "Forbidden operation"),
      @ApiResponse(responseCode = "404", description = "Space not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getSpaceProjects(@Parameter(description = "Managing space id", required = true)
                                   @QueryParam("spaceId") String spaceId,
                                   @Parameter(description = "Offset")
                                   @QueryParam("offset") @DefaultValue("0") int offset,
                                   @Parameter(description = "Limit, 0 for every project")
                                   @QueryParam("limit") @DefaultValue("0") int limit) {
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      EligibleProjects projects = processesService.listEligibleProjects(spaceId, offset, limit, currentIdentityId);
      return Response.ok(EntityBuilder.translate(projects, getLocale())).cacheControl(PER_USER_CACHE_CONTROL).build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' lists the projects of a space that does not exist", currentIdentityId, e);
      return Response.status(Response.Status.NOT_FOUND).entity("Space not found").build();
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' isn't allowed to list the projects of the space '{}'", currentIdentityId, spaceId, e);
      return Response.status(Response.Status.FORBIDDEN).entity("Not allowed to list the projects of this space").build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error listing the projects of the space '{}'", spaceId, e);
      return Response.serverError().build();
    }
  }

  /**
   * Lists the labels of a task project a process of a space may feed, for the
   * process editor.
   *
   * @param projectId the project id
   * @param spaceId the managing space id
   * @return the labels [{id, name}], 400 when a process of the space may not
   *         feed the project, 404 when the space does not exist, 403 unless the
   *         user is a processes administrator or a manager of the space
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/projects/{projectId}/labels")
  @Operation(summary = "Lists the labels of a task project a process of a space may feed", description = "Lists the labels of a task project attached to the managing space, which the process editor offers", method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Project not eligible"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "403", description = "Forbidden operation"),
      @ApiResponse(responseCode = "404", description = "Space not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getProjectLabels(@Parameter(description = "Task project id", required = true)
                                   @PathParam("projectId") long projectId,
                                   @Parameter(description = "Managing space id", required = true)
                                   @QueryParam("spaceId") String spaceId) {
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      List<WorkFlowLabel> labels = processesService.listProjectLabels(spaceId, projectId, currentIdentityId);
      return Response.ok(labels).cacheControl(PER_USER_CACHE_CONTROL).build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' lists the labels of a project of a space that does not exist", currentIdentityId, e);
      return Response.status(Response.Status.NOT_FOUND).entity("Space not found").build();
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' isn't allowed to list the labels of the project '{}'", currentIdentityId, projectId, e);
      return Response.status(Response.Status.FORBIDDEN).entity("Not allowed to list the labels of this project").build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error listing the labels of the project '{}'", projectId, e);
      return Response.serverError().build();
    }
  }

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("users")
  @Path("/permissions")
  @Operation(
          summary = "checks whether the current user administers every process",
          description = "checks whether the current user administers every process: a member of /platform/processes or a platform administrator",
          method = "GET")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "403", description = "The current user isn't an active user"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),})
  public Response isProcessesManager() {

    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      boolean isProcessesAdministrator = processesService.isProcessesAdministrator(currentIdentityId);
      return Response.ok(String.valueOf(isProcessesAdministrator))
                     .type(MediaType.TEXT_PLAIN)
                     .cacheControl(PER_USER_CACHE_CONTROL)
                     .build();
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' isn't an active user", currentIdentityId, e);
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (Exception e) {
      LOG.warn("Error while checking user permissions", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @DELETE
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("users")
  @Path("/workflow/{workflowId}")
  @Operation(
          summary = "delete a workflow by its id",
          description = "delete a workflow by its id",
          method = "DELETE")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
          @ApiResponse(responseCode = "404", description = "Object not found"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response deleteWorkflow(@Parameter(description = "Workflow id to delete", required = true)
                                 @PathParam("workflowId") Long workflowId) {
    if (workflowId == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Workflow id is mandatory").build();
    }
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      this.processesService.deleteWorkflowById(workflowId, currentIdentityId);
      return Response.ok("ok").type(MediaType.TEXT_PLAIN).build();
    } catch (ObjectNotFoundException | EntityNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' isn't allowed to delete the workflow '{}'", currentIdentityId, workflowId, e);
      return Response.status(Response.Status.FORBIDDEN).entity("Not allowed to delete this workflow").build();
    } catch (Exception e) {
      LOG.warn("Error while deleting a workflow", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  /**
   * Counts the open or completed requests of the process that a task project
   * feeds, the one of lowest id when several do, for a user who may see them.
   *
   * @param projectId the task project id
   * @param isCompleted whether to count the completed requests or the open ones
   * @return the count as text, or the refusal status
   * @deprecated a task project may be fed by several processes: use
   *             {@link #countRequests(Long, Boolean)}. Since 7.3.0.50, for
   *             removal.
   */
  @Deprecated(since = "7.3.0.50", forRemoval = true)
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("users")
  @Path("/countWorks/{projectId}")
  @Operation(
          summary = "Count the requests of the process of a task project by completed and uncompleted status",
          description = "Deprecated: use GET /workflows/{workflowId}/requests/count. Counts the requests of the process of lowest id the project feeds, for a user who may see them",
          method = "GET",
          deprecated = true)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
          @ApiResponse(responseCode = "404", description = "Object not found"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),})
  @SuppressWarnings("removal")
  public Response countWorksByWorkflow(@Parameter(description = "Tasks project id", required = true)
                                       @PathParam("projectId") Long projectId,
                                       @Parameter(description = "Processes properties to expand.")
                                       @QueryParam("isCompleted") @DefaultValue("true") Boolean isCompleted) {
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    if (projectId == null || isCompleted == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Project id is mandatory").build();
    }
    WorkFlow workFlow = processesService.getWorkFlowByProjectId(projectId);
    if (workFlow == null) {
      return Response.status(Response.Status.NOT_FOUND).entity("workflow not found").build();
    }
    return countRequests(workFlow.getId(), isCompleted, currentIdentityId);
  }

  /**
   * Counts the requests of a process, open, completed or all, whatever project
   * their task is in, for a processes administrator, a user who can edit the
   * process or a user who sees its pending requests.
   *
   * @param workflowId the process id
   * @param isCompleted true to count the completed requests, false the open
   *          ones, absent to count them all
   * @return the count as text, or the refusal status
   */
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("users")
  @Path("/workflows/{workflowId}/requests/count")
  @Operation(
          summary = "Counts the open, completed or all requests of a process",
          description = "Counts the requests a process created, open, completed or all, whatever task project they are in, for a processes administrator, a user who can edit the process or a member of its managing space",
          method = "GET")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
          @ApiResponse(responseCode = "404", description = "Object not found"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),})
  public Response countRequests(@Parameter(description = "Process id", required = true)
                                @PathParam("workflowId") Long workflowId,
                                @Parameter(description = "true to count the completed requests, false the open ones, absent to count them all")
                                @QueryParam("isCompleted") Boolean isCompleted) {
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    if (workflowId == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Process id is mandatory").build();
    }
    return countRequests(workflowId, isCompleted, currentIdentityId);
  }

  /**
   * Answers the request count of a process for a user, or the refusal status.
   *
   * @param workflowId the process id
   * @param completed whether to count the completed requests or the open ones,
   *          null for all
   * @param currentIdentityId the identity id of the user asking
   * @return the count as text, 403 or 404
   */
  private Response countRequests(long workflowId, Boolean completed, long currentIdentityId) {
    try {
      long count = processesService.countRequests(workflowId, completed, currentIdentityId);
      return Response.ok(String.valueOf(count)).type(MediaType.TEXT_PLAIN).cacheControl(PER_USER_CACHE_CONTROL).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' isn't allowed to count the requests of the process '{}'", currentIdentityId, workflowId, e);
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (Exception e) {
      LOG.error("Error while counting the requests of the process '{}'", workflowId, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  @DELETE
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("users")
  @Path("/work/{workId}")
  @Operation(
          summary = "delete a work by its id",
          description = "delete a work by its id",
          method = "DELETE")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
          @ApiResponse(responseCode = "404", description = "Object not found"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response deleteWork(@Parameter(description = "work id to delete", required = true)
                             @PathParam("workId") Long workId) {

    if (workId == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("work id is mandatory").build();
    }
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      processesService.deleteWorkById(workId, currentIdentityId);
      return Response.ok("ok").type(MediaType.TEXT_PLAIN).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity("Request not found").build();
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' isn't allowed to delete the request '{}'", currentIdentityId, workId, e);
      return Response.status(Response.Status.FORBIDDEN).entity("Not allowed to delete this request").build();
    } catch (Exception e) {
      LOG.error("Error while deleting a work", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Completes or reopens a request, answered with its process in the user's
   * language.
   *
   * @param completed whether the request is completed, under the key
   *          "value"
   * @param workId the request id
   * @return the updated request, or the refusal status
   */
  @PATCH
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/work/{workId}")
  @Operation(
          summary = "cancel or resume a work by its id",
          description = "cancel or resume a work by its id",
          method = "PATCH")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
          @ApiResponse(responseCode = "404", description = "Object not found"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response updateWorkCompleted(@Parameter(description = "completed object property", required = true) Map<String,Boolean> completed,
                                      @Parameter(description = "work id to be updated", required = true)
                                      @PathParam("workId") Long workId) {

    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    if (workId == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("work id is mandatory").build();
    }
    if (completed == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("completed object is mandatory").build();
    }
    Boolean completedValue = completed.get("value");
    if (completedValue == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("completed property value should not be null").build();
    }
    try {
      Work newWork = processesService.updateWorkCompleted(workId, completedValue, currentIdentityId);
      return Response.ok(EntityBuilder.toWorkEntity(processesService, newWork, "workFlow", getLocale())).build();
    } catch (ObjectNotFoundException | EntityNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' isn't allowed to update the request '{}'", currentIdentityId, workId, e);
      return Response.status(Response.Status.FORBIDDEN).entity("Not allowed to update this request").build();
    } catch (Exception e) {
      LOG.error("Error while canceling a work", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Creates a request draft, answered with its process in the user's
   * language.
   *
   * @param workEntity the draft to create
   * @return the created draft, or the refusal status
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/workDraft")
  @Operation(
          summary = "Creates a new WorkDraft",
          description = "Creates a new WorkDraft",
          method = "POST")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
                          @ApiResponse(responseCode = "400", description = "Invalid query input"),
                          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
                          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
                          @ApiResponse(responseCode = "404", description = "Object not found"),
                          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response createWorkDraft(@RequestBody(description = "WorkDaft object to create", required = true)
  WorkEntity workEntity) {
    if (workEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("workDraft object is mandatory").build();
    }
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      Work newWork = processesService.createWorkDraft(EntityBuilder.toDraft(workEntity), currentIdentityId);
      return Response.ok(EntityBuilder.toEntity(newWork, getLocale())).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(WORKFLOW_NOT_FOUND_MESSAGE).build();
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' isn't allowed to create a request draft", currentIdentityId, e);
      return Response.status(Response.Status.FORBIDDEN).entity("Not allowed to add a request to this workflow").build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error creating a work draft", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  /**
   * Updates a request draft, answered with its process in the user's
   * language.
   *
   * @param workEntity the draft to update
   * @return the updated draft, or the refusal status
   */
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/workDraft")
  @Operation(
          summary = "Updates a new workDraft",
          description = "Updates a new workDraft",
          method = "PUT")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
                          @ApiResponse(responseCode = "400", description = "Invalid query input"),
                          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
                          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
                          @ApiResponse(responseCode = "404", description = "Object not found"),
                          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response updateWorkDraft(@RequestBody(description = "Work object to update", required = true)
  WorkEntity workEntity) {
    if (workEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("workDraft object is mandatory").build();
    }
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      Work newWork = processesService.updateWorkDraft(EntityBuilder.toDraft(workEntity), currentIdentityId);
      return Response.ok(EntityBuilder.toEntity(newWork, getLocale())).build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' attempts to update a not existing Work draft '{}'", currentIdentityId, e);
      return Response.status(Response.Status.NOT_FOUND).entity("Work draft not found").build();
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' isn't allowed to update the request draft", currentIdentityId, e);
      return Response.status(Response.Status.FORBIDDEN).entity("Not allowed to update this request draft").build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error updating a Work draft", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  /**
   * Lists the current user's request drafts, their processes in the user's
   * language.
   *
   * @param userId the current user's identity id
   * @param expand the properties to expand
   * @param query a search on the draft
   * @param offset the offset of the first draft
   * @param limit the number of drafts
   * @return the drafts, or the refusal status
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/workDrafts")
  @Operation(
          summary = "Retrieves the list of workDrafts",
          description = "Retrieves the list of workDrafts for an authenticated user",
          method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getWorkDrafts(@Parameter(description = "Identity technical identifier", required = false)
                                @QueryParam("userId") Long userId,
                                @Parameter(description = "Processes properties to expand.", required = false)
                                @QueryParam("expand") String expand,
                                @Parameter(description = "Work query.", required = false)
                                @QueryParam("query") String query,
                                @Parameter(description = "Offset of results to return", required = false)
                                @Schema(defaultValue = "0")
                                @QueryParam("offset") int offset,
                                @Parameter(description = "Limit of results to return", required = false)
                                @Schema(defaultValue = "10")
                                @QueryParam("limit") int limit) {
    try {
      long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      if (currentIdentityId == 0) {
        return Response.status(Response.Status.UNAUTHORIZED).build();
      }

      if (userId != null && userId != currentIdentityId) {
        return Response.status(Response.Status.FORBIDDEN).entity(ONLY_CURRENT_USER_MESSAGE).build();
      }
      long userIdentityId = currentIdentityId;
      WorkFilter workFilter = new WorkFilter();
      if (query != null) {
        workFilter.setQuery(query);
      }
      workFilter.setIsDraft(true);
      List<Work> works = processesService.getWorkDrafts(userIdentityId, workFilter, offset, limit);
      return Response.ok(EntityBuilder.toWorkEntityList(works, getLocale())).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving list of work drafts", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @DELETE
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("users")
  @Path("/workDraft/{draftId}")
  @Operation(
          summary = "delete a work draft by its id",
          description = "delete a work draft by its id",
          method = "DELETE")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
          @ApiResponse(responseCode = "404", description = "Object not found"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response deleteWorkDraft(@Parameter(description = "Work draft id to delete", required = true)
                                 @PathParam("draftId") Long workflowId) {
    if (workflowId == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Work draft id is mandatory").build();
    }
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      this.processesService.deleteWorkDraftById(workflowId, currentIdentityId);
      return Response.ok("ok").type(MediaType.TEXT_PLAIN).build();
    } catch (ObjectNotFoundException | EntityNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity("Work draft not found").build();
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' isn't allowed to delete the request draft '{}'", currentIdentityId, workflowId, e);
      return Response.status(Response.Status.FORBIDDEN).entity("Not allowed to delete this request draft").build();
    } catch (Exception e) {
      LOG.warn("Error while deleting a work draft", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/works/statuses")
  @Operation(
          summary = "Retrieves the list of workDrafts",
          description = "Retrieves the list of workDrafts for an authenticated user",
          method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getAvailableWorkStatuses() {
    try {
      long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      if (currentIdentityId == 0) {
        return Response.status(Response.Status.UNAUTHORIZED).build();
      }
      List<WorkStatus> statuses = processesService.getAvailableWorkStatuses();
      return Response.ok(statuses).type(MediaType.APPLICATION_JSON_TYPE).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving list of work drafts", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  /**
   * Reads one of the current user's requests, its expanded process in the
   * user's language.
   *
   * @param workId the request id
   * @param expand the properties to expand
   * @return the request, or the refusal status
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/works/{workId}")
  @Operation(
          summary = "Retrieves a work by its given id",
          description = "Retrieves a work by its given id",
          method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "404", description = "Object not found"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getWorkById(@Parameter(description = "Work id.", required = true)
                              @PathParam("workId") Long workId,
                              @Parameter(description = "Processes properties to expand.")
                              @QueryParam("expand") String expand) {
    try {
      long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      if (currentIdentityId == 0) {
        return Response.status(Response.Status.UNAUTHORIZED).build();
      }
      if (workId == null) {
        return Response.status(Response.Status.BAD_REQUEST).build();
      }
      Work work = processesService.getWorkById(currentIdentityId, workId);
      return Response.ok(EntityBuilder.toWorkEntity(processesService, work, expand, getLocale())).build();
    } catch (EntityNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (Exception e) {
      LOG.warn("Error while getting work", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  /**
   * Reads a process the current user sees, its texts in the user's language,
   * or as stored, in the default language, for the process editor.
   *
   * @param workflowId the process id
   * @param expand the properties to expand
   * @param translate whether the texts are in the user's language
   * @return the process, or the refusal status
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/workflows/{workflowId}")
  @Operation(
          summary = "Retrieves a workflow by its given id",
          description = "Retrieves a workflow by its given id",
          method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "403", description = "Forbidden operation"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getWorkFlowById(@Parameter(description = "workflow id", required = true)
                               @PathParam("workflowId") Long workflowId,
                               @Parameter(description = "Processes properties to expand")
                               @QueryParam("expand") String expand,
                               @Parameter(description = "Whether the title, description and summary are in the user's language, true by default; false returns their stored default-language values, which the process editor edits")
                               @QueryParam("translate") @DefaultValue("true") boolean translate) {
    try {
      long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      if (currentIdentityId == 0) {
        return Response.status(Response.Status.UNAUTHORIZED).build();
      }
      if (workflowId == null) {
        return Response.status(Response.Status.BAD_REQUEST).build();
      }
      WorkFlow workFlow = processesService.getWorkFlow(workflowId, currentIdentityId);
      if (workFlow == null) {
        return Response.status(Response.Status.NOT_FOUND).build();
      }
      WorkFlowEntity workFlowEntity = EntityBuilder.toEntity(workFlow, expand, translate ? getLocale() : null);
      if (workFlow.getAcl() != null && workFlow.getAcl().isCanEdit()) {
        // The editor shows the process's own forms; the Service checks the right
        workFlowEntity.setRequestFormIds(processesService.getProcessFormIds(workflowId, currentIdentityId));
        workFlowEntity.setRequesterFormIds(workFlow.getRequesterFormIds());
      }
      return Response.ok(workFlowEntity).cacheControl(PER_USER_CACHE_CONTROL).build();
    } catch (IllegalAccessException e) {
      LOG.debug("User isn't allowed to see the workflow '{}'", workflowId, e);
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (Exception e) {
      LOG.warn("Error while getting workflow", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @POST
  @Path("/attachment/newDoc")
  @RolesAllowed("users")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(
          summary = "create new form document",
          description = "create new form document",
          method = "POST")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "404", description = "Not found"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response createNewFormDocument(@Parameter(description = "title", required = true)
                                        @Schema(defaultValue = "20")
                                        @FormParam("title") String title,
                                        @Parameter(description = "path of new document", required = true)
                                        @FormParam("path") String path,
                                        @Parameter(description = "New destination path's drive", required = true)
                                        @FormParam("pathDrive") String pathDrive,
                                        @Parameter(description = "template name of new document", required = true)
                                        @Schema(defaultValue = "20")
                                        @FormParam("templateName") String templateName,
                                        @Parameter(description = "entity type")
                                        @FormParam("entityType") String entityType,
                                        @Parameter(description = "entity id")
                                        @FormParam("entityId") Long entityId) {
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    if (StringUtils.isEmpty(title)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("New document title is mandatory").build();
    }
    if (StringUtils.isEmpty(templateName)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("New document template name is mandatory").build();
    }
    if (StringUtils.isEmpty(path)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("New document path is mandatory").build();
    }
    if (StringUtils.isEmpty(pathDrive)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("New destination path's drive is mandatory").build();
    }
    try {
      Attachment attachment = processesAttachmentService.createNewFormDocument(currentIdentityId, title, path, pathDrive, templateName, entityType, entityId);
      return Response.ok(org.exoplatform.services.attachments.utils.EntityBuilder.fromAttachment(identityManager, attachment))
                     .build();
    } catch (ItemExistsException e) {
      return Response.status(Response.Status.CONFLICT)
                     .entity("Document with the same name already exist in this current path")
                     .build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(WORKFLOW_NOT_FOUND_MESSAGE).build();
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' isn't allowed to add a document to the workflow '{}'", currentIdentityId, entityId, e);
      return Response.status(Response.Status.FORBIDDEN).entity("Not allowed to edit this workflow").build();
    } catch (Exception e) {
      LOG.error("Error when trying to a new document with type ", templateName, e);
      return Response.serverError().entity("Error when trying to a new document with type " + templateName).build();
    }
  }

  @GET
  @Path( "/illustration/{workflowId}")
  @RolesAllowed("users")
  @Operation(
          summary = "Gets a workflow illustration image by its id",
          description = "Gets a workflow illustration image by its id",
          method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "403", description = "Forbidden operation"),
          @ApiResponse(responseCode = "404", description = "Resource not found") })
  public Response getImageIllustration(@Context Request request,
                                             @Parameter(description = "workflow id", required = true) @PathParam("workflowId") Long workflowId,
                                             @Parameter(description = "Optional last modified parameter") @QueryParam("v") long lastModified) {

    if (workflowId == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("workflow id is mandatory").build();
    }
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      IllustrativeAttachment illustrativeAttachment = processesService.getWorkFlowIllustration(workflowId, currentIdentityId);
      Long lastUpdated = illustrativeAttachment.getLastUpdated();
      EntityTag eTag = new EntityTag(String.valueOf(lastUpdated), true);
      Response.ResponseBuilder builder = request.evaluatePreconditions(eTag);
      if (builder == null) {
        InputStream stream = illustrativeAttachment.getFileInputStream();
        builder = Response.ok(stream, illustrativeAttachment.getMimeType());
        builder.tag(eTag);
        if (lastModified > 0) {
          builder.lastModified(new Date(lastUpdated));
          builder.expires(new Date(System.currentTimeMillis() + CACHE_DURATION_MILLISECONDS));
        }
      }
      builder.cacheControl(lastModified > 0 ? ILLUSTRATION_CACHE_CONTROL : UNVERSIONED_ILLUSTRATION_CACHE_CONTROL);
      return builder.build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("Illustrative image of workflow '{}' not found", workflowId, e);
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' isn't allowed to see the workflow '{}'", currentIdentityId, workflowId, e);
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (Exception e) {
      LOG.error("An error occurred while getting image illustration", e);
      return Response.serverError().build();
    }
  }

  /**
   * Returns the current user's language, which the portal's localization
   * filter resolves for every REST request.
   *
   * @return the user's locale, or null outside a localized request
   */
  protected Locale getLocale() {
    return LocalizationFilter.getCurrentLocale();
  }

}
