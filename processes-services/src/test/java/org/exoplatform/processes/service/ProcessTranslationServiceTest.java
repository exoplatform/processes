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
package org.exoplatform.processes.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.LocaleConfigService;

import io.meeds.social.translation.service.TranslationService;

@ExtendWith(MockitoExtension.class)
class ProcessTranslationServiceTest {

  @Mock
  private TranslationService        translationService;

  @Mock
  private LocaleConfigService       localeConfigService;

  @Mock
  private LocaleConfig              defaultLocaleConfig;

  private ProcessTranslationService processTranslationService;

  @BeforeEach
  void setUp() {
    lenient().when(localeConfigService.getDefaultLocaleConfig()).thenReturn(defaultLocaleConfig);
    lenient().when(defaultLocaleConfig.getLocale()).thenReturn(Locale.ENGLISH);
    processTranslationService = new ProcessTranslationService(translationService, localeConfigService);
  }

  /**
   * A reader in another language gets the translation written for that very
   * language, and the stored text where it has none or a blank one.
   * Mutants: the translation ignored, or a blank translation kept.
   */
  @Test
  void aReaderInAnotherLanguageGetsItsTranslationOrTheStoredText() {
    WorkFlow workFlow = workFlow();
    when(translationService.getTranslationLabel("process", "9", "title", Locale.FRENCH)).thenReturn("Congés");
    when(translationService.getTranslationLabel("process", "9", "description", Locale.FRENCH)).thenReturn(" ");
    when(translationService.getTranslationLabel("process", "9", "summary", Locale.FRENCH)).thenReturn(null);

    assertEquals("Congés", processTranslationService.getTitle(workFlow, Locale.FRENCH));
    assertEquals("Ask for leave", processTranslationService.getDescription(workFlow, Locale.FRENCH));
    assertEquals("Leave rules", processTranslationService.getSummary(workFlow, Locale.FRENCH));
  }

  /**
   * A reader in the default language, or with no language, gets the stored
   * column even when a default-language translation is written: the column is
   * the default-language value, whoever wrote it last. No translation is read.
   * Mutants: the default-language guard removed, or the null-locale guard
   * removed.
   */
  @Test
  void aReaderInTheDefaultLanguageGetsTheStoredText() {
    WorkFlow workFlow = workFlow();

    assertEquals("Leave", processTranslationService.getTitle(workFlow, Locale.ENGLISH));
    assertEquals("Leave", processTranslationService.getTitle(workFlow, null));
    verify(translationService, never()).getTranslationLabel(anyString(), anyString(), anyString(), any(Locale.class));
  }

  /**
   * A translation that can't be read leaves the stored text.
   * Mutant: the failure propagated to the reader.
   */
  @Test
  void anUnreadableTranslationLeavesTheStoredText() {
    when(translationService.getTranslationLabel("process", "9", "title", Locale.FRENCH)).thenThrow(new IllegalStateException("no plugin"));

    assertEquals("Leave", processTranslationService.getTitle(workFlow(), Locale.FRENCH));
  }

  /**
   * After a save, a default-language translation that differs from the column
   * is rewritten to it; one that equals it is left, and a field with none
   * stays without one.
   * Mutants: the rewrite removed, the existence check removed (writing a
   * translation for every field), or the equality check removed.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  void aSaveRewritesAStaleDefaultLanguageTranslationOnly() throws Exception {
    when(translationService.getTranslationLabel("process", "9", "title", Locale.ENGLISH)).thenReturn("Old leave");
    when(translationService.getTranslationLabel("process", "9", "description", Locale.ENGLISH)).thenReturn("Ask for leave");
    when(translationService.getTranslationLabel("process", "9", "summary", Locale.ENGLISH)).thenReturn(null);

    processTranslationService.keepDefaultLanguage(workFlow());

    verify(translationService).saveTranslationLabel("process", "9", "title", Locale.ENGLISH, "Leave");
    verify(translationService, never()).saveTranslationLabel("process", "9", "description", Locale.ENGLISH, "Ask for leave");
    verify(translationService, never()).saveTranslationLabel("process", "9", "summary", Locale.ENGLISH, "Leave rules");
  }

  /**
   * A deleted process's translations are deleted; a failure doesn't reach
   * the delete that called it.
   * Mutants: the delete removed, or the failure propagated.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  void aDeletedProcessLosesItsTranslations() throws Exception {
    processTranslationService.deleteTranslations(9L);
    verify(translationService).deleteTranslationLabels("process", "9");

    doThrow(new ObjectNotFoundException("gone")).when(translationService).deleteTranslationLabels("process", "10");
    processTranslationService.deleteTranslations(10L);
  }

  /**
   * @return a process with its stored default-language texts
   */
  private static WorkFlow workFlow() {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(9L);
    workFlow.setTitle("Leave");
    workFlow.setDescription("Ask for leave");
    workFlow.setSummary("Leave rules");
    return workFlow;
  }
}
