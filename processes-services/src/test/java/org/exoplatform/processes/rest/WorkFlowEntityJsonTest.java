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
package org.exoplatform.processes.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringReader;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.rest.model.WorkFlowEntity;
import org.exoplatform.processes.rest.util.EntityBuilder;
import org.exoplatform.ws.frameworks.json.impl.JsonDefaultHandler;
import org.exoplatform.ws.frameworks.json.impl.JsonParserImpl;
import org.exoplatform.ws.frameworks.json.impl.ObjectBuilder;

/**
 * The process editor's save body is read by the eXo WS JSON reader that
 * serves the processes REST resource, which binds a map only when its values
 * are plain types: the translations ride as one flat map per field.
 */
class WorkFlowEntityJsonTest {

  /**
   * A save body carrying the title and description per language is bound by
   * the REST resource's own JSON reader, and reaches the process the Service
   * saves; a field not sent stays unstated.
   * Mutant: the translations carried as one map of maps, which the reader
   * refuses before the resource runs.
   *
   * @throws Exception when the body can't be bound
   */
  @Test
  void theEditorsSaveBodyBindsItsTranslations() throws Exception {
    String body = "{\"id\":9,\"title\":\"Leave\",\"description\":\"Ask for leave\",\"summary\":\"Rules\",\"enabled\":true,"
        + "\"titleTranslations\":{\"en\":\"Leave\",\"fr\":\"Congés\",\"pt-BR\":\"Férias\"},"
        + "\"descriptionTranslations\":{\"fr\":\"Demander un congé\"}}";
    JsonParserImpl parser = new JsonParserImpl();
    JsonDefaultHandler handler = new JsonDefaultHandler();
    parser.parse(new StringReader(body), handler);

    WorkFlowEntity entity = ObjectBuilder.createObject(WorkFlowEntity.class, handler.getJsonObject());

    assertEquals(Map.of("en", "Leave", "fr", "Congés", "pt-BR", "Férias"), entity.getTitleTranslations());
    assertEquals(Map.of("fr", "Demander un congé"), entity.getDescriptionTranslations());
    assertNull(entity.getSummaryTranslations());
    WorkFlow workFlow = EntityBuilder.fromEntity(entity);
    assertEquals(Map.of("en", "Leave", "fr", "Congés", "pt-BR", "Férias"), workFlow.getTitleTranslations());
    assertEquals(Map.of("fr", "Demander un congé"), workFlow.getDescriptionTranslations());
    assertNull(workFlow.getSummaryTranslations());
  }
}
