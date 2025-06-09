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

import java.util.List;
import java.util.Map;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFilter;
import org.exoplatform.processes.rest.model.WorkEntity;
import org.exoplatform.processes.service.ProcessService;
import org.exoplatform.processes.service.RequestService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.http.PATCH;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Path("/request")
@Tag(name = "/request", description = "Manages requests")
public class RequestRest implements ResourceContainer {

  private static final Log     LOG = ExoLogger.getLogger(RequestRest.class);

  private final ProcessService processService;

  private final RequestService requestService;

  public RequestRest(ProcessService processService, RequestService requestService) {
    this.processService = processService;
    this.requestService = requestService;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(summary = "Retrieves the list of requests", description = "Retrieves the list of requests for an authenticated user", method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getWorks(@Parameter(description = "Identity technical identifier")
  @QueryParam("userName")
  String userName,
                           @Parameter(description = "Processes properties to expand.")
                           @QueryParam("expand")
                           String expand,
                           @Parameter(description = "request completed property")
                           @QueryParam("completed")
                           Boolean completed,
                           @Parameter(description = "requests status")
                           @QueryParam("status")
                           String status,
                           @Parameter(description = "requests query")
                           @QueryParam("query")
                           String query,
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
    String currentUserName = currentIdentity.getUserId();
    if (!StringUtils.isEmpty(userName)) {
      currentUserName = userName;
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
    List<Work> works = requestService.getWorks(currentUserName, workFilter, offset, limit);
    return Response.ok(EntityBuilder.toWorkEntityList(processService, works, expand)).build();
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/{workId}")
  @Operation(summary = "Retrieves a request by its given id", description = "Retrieves a request by its given id", method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "404", description = "Object not found"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getWorkById(@Parameter(description = "request id.", required = true)
  @PathParam("workId")
  Long workId,
                              @Parameter(description = "Processes properties to expand.")
                              @QueryParam("expand")
                              String expand) {
    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    if (currentIdentity == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    if (workId == null) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    try {
      Work work = requestService.getWorkById(currentIdentity.getUserId(), workId);
      return Response.ok(EntityBuilder.toWorkEntity(processService, work, expand)).build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' is not allowed to access to the request with id '{}'", currentIdentity.getUserId(), workId, e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity("request not found").build();
    }
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(summary = "Creates a request", description = "Creates a request", method = "POST")
  @ApiResponses(value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response createWork(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "request object to create", required = true)
  WorkEntity workEntity) {
    if (workEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("request object is mandatory").build();
    }
    if (workEntity.getProjectId() == 0 && workEntity.getWorkFlow().getProjectId() == 0) {
      return Response.status(Response.Status.BAD_REQUEST).entity("request projectId object is mandatory").build();
    }
    if (!workEntity.getWorkFlow().isEnabled()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Workflow is disabled").build();
    }
    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    if (currentIdentity == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      Work newWork = requestService.createWork(EntityBuilder.toWork(processService, workEntity), currentIdentity.getUserId());
      return Response.ok(EntityBuilder.toWorkEntity(processService, newWork, "workFlow")).build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' is not allowed to create a request on the process with id '{}'",
                currentIdentity.getUserId(),
                workEntity.getWorkFlow().getId(),
                e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(summary = "Updates a new request", description = "Updates a new request", method = "PUT")
  @ApiResponses(value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response updateWork(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "request object to update", required = true)
  WorkEntity workEntity) {
    if (workEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("request object is mandatory").build();
    }
    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    if (currentIdentity == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      Work newWork = requestService.updateWork(EntityBuilder.toWork(processService, workEntity), currentIdentity.getUserId());
      return Response.ok(EntityBuilder.toWorkEntity(processService, newWork, "workFlow")).build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' attempts to update a not existing request '{}'", currentIdentity.getUserId(), e);
      return Response.status(Response.Status.NOT_FOUND).entity("request not found").build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' is not allowed to update the process with id '{}'",
                currentIdentity.getUserId(),
                workEntity.getId(),
                e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    }
  }

  @PATCH
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/{workId}")
  @Operation(summary = "cancel or resume a request by its id", description = "cancel or resume a request by its id", method = "PATCH")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response updateWorkCompleted(@Parameter(description = "completed object property", required = true)
  Map<String, Boolean> completed,
                                      @Parameter(description = "request id to be updated", required = true)
                                      @PathParam("workId")
                                      Long workId) {

    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    if (currentIdentity == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    if (workId == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("request id is mandatory").build();
    }
    if (completed == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("completed object is mandatory").build();
    }
    Boolean completedValue = completed.get("value");
    if (completedValue == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("completed property value should not be null").build();
    }
    try {
      Work newWork = requestService.updateWorkCompleted(workId, currentIdentity.getUserId(), completedValue);
      return Response.ok(EntityBuilder.toWorkEntity(processService, newWork, "workFlow")).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' is not allowed to complete the request with id '{}'", currentIdentity.getUserId(), workId, e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    }
  }

  @DELETE
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("users")
  @Path("/{workId}")
  @Operation(summary = "delete a request by its id", description = "delete a request by its id", method = "DELETE")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response deleteWork(@Parameter(description = "request id to delete", required = true)
  @PathParam("workId")
  Long workId) {

    if (workId == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("request id is mandatory").build();
    }
    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    if (currentIdentity == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      requestService.deleteWorkById(workId, currentIdentity.getUserId());
      return Response.ok("ok").type(MediaType.TEXT_PLAIN).build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' is not allowed to delete the request with id '{}'", currentIdentity.getUserId(), workId, e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }
}
