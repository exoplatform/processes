package org.exoplatform.processes.notification.utils;

import org.exoplatform.commons.api.notification.model.ArgumentLiteral;

public class NotificationArguments {

  public static final ArgumentLiteral<String> REQUEST_CREATOR        = new ArgumentLiteral<>(String.class, "REQUEST_CREATOR");

  public static final ArgumentLiteral<String> REQUEST_PROCESS        = new ArgumentLiteral<>(String.class, "REQUEST_PROCESS");

  public static final ArgumentLiteral<String> PROCESS_URL            = new ArgumentLiteral<>(String.class, "PROCESS_URL");

  public static final ArgumentLiteral<String> REQUEST_TITLE          = new ArgumentLiteral<>(String.class, "REQUEST_TITLE");

  public static final ArgumentLiteral<String> REQUEST_DESCRIPTION    = new ArgumentLiteral<>(String.class, "REQUEST_DESCRIPTION");

  public static final ArgumentLiteral<String> REQUEST_URL            = new ArgumentLiteral<>(String.class, "REQUEST_URL");

  public static final ArgumentLiteral<String> REQUEST_COMMENT_AUTHOR = new ArgumentLiteral<>(String.class, "REQUEST_COMMENT_AUTHOR");

  public static final ArgumentLiteral<String> REQUEST_COMMENT        = new ArgumentLiteral<>(String.class, "REQUEST_COMMENT");

  public static final ArgumentLiteral<String> REQUEST_COMMENT_URL    = new ArgumentLiteral<>(String.class, "REQUEST_COMMENT_URL");

  public static final ArgumentLiteral<String> WORKFLOW_PROJECT_ID    = new ArgumentLiteral<>(String.class, "WORKFLOW_PROJECT_ID");

}
