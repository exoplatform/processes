package org.exoplatform.processes.notification.plugin;

import junit.framework.TestCase;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.processes.notification.utils.NotificationArguments;
import org.exoplatform.processes.notification.utils.NotificationUtils;
import org.exoplatform.services.idgenerator.IDGeneratorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.management.*"})
@PrepareForTest({CommonsUtils.class, NotificationUtils.class, PluginKey.class, CommonsUtils.class, ExoContainerContext.class})
public class RequestCommentPluginTest extends TestCase {

    @Mock
    private InitParams initParams;

    private RequestCommentPlugin requestCommentPlugin;

    @Before
    public void setUp() {
        this.requestCommentPlugin = new RequestCommentPlugin(initParams);
        PowerMockito.mockStatic(CommonsUtils.class);
        PowerMockito.mockStatic(NotificationUtils.class);
        PowerMockito.mockStatic(ExoContainerContext.class);
        when(ExoContainerContext.getService(IDGeneratorService.class)).thenReturn(null);
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