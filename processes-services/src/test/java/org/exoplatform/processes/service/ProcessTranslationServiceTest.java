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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.LocaleConfigService;

import io.meeds.social.translation.model.TranslationField;
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
   * is removed, so that the column stands, and never rewritten through the
   * single-label save, which sanitizes it into an encoded copy; one that
   * equals the column is left, and a field with none stays without one.
   * Mutants: the removal dropped, the existence check removed, the equality
   * check removed, or the label rewritten instead.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  void aSaveRemovesAStaleDefaultLanguageTranslationOnly() throws Exception {
    when(translationService.getTranslationLabel("process", "9", "title", Locale.ENGLISH)).thenReturn("Old leave");
    when(translationService.getTranslationLabel("process", "9", "description", Locale.ENGLISH)).thenReturn("Ask for leave");
    when(translationService.getTranslationLabel("process", "9", "summary", Locale.ENGLISH)).thenReturn(null);

    processTranslationService.keepDefaultLanguage(workFlow());

    verify(translationService).deleteTranslationLabel("process", "9", "title", Locale.ENGLISH);
    verify(translationService, never()).deleteTranslationLabel("process", "9", "description", Locale.ENGLISH);
    verify(translationService, never()).deleteTranslationLabel("process", "9", "summary", Locale.ENGLISH);
    verify(translationService, never()).saveTranslationLabel(anyString(), anyString(), anyString(), any(Locale.class), anyString());
  }

  /**
   * A save writes each stated field's labels by language, the default
   * language's being the field's own value whatever was sent for it, a blank
   * label dropped and a language tag read with an underscore or a dash; a
   * field not stated, or a save stating none, writes nothing.
   * Mutants: the default language's label taken from the request, a blank
   * label kept, an unstated field written, or the labels written for a
   * process with no id.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  void aSaveWritesTheStatedTranslationsWithTheStoredDefaultValue() throws Exception {
    Map<String, String> titles = new HashMap<>();
    titles.put("en", "Not the stored title");
    titles.put("fr", "Congés");
    titles.put("pt_BR", "Férias");
    titles.put("de", " ");
    Map<String, Map<String, String>> translations = Map.of("title", titles, "other", Map.of("fr", "ignored"));

    processTranslationService.saveTranslations(workFlow(), translations);

    Map<Locale, String> expected = new LinkedHashMap<>();
    expected.put(Locale.FRENCH, "Congés");
    expected.put(Locale.forLanguageTag("pt-BR"), "Férias");
    expected.put(Locale.ENGLISH, "Leave");
    verify(translationService).saveTranslationLabels("process", "9", "title", expected, false);
    verify(translationService, never()).saveTranslationLabels(eq("process"), eq("9"), eq("description"), anyMap(), anyBoolean());
    verify(translationService, never()).saveTranslationLabels(eq("process"), eq("9"), eq("summary"), anyMap(), anyBoolean());

    processTranslationService.saveTranslations(workFlow(), null);
    WorkFlow unsaved = workFlow();
    unsaved.setId(0);
    processTranslationService.saveTranslations(unsaved, translations);
    verify(translationService).saveTranslationLabels(anyString(), anyString(), anyString(), anyMap(), anyBoolean());
  }

  /**
   * A stated field left with no label, its column blank and every sent label
   * blank, loses its stored translations instead of failing the save.
   * Mutant: the empty labels written, which the translation service refuses.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  void aFieldLeftWithNoLabelLosesItsTranslations() throws Exception {
    WorkFlow workFlow = workFlow();
    workFlow.setSummary("");
    TranslationField stored = new TranslationField();
    stored.setLabels(Map.of(Locale.FRENCH, "Règles"));
    when(translationService.getTranslationField("process", "9", "summary")).thenReturn(stored);

    processTranslationService.saveTranslations(workFlow, Map.of("summary", Map.of("fr", " ")));

    verify(translationService).deleteTranslationLabel("process", "9", "summary", Locale.FRENCH);
    verify(translationService, never()).saveTranslationLabels(eq("process"), eq("9"), eq("summary"), anyMap(), anyBoolean());
  }

  /**
   * A translation that can't be written fails the save it belongs to, so
   * that the editor is told and can save again.
   * Mutant: the failure swallowed.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  void aTranslationThatCantBeWrittenFailsTheSave() throws Exception {
    doThrow(new ObjectNotFoundException("gone")).when(translationService)
                                                 .saveTranslationLabels(eq("process"), eq("9"), eq("title"), anyMap(), eq(false));

    org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                                                  () -> processTranslationService.saveTranslations(workFlow(),
                                                                                                   Map.of("title", Map.of("fr", "Congés"))));
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
