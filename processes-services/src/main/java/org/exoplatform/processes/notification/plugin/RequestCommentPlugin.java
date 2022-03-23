package org.exoplatform.processes.notification.plugin;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.plugin.BaseNotificationPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.processes.notification.utils.NotificationArguments;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class RequestCommentPlugin extends BaseNotificationPlugin {

  private static final Log   LOG = ExoLogger.getLogger(CreateRequestPlugin.class);

  public final static String ID  = "RequestCommentPlugin";

  public RequestCommentPlugin(InitParams initParams) {
    super(initParams);
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public boolean isValid(NotificationContext notificationContext) {
    return !notificationContext.value(NotificationArguments.REQUEST_COMMENT_AUTHOR)
                               .equals(notificationContext.value(NotificationArguments.REQUEST_CREATOR));
  }

  @Override
  protected NotificationInfo makeNotification(NotificationContext notificationContext) {
    String requester = notificationContext.value(NotificationArguments.REQUEST_CREATOR);
    String processUrl = notificationContext.value(NotificationArguments.PROCESS_URL);
    String processTitle = notificationContext.value(NotificationArguments.REQUEST_PROCESS);
    String requestTitle = notificationContext.value(NotificationArguments.REQUEST_TITLE);
    String commentAuthor = notificationContext.value(NotificationArguments.REQUEST_COMMENT_AUTHOR);
    String comment = notificationContext.value(NotificationArguments.REQUEST_COMMENT);
    String requestCommentUrl = notificationContext.value(NotificationArguments.REQUEST_COMMENT_URL);
    return NotificationInfo.instance()
                           .setFrom(commentAuthor)
                           .to(requester)
                           .with(NotificationArguments.REQUEST_CREATOR.getKey(), requester)
                           .with(NotificationArguments.REQUEST_PROCESS.getKey(), processTitle)
                           .with(NotificationArguments.REQUEST_TITLE.getKey(), requestTitle)
                           .with(NotificationArguments.REQUEST_COMMENT_AUTHOR.getKey(), commentAuthor)
                           .with(NotificationArguments.PROCESS_URL.getKey(), processUrl)
                           .with(NotificationArguments.REQUEST_COMMENT.getKey(), comment)
                           .with(NotificationArguments.REQUEST_COMMENT_URL.getKey(), requestCommentUrl)
                           .key(getKey())
                           .end();
  }
}
