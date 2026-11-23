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
package org.exoplatform.processes.mcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeMap;

import org.junit.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The definition of submit_work_request takes an optional agent title, and a
 * booted instance takes the new text through mcp-server's upgrade digests: a
 * saved definition whose digest of {description, input_schema} (sorted keys,
 * compact JSON, SHA-256) is listed in ai-tool-definition-upgrades.json is
 * replaced by the shipped one, any other saved text being an administrator's
 * edit (EXO-92568).
 */
public class ProcessesToolDefinitionUpgradeTest {

  private static final String TOOL               = "submit_work_request";

  /** The only text submit_work_request shipped before EXO-92568. */
  private static final String SHIPPED_DIGEST     = "2410f9f21bddc010b503710ce33e78b4945ebf40c5e4c29daad80d9b249af7c0";

  /** The text EXO-92568 shipped, the optional title's: Forms on processes replaces it. */
  private static final String TITLE_DIGEST       = "b44da91deb5c3a16baa1f1aa4682058a93906d57ecb019897ee8cedd446c712f";

  /**
   * The first text of Forms on processes (EXO-94526), whose refusal named a
   * field by its key alone: the text that names its form replaces it.
   */
  private static final String FORM_VALUES_DIGEST = "6bfd32b490e70f6c95d545ea7dfbca7378399d0648d8999e537c459cd66f65dd";

  /**
   * The text whose refusal named a field's form, before a request carried its
   * board's forms: the text naming every form the request carries replaces it.
   */
  private static final String FORM_ID_DIGEST     = "82e841b90e02b04a5aa29c87302b10f2357355863189f3d93ecfa49a2fb03ee1";

  /**
   * The text naming every form a request carries, before a process with
   * documents to fill was handed to the Process app: the text that hands it
   * off replaces it.
   */
  private static final String BOARD_FORMS_DIGEST = "ae731d2d7c38625543a885431086495d90c8e1513a05f662baf3e8da2b3e154a";

  /**
   * The text Forms on processes (EXO-94526) shipped, as `jq -jcS
   * '{description, input_schema}' | shasum -a 256` gives it: the first column
   * wording (EXO-94923) replaces it.
   */
  private static final String FORMS_DIGEST       = "757e20e45e52d23ed8cbc915a2778cd8660dbe451496a70bd23e665e018c0f64";

  /**
   * The text EXO-94923 ships, the request created in the board's first
   * column: the next change of this tool lists it.
   */
  private static final String CURRENT_DIGEST     = "e012bfc50f85a9c8891283a9041764304a43a6e3da61293c5bc13f0a6ba2c375";

  /**
   * The texts the three other tools EXO-94923 rewords shipped before it,
   * which named the five former columns, by tool.
   */
  private static final java.util.Map<String, String> COLUMN_WORDINGS = java.util.Map.of(
      "get_my_requests", "72f3b1d9ba6bd2eea9245ad552e549445bfa0fe0d69171f0d45e93440d561a52",
      "cancel_work_request", "7dca2e6cb1ca688055db93439d0734cba8629abd28e1ecc709dc8f5a0137e7b0",
      "get_pending_approvals", "5d9b3980c731acbd45b4f0688e2fe30eea1324ab376e6f9491399dbd0fa02350");

  /**
   * The title is optional and its description says where it goes and that it
   * is shortened. Mutation: title required again, and this test fails.
   *
   * @throws Exception never
   */
  @Test
  public void theTitleIsOptionalAndGoesAfterTheFixedRule() throws Exception {
    JsonNode definition = definition();
    JsonNode schema = definition.get("input_schema");
    Set<String> required = new HashSet<>();
    schema.get("required").forEach(name -> required.add(name.asString()));
    assertEquals(Set.of("process_id"), required);
    String title = schema.get("properties").get("title").get("description").asString();
    assertTrue(title, title.startsWith("Optional.") && title.contains("'<process> - <yyyy/MM/dd> - <requester> - <title>'")
        && title.contains("250 characters"));
  }

  /**
   * The upgrades file lists every text shipped before, never the current one,
   * whose digest the test computes as mcp-server does. Mutation: a shipped
   * digest removed, or the current one listed, and this test fails.
   *
   * @throws Exception never
   */
  @Test
  public void theUpgradesReplaceTheShippedTextOnly() throws Exception {
    Set<String> replaced = new HashSet<>();
    JsonMapper.shared()
              .readTree(getClass().getResourceAsStream("/ai-tool-definition-upgrades.json"))
              .get("tools")
              .forEach(tool -> {
                if (TOOL.equals(tool.get("name").asString())) {
                  tool.get("replaced_definitions").forEach(digest -> replaced.add(digest.asString()));
                }
              });
    assertTrue(replaced.toString(), replaced.contains(SHIPPED_DIGEST));
    assertTrue(replaced.toString(), replaced.contains(TITLE_DIGEST));
    assertTrue(replaced.toString(), replaced.contains(FORM_VALUES_DIGEST));
    assertTrue(replaced.toString(), replaced.contains(FORM_ID_DIGEST));
    assertTrue(replaced.toString(), replaced.contains(BOARD_FORMS_DIGEST));
    assertTrue(replaced.toString(), replaced.contains(FORMS_DIGEST));

    JsonNode definition = definition();
    ObjectNode text = JsonMapper.shared().createObjectNode();
    text.put("description", definition.get("description").asString());
    text.set("input_schema", definition.get("input_schema"));
    String digest = sha256(canonical(text));
    assertEquals("the current text's digest, which the next change of the tool lists", CURRENT_DIGEST, digest);
    assertFalse("the current text replaces nothing", replaced.contains(digest));
  }

  /**
   * Each other tool EXO-94923 rewords lists the text it shipped with the five
   * former columns, never its current one, so a booted instance takes the new
   * text unless an administrator edited it. Mutation: drop a tool's entry, or
   * list its current text, and this test fails.
   *
   * @throws Exception never
   */
  @Test
  public void theColumnFreeWordingsReplaceTheColumnOnes() throws Exception {
    java.util.Map<String, Set<String>> replaced = new java.util.HashMap<>();
    JsonMapper.shared()
              .readTree(getClass().getResourceAsStream("/ai-tool-definition-upgrades.json"))
              .get("tools")
              .forEach(tool -> tool.get("replaced_definitions")
                                   .forEach(digest -> replaced.computeIfAbsent(tool.get("name").asString(), name -> new HashSet<>())
                                                              .add(digest.asString())));
    for (java.util.Map.Entry<String, String> wording : COLUMN_WORDINGS.entrySet()) {
      Set<String> listed = replaced.getOrDefault(wording.getKey(), Set.of());
      assertTrue(wording.getKey() + " " + listed, listed.contains(wording.getValue()));
      JsonNode definition = definition(wording.getKey());
      ObjectNode text = JsonMapper.shared().createObjectNode();
      text.put("description", definition.get("description").asString());
      text.set("input_schema", definition.get("input_schema"));
      assertFalse(wording.getKey() + " lists its current text", listed.contains(sha256(canonical(text))));
    }
  }

  /**
   * The form values are an optional argument the space scope does not accept,
   * and the description no longer forbids them: it sends the agent to the
   * process's forms first, through get_form_places and get_form on the
   * process's place, which lists every form the request carries, the
   * board's included, and to the required fields of every form. A field's
   * refusal names its form id before its key, or its key alone. Mutation:
   * form_values required, listed in allowed_args, the old sentence restored,
   * the refusal's form id dropped, the board's forms left out, or the hand-off
   * of a process with documents to fill dropped, and this test fails. That
   * process is not submitted here: the tool refuses it and the agent gives the
   * user the process's link to finish in the Process app.
   *
   * @throws Exception never
   */
  @Test
  public void theFormValuesAreOptionalAndUnscopedOnly() throws Exception {
    JsonNode definition = definition();
    JsonNode schema = definition.get("input_schema");
    JsonNode formValues = schema.get("properties").get("form_values");
    assertNotNull(formValues);
    assertEquals("object", formValues.get("type").asString());
    Set<String> required = new HashSet<>();
    schema.get("required").forEach(name -> required.add(name.asString()));
    assertFalse(required.contains("form_values"));
    Set<String> allowed = new HashSet<>();
    definition.get("space_scope").get("allowed_args").forEach(name -> allowed.add(name.asString()));
    assertEquals(Set.of("title", "description"), allowed);
    assertTrue(definition.get("require_approval").asBoolean());
    String description = definition.get("description").asString();
    assertFalse(description, description.contains("NO structured field map"));
    assertTrue(description, description.contains("get_form_places") && description.contains("get_form with")
        && description.contains("'processes.process'") && description.contains("every required field"));
    assertTrue(description, description.contains("forms.value.required:<formId>:<key>") && description.contains("the key alone"));
    assertTrue(description, description.contains("those of its board") && description.contains("lists every form the request carries")
        && description.contains("every required field of every form"));
    assertTrue(description, description.contains("processes.request.documentToFill") && description.contains("'requestUrl'")
        && description.contains("Process app"));
  }

  /**
   * Reads the shipped definition of submit_work_request.
   *
   * @return the definition
   * @throws Exception never
   */
  private JsonNode definition() throws Exception {
    return definition(TOOL);
  }

  /**
   * Reads a tool's shipped definition.
   *
   * @param name the tool's name
   * @return its definition
   * @throws Exception when the file can't be read or holds no such tool
   */
  private JsonNode definition(String name) throws Exception {
    JsonNode found = null;
    for (JsonNode tool : JsonMapper.shared()
                                   .readTree(getClass().getResourceAsStream("/ai-tool-definitions.json"))
                                   .get("tools")) {
      if (name.equals(tool.get("name").asString())) {
        found = tool;
      }
    }
    assertNotNull(found);
    return found;
  }

  /**
   * Writes a JSON node with its object keys sorted, compact, as
   * mcp-server's definition digest does.
   *
   * @param node the node
   * @return the text
   */
  private static String canonical(JsonNode node) {
    if (node.isObject()) {
      TreeMap<String, JsonNode> sorted = new TreeMap<>();
      node.properties().forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
      StringBuilder text = new StringBuilder("{");
      sorted.forEach((key, value) -> {
        if (text.length() > 1) {
          text.append(',');
        }
        text.append(JsonMapper.shared().getNodeFactory().stringNode(key).toString()).append(':').append(canonical(value));
      });
      return text.append('}').toString();
    }
    if (node.isArray()) {
      StringBuilder text = new StringBuilder("[");
      for (JsonNode element : node) {
        if (text.length() > 1) {
          text.append(',');
        }
        text.append(canonical(element));
      }
      return text.append(']').toString();
    }
    return node.toString();
  }

  /**
   * Hashes a text.
   *
   * @param text the text
   * @return its SHA-256, in lower-case hexadecimal
   * @throws Exception never
   */
  private static String sha256(String text) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
  }

}
