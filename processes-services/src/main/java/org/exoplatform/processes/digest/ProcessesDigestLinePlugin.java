/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exoplatform.processes.digest;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.notification.utils.NotificationArguments;
import org.exoplatform.processes.notification.utils.NotificationUtils;
import org.exoplatform.processes.service.ProcessTranslationService;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.commons.digest.model.DigestItem;
import io.meeds.commons.digest.model.DigestLine;
import io.meeds.commons.digest.plugin.DigestLineContext;
import io.meeds.commons.digest.plugin.DigestLinePlugin;

/**
 * The digest email lines of the request notifications: a new request to
 * handle, a comment on my request. The request is read fresh from the stored
 * request id; a deleted request gives no line.
 */
public class ProcessesDigestLinePlugin extends DigestLinePlugin {

  public static final String  CREATE_REQUEST_PLUGIN  = "CreateRequestPlugin";

  public static final String  REQUEST_COMMENT_PLUGIN = "RequestCommentPlugin";

  private static final String LINE_KEY_PREFIX        = "digest.line.";

  private ProcessesService    processesService;

  private IdentityManager     identityManager;

  private ProcessTranslationService processTranslationService;

  public ProcessesDigestLinePlugin(InitParams params) {
    super(params);
  }

  /**
   * Builds the plugin on given services.
   *
   * @param params the plugin parameters
   * @param processesService the processes service
   * @param identityManager the identity manager
   * @param processTranslationService the per-language texts of a process
   */
  ProcessesDigestLinePlugin(InitParams params,
                            ProcessesService processesService,
                            IdentityManager identityManager,
                            ProcessTranslationService processTranslationService) {
    super(params);
    this.processesService = processesService;
    this.identityManager = identityManager;
    this.processTranslationService = processTranslationService;
  }

  /**
   * Builds the digest line of a request notification, the process named in
   * the recipient's language.
   *
   * @param item the notification digested
   * @param context the recipient, their language and time zone
   * @return the line, or null when the request is gone
   */
  @Override
  public DigestLine buildLine(DigestItem item, DigestLineContext context) {
    Work work = findWork(item.getParam(NotificationArguments.REQUEST_ID.getKey()));
    if (work == null) {
      return null;
    }
    String key = LINE_KEY_PREFIX + item.getPluginId();
    return switch (item.getPluginId()) {
      case CREATE_REQUEST_PLUGIN -> DigestLine.of(key, work.getTitle(), processName(work, context.getLocale())).withUrl(requestUrl(work));
      case REQUEST_COMMENT_PLUGIN -> DigestLine.of(key,
                                                   fullName(item.getParam(NotificationArguments.REQUEST_COMMENT_AUTHOR.getKey())),
                                                   work.getTitle())
                                               .withUrl(commentsUrl(work));
      default -> null;
    };
  }

  /**
   * The id-only read: the per-user read of the service only returns the
   * requests the user created, and a new request is announced to the managers
   */
  private Work findWork(String requestId) {
    if (StringUtils.isBlank(requestId)) {
      return null;
    }
    try {
      return getProcessesService().getWorkById(Long.parseLong(requestId));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Names a request's process in the digest recipient's language.
   *
   * @param work the request
   * @param locale the recipient's language
   * @return the process title, or an empty name when the request has none
   */
  private String processName(Work work, Locale locale) {
    if (work.getWorkFlow() == null) {
      return "";
    }
    String title = getProcessTranslationService().getTitle(work.getWorkFlow(), locale);
    return StringUtils.isBlank(title) ? "" : title;
  }

  protected String requestUrl(Work work) {
    return NotificationUtils.getRequestLink(work.getId());
  }

  protected String commentsUrl(Work work) {
    return NotificationUtils.getRequestCommentsLink(work.getId());
  }

  private String fullName(String username) {
    if (StringUtils.isBlank(username)) {
      return "";
    }
    Identity identity = getIdentityManager().getOrCreateUserIdentity(username);
    String fullName = identity == null || identity.getProfile() == null ? null : identity.getProfile().getFullName();
    return StringUtils.isBlank(fullName) ? username : fullName;
  }

  private ProcessesService getProcessesService() {
    if (processesService == null) {
      processesService = ExoContainerContext.getService(ProcessesService.class);
    }
    return processesService;
  }

  /**
   * Returns the per-language texts of a process, looked up on first use like
   * the other services of this Kernel plugin.
   *
   * @return the process translation service
   */
  private ProcessTranslationService getProcessTranslationService() {
    if (processTranslationService == null) {
      processTranslationService = ExoContainerContext.getService(ProcessTranslationService.class);
    }
    return processTranslationService;
  }

  private IdentityManager getIdentityManager() {
    if (identityManager == null) {
      identityManager = ExoContainerContext.getService(IdentityManager.class);
    }
    return identityManager;
  }

}
