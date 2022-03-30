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
