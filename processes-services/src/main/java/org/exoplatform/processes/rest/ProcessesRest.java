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
import javax.persistence.EntityNotFoundException;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

import org.exoplatform.services.rest.http.PATCH;
import org.apache.commons.lang.StringUtils;
import org.exoplatform.common.http.HTTPStatus;
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

import io.swagger.annotations.*;

@Path("/v1/processes")
@Api(value = "/v1/processes", description = "Manages processes") // NOSONAR
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
                                  @ApiParam(value = "filter workflow that i manage", required = false)
                                  @QueryParam("manager") Boolean manager,
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
                                  @ApiParam("work completed property") @QueryParam("completed") Boolean completed,
                                  @ApiParam("Works status") @QueryParam("status") String status,
                                  @ApiParam("Works query") @QueryParam("query") String query,
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

  @PATCH
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/work/{workId}")
  @ApiOperation(value = "cancel or resume a work by its id", httpMethod = "PATCH", response = Response.class, produces = "text/plain")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response updateWorkCompleted(@ApiParam(value = "completed object property", required = true) Map<String,Boolean> completed,
                                      @ApiParam(value = "work id to be updated", required = true)
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
  @ApiOperation(value = "Creates a new WorkDraft", httpMethod = "POST", response = Response.class, consumes = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
                          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
                          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
                          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response createWorkDraft(@ApiParam(value = "WorkDaft object to create", required = true)
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
  @ApiOperation(value = "Updates a new workDraft", httpMethod = "PUT", response = Response.class, consumes = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
                          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
                          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
                          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response updateWorkDraft(@ApiParam(value = "Work object to update", required = true)
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
  @ApiOperation(value = "Retrieves the list of workDrafts for an authenticated user", httpMethod = "GET", response = Response.class, produces = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response getWorkDrafts(@ApiParam(value = "Identity technical identifier", required = false)
                                @QueryParam("userId") Long userId,
                                @ApiParam(value = "Processes properties to expand.", required = false)
                                @QueryParam("expand") String expand,
                                @ApiParam(value = "Work query.", required = false)
                                @QueryParam("query") String query,
                                @ApiParam(value = "Offset of results to return", required = false, defaultValue = "0")
                                @QueryParam("offset") int offset,
                                @ApiParam(value = "Limit of results to return", required = false, defaultValue = "10")
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
  @ApiOperation(value = "delete a work draft by its id", httpMethod = "DELETE", response = Response.class, produces = "text/plain")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Object not found"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response deleteWorkDraft(@ApiParam(value = "Work draft id to delete", required = true)
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
  @ApiOperation(value = "Retrieves the list of workDrafts for an authenticated user", httpMethod = "GET", response = Response.class, produces = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
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
  @ApiOperation(value = "Retrieves a work by its given id", httpMethod = "GET", response = Response.class, produces = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Object not found"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response getWorkById(@ApiParam(value = "Work id.", required = true)
                              @PathParam("workId") Long workId,
                              @ApiParam(value = "Processes properties to expand.")
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
  @ApiOperation(value = "Retrieves a workflow by its given id", httpMethod = "GET", response = Response.class, produces = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
      @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Not found"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response getWorkFlowById(@ApiParam(value = "workflow id", required = true)
                               @PathParam("workflowId") Long workflowId,
                               @ApiParam(value = "Processes properties to expand")
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
  @ApiOperation(value = "create new form document", httpMethod = "POST", response = Response.class, notes = "This returns a new created document")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Not found"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response createNewFormDocument(@ApiParam(value = "title", required = true, defaultValue = "20")
                                       @FormParam("title") String title,
                                        @ApiParam(value = "path of new document", required = true)
                                        @FormParam("path") String path,
                                        @ApiParam(value = "New destination path's drive", required = true)
                                        @FormParam("pathDrive") String pathDrive,
                                        @ApiParam(value = "template name of new document", required = true, defaultValue = "20")
                                        @FormParam("templateName") String templateName,
                                        @ApiParam(value = "entity type")
                                        @FormParam("entityType") String entityType,
                                        @ApiParam(value = "entity id")
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
  @ApiOperation(value = "Gets a workflow illustration image by its id", httpMethod = "GET", response = Response.class)
  @ApiResponses(value = { @ApiResponse(code = 200, message = "Request fulfilled"),
          @ApiResponse(code = 500, message = "Internal server error"), @ApiResponse(code = 400, message = "Invalid query input"),
          @ApiResponse(code = 404, message = "Resource not found") })
  public Response getImageIllustration(@Context Request request,
                                             @ApiParam(value = "workflow id", required = true) @PathParam("workflowId") Long workflowId,
                                             @ApiParam(value = "Optional last modified parameter") @QueryParam("v") long lastModified) {
    
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
