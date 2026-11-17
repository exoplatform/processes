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
package org.exoplatform.processes.notification.plugin;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.plugin.BaseNotificationPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.processes.notification.utils.NotificationArguments;

public class RequestCommentPlugin extends BaseNotificationPlugin {

  public static final  String ID  = "RequestCommentPlugin";

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

  /**
   * Builds the notification of a comment on a request, naming its process by its stored
   * title and, when known, by its id.
   *
   * @param notificationContext the notification context
   * @return the notification
   */
  @Override
  protected NotificationInfo makeNotification(NotificationContext notificationContext) {
    String requester = notificationContext.value(NotificationArguments.REQUEST_CREATOR);
    String processUrl = notificationContext.value(NotificationArguments.PROCESS_URL);
    String processTitle = notificationContext.value(NotificationArguments.REQUEST_PROCESS);
    String requestTitle = notificationContext.value(NotificationArguments.REQUEST_TITLE);
    String commentAuthor = notificationContext.value(NotificationArguments.REQUEST_COMMENT_AUTHOR);
    String comment = notificationContext.value(NotificationArguments.REQUEST_COMMENT);
    String requestCommentUrl = notificationContext.value(NotificationArguments.REQUEST_COMMENT_URL);
    String requestId = notificationContext.value(NotificationArguments.REQUEST_ID);
    NotificationInfo notificationInfo = NotificationInfo.instance()
                           .setFrom(commentAuthor)
                           .to(requester)
                           .with(NotificationArguments.REQUEST_CREATOR.getKey(), requester)
                           .with(NotificationArguments.REQUEST_PROCESS.getKey(), processTitle)
                           .with(NotificationArguments.REQUEST_TITLE.getKey(), requestTitle)
                           .with(NotificationArguments.REQUEST_COMMENT_AUTHOR.getKey(), commentAuthor)
                           .with(NotificationArguments.PROCESS_URL.getKey(), processUrl)
                           .with(NotificationArguments.REQUEST_COMMENT.getKey(), comment)
                           .with(NotificationArguments.REQUEST_COMMENT_URL.getKey(), requestCommentUrl)
                           .with(NotificationArguments.REQUEST_ID.getKey(), requestId)
                           .key(getKey())
                           .end();
    // The process id, from which each recipient reads the process's title in
    // their own language; a notification of an unknown process has none
    String workflowId = notificationContext.value(NotificationArguments.WORKFLOW_ID);
    if (workflowId != null) {
      notificationInfo.with(NotificationArguments.WORKFLOW_ID.getKey(), workflowId);
    }
    return notificationInfo;
  }
}
