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
package org.exoplatform.processes.Utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * The fixed rule that names the task of a new request: « Process name -
 * yyyy/MM/dd - Requester name », followed by « - AI title » when the AI agent
 * sent one. A title is at most {@link #MAX_LENGTH} characters, the size of a
 * task title: the AI title is shortened first, the process name only when the
 * fixed part alone is longer.
 */
public final class RequestTitle {

  /** The size of a task title (TASK_TASKS.TITLE), in Java characters. */
  public static final int                MAX_LENGTH = 250;

  /** The separator between the parts of a title. */
  public static final String             SEPARATOR  = " - ";

  private static final DateTimeFormatter DATE       = DateTimeFormatter.ofPattern("yyyy/MM/dd");

  /**
   * Control characters (C0 and C1, NEL included) and the Unicode line and
   * paragraph separators.
   */
  private static final Pattern           LINE_BREAKS = Pattern.compile("[\\p{Cc}\\p{Zl}\\p{Zp}]");

  /**
   * Not instantiated.
   */
  private RequestTitle() {
  }

  /**
   * Builds the title of a new request.
   *
   * @param processTitle the process's title when the request is created
   * @param date the creation date, in the server's time zone
   * @param requesterName the requester's full name
   * @param agentTitle the title the AI agent sent, or null: control
   *          characters (C0, C1) and the Unicode line and paragraph separators
   *          become spaces, and a blank one adds nothing
   * @return the title, at most {@link #MAX_LENGTH} characters
   */
  public static String of(String processTitle, LocalDate date, String requesterName, String agentTitle) {
    String suffix = SEPARATOR + DATE.format(date) + SEPARATOR + StringUtils.trimToEmpty(requesterName);
    String process = StringUtils.trimToEmpty(processTitle);
    if (process.length() + suffix.length() >= MAX_LENGTH) {
      // A requester name longer than a title is cut too, as a last resort
      return cut(StringUtils.stripEnd(cut(process, MAX_LENGTH - suffix.length()), null) + suffix, MAX_LENGTH);
    }
    String base = process + suffix;
    String agent = StringUtils.trimToEmpty(LINE_BREAKS.matcher(StringUtils.defaultString(agentTitle)).replaceAll(" "));
    int room = MAX_LENGTH - base.length() - SEPARATOR.length();
    if (agent.isEmpty() || room <= 0) {
      return base;
    }
    agent = StringUtils.stripEnd(cut(agent, room), null);
    return agent.isEmpty() ? base : base + SEPARATOR + agent;
  }

  /**
   * Cuts a text to at most a number of Java characters, never inside a
   * surrogate pair.
   *
   * @param text the text
   * @param length the maximum length, 0 or less for an empty text
   * @return the text, or its longest prefix that fits
   */
  private static String cut(String text, int length) {
    if (length <= 0) {
      return "";
    }
    if (text.length() <= length) {
      return text;
    }
    int end = length;
    if (Character.isHighSurrogate(text.charAt(end - 1))) {
      end--;
    }
    return text.substring(0, end);
  }

}
