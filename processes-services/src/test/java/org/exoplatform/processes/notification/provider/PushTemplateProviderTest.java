/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.processes.notification.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.plugin.NotificationPluginUtils;
import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.notification.template.TemplateUtils;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.processes.notification.plugin.CreateRequestPlugin;
import org.exoplatform.processes.notification.utils.NotificationArguments;
import org.exoplatform.processes.notification.utils.NotificationUtils;
import org.exoplatform.social.core.identity.model.Profile;

class PushTemplateProviderTest {

  /**
   * A request's push notification is still sent when its notification stores
   * no process title: the title is empty, never null (EXO-94567).
   * Mutant: a missing stored title returned as null by the shared title read.
   */
  @Test
  void aPushIsSentWithAnEmptyTitleWhenNoneIsStored() {
    InitParams initParams = mock(InitParams.class);
    ValueParam channelParam = new ValueParam();
    channelParam.setName("channel-id");
    channelParam.setValue("PUSH_CHANNEL");
    when(initParams.getValueParam("channel-id")).thenReturn(channelParam);
    Profile profile = mock(Profile.class);
    when(profile.getFullName()).thenReturn("Requester Name");
    NotificationContext notificationContext = mock(NotificationContext.class);
    NotificationInfo notification = new NotificationInfo();
    notification.key(CreateRequestPlugin.ID);
    notification.setTo("receiver");
    notification.with(NotificationArguments.REQUEST_CREATOR.getKey(), "requester");
    when(notificationContext.getNotificationInfo()).thenReturn(notification);
    AtomicReference<TemplateContext> rendered = new AtomicReference<>();

    try (MockedStatic<TemplateUtils> templateUtils = mockStatic(TemplateUtils.class);
         MockedStatic<NotificationPluginUtils> pluginUtils = mockStatic(NotificationPluginUtils.class);
         MockedStatic<NotificationUtils> notificationUtils = mockStatic(NotificationUtils.class, CALLS_REAL_METHODS)) {
      templateUtils.when(() -> TemplateUtils.processGroovy(any())).thenAnswer(invocation -> {
        rendered.set(invocation.getArgument(0));
        return "body";
      });
      pluginUtils.when(() -> NotificationPluginUtils.getLanguage(anyString())).thenReturn("en");
      notificationUtils.when(() -> NotificationUtils.getUserProfile("requester")).thenReturn(profile);

      new PushTemplateProvider(initParams).getTemplateBuilder()
                                          .get(PluginKey.key(CreateRequestPlugin.ID))
                                          .buildMessage(notificationContext);
    }

    assertNotNull(rendered.get(), "the push body was not rendered");
    assertEquals("", rendered.get().get("PROCESS_TITLE"));
  }
}
