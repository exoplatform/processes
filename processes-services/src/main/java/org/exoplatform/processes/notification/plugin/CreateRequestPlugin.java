package org.exoplatform.processes.notification.plugin;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.plugin.BaseNotificationPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.processes.notification.utils.NotificationArguments;
import org.exoplatform.processes.notification.utils.NotificationUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.util.List;

public class CreateRequestPlugin extends BaseNotificationPlugin {

  private static final Log   LOG = ExoLogger.getLogger(CreateRequestPlugin.class);

  public final static String ID  = "CreateRequestPlugin";

  public CreateRequestPlugin(InitParams initParams) {
    super(initParams);
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public boolean isValid(NotificationContext notificationContext) {
    return true;
  }

  @Override
  protected NotificationInfo makeNotification(NotificationContext notificationContext) {
    String requester = notificationContext.value(NotificationArguments.REQUEST_CREATOR);
    String process = notificationContext.value(NotificationArguments.REQUEST_PROCESS);
    String processUrl = notificationContext.value(NotificationArguments.PROCESS_URL);
    String requestTitle = notificationContext.value(NotificationArguments.REQUEST_TITLE);
    String requestDescription = notificationContext.value(NotificationArguments.REQUEST_DESCRIPTION);
    String requestUrl = notificationContext.value(NotificationArguments.REQUEST_URL);
    String workflowProjectId = notificationContext.value(NotificationArguments.WORKFLOW_PROJECT_ID);
    List<String> receivers = NotificationUtils.getReceivers(Long.parseLong(workflowProjectId), requester, false);
    return NotificationInfo.instance()
                           .setFrom(requester)
                           .to(receivers)
                           .with(NotificationArguments.REQUEST_CREATOR.getKey(), requester)
                           .with(NotificationArguments.REQUEST_PROCESS.getKey(), process)
                           .with(NotificationArguments.REQUEST_TITLE.getKey(), requestTitle)
                           .with(NotificationArguments.REQUEST_DESCRIPTION.getKey(), requestDescription)
                           .with(NotificationArguments.PROCESS_URL.getKey(), processUrl)
                           .with(NotificationArguments.REQUEST_URL.getKey(), requestUrl)
                           .key(getKey())
                           .end();
  }
}
