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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Test;

/**
 * The fixed rule of a request's title and its 250-character limit
 * (EXO-92568).
 */
public class RequestTitleTest {

  private static final LocalDate DATE  = LocalDate.of(2026, 10, 22);

  /** « Leave request - 2026/10/22 - Jean Martin », 40 characters. */
  private static final String    BASE  = "Leave request - 2026/10/22 - Jean Martin";

  /**
   * Process, date and requester, joined by « - »; the agent's title after
   * them, line breaks turned into spaces; a blank or absent one adds nothing.
   * Mutations: agent title first, separator changed, date pattern changed,
   * control characters or Unicode separators kept: this test fails.
   */
  @Test
  public void theFixedRuleThenTheAgentTitle() {
    assertEquals(BASE, RequestTitle.of("Leave request", DATE, "Jean Martin", null));
    assertEquals(BASE, RequestTitle.of(" Leave request ", DATE, "Jean Martin", "  \n "));
    assertEquals(BASE + " - Summer holidays", RequestTitle.of("Leave request", DATE, "Jean Martin", "Summer holidays"));
    assertEquals(BASE + " - Summer  holidays", RequestTitle.of("Leave request", DATE, "Jean Martin", "Summer\r\nholidays"));
    assertEquals(BASE + " - Summer holidays - in July - again",
                 RequestTitle.of("Leave request", DATE, "Jean Martin", "Summer\u2028holidays\u0085-\u2029in July\u009F-\tagain"));
  }

  /**
   * A title is at most 250 characters: the agent's title is cut first, and
   * dropped with its separator when nothing of it fits. Mutations: limit 251,
   * separator kept with no room, no cut: this test fails.
   */
  @Test
  public void aLongAgentTitleIsShortened() {
    String agent = "x".repeat(300);
    String title = RequestTitle.of("Leave request", DATE, "Jean Martin", agent);
    assertEquals(RequestTitle.MAX_LENGTH, title.length());
    assertEquals(BASE + " - " + "x".repeat(RequestTitle.MAX_LENGTH - BASE.length() - 3), title);

    String fits = "y".repeat(RequestTitle.MAX_LENGTH - BASE.length() - 3);
    assertEquals(BASE + " - " + fits, RequestTitle.of("Leave request", DATE, "Jean Martin", fits));

    // A fixed part of 247 characters leaves room for the separator only
    String process = "p".repeat(247 - " - 2026/10/22 - Jean Martin".length());
    String base = process + " - 2026/10/22 - Jean Martin";
    assertEquals(247, base.length());
    assertEquals(base, RequestTitle.of(process, DATE, "Jean Martin", "Summer holidays"));
    assertEquals(base.substring(1) + " - S", RequestTitle.of(process.substring(1), DATE, "Jean Martin", "Summer holidays"));
    // Room for one character only: an emoji does not fit, and its separator goes
    assertEquals(base.substring(1), RequestTitle.of(process.substring(1), DATE, "Jean Martin", "\uD83D\uDE00 Summer"));
  }

  /**
   * A cut never splits a surrogate pair, and never leaves a trailing space.
   * Mutation: cut at the plain index and this test fails.
   */
  @Test
  public void aCutKeepsWholeCharacters() {
    int room = RequestTitle.MAX_LENGTH - BASE.length() - 3;
    String agent = "a".repeat(room - 1) + "😀" + "b";
    String title = RequestTitle.of("Leave request", DATE, "Jean Martin", agent);
    assertEquals(BASE + " - " + "a".repeat(room - 1), title);
    assertFalse(Character.isHighSurrogate(title.charAt(title.length() - 1)));

    String spaced = "a".repeat(room - 2) + "  b";
    assertEquals(BASE + " - " + "a".repeat(room - 2), RequestTitle.of("Leave request", DATE, "Jean Martin", spaced));
  }

  /**
   * A fixed part longer than a title cuts the process name, never the date
   * or the requester, leaves no trailing space before them, and leaves no
   * room for the agent's title. Mutation: cut the whole title from its end, or
   * keep the space the cut leaves, and this test fails.
   */
  @Test
  public void aLongProcessNameIsShortened() {
    String title = RequestTitle.of("p".repeat(400), DATE, "Jean Martin", "Summer holidays");
    assertEquals(RequestTitle.MAX_LENGTH, title.length());
    assertTrue(title, title.endsWith(" - 2026/10/22 - Jean Martin"));
    assertTrue(title.startsWith("ppp"));

    // A cut right after a space leaves no double space before the date
    String spaced = RequestTitle.of("p".repeat(222) + " q" + "p".repeat(100), DATE, "Jean Martin", null);
    assertEquals("p".repeat(222) + " - 2026/10/22 - Jean Martin", spaced);
  }

}
