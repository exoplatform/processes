package org.exoplatform.processes.listener;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.processes.notification.plugin.CancelRequestPlugin;
import org.exoplatform.processes.notification.utils.NotificationArguments;
import org.exoplatform.processes.notification.utils.NotificationUtils;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.TaskDto;

public class CancelRequestNotificationListener extends Listener<TaskDto, ProjectDto> {

  private final ProcessesService processesService;

  /**
   * Builds the listener.
   *
   * @param processesService reads the request's process
   */
  public CancelRequestNotificationListener(ProcessesService processesService) {
    this.processesService = processesService;
  }

  /**
   * Notifies the managers of a request's process that it was canceled.
   *
   * @param event the canceled request's task and its project
   */
  @Override
  public void onEvent(Event<TaskDto, ProjectDto> event) {
    NotificationContext ctx = buildContext(event);
    if (ctx != null) {
      ctx.getNotificationExecutor().with(ctx.makeCommand(PluginKey.key(CancelRequestPlugin.ID))).execute(ctx);
    }
  }

  /**
   * Builds the notification of a canceled request.
   *
   * @param event the canceled request's task and its project
   * @return the notification context, or null when nothing is notified
   */
  NotificationContext buildContext(Event<TaskDto, ProjectDto> event) {
    TaskDto taskDto = event.getSource();
    ProjectDto projectDto = event.getData();
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(NotificationArguments.REQUEST_CREATOR, taskDto.getCreatedBy());
    NotificationUtils.appendProcess(ctx,
                                    processesService,
                                    processesService.getRequestWorkFlowId(taskDto.getId()),
                                    projectDto.getName());
    ctx.append(NotificationArguments.REQUEST_TITLE, taskDto.getTitle());
    ctx.append(NotificationArguments.PROCESS_URL, NotificationUtils.getProcessLink(projectDto.getId()));
    ctx.append(NotificationArguments.REQUEST_URL, NotificationUtils.getRequestLink(taskDto.getId()));
    ctx.append(NotificationArguments.WORKFLOW_PROJECT_ID, String.valueOf(projectDto.getId()));
    return ctx;
  }
}
