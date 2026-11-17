/*
 * Copyright (C) 2026 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.processes.notification.pwa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.notification.utils.NotificationArguments;
import org.exoplatform.processes.service.ProcessTranslationService;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

class CreateRequestPwaPluginTest {

  /**
   * The mobile push of a new request names its process in the recipient's
   * language, read from the process the notification names by its id.
   * Mutants: the stored process title used, or the recipient's language
   * ignored.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  void thePushNamesTheProcessInTheRecipientsLanguage() throws Exception {
    ResourceBundleService resourceBundleService = mock(ResourceBundleService.class);
    IdentityManager identityManager = mock(IdentityManager.class);
    ProcessesService processesService = mock(ProcessesService.class);
    ProcessTranslationService processTranslationService = mock(ProcessTranslationService.class);
    LocaleConfig localeConfig = mock(LocaleConfig.class);
    when(localeConfig.getLocale()).thenReturn(Locale.FRENCH);
    when(resourceBundleService.getSharedString("pwa.notification.CreateRequestPwaPlugin.title", Locale.FRENCH))
        .thenReturn("{0} a demandé : {1}");
    Identity requester = new Identity(OrganizationIdentityProvider.NAME, "john");
    Profile profile = new Profile(requester);
    profile.setProperty(Profile.FULL_NAME, "John Smith");
    requester.setProfile(profile);
    when(identityManager.getOrCreateUserIdentity("john")).thenReturn(requester);
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(7L);
    workFlow.setTitle("Leave");
    when(processesService.getWorkFlow(7L, null)).thenReturn(workFlow);
    when(processTranslationService.getTitle(workFlow, Locale.FRENCH)).thenReturn("Congés");
    NotificationInfo notification = NotificationInfo.instance()
                                                    .with(NotificationArguments.REQUEST_CREATOR.getKey(), "john")
                                                    .with(NotificationArguments.REQUEST_PROCESS.getKey(), "Leave")
                                                    .with(NotificationArguments.WORKFLOW_ID.getKey(), "7")
                                                    .with(NotificationArguments.REQUEST_URL.getKey(), "/portal/request/3");

    try (MockedStatic<CommonsUtils> commonsUtils = mockStatic(CommonsUtils.class)) {
      commonsUtils.when(CommonsUtils::getCurrentDomain).thenReturn("");
      commonsUtils.when(() -> CommonsUtils.getService(ProcessesService.class)).thenReturn(processesService);
      commonsUtils.when(() -> CommonsUtils.getService(ProcessTranslationService.class)).thenReturn(processTranslationService);

      String title = new CreateRequestPwaPlugin(resourceBundleService, identityManager).process(notification, localeConfig).getTitle();

      assertEquals("John Smith a demandé : Congés", title);
    }
  }
}
