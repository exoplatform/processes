/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.mockito.MockedStatic;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.plugin.NotificationPluginUtils;
import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.notification.mail.MailPalette;
import org.exoplatform.commons.notification.template.TemplateUtils;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.portal.branding.BrandingService;
import org.exoplatform.portal.branding.MailDocumentShell;
import org.exoplatform.processes.notification.plugin.CreateRequestPlugin;
import org.exoplatform.processes.notification.plugin.RequestCommentPlugin;
import org.exoplatform.processes.notification.utils.NotificationArguments;
import org.exoplatform.processes.notification.utils.NotificationUtils;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.notification.LinkProviderUtils;

import groovy.text.GStringTemplateEngine;

/**
 * The mail of a request notification is written in its recipient's palette:
 * the builder binds it and keeps the user's black text readable, the template
 * renders on it.
 */
public class MailTemplatePaletteTest {

  private static final String TEMPLATES = "../processes-webapp/src/main/webapp/WEB-INF/notification/templates/mail/";

  private static final String BLACK_TEXT = "<span style=\"color:#000000\">hello</span>";

  @Test
  public void theRecipientsPaletteIsBoundAndTheDescriptionKeepsReadable() {
    TemplateContext templateContext = build(CreateRequestPlugin.ID);
    assertEquals("dark", MailPalette.of(templateContext).get(MailPalette.MODE));
    assertEquals("#1E2328", MailPalette.of(templateContext).get("surface"));
    String description = (String) templateContext.get("REQUEST_DESCRIPTION");
    assertFalse(description, description.contains("#000000"));
  }

  @Test
  public void theCommentKeepsReadableInTheRecipientsPalette() {
    TemplateContext templateContext = build(RequestCommentPlugin.ID);
    assertEquals("dark", MailPalette.of(templateContext).get(MailPalette.MODE));
    String comment = (String) templateContext.get("REQUEST_COMMENT");
    assertFalse(comment, comment.contains("#000000"));
  }

  @Test
  public void theTemplatesAreWrittenInTheRecipientsPalette() throws Exception {
    for (String name : new String[] { "CreateRequestPlugin", "CancelRequestPlugin", "RequestCommentPlugin" }) {
      String html = render(TEMPLATES + name + ".gtmpl", darkPalette());
      assertTrue(name + html, html.contains("bgcolor=\"#1E2328\""));
      assertTrue(name + html, html.contains("class=\"mail-surface mail-text\""));
      assertTrue(name + html, html.contains("class=\"mail-bg"));
      for (String literal : new String[] { "#ffffff", "#333333", "#2f5e92", "#d8d8d8", "#f9f9f9", "#999999", "#f1f1f1", "#c7c7c7" }) {
        assertFalse(name + " no light literal left: " + literal, html.contains(literal));
      }
    }
  }

  @Test
  public void aContextWithoutPaletteRendersInTheLightDefaults() throws Exception {
    String html = render(TEMPLATES + "CreateRequestPlugin.gtmpl", null);
    assertTrue(html, html.contains("bgcolor=\"#FFFFFF\""));
    assertFalse(html, html.contains("null"));
  }

  private static TemplateContext build(String pluginId) {
    BrandingService brandingService = mock(BrandingService.class);
    when(brandingService.getDisplayMode("mary")).thenReturn("dark");
    when(brandingService.getMailPalette(true)).thenReturn(new HashMap<>(Map.of("surface", "#1E2328")));
    IdentityManager identityManager = mock(IdentityManager.class);
    Identity receiver = mock(Identity.class);
    Profile profile = mock(Profile.class);
    when(identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "mary")).thenReturn(receiver);
    when(receiver.getProfile()).thenReturn(profile);
    when(receiver.getRemoteId()).thenReturn("mary");
    when(profile.getProperty(Profile.FIRST_NAME)).thenReturn("Mary");
    when(profile.getFullName()).thenReturn("John Doe");
    when(profile.getUrl()).thenReturn("/john");
    NotificationInfo notification = mock(NotificationInfo.class);
    when(notification.getKey()).thenReturn(new PluginKey(pluginId));
    when(notification.getValueOwnerParameter(anyString())).thenReturn(BLACK_TEXT);
    when(notification.getTo()).thenReturn("mary");
    when(notification.getValueOwnerParameter(NotificationArguments.REQUEST_COMMENT.getKey())).thenReturn(BLACK_TEXT);
    NotificationContext ctx = mock(NotificationContext.class);
    when(ctx.getNotificationInfo()).thenReturn(notification);
    AtomicReference<TemplateContext> rendered = new AtomicReference<>();

    try (MockedStatic<CommonsUtils> commonsUtils = mockStatic(CommonsUtils.class);
         MockedStatic<NotificationPluginUtils> pluginUtils = mockStatic(NotificationPluginUtils.class);
         MockedStatic<LinkProviderUtils> linkProviderUtils = mockStatic(LinkProviderUtils.class);
         MockedStatic<NotificationUtils> notificationUtils = mockStatic(NotificationUtils.class);
         MockedStatic<TemplateUtils> templateUtils = mockStatic(TemplateUtils.class)) {
      commonsUtils.when(() -> CommonsUtils.getService(BrandingService.class)).thenReturn(brandingService);
      pluginUtils.when(() -> NotificationPluginUtils.getLanguage(anyString())).thenReturn("en");
      linkProviderUtils.when(() -> LinkProviderUtils.getRedirectUrl(anyString(), anyString())).thenReturn("/settings");
      linkProviderUtils.when(() -> LinkProviderUtils.getUserAvatarUrl(any())).thenReturn("/avatar");
      notificationUtils.when(() -> NotificationUtils.getUserProfile(anyString())).thenReturn(profile);
      notificationUtils.when(() -> NotificationUtils.formatMention(anyString())).thenAnswer(call -> call.getArgument(0));
      templateUtils.when(() -> TemplateUtils.processSubject(any(TemplateContext.class))).thenReturn("subject");
      templateUtils.when(() -> TemplateUtils.processGroovy(any(TemplateContext.class))).thenAnswer(call -> {
        rendered.set(call.getArgument(0));
        return "body";
      });

      new MailTemplateProvider(new InitParams(), identityManager).getTemplateBuilder()
                                                                 .get(PluginKey.key(pluginId))
                                                                 .buildMessage(ctx);
    }
    return rendered.get();
  }

  private static Map<String, String> darkPalette() {
    Map<String, String> palette = new HashMap<>(MailDocumentShell.LIGHT_DEFAULTS);
    palette.put(MailDocumentShell.BACKGROUND, "#14181B");
    palette.put(MailDocumentShell.SURFACE, "#1E2328");
    palette.put(MailDocumentShell.TEXT, "#E6E9EC");
    palette.put(MailDocumentShell.SECONDARY_TEXT, "#9AA3AB");
    palette.put(MailDocumentShell.LINK, "#5FA9AC");
    palette.put(MailDocumentShell.BORDER, "#3A4148");
    palette.put(MailPalette.MODE, "dark");
    return palette;
  }

  private static String render(String template, Map<String, String> palette) throws Exception {
    StringWriter writer = new StringWriter();
    TemplateContext ctx = new TemplateContext();
    ctx.put("_writer", writer);
    ctx.put("_templateContext", ctx);
    ctx.put("_ctx", new Element(writer));
    for (String key : new String[] { "PROCESS_TITLE", "REQUEST_TITLE", "REQUEST_DESCRIPTION", "REQUEST_COMMENT", "REQUEST_COMMENT_AUTHOR",
        "REQUEST_COMMENT_URL", "PROCESS_URL", "REQUEST_URL", "USER", "PROFILE_URL", "AVATAR", "FIRST_NAME", "FOOTER_LINK" }) {
      ctx.put(key, key.toLowerCase());
    }
    if (palette != null) {
      ctx.put(MailPalette.CONTEXT_KEY, palette);
    }
    new GStringTemplateEngine().createTemplate(Files.readString(Paths.get(template), StandardCharsets.UTF_8))
                               .make(ctx)
                               .writeTo(writer);
    return writer.toString();
  }

  /** The template's helper, as the mail channel gives it: resources and includes. */
  public static class Element {
    private final StringWriter writer;

    public Element(StringWriter writer) {
      this.writer = writer;
    }

    public String appRes(String key, String... args) {
      return key;
    }

    public void include(String path, TemplateContext context) {
      writer.write("<include " + path + "/>");
    }
  }
}
