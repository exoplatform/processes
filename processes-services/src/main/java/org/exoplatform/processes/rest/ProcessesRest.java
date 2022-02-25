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

import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.persistence.EntityNotFoundException;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.rest.model.WorkEntity;
import org.exoplatform.processes.rest.model.WorkFlowEntity;
import org.exoplatform.processes.rest.util.EntityBuilder;
import org.exoplatform.processes.rest.util.RestUtils;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.swagger.annotations.*;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

@Path("/v1/processes")
@Api(value = "/v1/processes", description = "Manages processes") // NOSONAR
public class ProcessesRest implements ResourceContainer {

  private static final Log LOG = ExoLogger.getLogger(ProcessesRest.class);

  private ProcessesService processesService;

  private IdentityManager  identityManager;

  private static final String PROCESSES_SPACE_GROUP_ID      = "/spaces/processes_space";


  public ProcessesRest(ProcessesService processesService, IdentityManager identityManager) {
    this.processesService = processesService;
    this.identityManager = identityManager;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/workflows")
  @ApiOperation(value = "Retrieves the list of workFlows for an authenticated user", httpMethod = "GET", response = Response.class, produces = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
      @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Not found"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response getWorkFlows(@ApiParam(value = "Identity technical identifier", required = false)
  @QueryParam("userId")
  Long userId,
                                  @ApiParam(value = "filter workflow by status", required = false)
                                  @QueryParam("enabled") Boolean enabled,
                                  @ApiParam(value = "Search query entered by the user", required = false)
                                  @QueryParam("query")
                                  String query,
                                  @ApiParam(value = "Processes properties to expand.", required = false)
                                  @QueryParam("expand")
                                  String expand,
                                  @ApiParam(value = "Offset of results to return", required = false, defaultValue = "0")
                                  @QueryParam("offset")
                                  int offset,
                                  @ApiParam(value = "Limit of results to return", required = false, defaultValue = "10")
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
      filter.setQuery(query);
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
  @ApiOperation(value = "Creates a new WorkFlow", httpMethod = "POST", response = Response.class, consumes = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response createWorkFlow(@ApiParam(value = "WorkFlow object to create", required = true)
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
  @ApiOperation(value = "Updates a workFlow", httpMethod = "PUT", response = Response.class, consumes = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Object to update not found"),
      @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response updateWorkFlow(@ApiParam(value = "WorkFlow object to update", required = true)
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
  @ApiOperation(value = "Retrieves the list of works for an authenticated user", httpMethod = "GET", response = Response.class, produces = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response getWorks(@ApiParam(value = "Identity technical identifier", required = false)
                                  @QueryParam("userId")
                                          Long userId,
                                  @ApiParam(value = "Processes properties to expand.", required = false)
                                  @QueryParam("expand")
                                          String expand,
                                  @ApiParam(value = "Offset of results to return", required = false, defaultValue = "0")
                                  @QueryParam("offset")
                                          int offset,
                                  @ApiParam(value = "Limit of results to return", required = false, defaultValue = "10")
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
      List<Work> workFlows = processesService.getWorks(userIdentityId, offset, limit);
      return Response.ok(EntityBuilder.toWorkEntityList(processesService, workFlows, expand)).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving list of workFlows", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }


  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/works")
  @ApiOperation(value = "Creates a Work", httpMethod = "POST", response = Response.class, consumes = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response createWork(@ApiParam(value = "Work object to create", required = true)
                                     WorkEntity workEntity) {
    if (workEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Work object is mandatory").build();
    }
    if (workEntity.getProjectId() == 0 && workEntity.getWorkFlow().getProjectId() == 0 ) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Work projectId object is mandatory").build();
    }
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      Work newWork = processesService.createWork(EntityBuilder.toWork(processesService,workEntity),currentIdentityId);
      return Response.ok(EntityBuilder.toWorkEntity(processesService, newWork, "")).build();
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
  @ApiOperation(value = "Updates a new work", httpMethod = "PUT", response = Response.class, consumes = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.NO_CONTENT, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response updateWork(@ApiParam(value = "Work object to update", required = true)
                                         WorkEntity workEntity) {
    if (workEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Work object is mandatory").build();
    }
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      Work newWork = processesService.updateWork(EntityBuilder.toWork(processesService, workEntity),
              currentIdentityId);
      return Response.ok(EntityBuilder.toWorkEntity(processesService,newWork, "")).build();
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
  @ApiOperation(value = "checks is current user is a processes manager", httpMethod = "GET", response = Response.class, produces = "text/plain")
  @ApiResponses(value = {@ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),})
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
  @ApiOperation(value = "delete a workflow by its id", httpMethod = "DELETE", response = Response.class, produces = "text/plain")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Object not found"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response deleteWorkflow(@ApiParam(value = "Workflow id to delete", required = true)
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
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/processesSpace/info")
  @ApiOperation(value = "get Processes Space info", httpMethod = "GET", response = Response.class, produces = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
            @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Object not found"),
            @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
            @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response getProcessesSpaceInfo() {
    long currentIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (currentIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      SpaceService spaceService = CommonsUtils.getService(SpaceService.class);
      Space processesSpace = spaceService.getSpaceByGroupId(PROCESSES_SPACE_GROUP_ID);
      if (processesSpace == null) {
        return Response.status(Response.Status.NOT_FOUND).build();
      }
      return Response.ok(processesSpace).type(MediaType.APPLICATION_JSON_TYPE).build();
    } catch (Exception e) {
      LOG.error("Error while getting processes space info", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("users")
  @Path("/countWorks/{projectId}")
  @ApiOperation(value = "Count tasks by completed and uncompleted status", httpMethod = "GET", response = Response.class, produces = "text/plain")
  @ApiResponses(value = {@ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Object not found"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),})
  public Response countWorksByWorkflow(@ApiParam(value = "Tasks project id", required = true)
                                       @PathParam("projectId") Long projectId,
                                       @ApiParam(value = "Processes properties to expand.")
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
  @ApiOperation(value = "delete a work by its id", httpMethod = "DELETE", response = Response.class, produces = "text/plain")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response deleteWork(@ApiParam(value = "work id to delete", required = true)
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
}
