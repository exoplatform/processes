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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import org.exoplatform.processes.rest.model.WorkEntity;
import org.exoplatform.processes.rest.util.EntityBuilder;
import org.exoplatform.ws.frameworks.json.impl.JsonDefaultHandler;
import org.exoplatform.ws.frameworks.json.impl.JsonParserImpl;
import org.exoplatform.ws.frameworks.json.impl.ObjectBuilder;

/**
 * The form values a request carries: read from the REST body's string and from
 * an agent's argument, their shape refused with one code, a JSON null kept for
 * the plugin to refuse.
 */
public class RequestFormValuesTest {

  /**
   * Form id → field key → values is read in order, with mixed scalar values.
   */
  @Test
  public void formValuesAreReadByFormAndField() {
    Map<Long, Map<String, List<Object>>> values = RequestFormValues.parse("{\"12\":{\"reason\":[\"Holiday\"],\"days\":[3]}}");

    assertEquals(List.of("Holiday"), values.get(12L).get("reason"));
    assertEquals(List.of(3), values.get(12L).get("days"));
  }

  /**
   * A JSON null for a field and for a whole form is kept as null, so the
   * plugin refuses it with forms.value.nullNotAllowed rather than reading it as
   * a clear. Mutation: drop the null or copy with a collector that refuses it,
   * and this test fails.
   */
  @Test
  public void aJsonNullIsKeptForTheForm() {
    Map<Long, Map<String, List<Object>>> values = RequestFormValues.parse("{\"12\":{\"reason\":null},\"13\":null}");

    assertTrue(values.get(12L).containsKey("reason"));
    assertNull(values.get(12L).get("reason"));
    assertTrue(values.containsKey(13L));
    assertNull(values.get(13L));
  }

  /**
   * An empty array is a clear and is kept as such; a blank or absent text is no
   * value at all.
   */
  @Test
  public void anEmptyArrayIsAClearAndABlankTextIsNothing() {
    assertEquals(List.of(), RequestFormValues.parse("{\"12\":{\"reason\":[]}}").get(12L).get("reason"));
    assertTrue(RequestFormValues.parse(null).isEmpty());
    assertTrue(RequestFormValues.parse(" ").isEmpty());
  }

  /**
   * Every shape that is not form id → field key → array is refused with one
   * code. Mutation: accept a scalar field value or a non-numeric form id, and
   * this test fails.
   */
  @Test
  public void aMalformedShapeIsRefused() {
    for (String json : List.of("not json", "[1]", "{\"abc\":{}}", "{\"0\":{}}", "{\"12\":[1]}", "{\"12\":{\"reason\":\"x\"}}")) {
      IllegalArgumentException refusal = assertThrows(json, IllegalArgumentException.class, () -> RequestFormValues.parse(json));
      assertEquals(RequestFormValues.FORM_VALUES_INVALID, refusal.getMessage());
    }
  }

  /**
   * An agent's argument, maps and lists as the tool call parses them, is read
   * the same way, a null field kept.
   */
  @Test
  public void anAgentArgumentIsReadTheSameWay() {
    Map<String, Object> fields = new HashMap<>();
    fields.put("reason", null);
    fields.put("days", List.of(3));

    Map<Long, Map<String, List<Object>>> values = RequestFormValues.fromObject(Map.of("12", fields));

    assertNull(values.get(12L).get("reason"));
    assertEquals(List.of(3), values.get(12L).get("days"));
  }

  /**
   * Through the REST provider that binds the body, as the request drawer sends
   * it: the form values arrive as a string, read on the creation path, and a
   * malformed one is refused with processes.request.formValuesInvalid; the
   * update path leaves them out. Mutation: read them in toWork, or skip them in
   * toNewWork, and this test fails.
   *
   * @throws Exception when the provider cannot bind the body
   */
  @Test
  public void theDrawerBodyIsBoundByTheRealProvider() throws Exception {
    WorkEntity body = bind("{\"title\":\"t\",\"workFlow\":{\"id\":7},\"formValues\":\"{\\\"12\\\":{\\\"reason\\\":[\\\"Holiday\\\"]}}\"}");
    WorkEntity malformed = bind("{\"title\":\"t\",\"workFlow\":{\"id\":7},\"formValues\":\"{\\\"12\\\":\\\"x\\\"}\"}");

    assertEquals(List.of("Holiday"), EntityBuilder.toNewWork(null, body).getFormValues().get(12L).get("reason"));
    assertNull(EntityBuilder.toWork(null, body).getFormValues());
    assertEquals(RequestFormValues.FORM_VALUES_INVALID,
                 assertThrows(IllegalArgumentException.class, () -> EntityBuilder.toNewWork(null, malformed)).getMessage());
  }

  private static WorkEntity bind(String json) throws Exception {
    JsonParserImpl parser = new JsonParserImpl();
    JsonDefaultHandler handler = new JsonDefaultHandler();
    parser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), handler);
    return ObjectBuilder.createObject(WorkEntity.class, handler.getJsonObject());
  }
}
