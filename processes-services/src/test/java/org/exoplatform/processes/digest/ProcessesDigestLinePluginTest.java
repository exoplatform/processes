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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValuesParam;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.service.ProcessTranslationService;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.LocaleConfigService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.commons.digest.model.DigestItem;
import io.meeds.commons.digest.model.DigestLine;
import io.meeds.commons.digest.plugin.DigestLineContext;
import io.meeds.social.translation.service.TranslationService;

@RunWith(MockitoJUnitRunner.class)
public class ProcessesDigestLinePluginTest {

  private static final DigestLineContext CONTEXT = new DigestLineContext("ayoub", Locale.ENGLISH, ZoneId.of("Europe/Paris"));

  @Mock
  private ProcessesService               processesService;

  @Mock
  private IdentityManager                identityManager;

  @Mock
  private TranslationService             translationService;

  @Mock
  private LocaleConfigService            localeConfigService;

  @Mock
  private LocaleConfig                   defaultLocaleConfig;

  private ProcessesDigestLinePlugin      plugin;

  @Before
  public void setUp() {
    InitParams params = new InitParams();
    ValuesParam pluginIds = new ValuesParam();
    pluginIds.setName("pluginIds");
    pluginIds.setValues(new ArrayList<>(List.of(ProcessesDigestLinePlugin.CREATE_REQUEST_PLUGIN,
                                                ProcessesDigestLinePlugin.REQUEST_COMMENT_PLUGIN)));
    params.addParameter(pluginIds);
    // The request links need the running platform: plain markers here
    lenient().when(localeConfigService.getDefaultLocaleConfig()).thenReturn(defaultLocaleConfig);
    lenient().when(defaultLocaleConfig.getLocale()).thenReturn(Locale.ENGLISH);
    ProcessTranslationService processTranslationService = new ProcessTranslationService(translationService, localeConfigService);
    plugin = new ProcessesDigestLinePlugin(params, processesService, identityManager, processTranslationService) {
      @Override
      protected String requestUrl(Work work) {
        return "request:" + work.getId();
      }

      @Override
      protected String commentsUrl(Work work) {
        return "comments:" + work.getId();
      }
    };
    lenient().when(identityManager.getOrCreateUserIdentity("john")).thenReturn(identity("john", "15", "John Smith"));

    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(3);
    workFlow.setTitle("Leave request");
    Work work = new Work();
    work.setId(7);
    work.setTitle("Two weeks in October");
    work.setCreatedBy("john");
    work.setWorkFlow(workFlow);
    // The recipient (ayoub, a manager) is not the creator: the id-only read
    lenient().when(processesService.getWorkById(7L)).thenReturn(work);
  }

  @Test
  public void testNewRequestLine() {
    DigestLine line = plugin.buildLine(item(ProcessesDigestLinePlugin.CREATE_REQUEST_PLUGIN, "REQUEST_ID", "7", "REQUEST_CREATOR", "john"),
                                       CONTEXT);
    assertNotNull(line);
    assertEquals("digest.line.CreateRequestPlugin", line.getLabelKey());
    assertEquals(List.of("Two weeks in October", "Leave request"), line.getArgs());
    assertEquals("request:7", line.getUrl());
  }

  /**
   * A recipient reading the platform in another language gets the process's
   * title in that language when it is translated, and its stored title when it
   * is not.
   * Mutants: the line reading the stored title, or the recipient's language
   * replaced by the default one.
   */
  @Test
  public void testNewRequestLineNamesTheProcessInTheRecipientsLanguage() {
    DigestLineContext french = new DigestLineContext("ayoub", Locale.FRENCH, ZoneId.of("Europe/Paris"));
    when(translationService.getTranslationLabel("process", "3", "title", Locale.FRENCH)).thenReturn("Demande de congés");
    DigestItem newRequest = item(ProcessesDigestLinePlugin.CREATE_REQUEST_PLUGIN, "REQUEST_ID", "7", "REQUEST_CREATOR", "john");

    assertEquals(List.of("Two weeks in October", "Demande de congés"), plugin.buildLine(newRequest, french).getArgs());

    when(translationService.getTranslationLabel("process", "3", "title", Locale.FRENCH)).thenReturn(null);
    assertEquals(List.of("Two weeks in October", "Leave request"), plugin.buildLine(newRequest, french).getArgs());
  }

  @Test
  public void testCommentLineNamesTheAuthor() {
    DigestLine line = plugin.buildLine(item(ProcessesDigestLinePlugin.REQUEST_COMMENT_PLUGIN,
                                            "REQUEST_ID", "7", "REQUEST_COMMENT_AUTHOR", "john"),
                                       CONTEXT);
    assertNotNull(line);
    assertEquals(List.of("John Smith", "Two weeks in October"), line.getArgs());
    assertEquals("comments:7", line.getUrl());
  }

  @Test
  public void testDeletedOrUnknownRequestGivesNoLine() {
    assertNull(plugin.buildLine(item(ProcessesDigestLinePlugin.CREATE_REQUEST_PLUGIN, "REQUEST_ID", "404"), CONTEXT));
    assertNull(plugin.buildLine(item(ProcessesDigestLinePlugin.CREATE_REQUEST_PLUGIN, "REQUEST_TITLE", "no id stored"), CONTEXT));
  }

  @Test
  public void testUnknownTypeGivesNoLine() {
    assertNull(plugin.buildLine(item("CancelRequestPlugin", "REQUEST_ID", "7"), CONTEXT));
  }

  private static DigestItem item(String pluginId, String... params) {
    Map<String, String> map = new LinkedHashMap<>();
    for (int i = 0; i + 1 < params.length; i += 2) {
      map.put(params[i], params[i + 1]);
    }
    return new DigestItem(1, "ayoub", pluginId, "process", Instant.now(), map);
  }

  private static Identity identity(String username, String id, String fullName) {
    Identity identity = new Identity(OrganizationIdentityProvider.NAME, username);
    identity.setId(id);
    Profile profile = new Profile(identity);
    profile.setProperty(Profile.FULL_NAME, fullName);
    identity.setProfile(profile);
    return identity;
  }

}
