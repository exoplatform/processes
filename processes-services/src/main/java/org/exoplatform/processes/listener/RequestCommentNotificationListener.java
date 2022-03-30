/*
 * Copyright (C) 2022 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.processes.listener;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.notification.plugin.RequestCommentPlugin;
import org.exoplatform.processes.notification.utils.NotificationArguments;
import org.exoplatform.processes.notification.utils.NotificationUtils;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.task.dto.CommentDto;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.integration.TaskCommentNotificationListener;

public class RequestCommentNotificationListener extends TaskCommentNotificationListener {
  private ProcessesService processesService;

  public RequestCommentNotificationListener(OrganizationService organizationService, ProcessesService processesService) {
    super(organizationService);
    this.processesService = processesService;
  }

  @Override
  public void onEvent(Event<TaskDto, CommentDto> event) throws Exception {
    CommentDto comment = event.getData();
    TaskDto task = event.getSource();
    ProjectDto project = task.getStatus().getProject();
    WorkFlow workFlow = processesService.getWorkFlowByProjectId(project.getId());
    if (workFlow == null) {
      return;
    }
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(NotificationArguments.REQUEST_CREATOR, task.getCreatedBy());
    ctx.append(NotificationArguments.REQUEST_TITLE, task.getTitle());
    ctx.append(NotificationArguments.REQUEST_PROCESS, workFlow.getTitle());
    ctx.append(NotificationArguments.REQUEST_COMMENT_AUTHOR, comment.getAuthor());
    ctx.append(NotificationArguments.REQUEST_COMMENT, comment.getComment());
    ctx.append(NotificationArguments.PROCESS_URL, NotificationUtils.getProcessLink(project.getId()));
    ctx.append(NotificationArguments.REQUEST_COMMENT_URL, NotificationUtils.getRequestCommentsLink(task.getId()));
    ctx.getNotificationExecutor()
            .with(ctx.makeCommand(PluginKey.key(RequestCommentPlugin.ID))).execute(ctx);
  }
}
