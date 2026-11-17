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

import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.resources.LocaleConfigService;

import io.meeds.social.translation.service.TranslationService;

/**
 * Reads and maintains the per-language title, description and summary of a
 * process. The process's own columns hold the platform's default language: a
 * reader in another language gets that language's translation when one is
 * written, and the column otherwise; a reader in the default language always
 * gets the column, so a process never translated reads as stored.
 */
public class ProcessTranslationService {

  /** The object type of a process's translations */
  public static final String       OBJECT_TYPE        = "process";

  /** The translated field holding a process's title */
  public static final String       TITLE_FIELD        = "title";

  /** The translated field holding a process's description */
  public static final String       DESCRIPTION_FIELD  = "description";

  /** The translated field holding a process's summary */
  public static final String       SUMMARY_FIELD      = "summary";

  /** Every translated field of a process */
  public static final List<String> FIELDS             = List.of(TITLE_FIELD, DESCRIPTION_FIELD, SUMMARY_FIELD);

  private static final Log         LOG                = ExoLogger.getLogger(ProcessTranslationService.class);

  private final TranslationService translationService;

  private final LocaleConfigService localeConfigService;

  /**
   * Builds the service.
   *
   * @param translationService the platform's translation service
   * @param localeConfigService the locale configuration, which names the
   *          platform's default language
   */
  public ProcessTranslationService(TranslationService translationService, LocaleConfigService localeConfigService) {
    this.translationService = translationService;
    this.localeConfigService = localeConfigService;
  }

  /**
   * Returns a process's title in a reader's language.
   *
   * @param workFlow the process, as stored
   * @param locale the reader's language, or null for the stored value
   * @return the title in that language, or the stored title
   */
  public String getTitle(WorkFlow workFlow, Locale locale) {
    return workFlow == null ? null : getLabel(workFlow.getId(), TITLE_FIELD, workFlow.getTitle(), locale);
  }

  /**
   * Returns a process's description in a reader's language.
   *
   * @param workFlow the process, as stored
   * @param locale the reader's language, or null for the stored value
   * @return the description in that language, or the stored description
   */
  public String getDescription(WorkFlow workFlow, Locale locale) {
    return workFlow == null ? null : getLabel(workFlow.getId(), DESCRIPTION_FIELD, workFlow.getDescription(), locale);
  }

  /**
   * Returns a process's summary in a reader's language.
   *
   * @param workFlow the process, as stored
   * @param locale the reader's language, or null for the stored value
   * @return the summary in that language, or the stored summary
   */
  public String getSummary(WorkFlow workFlow, Locale locale) {
    return workFlow == null ? null : getLabel(workFlow.getId(), SUMMARY_FIELD, workFlow.getSummary(), locale);
  }

  /**
   * Returns one translated field of a process in a reader's language: the
   * translation written for that very language, unless it is the default
   * language, whose value is the stored column.
   *
   * @param workflowId the process's id
   * @param fieldName the translated field
   * @param storedValue the column's value, in the default language
   * @param locale the reader's language, or null for the stored value
   * @return the translation, or the stored value
   */
  public String getLabel(long workflowId, String fieldName, String storedValue, Locale locale) {
    if (workflowId <= 0 || locale == null || isDefaultLocale(locale)) {
      return storedValue;
    }
    try {
      String label = translationService.getTranslationLabel(OBJECT_TYPE, String.valueOf(workflowId), fieldName, locale);
      return StringUtils.isBlank(label) ? storedValue : label;
    } catch (RuntimeException e) {
      LOG.debug("Can't read the {} translation of the process {} in {}, its stored value is used",
                fieldName,
                workflowId,
                locale,
                e);
      return storedValue;
    }
  }

  /**
   * Rewrites, after a process is saved, each default-language translation that
   * no longer equals the column, so that the editor, which shows that
   * translation in its default field, and every reader agree with the column
   * whoever wrote it. A field with no default-language translation stays
   * without one.
   *
   * @param workFlow the process as saved
   */
  public void keepDefaultLanguage(WorkFlow workFlow) {
    if (workFlow == null || workFlow.getId() <= 0) {
      return;
    }
    Locale defaultLocale = getDefaultLocale();
    String objectId = String.valueOf(workFlow.getId());
    keepDefaultLanguage(objectId, TITLE_FIELD, workFlow.getTitle(), defaultLocale);
    keepDefaultLanguage(objectId, DESCRIPTION_FIELD, workFlow.getDescription(), defaultLocale);
    keepDefaultLanguage(objectId, SUMMARY_FIELD, workFlow.getSummary(), defaultLocale);
  }

  /**
   * Deletes every translation of a deleted process. The caller has checked
   * the right to delete it; a failure is logged and leaves the process
   * deleted.
   *
   * @param workflowId the deleted process's id
   */
  public void deleteTranslations(long workflowId) {
    try {
      translationService.deleteTranslationLabels(OBJECT_TYPE, String.valueOf(workflowId));
    } catch (Exception e) {
      LOG.warn("Can't delete the translations of the deleted process {}", workflowId, e);
    }
  }

  /**
   * Returns the platform's default language.
   *
   * @return the default locale
   */
  public Locale getDefaultLocale() {
    return localeConfigService.getDefaultLocaleConfig().getLocale();
  }

  /**
   * Rewrites one field's default-language translation when it exists and
   * differs from the column.
   *
   * @param objectId the process's id
   * @param fieldName the translated field
   * @param storedValue the column's value
   * @param defaultLocale the platform's default language
   */
  private void keepDefaultLanguage(String objectId, String fieldName, String storedValue, Locale defaultLocale) {
    try {
      String label = translationService.getTranslationLabel(OBJECT_TYPE, objectId, fieldName, defaultLocale);
      if (label != null && !label.equals(storedValue)) {
        translationService.saveTranslationLabel(OBJECT_TYPE, objectId, fieldName, defaultLocale, storedValue);
      }
    } catch (Exception e) {
      LOG.warn("Can't align the default-language {} translation of the process {} with its stored value",
               fieldName,
               objectId,
               e);
    }
  }

  /**
   * Tells whether a reader's language is the platform's default language.
   *
   * @param locale the reader's language
   * @return true for the default language
   */
  private boolean isDefaultLocale(Locale locale) {
    Locale defaultLocale = getDefaultLocale();
    return defaultLocale != null && defaultLocale.toLanguageTag().equals(locale.toLanguageTag());
  }
}
