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

import java.io.InputStream;
import java.util.Date;

import javax.annotation.security.RolesAllowed;
import javax.jcr.ItemExistsException;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.model.IllustrativeAttachment;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.service.ProcessAttachmentService;
import org.exoplatform.processes.service.ProcessService;
import org.exoplatform.services.attachments.model.Attachment;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Path("/process/attachment")
@Tag(name = "/process/attachment", description = "Manages processes attachments")

public class AttachmentRest implements ResourceContainer {

  private static final Log          LOG                         = ExoLogger.getLogger(AttachmentRest.class);

  private static final int          CACHE_DURATION_SECONDS      = 31536000;

  private static final long         CACHE_DURATION_MILLISECONDS = CACHE_DURATION_SECONDS * 1000L;

  private static final CacheControl ILLUSTRATION_CACHE_CONTROL  = new CacheControl();

  static {
    ILLUSTRATION_CACHE_CONTROL.setMaxAge(CACHE_DURATION_SECONDS);
  }

  private final ProcessService           processService;

  private final IdentityManager          identityManager;

  private final ProcessAttachmentService processAttachmentService;

  public AttachmentRest(ProcessService processService,
                        IdentityManager identityManager,
                        ProcessAttachmentService processAttachmentService) {
    this.processService = processService;
    this.identityManager = identityManager;
    this.processAttachmentService = processAttachmentService;
  }

  @POST
  @RolesAllowed("users")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "create new form document", description = "create new form document", method = "POST")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response createNewFormDocument(@Parameter(description = "title", required = true)
  @Schema(defaultValue = "20")
  @FormParam("title")
  String title,
                                        @Parameter(description = "path of new document", required = true)
                                        @FormParam("path")
                                        String path,
                                        @Parameter(description = "New destination path's drive", required = true)
                                        @FormParam("pathDrive")
                                        String pathDrive,
                                        @Parameter(description = "template name of new document", required = true)
                                        @Schema(defaultValue = "20")
                                        @FormParam("templateName")
                                        String templateName,
                                        @Parameter(description = "entity type")
                                        @FormParam("entityType")
                                        String entityType,
                                        @Parameter(description = "entity id")
                                        @FormParam("entityId")
                                        Long entityId) {
    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    if (currentIdentity == null) {
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
      Attachment attachment = processAttachmentService.createNewFormDocument(currentIdentity.getUserId(),
                                                                             title,
                                                                             path,
                                                                             pathDrive,
                                                                             templateName,
                                                                             entityType,
                                                                             entityId);
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
  @Path("/{workflowId}")
  @RolesAllowed("users")
  @Operation(summary = "Gets a workflow illustration image by its id", description = "Gets a workflow illustration image by its id", method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "500", description = "Internal server error"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "404", description = "Resource not found") })
  public Response getImageIllustration(@Context
  Request request,
                                       @Parameter(description = "workflow id", required = true)
                                       @PathParam("workflowId")
                                       Long workflowId,
                                       @Parameter(description = "Optional last modified parameter")
                                       @QueryParam("v")
                                       long lastModified) {

    Identity currentIdentity = ConversationState.getCurrent().getIdentity();
    if (currentIdentity == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    if (workflowId == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("workflow id is mandatory").build();
    }
    try {
      WorkFlow workFlow = processService.getWorkFlow(workflowId, currentIdentity.getUserId());
      if (workFlow == null) {
        return Response.status(Response.Status.NOT_FOUND).entity("workflow not found").build();
      }
      Long illustrationId = workFlow.getIllustrativeAttachment().getId();
      IllustrativeAttachment illustrativeAttachment = processService.getIllustrationImageById(illustrationId,
                                                                                              currentIdentity.getUserId());
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
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' is not allowed to illustration of the request", currentIdentity.getUserId(), e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (ObjectNotFoundException e) {
      LOG.error("Illustrative image not found", e);
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }
}
