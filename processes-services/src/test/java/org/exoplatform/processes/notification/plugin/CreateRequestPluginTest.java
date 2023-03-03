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
public class CreateRequestPluginTest {

  private static final MockedStatic<CommonsUtils>        COMMONS_UTILS       = mockStatic(CommonsUtils.class);

  private static final MockedStatic<ExoContainerContext> EXO_CONTAINER_CONTEXT = mockStatic(ExoContainerContext.class);

  private static final MockedStatic<NotificationUtils>   NOTIFICATION_UTILS   = mockStatic(NotificationUtils.class);

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    COMMONS_UTILS.close();
    EXO_CONTAINER_CONTEXT.close();
    NOTIFICATION_UTILS.close();
  }

  @Mock
  private InitParams          initParams;

  @Mock
  private ProcessesService processesService;

  private CreateRequestPlugin createRequestPlugin;

  @Before
  public void setUp() throws Exception {
    this.createRequestPlugin = new CreateRequestPlugin(initParams);
    EXO_CONTAINER_CONTEXT.when(() -> ExoContainerContext.getService(IDGeneratorService.class)).thenReturn(null);
    COMMONS_UTILS.when(() -> CommonsUtils.getService(ProcessesService.class)).thenReturn(processesService);
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
    ctx.append(NotificationArguments.WORKFLOW_PROJECT_ID, "1");
    List<String> receivers = new ArrayList<>();
    receivers.add("user1");
    receivers.add("user2");
    NOTIFICATION_UTILS.when(() -> NotificationUtils.getReceivers(1l , "root", true)).thenReturn(receivers);
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
