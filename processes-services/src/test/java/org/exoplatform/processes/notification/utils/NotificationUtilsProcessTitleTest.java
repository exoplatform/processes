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
package org.exoplatform.processes.notification.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.service.ProcessTranslationService;
import org.exoplatform.processes.service.ProcessesService;

@ExtendWith(MockitoExtension.class)
class NotificationUtilsProcessTitleTest {

  @Mock
  private ProcessesService          processesService;

  @Mock
  private ProcessTranslationService processTranslationService;

  @Mock
  private NotificationContext       ctx;

  /**
   * A notification names its request's process by its id and by its stored
   * default-language title; a process that can't be read is named by the
   * given title only, with no id.
   * Mutants: the id not appended, the given title stored for a readable
   * process, or an id appended for an unreadable one.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  void aNotificationNamesItsProcessByIdAndStoredTitle() throws Exception {
    when(processesService.getWorkFlow(9L, null)).thenReturn(workFlow());

    NotificationUtils.appendProcess(ctx, processesService, 9L, "Shared project");

    verify(ctx).append(NotificationArguments.REQUEST_PROCESS, "Leave");
    verify(ctx).append(NotificationArguments.WORKFLOW_ID, "9");
    verify(ctx, never()).append(NotificationArguments.REQUEST_PROCESS, "Shared project");
  }

  /**
   * @throws Exception never, the services are mocks
   * @see #aNotificationNamesItsProcessByIdAndStoredTitle()
   */
  @Test
  void anUnreadableProcessIsNamedByTheGivenTitle() throws Exception {
    when(processesService.getWorkFlow(404L, null)).thenReturn(null);

    NotificationUtils.appendProcess(ctx, processesService, 404L, "Shared project");
    NotificationUtils.appendProcess(ctx, processesService, null, "Old project");

    verify(ctx).append(NotificationArguments.REQUEST_PROCESS, "Shared project");
    verify(ctx).append(NotificationArguments.REQUEST_PROCESS, "Old project");
    verify(ctx, never()).append(eq(NotificationArguments.WORKFLOW_ID), any());
  }

  /**
   * Each recipient reads the process's title in their own language, the
   * channel's language tag read with an underscore or a dash; a notification
   * with no process id, or whose process is gone, keeps its stored title.
   * Mutants: the stored title always used, the language ignored, or a gone
   * process answered with no title.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  void eachRecipientReadsTheProcessTitleInTheirLanguage() throws Exception {
    NotificationInfo notification = NotificationInfo.instance()
                                                    .with(NotificationArguments.REQUEST_PROCESS.getKey(), "Leave")
                                                    .with(NotificationArguments.WORKFLOW_ID.getKey(), "9");
    NotificationInfo oldNotification = NotificationInfo.instance()
                                                       .with(NotificationArguments.REQUEST_PROCESS.getKey(), "Old leave");
    NotificationInfo goneProcess = NotificationInfo.instance()
                                                   .with(NotificationArguments.REQUEST_PROCESS.getKey(), "Gone")
                                                   .with(NotificationArguments.WORKFLOW_ID.getKey(), "10");
    WorkFlow workFlow = workFlow();
    when(processesService.getWorkFlow(9L, null)).thenReturn(workFlow);
    when(processTranslationService.getTitle(workFlow, Locale.forLanguageTag("pt-BR"))).thenReturn("Férias");
    when(processTranslationService.getTitle(workFlow, Locale.FRENCH)).thenReturn("Congés");

    try (MockedStatic<CommonsUtils> commonsUtils = mockStatic(CommonsUtils.class)) {
      commonsUtils.when(() -> CommonsUtils.getService(ProcessesService.class)).thenReturn(processesService);
      commonsUtils.when(() -> CommonsUtils.getService(ProcessTranslationService.class)).thenReturn(processTranslationService);

      assertEquals("Férias", NotificationUtils.getProcessTitle(notification, "pt_BR"));
      assertEquals("Congés", NotificationUtils.getProcessTitle(notification, Locale.FRENCH));
      assertEquals("Leave", NotificationUtils.getProcessTitle(notification, (String) null));
      assertEquals("Old leave", NotificationUtils.getProcessTitle(oldNotification, "fr"));
      assertEquals("Gone", NotificationUtils.getProcessTitle(goneProcess, "fr"));
    }
  }

  /**
   * @return a process with its stored default-language title
   */
  private static WorkFlow workFlow() {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(9L);
    workFlow.setTitle("Leave");
    return workFlow;
  }
}
