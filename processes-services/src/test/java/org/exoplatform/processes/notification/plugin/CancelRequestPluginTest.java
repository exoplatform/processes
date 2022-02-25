package org.exoplatform.processes.notification.plugin;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
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

import static org.junit.Assert.*;
import static org.powermock.api.mockito.PowerMockito.when;


@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "javax.management.*" })
@PrepareForTest({ CommonsUtils.class, NotificationUtils.class, CommonsUtils.class, ExoContainerContext.class })
public class CancelRequestPluginTest {

  @Mock
  private InitParams          initParams;

  private CancelRequestPlugin cancelRequestPlugin;

  @Before
  public void setUp() throws Exception {
    this.cancelRequestPlugin = new CancelRequestPlugin(initParams);
    PowerMockito.mockStatic(CommonsUtils.class);
    PowerMockito.mockStatic(NotificationUtils.class);
    PowerMockito.mockStatic(ExoContainerContext.class);
    when(ExoContainerContext.getService(IDGeneratorService.class)).thenReturn(null);
  }

  @Test
  public void makeNotification() {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(NotificationArguments.REQUEST_CREATOR, "root");
    List<String> receivers = new ArrayList<>();
    receivers.add("user1");
    receivers.add("user1");
    ctx.append(NotificationArguments.PROCESS_URL, "http://exoplatfrom.com/dw/tasks/projectDetail/1");
    when(NotificationUtils.getProcessAdmins("root")).thenReturn(receivers);
    NotificationInfo notificationInfo = cancelRequestPlugin.makeNotification(ctx);
    assertEquals("root", notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_CREATOR.getKey()));
    assertEquals("http://exoplatfrom.com/dw/tasks/projectDetail/1",
                 notificationInfo.getValueOwnerParameter(NotificationArguments.PROCESS_URL.getKey()));
    assertEquals("root", notificationInfo.getFrom());
    assertEquals(receivers, notificationInfo.getSendToUserIds());
  }
}
