package org.exoplatform.processes.listener;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.processes.notification.plugin.CancelRequestPlugin;
import org.exoplatform.processes.notification.plugin.CreateRequestPlugin;
import org.exoplatform.processes.notification.utils.NotificationArguments;
import org.exoplatform.processes.notification.utils.NotificationUtils;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.TaskDto;

public class CancelRequestNotificationListener extends Listener<TaskDto, ProjectDto> {
  @Override
  public void onEvent(Event<TaskDto, ProjectDto> event) {
    TaskDto taskDto = event.getSource();
    ProjectDto projectDto = event.getData();
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(NotificationArguments.REQUEST_CREATOR, taskDto.getCreatedBy());
    ctx.append(NotificationArguments.PROCESS_URL, NotificationUtils.getProcessLink(projectDto.getId()));
    ctx.getNotificationExecutor()
            .with(ctx.makeCommand(PluginKey.key(CancelRequestPlugin.ID))).execute(ctx);
  }
}
