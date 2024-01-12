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
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
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

  static {
    ILLUSTRATION_CACHE_CONTROL.setMaxAge(CACHE_DURATION_SECONDS);
  }

  public ProcessesRest(ProcessesService processesService,
                       IdentityManager identityManager,
                       ProcessesAttachmentService processesAttachmentService) {
    this.processesService = processesService;
    this.identityManager = identityManager;
    this.processesAttachmentService = processesAttachmentService;
  }

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
    try {
      long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      if (currentIdentityId == 0) {
        return Response.status(Response.Status.UNAUTHORIZED).build();
      }
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
      long userIdentityId = currentIdentityId;
      if (userId != null) {
        userIdentityId = userId;
      }
      List<WorkFlow> workFlows = processesService.getWorkFlows(filter, offset, limit, userIdentityId);
      return Response.ok(EntityBuilder.toRestEntities(workFlows, expand)).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving list of workFlows", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

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
      return Response.ok(EntityBuilder.toEntity(newWorkFlow, "")).build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to create a Work WorkFlow", e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error creating a WorkFlow", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

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
      return Response.ok(EntityBuilder.toEntity(newWorkFlow, "")).build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' attempts to update a not existing work workFlow '{}'", currentIdentityId, e);
      return Response.status(Response.Status.NOT_FOUND).entity("Work workFlow not found").build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' attempts to update a work workFlow for owner '{}'", currentIdentityId, e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error updating a work workFlow", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

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

      long userIdentityId = currentIdentityId;
      if (userId != null) {
        userIdentityId = userId;
      }
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
      return Response.ok(EntityBuilder.toWorkEntityList(processesService, works, expand)).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving list of works", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }


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
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response createWork(@RequestBody(description = "Work object to create", required = true)
                                     WorkEntity workEntity) {
    if (workEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Work object is mandatory").build();
    }
    if (workEntity.getProjectId() == 0 && workEntity.getWorkFlow().getProjectId() == 0 ) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Work projectId object is mandatory").build();
    }
    if (!workEntity.getWorkFlow().isEnabled()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Workflow is disabled").build();
    }
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      Work newWork = processesService.createWork(EntityBuilder.toWork(processesService,workEntity),currentIdentityId);
      return Response.ok(EntityBuilder.toWorkEntity(processesService, newWork, "workFlow")).build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to create a Work Work", e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error creating a Work", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

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
      return Response.ok(EntityBuilder.toWorkEntity(processesService, newWork, "workFlow")).build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' attempts to update a not existing work workFlow '{}'", currentIdentityId, e);
      return Response.status(Response.Status.NOT_FOUND).entity("Work workFlow not found").build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' attempts to update a work workFlow for owner '{}'", currentIdentityId, e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error updating a work workFlow", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("users")
  @Path("/permissions")
  @Operation(
          summary = "checks is current user is a processes manager",
          description = "checks is current user is a processes manager",
          method = "GET")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),})
  public Response isProcessesManager() {

    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      Identity identity = ConversationState.getCurrent().getIdentity();
      boolean isProcessesGroupMember = RestUtils.isProcessesGroupMember(identity);
      return Response.ok(String.valueOf(isProcessesGroupMember)).type(MediaType.TEXT_PLAIN).build();
    } catch (Exception e) {
      LOG.warn("Error while checking user permissions", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @DELETE
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("processes")
  @Path("/workflow/{workflowId}")
  @Operation(
          summary = "delete a workflow by its id",
          description = "delete a workflow by its id",
          method = "DELETE")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "404", description = "Object not found"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response deleteWorkflow(@Parameter(description = "Workflow id to delete", required = true)
                                 @PathParam("workflowId") Long workflowId) {
    if (workflowId == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Workflow id is mandatory").build();
    }
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    Identity identity = ConversationState.getCurrent().getIdentity();
    if (currentIdentityId == 0 || !RestUtils.isProcessesGroupMember(identity)) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      this.processesService.deleteWorkflowById(workflowId);
      return Response.ok("ok").type(MediaType.TEXT_PLAIN).build();
    } catch (EntityNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (Exception e) {
      LOG.warn("Error while deleting a workflow", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("users")
  @Path("/countWorks/{projectId}")
  @Operation(
          summary = "Count tasks by completed and uncompleted status",
          description = "Count tasks by completed and uncompleted status",
          method = "GET")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "404", description = "Object not found"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"),})
  public Response countWorksByWorkflow(@Parameter(description = "Tasks project id", required = true)
                                       @PathParam("projectId") Long projectId,
                                       @Parameter(description = "Processes properties to expand.")
                                       @QueryParam("isCompleted") @DefaultValue("true") Boolean isCompleted) {
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    if (projectId == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Project id is mandatory").build();
    }
    try {
      WorkFlow workFlow = processesService.getWorkFlowByProjectId(projectId);
      if (workFlow == null) {
        return Response.status(Response.Status.NOT_FOUND).entity("workflow not found").build();
      }
      int worksCount = processesService.countWorksByWorkflow(projectId, isCompleted);
      return Response.ok(String.valueOf(worksCount)).type(MediaType.TEXT_PLAIN).build();
    } catch (Exception e) {
      LOG.error("Error while getting works count", e);
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
      processesService.deleteWorkById(workId);
      return Response.ok("ok").type(MediaType.TEXT_PLAIN).build();
    } catch (Exception e) {
      LOG.error("Error while deleting a work", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

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
      Work newWork = processesService.updateWorkCompleted(workId, completedValue);
      return Response.ok(EntityBuilder.toWorkEntity(processesService, newWork, "workFlow")).build();
    } catch (EntityNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (Exception e) {
      LOG.error("Error while canceling a work", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

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
      Work newWork = processesService.createWorkDraft(EntityBuilder.fromEntity(workEntity), currentIdentityId);
      return Response.ok(EntityBuilder.toEntity(newWork)).build();
    } catch (Exception e) {
      LOG.warn("Error creating a work draft", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

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
      Work newWork = processesService.updateWorkDraft(EntityBuilder.fromEntity(workEntity), currentIdentityId);
      return Response.ok(EntityBuilder.toEntity(newWork)).build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' attempts to update a not existing Work draft '{}'", currentIdentityId, e);
      return Response.status(Response.Status.NOT_FOUND).entity("Work draft not found").build();
    } catch (Exception e) {
      LOG.warn("Error updating a Work draft", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

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

      long userIdentityId = currentIdentityId;
      if (userId != null) {
        userIdentityId = userId;
      }
      WorkFilter workFilter = new WorkFilter();
      if (query != null) {
        workFilter.setQuery(query);
      }
      workFilter.setIsDraft(true);
      List<Work> works = processesService.getWorkDrafts(userIdentityId, workFilter, offset, limit);
      return Response.ok(EntityBuilder.toWorkEntityList(works)).build();
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
      this.processesService.deleteWorkDraftById(workflowId);
      return Response.ok("ok").type(MediaType.TEXT_PLAIN).build();
    } catch (EntityNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity("Work draft not found").build();
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
      return Response.ok(EntityBuilder.toWorkEntity(processesService, work, expand)).build();
    } catch (EntityNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (Exception e) {
      LOG.warn("Error while getting work", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

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
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getWorkFlowById(@Parameter(description = "workflow id", required = true)
                               @PathParam("workflowId") Long workflowId,
                               @Parameter(description = "Processes properties to expand")
                               @QueryParam("expand") String expand) {
    try {
      long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      if (currentIdentityId == 0) {
        return Response.status(Response.Status.UNAUTHORIZED).build();
      }
      if (workflowId == null) {
        return Response.status(Response.Status.BAD_REQUEST).build();
      }
      WorkFlow workFlow = processesService.getWorkFlow(workflowId);
      if (workFlow == null) {
        return Response.status(Response.Status.NOT_FOUND).build();
      }
      return Response.ok(EntityBuilder.toEntity(workFlow, expand)).build();
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
          @ApiResponse(responseCode = "404", description = "Resource not found") })
  public Response getImageIllustration(@Context Request request,
                                             @Parameter(description = "workflow id", required = true) @PathParam("workflowId") Long workflowId,
                                             @Parameter(description = "Optional last modified parameter") @QueryParam("v") long lastModified) {

    if (workflowId == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("workflow id is mandatory").build();
    }
    try {
      WorkFlow workFlow = processesService.getWorkFlow(workflowId);
      if (workFlow == null) {
        return Response.status(Response.Status.NOT_FOUND).entity("workflow not found").build();
      }
      Long illustrationId = workFlow.getIllustrativeAttachment().getId();
      IllustrativeAttachment illustrativeAttachment = processesService.getIllustrationImageById(illustrationId);
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
          builder.cacheControl(ILLUSTRATION_CACHE_CONTROL);
        }
      }
      return builder.build();
    } catch (ObjectNotFoundException e) {
      LOG.error("Illustrative image not found", e);
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (Exception e) {
      LOG.error("An error occurred while getting image illustration", e);
      return Response.serverError().build();
    }
  }
}
