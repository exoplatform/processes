package org.exoplatform.processes.listener;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.notification.plugin.CreateRequestPlugin;
import org.exoplatform.processes.notification.utils.NotificationArguments;
import org.exoplatform.processes.notification.utils.NotificationUtils;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.task.dto.ProjectDto;

public class CreateRequestNotificationListener extends Listener<Work, ProjectDto> {
  @Override
  public void onEvent(Event<Work, ProjectDto> event) {
    Work work = event.getSource();
    ProjectDto projectDto = event.getData();
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(NotificationArguments.REQUEST_CREATOR, work.getCreatedBy());
    ctx.append(NotificationArguments.REQUEST_PROCESS, projectDto.getName());
    ctx.append(NotificationArguments.REQUEST_TITLE, work.getTitle());
    ctx.append(NotificationArguments.REQUEST_DESCRIPTION, work.getDescription());
    ctx.append(NotificationArguments.PROCESS_URL, NotificationUtils.getProcessLink(projectDto.getId()));
    ctx.append(NotificationArguments.REQUEST_URL, NotificationUtils.getRequestLink(work.getId()));
    ctx.append(NotificationArguments.WORKFLOW_PROJECT_ID, String.valueOf(work.getProjectId()));
    ctx.getNotificationExecutor()
            .with(ctx.makeCommand(PluginKey.key(CreateRequestPlugin.ID))).execute(ctx);
  }
}
