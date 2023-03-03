package org.exoplatform.processes.notification.plugin;

import static org.junit.Assert.assertEquals;
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
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.idgenerator.IDGeneratorService;


@RunWith(MockitoJUnitRunner.class)
public class CancelRequestPluginTest {

  private static final MockedStatic<CommonsUtils>        COMMONS_UTILS       = mockStatic(CommonsUtils.class);

  private static final MockedStatic<ExoContainerContext> EXO_CONTAINER_CONTEXT = mockStatic(ExoContainerContext.class);

  private static final MockedStatic<NotificationUtils>   NOTIFICATION_UTILS   = mockStatic(NotificationUtils.class);

  @Mock
  private InitParams          initParams;

  @Mock
  private ProcessesService processesService;

  private CancelRequestPlugin cancelRequestPlugin;

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    COMMONS_UTILS.close();
    EXO_CONTAINER_CONTEXT.close();
    NOTIFICATION_UTILS.close();
  }

  @Before
  public void setUp() throws Exception {
    this.cancelRequestPlugin = new CancelRequestPlugin(initParams);
    EXO_CONTAINER_CONTEXT.when(() -> ExoContainerContext.getService(IDGeneratorService.class)).thenReturn(null);
    COMMONS_UTILS.when(() -> CommonsUtils.getService(ProcessesService.class)).thenReturn(processesService);
  }

  @Test
  public void makeNotification() {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(NotificationArguments.REQUEST_CREATOR, "root");
    ctx.append(NotificationArguments.WORKFLOW_PROJECT_ID, "1");
    List<String> receivers = new ArrayList<>();
    receivers.add("user1");
    receivers.add("user2");
    ctx.append(NotificationArguments.PROCESS_URL, "http://exoplatfrom.com/dw/tasks/projectDetail/1");
    NOTIFICATION_UTILS.when(() -> NotificationUtils.getReceivers(1l , "root", true)).thenReturn(receivers);
    NotificationInfo notificationInfo = cancelRequestPlugin.makeNotification(ctx);
    assertEquals("root", notificationInfo.getValueOwnerParameter(NotificationArguments.REQUEST_CREATOR.getKey()));
    assertEquals("http://exoplatfrom.com/dw/tasks/projectDetail/1",
                 notificationInfo.getValueOwnerParameter(NotificationArguments.PROCESS_URL.getKey()));
    assertEquals("root", notificationInfo.getFrom());
    assertEquals(receivers, notificationInfo.getSendToUserIds());
  }
}
