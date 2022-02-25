package org.exoplatform.processes.notification.plugin;

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

import static org.junit.Assert.*;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "javax.management.*" })
@PrepareForTest({ CommonsUtils.class, NotificationUtils.class, PluginKey.class, CommonsUtils.class, ExoContainerContext.class })
public class CreateRequestPluginTest {

  @Mock
  private InitParams          initParams;

  private CreateRequestPlugin createRequestPlugin;

  @Before
  public void setUp() throws Exception {
    this.createRequestPlugin = new CreateRequestPlugin(initParams);
    PowerMockito.mockStatic(CommonsUtils.class);
    PowerMockito.mockStatic(NotificationUtils.class);
    PowerMockito.mockStatic(ExoContainerContext.class);
    when(ExoContainerContext.getService(IDGeneratorService.class)).thenReturn(null);
  }

  @Test
  public void makeNotification() {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(NotificationArguments.REQUEST_CREATOR, "root");
    ctx.append(NotificationArguments.REQUEST_PROCESS, "test project");
    ctx.append(NotificationArguments.REQUEST_TITLE, "my request");
    ctx.append(NotificationArguments.REQUEST_DESCRIPTION, "test description");
    ctx.append(NotificationArguments.PROCESS_URL, "http://exoplatfrom.com/dw/tasks/projectDetail/1");
    ctx.append(NotificationArguments.REQUEST_URL, "http://exoplatfrom.com/dw/tasks/taskDetail/1");
    List<String> receivers = new ArrayList<>();
    receivers.add("user1");
    receivers.add("user1");
    when(NotificationUtils.getProcessAdmins("root")).thenReturn(receivers);
    NotificationInfo notificationInfo = createRequestPlugin.makeNotification(ctx);
    assertEquals("root", notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_CREATOR.getKey()));
    assertEquals("http://exoplatfrom.com/dw/tasks/projectDetail/1",
                 notificationInfo.getValueOwnerParameter(NotificationArguments.PROCESS_URL.getKey()));
    assertEquals("http://exoplatfrom.com/dw/tasks/taskDetail/1",
                 notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_URL.getKey()));
    assertEquals("test description", notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_DESCRIPTION.getKey()));
    assertEquals("my request", notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_TITLE.getKey()));
    assertEquals("root", notificationInfo.getFrom());
    assertEquals(receivers, notificationInfo.getSendToUserIds());
  }
}
