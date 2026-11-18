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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the form values a request or a request draft carries: a JSON object,
 * form id → field key → array of values. The shape is checked here, the values
 * themselves by the request values plugin. A JSON null, for a form or for a
 * field, is kept as null, so the plugin refuses it with its own code; every
 * copy uses {@link Map#put}, which keeps a null.
 */
public final class RequestFormValues {

  /** The code of a body whose form values cannot be read. */
  public static final String FORM_VALUES_INVALID = "processes.request.formValuesInvalid";

  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  private RequestFormValues() {
  }

  /**
   * Reads the form values a REST body carries as a string holding a JSON
   * object.
   *
   * @param json the JSON text, or null or blank for none
   * @return form id → field key → values, empty for none
   * @throws IllegalArgumentException with {@link #FORM_VALUES_INVALID} when the
   *           text is not such an object
   */
  public static Map<Long, Map<String, List<Object>>> parse(String json) {
    if (StringUtils.isBlank(json)) {
      return new LinkedHashMap<>();
    }
    JsonNode root;
    try {
      root = JSON_MAPPER.readTree(json);
    } catch (JacksonException e) {
      throw new IllegalArgumentException(FORM_VALUES_INVALID, e);
    }
    return fromNode(root);
  }

  /**
   * Reads the form values an agent's tool call carries as a JSON-like object:
   * maps, lists and scalars.
   *
   * @param values the parsed argument, or null for none
   * @return form id → field key → values, empty for none
   * @throws IllegalArgumentException with {@link #FORM_VALUES_INVALID} when the
   *           argument is not such an object
   */
  public static Map<Long, Map<String, List<Object>>> fromObject(Object values) {
    if (values == null) {
      return new LinkedHashMap<>();
    }
    JsonNode root;
    try {
      root = JSON_MAPPER.valueToTree(values);
    } catch (IllegalArgumentException | JacksonException e) {
      throw new IllegalArgumentException(FORM_VALUES_INVALID, e);
    }
    return fromNode(root);
  }

  /**
   * @param root a JSON node
   * @return its form values
   * @throws IllegalArgumentException when the node is not form id → field key
   *           → array (or null)
   */
  private static Map<Long, Map<String, List<Object>>> fromNode(JsonNode root) {
    if (root == null || root.isNull()) {
      return new LinkedHashMap<>();
    }
    if (!root.isObject()) {
      throw new IllegalArgumentException(FORM_VALUES_INVALID);
    }
    Map<Long, Map<String, List<Object>>> forms = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> form : root.properties()) {
      Long formId = toFormId(form.getKey());
      JsonNode fields = form.getValue();
      if (fields == null || fields.isNull()) {
        forms.put(formId, null);
        continue;
      }
      if (!fields.isObject()) {
        throw new IllegalArgumentException(FORM_VALUES_INVALID);
      }
      Map<String, List<Object>> values = new LinkedHashMap<>();
      for (Map.Entry<String, JsonNode> field : fields.properties()) {
        values.put(field.getKey(), toValues(field.getValue()));
      }
      forms.put(formId, values);
    }
    return forms;
  }

  /**
   * @param key a form id as a JSON key
   * @return the form id
   * @throws IllegalArgumentException when it is not a strictly positive number
   */
  private static Long toFormId(String key) {
    try {
      long formId = Long.parseLong(key);
      if (formId <= 0) {
        throw new IllegalArgumentException(FORM_VALUES_INVALID);
      }
      return formId;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(FORM_VALUES_INVALID, e);
    }
  }

  /**
   * @param node a field's JSON value
   * @return its values, null for a JSON null
   * @throws IllegalArgumentException when it is neither an array nor null
   */
  private static List<Object> toValues(JsonNode node) {
    if (node == null || node.isNull()) {
      return null; // NOSONAR a JSON null is refused by the plugin, with its own code
    }
    if (!node.isArray()) {
      throw new IllegalArgumentException(FORM_VALUES_INVALID);
    }
    List<Object> values = new ArrayList<>();
    for (JsonNode value : node) {
      values.add(JSON_MAPPER.treeToValue(value, Object.class));
    }
    return values;
  }
}
