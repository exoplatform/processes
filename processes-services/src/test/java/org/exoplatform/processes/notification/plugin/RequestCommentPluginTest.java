package org.exoplatform.processes.notification.plugin;

import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.processes.notification.utils.NotificationArguments;
import org.exoplatform.processes.notification.utils.NotificationUtils;
import org.exoplatform.services.idgenerator.IDGeneratorService;

import junit.framework.TestCase;

@RunWith(MockitoJUnitRunner.class)
public class RequestCommentPluginTest extends TestCase {

  private static final MockedStatic<CommonsUtils>        COMMONS_UTILS         = mockStatic(CommonsUtils.class);

  private static final MockedStatic<ExoContainerContext> EXO_CONTAINER_CONTEXT = mockStatic(ExoContainerContext.class);

  private static final MockedStatic<NotificationUtils>   NOTIFICATION_UTILS    = mockStatic(NotificationUtils.class);

  @Mock
  private InitParams                                     initParams;

  private RequestCommentPlugin                           requestCommentPlugin;

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    COMMONS_UTILS.close();
    EXO_CONTAINER_CONTEXT.close();
    NOTIFICATION_UTILS.close();
  }

  @Before
  public void setUp() {
    this.requestCommentPlugin = new RequestCommentPlugin(initParams);
    EXO_CONTAINER_CONTEXT.when(() -> ExoContainerContext.getService(IDGeneratorService.class)).thenReturn(null);
  }

  @Test
  public void isValid() {
    NotificationContext ctx1 = NotificationContextImpl.cloneInstance();
    ctx1.append(NotificationArguments.REQUEST_CREATOR, "root");
    ctx1.append(NotificationArguments.REQUEST_COMMENT_AUTHOR, "user");
    boolean valid = requestCommentPlugin.isValid(ctx1);
    assertTrue(valid);
    NotificationContext ctx2 = NotificationContextImpl.cloneInstance();
    ctx2.append(NotificationArguments.REQUEST_CREATOR, "root");
    ctx2.append(NotificationArguments.REQUEST_COMMENT_AUTHOR, "root");
    valid = requestCommentPlugin.isValid(ctx2);
    assertFalse(valid);
  }

  @Test
  public void testMakeNotification() {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(NotificationArguments.REQUEST_CREATOR, "root");
    ctx.append(NotificationArguments.REQUEST_TITLE, "test request");
    ctx.append(NotificationArguments.REQUEST_PROCESS, "test process");
    ctx.append(NotificationArguments.REQUEST_COMMENT_AUTHOR, "user");
    ctx.append(NotificationArguments.REQUEST_COMMENT, "test");
    ctx.append(NotificationArguments.PROCESS_URL, "http://exoplatfrom.com/dw/tasks/projectDetail/1");
    ctx.append(NotificationArguments.REQUEST_COMMENT_URL,
               "http://exoplatfrom.com/dw/processes/myRequests/requestDetails/1/comments");

    List<String> receivers = new ArrayList<>();
    receivers.add("root");
    NotificationInfo notificationInfo = requestCommentPlugin.makeNotification(ctx);
    assertEquals("root", notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_CREATOR.getKey()));
    assertEquals("http://exoplatfrom.com/dw/tasks/projectDetail/1",
                 notificationInfo.getValueOwnerParameter(NotificationArguments.PROCESS_URL.getKey()));
    assertEquals("http://exoplatfrom.com/dw/processes/myRequests/requestDetails/1/comments",
                 notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_COMMENT_URL.getKey()));
    assertEquals("test", notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_COMMENT.getKey()));
    assertEquals("test request", notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_TITLE.getKey()));
    assertEquals("user", notificationInfo.getFrom());
    assertEquals(receivers, notificationInfo.getSendToUserIds());
  }
}
