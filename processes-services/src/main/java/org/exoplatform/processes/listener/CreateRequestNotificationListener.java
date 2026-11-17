package org.exoplatform.processes.listener;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.notification.plugin.CreateRequestPlugin;
import org.exoplatform.processes.notification.utils.NotificationArguments;
import org.exoplatform.processes.notification.utils.NotificationUtils;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.task.dto.ProjectDto;

public class CreateRequestNotificationListener extends Listener<Work, ProjectDto> {

  private final ProcessesService processesService;

  /**
   * Builds the listener.
   *
   * @param processesService reads the request's process
   */
  public CreateRequestNotificationListener(ProcessesService processesService) {
    this.processesService = processesService;
  }

  /**
   * Notifies the managers of a request's process that it was created.
   *
   * @param event the created request and its task project
   */
  @Override
  public void onEvent(Event<Work, ProjectDto> event) {
    NotificationContext ctx = buildContext(event);
    if (ctx != null) {
      ctx.getNotificationExecutor().with(ctx.makeCommand(PluginKey.key(CreateRequestPlugin.ID))).execute(ctx);
    }
  }

  /**
   * Builds the notification of a created request.
   *
   * @param event the created request and its task project
   * @return the notification context, or null when nothing is notified
   */
  NotificationContext buildContext(Event<Work, ProjectDto> event) {
    Work work = event.getSource();
    ProjectDto projectDto = event.getData();
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(NotificationArguments.REQUEST_CREATOR, work.getCreatedBy());
    NotificationUtils.appendProcess(ctx, processesService, work.getWorkFlowId(), projectDto.getName());
    ctx.append(NotificationArguments.REQUEST_TITLE, work.getTitle());
    ctx.append(NotificationArguments.REQUEST_DESCRIPTION, work.getDescription());
    ctx.append(NotificationArguments.PROCESS_URL, NotificationUtils.getProcessLink(projectDto.getId()));
    ctx.append(NotificationArguments.REQUEST_URL, NotificationUtils.getRequestLink(work.getId()));
    ctx.append(NotificationArguments.REQUEST_ID, String.valueOf(work.getId()));
    ctx.append(NotificationArguments.WORKFLOW_PROJECT_ID, String.valueOf(work.getProjectId()));
    return ctx;
  }
}
