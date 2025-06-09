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

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFilter;
import org.exoplatform.processes.rest.model.WorkEntity;
import org.exoplatform.processes.service.RequestService;
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

@Path("/draft")
@Tag(name = "/draft", description = "Manage drafts")
public class DraftRest implements ResourceContainer {
  private static final Log     LOG = ExoLogger.getLogger(DraftRest.class);

  private final RequestService requestService;

  public DraftRest(RequestService requestService) {
    this.requestService = requestService;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(summary = "Retrieves the list of workDrafts", description = "Retrieves the list of workDrafts for an authenticated user", method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getWorkDrafts(@Parameter(description = "Processes properties to expand.")
  @QueryParam("expand")
  String expand,
                                @Parameter(description = "Draft query.")
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
    WorkFilter workFilter = new WorkFilter();
    if (query != null) {
      workFilter.setQuery(query);
    }
    workFilter.setIsDraft(true);
    List<Work> works = requestService.getWorkDrafts(currentIdentity.getUserId(), workFilter, offset, limit);
    return Response.ok(EntityBuilder.toWorkEntityList(works)).build();
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(summary = "Creates a new WorkDraft", description = "Creates a new WorkDraft", method = "POST")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response createWorkDraft(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "WorkDaft object to create", required = true)
  WorkEntity workEntity) {
    if (workEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("workDraft object is mandatory").build();
    }
    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    if (currentIdentity == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      Work newWork = requestService.createWorkDraft(EntityBuilder.fromEntity(workEntity), currentIdentity.getUserId());
      return Response.ok(EntityBuilder.toEntity(newWork)).build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' is not allowed to create a draft on the process with id '{}'",
                currentIdentity.getUserId(),
                workEntity.getWorkFlow().getId(),
                e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    }
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(summary = "Updates a new workDraft", description = "Updates a new workDraft", method = "PUT")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response updateWorkDraft(@RequestBody(description = "Draft object to update", required = true)
  WorkEntity workEntity) {
    if (workEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("workDraft object is mandatory").build();
    }
    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    if (currentIdentity == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      Work newWork = requestService.updateWorkDraft(EntityBuilder.fromEntity(workEntity), currentIdentity.getUserId());
      return Response.ok(EntityBuilder.toEntity(newWork)).build();
    } catch (ObjectNotFoundException e) {
      LOG.debug("User '{}' attempts to update a not existing draft '{}'", currentIdentity.getUserId(), e);
      return Response.status(Response.Status.NOT_FOUND).entity("Draft not found").build();
    } catch (IllegalAccessException e) {
      LOG.debug("User '{}' is not allowed to update the draft with id '{}'",
                currentIdentity.getUserId(),
                workEntity.getDraftId(),
                e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    }
  }

  @DELETE
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("users")
  @Path("/{draftId}")
  @Operation(summary = "delete a draft by its id", description = "delete a draft by its id", method = "DELETE")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "404", description = "Object not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response deleteWorkDraft(@Parameter(description = "Draft id to delete", required = true)
  @PathParam("draftId")
  Long workflowId) {
    if (workflowId == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Draft id is mandatory").build();
    }
    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    if (currentIdentity == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      this.requestService.deleteWorkDraftById(workflowId, currentIdentity.getUserId());
      return Response.ok("ok").type(MediaType.TEXT_PLAIN).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity("Draft not found").build();
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' is not allowed to delete the draft with id '{}'", currentIdentity.getUserId(), workflowId, e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    }
  }
}
