/*
 * Copyright (C) 2025 eXo Platform SAS
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

import static org.exoplatform.processes.Utils.ProcessesUtils.isProcessAdmin;

import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.model.WorkStatus;
import org.exoplatform.processes.rest.model.WorkFlowEntity;
import org.exoplatform.processes.service.ProcessService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Path("/process")
@Tag(name = "/process", description = "Manages processes")
public class ProcessRest implements ResourceContainer {
  private static final Log     LOG = ExoLogger.getLogger(ProcessRest.class);

  private final ProcessService processService;

  public ProcessRest(ProcessService processService) {
    this.processService = processService;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(summary = "Retrieves the list of workFlows", description = "Retrieves the list of workFlows for an authenticated user", method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getWorkFlows(@Parameter(name = "Identity technical identifier")
  @QueryParam("userName")
  String userName,
                               @Parameter(description = "filter process by status")
                               @QueryParam("enabled")
                               Boolean enabled,
                               @Parameter(description = "filter process that i manage")
                               @QueryParam("manager")
                               Boolean manager,
                               @Parameter(description = "Search query entered by the user")
                               @QueryParam("query")
                               String query,
                               @Parameter(description = "Processes properties to expand.")
                               @QueryParam("expand")
                               String expand,
                               @Parameter(description = "Offset of results to return")
                               @Schema(defaultValue = "0")
                               @QueryParam("offset")
                               int offset,
                               @Parameter(description = "Limit of results to return")
                               @Schema(defaultValue = "10")
                               @QueryParam("limit")
                               int limit) {
    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    if (currentIdentity == null) {
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
    String currentUserName = currentIdentity.getUserId();
    if (!StringUtils.isEmpty(userName)) {
      currentUserName = userName;
    }
    List<WorkFlow> workFlows = processService.getWorkFlows(filter, offset, limit, currentUserName);
    return Response.ok(EntityBuilder.toRestEntities(workFlows, expand)).build();
  }

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("users")
  @Path("/count/{projectId}")
  @Operation(summary = "Count tasks by completed and uncompleted status", description = "Count tasks by completed and uncompleted status", method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "404", description = "Object not found"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response countWorksByWorkflow(@Parameter(description = "Tasks project id", required = true)
  @PathParam("projectId")
  Long projectId,
                                       @Parameter(description = "Processes properties to expand.")
                                       @QueryParam("isCompleted")
                                       @DefaultValue("true")
                                       Boolean isCompleted) {
    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    if (currentIdentity == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    if (projectId == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Project id is mandatory").build();
    }
    try {
      int worksCount = processService.countWorksByWorkflow(projectId, currentIdentity.getUserId(), isCompleted);
      return Response.ok(String.valueOf(worksCount)).type(MediaType.TEXT_PLAIN).build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' is not allowed to get requests count for the project with id '{}'",
                currentIdentity.getUserId(),
                projectId,
                e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity("Process not found").build();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/{workflowId}")
  @Operation(summary = "Retrieves a process by its given id", description = "Retrieves a process by its given id", method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getWorkFlowById(@Parameter(description = "Process id", required = true)
  @PathParam("workflowId")
  Long workflowId,
                                  @Parameter(description = "Process properties to expand")
                                  @QueryParam("expand")
                                  String expand) {
    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    if (currentIdentity == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    if (workflowId == null) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    try {
      WorkFlow workFlow = processService.getWorkFlow(workflowId, currentIdentity.getUserId());
      if (workFlow == null) {
        return Response.status(Response.Status.NOT_FOUND).build();
      }
      return Response.ok(EntityBuilder.toEntity(workFlow, expand)).build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' is not allowed to access to the process with id '{}'", currentIdentity.getUserId(), workflowId, e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity("Process not found").build();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/statuses")
  @Operation(summary = "Retrieves the list of workDrafts", description = "Retrieves the list of workDrafts for an authenticated user", method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getAvailableWorkStatuses() {
    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    if (currentIdentity == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    List<WorkStatus> statuses = processService.getAvailableWorkStatuses();
    return Response.ok(statuses).type(MediaType.APPLICATION_JSON_TYPE).build();
  }

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("users")
  @Path("/permissions")
  @Operation(summary = "checks is current user is a processes manager", description = "checks is current user is a processes manager", method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response isProcessesManager() {

    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    if (currentIdentity == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    Identity identity = ConversationState.getCurrent().getIdentity();
    return Response.ok(String.valueOf(isProcessAdmin(identity))).type(MediaType.TEXT_PLAIN).build();
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(summary = "Creates a new process", description = "Creates a new process", method = "POST")
  @ApiResponses(value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response createWorkFlow(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Process object to create", required = true)
  WorkFlowEntity workFlowEntity) {
    if (workFlowEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Process object is mandatory").build();
    }
    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    if (currentIdentity == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      WorkFlow newWorkFlow = processService.createWorkFlow(EntityBuilder.fromEntity(workFlowEntity), currentIdentity.getUserId());
      return Response.ok(EntityBuilder.toEntity(newWorkFlow, "")).build();
    } catch (IllegalAccessException e) {
      LOG.warn("User {} is not allowed to create the process", currentIdentity.getUserId(), e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    }
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(summary = "Updates a process", description = "Updates a process", method = "PUT")
  @ApiResponses(value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "404", description = "Object to update not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response updateWorkFlow(@RequestBody(description = "Process object to update", required = true)
  WorkFlowEntity workFlowEntity) {
    if (workFlowEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Process object is mandatory").build();
    }
    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    if (currentIdentity == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      WorkFlow newWorkFlow = processService.updateWorkFlow(EntityBuilder.fromEntity(workFlowEntity), currentIdentity.getUserId());
      return Response.ok(EntityBuilder.toEntity(newWorkFlow, "")).build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' attempts to update a not existing work process '{}'", currentIdentity.getUserId(), e);
      return Response.status(Response.Status.NOT_FOUND).entity("Work process not found").build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' is not allowed to update the process with id '{}'",
                currentIdentity.getUserId(),
                workFlowEntity.getId(),
                e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    }
  }

  @DELETE
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("processes")
  @Path("/{workflowId}")
  @Operation(summary = "delete a process by its id", description = "delete a process by its id", method = "DELETE")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "404", description = "Object not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response deleteWorkflow(@Parameter(description = "Process id to delete", required = true)
  @PathParam("workflowId")
  Long workflowId) {
    if (workflowId == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Process id is mandatory").build();
    }
    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    if (currentIdentity == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      this.processService.deleteWorkflowById(workflowId, currentIdentity.getUserId());
      return Response.ok("ok").type(MediaType.TEXT_PLAIN).build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' is not allowed to delete the process with id '{}'", currentIdentity.getUserId(), workflowId, e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity("Process not found").build();
    }
  }
}
