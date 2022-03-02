package org.exoplatform.processes.notification.plugin;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.plugin.BaseNotificationPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.processes.notification.utils.NotificationArguments;
import org.exoplatform.processes.notification.utils.NotificationUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class CancelRequestPlugin extends BaseNotificationPlugin {
  private static final Log   LOG = ExoLogger.getLogger(CancelRequestPlugin.class);

  public final static String ID  = "CancelRequestPlugin";

  public CancelRequestPlugin(InitParams initParams) {
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
    String process = notificationContext.value(NotificationArguments.PROCESS_URL);
    return NotificationInfo.instance()
                           .setFrom(requester)
                           .to(NotificationUtils.getProcessAdmins(requester))
                           .with(NotificationArguments.REQUEST_CREATOR.getKey(), requester)
                           .with(NotificationArguments.PROCESS_URL.getKey(), process)
                           .key(getKey())
                           .end();
  }
}
