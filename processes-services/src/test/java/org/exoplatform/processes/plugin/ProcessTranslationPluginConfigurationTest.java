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
package org.exoplatform.processes.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.exoplatform.processes.service.ProcessTranslationService;

import io.meeds.social.translation.plugin.TranslationPlugin;

/**
 * The process translations exist only once their plugin is registered on the
 * platform's translation service: the translation service refuses every
 * translation of an object type with no plugin. The plugin is a Kernel
 * component plugin, since the add-on's Spring context scans none of its
 * packages but the MCP one, where a self-registering bean would never be
 * created.
 */
class ProcessTranslationPluginConfigurationTest {

  private static final String CONFIGURATION       = "conf/portal/configuration.xml";

  private static final String TRANSLATION_SERVICE = "io.meeds.social.translation.service.TranslationService";

  /**
   * The Kernel configuration adds the plugin to the translation service, and
   * declares the service the readers use.
   * Mutants: the plugin entry removed, its target, set-method or type
   * changed, or the reader service not declared.
   *
   * @throws Exception when the configuration can't be read
   */
  @Test
  void thePluginIsAddedToTheTranslationService() throws Exception {
    Document configuration = readConfiguration();

    List<Element> plugins = new ArrayList<>();
    NodeList externalPlugins = configuration.getElementsByTagName("external-component-plugins");
    for (int i = 0; i < externalPlugins.getLength(); i++) {
      Element externalPlugin = (Element) externalPlugins.item(i);
      if (TRANSLATION_SERVICE.equals(text(externalPlugin, "target-component"))) {
        NodeList componentPlugins = externalPlugin.getElementsByTagName("component-plugin");
        for (int j = 0; j < componentPlugins.getLength(); j++) {
          plugins.add((Element) componentPlugins.item(j));
        }
      }
    }
    assertEquals(1, plugins.size());
    assertEquals("addPlugin", text(plugins.get(0), "set-method"));
    assertEquals(ProcessTranslationPlugin.class.getName(), text(plugins.get(0), "type"));

    List<String> componentTypes = new ArrayList<>();
    NodeList components = configuration.getElementsByTagName("component");
    for (int i = 0; i < components.getLength(); i++) {
      componentTypes.add(text((Element) components.item(i), "type"));
    }
    assertTrue(componentTypes.contains(ProcessTranslationService.class.getName()));
  }

  /**
   * The Kernel can build the plugin: it is a translation plugin with a public
   * constructor taking no service, so that the translation service it is
   * created for doesn't depend back on the processes service.
   * Mutant: the no-argument constructor removed or made non-public.
   *
   * @throws Exception when the plugin can't be built
   */
  @Test
  void theKernelBuildsThePluginWithoutAnyService() throws Exception {
    assertTrue(TranslationPlugin.class.isAssignableFrom(ProcessTranslationPlugin.class));
    assertEquals("process", ProcessTranslationPlugin.class.getConstructor().newInstance().getObjectType());
  }

  /**
   * @return the add-on's Kernel configuration
   * @throws Exception when it can't be read
   */
  private Document readConfiguration() throws Exception {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(CONFIGURATION)) {
      assertNotNull(stream, CONFIGURATION + " is missing");
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      return factory.newDocumentBuilder().parse(stream);
    }
  }

  /**
   * @param element an element
   * @param childName the name of one of its children
   * @return the trimmed text of its first such descendant, or null
   */
  private static String text(Element element, String childName) {
    NodeList children = element.getElementsByTagName(childName);
    return children.getLength() == 0 ? null : children.item(0).getTextContent().trim();
  }
}
