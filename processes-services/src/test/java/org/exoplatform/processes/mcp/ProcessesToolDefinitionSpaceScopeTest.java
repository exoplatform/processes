/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.processes.mcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;

import io.meeds.mcp.server.constant.Origin;
import io.meeds.mcp.server.model.ActingIdentity;
import io.meeds.mcp.server.model.SimpleToolDefinition;
import io.meeds.mcp.server.model.SpaceScopeDeclaration;
import io.meeds.mcp.server.model.SpaceScopeRefusedException;
import io.meeds.mcp.server.plugin.SpaceScopeResolver;
import io.meeds.mcp.server.service.McpToolSpaceScopeService;
import io.meeds.mcp.server.util.McpToolUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins the {@code space_scope} declarations the processes add-on ships: which
 * processes tools a call limited to one space may use, on which arguments.
 */
public class ProcessesToolDefinitionSpaceScopeTest {

  private static final Set<String> WRITES     = Set.of("submit_work_request", "cancel_work_request");

  private static final JsonMapper  MAPPER     = JsonMapper.builder().build();

  /** The role each space-scoped write declares in {@code space_role}. */
  private static final Map<String, String> SPACE_ROLES = Map.of("cancel_work_request", "member",
                                                                "submit_work_request", "member");

  /**
   * Exactly four processes tools declare a space scope, each well formed for
   * what it is: the request lookup and the request cancel by the request and
   * the {@value WorkRequestSpaceScopeResolver#NAME} resolver, the submit by the
   * process and the {@value ProcessSpaceScopeResolver#NAME} resolver, and the
   * process list by its space, the call's space when it names none. The other
   * listings read across every space the user sees and take no space, so they
   * stay undeclared, refused under a space scope. Every declared write keeps
   * its approval, and each accepts only the schema's other arguments: the
   * request's title and description for the submit, nothing for the others.
   * Mutants: a declaration dropped or added, its resolver or object argument
   * changed, or an argument added to the accepted ones.
   */
  @Test
  public void declaredToolsAreTheExpectedOnes() {
    Map<String, SimpleToolDefinition> tools = tools();
    Map<String, SimpleToolDefinition> declared = tools.values()
                                                      .stream()
                                                      .filter(tool -> tool.getSpaceScope() != null)
                                                      .collect(Collectors.toMap(SimpleToolDefinition::getName, Function.identity()));

    assertEquals(Set.of("list_processes", "get_request_details", "submit_work_request", "cancel_work_request"),
                 declared.keySet());
    declared.forEach((name, tool) -> {
      boolean write = WRITES.contains(name);
      assertEquals(name, !write, McpToolSpaceScopeService.isReadTool(tool));
      assertTrue(name, tool.getSpaceScope().isWellFormedFor(write));
      if (write) {
        assertTrue(name, tool.isRequireApproval());
      }
    });
    assertObject(declared.get("get_request_details"), "request_id", WorkRequestSpaceScopeResolver.NAME, List.of());
    assertObject(declared.get("cancel_work_request"), "request_id", WorkRequestSpaceScopeResolver.NAME, List.of());
    assertObject(declared.get("submit_work_request"), "process_id", ProcessSpaceScopeResolver.NAME, List.of("title", "description"));
    SpaceScopeDeclaration list = declared.get("list_processes").getSpaceScope();
    assertTrue(list.isSpaceKind());
    assertEquals("space_id", list.getSpaceArg());
    assertTrue(list.isUseScopeWhenAbsent());
    assertEquals(List.of(), list.getAllowedArgs() == null ? List.of() : list.getAllowedArgs());
    assertTrue(MAPPER.readTree(declared.get("list_processes").getInputSchema()).path("properties").has("space_id"));
    for (String listing : List.of("get_my_requests", "get_pending_approvals")) {
      assertNull(listing, tools.get(listing).getSpaceScope());
    }
  }

  /**
   * Through the real space scope check and the two resolvers, a run limited
   * to space 12 submits a request to a process of space 12, cancels and
   * reads a request of that space, as approved proposals for the writes; the
   * same calls on a process or a request of space 13, or one with no space,
   * a call naming no process or request, an argument a declaration doesn't
   * accept, and a write the run makes itself, are refused. The process list
   * reads the call's space when it names none, and refuses another space.
   * Mutants: a declaration dropped, its resolver renamed, its object argument
   * changed, or an argument added to the accepted ones.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void processesToolsAreEnforcedUnderASpaceScope() {
    SpaceScopeResolver processResolver = resolver(ProcessSpaceScopeResolver.NAME, Map.of("5", 12L, "6", 13L));
    SpaceScopeResolver requestResolver = resolver(WorkRequestSpaceScopeResolver.NAME, Map.of("50", 12L, "60", 13L));
    ObjectProvider<SpaceScopeResolver> provider = mock(ObjectProvider.class);
    when(provider.orderedStream()).thenAnswer(invocation -> Stream.of(processResolver, requestResolver));
    McpToolSpaceScopeService service = new McpToolSpaceScopeService(provider);
    ActingIdentity proposal = ActingIdentity.agent("support-desk", "SUPPORT_DESK", Origin.PROPOSAL, 12L);
    ActingIdentity run = ActingIdentity.agent("support-desk", "SUPPORT_DESK", Origin.MENTION, 12L);
    Map<String, SimpleToolDefinition> tools = tools();

    String submit = "{\"process_id\":5,\"title\":\"Leave\",\"description\":\"12-16 Aug\"}";
    assertEquals(submit, service.enforce("submit_work_request", tools.get("submit_work_request"), submit, proposal));
    assertEquals("{\"request_id\":50}",
                 service.enforce("cancel_work_request", tools.get("cancel_work_request"), "{\"request_id\":50}", proposal));
    assertEquals("{\"request_id\":50}",
                 service.enforce("get_request_details", tools.get("get_request_details"), "{\"request_id\":50}", run));
    assertThrows(SpaceScopeRefusedException.class,
                 () -> service.enforce("submit_work_request", tools.get("submit_work_request"), submit, run));
    for (String refused : List.of("{\"process_id\":6,\"title\":\"Leave\"}",
                                  "{\"process_id\":7,\"title\":\"Leave\"}",
                                  "{\"title\":\"Leave\"}",
                                  "{\"process_id\":5,\"title\":\"Leave\",\"project_id\":7}")) {
      assertThrows(refused, SpaceScopeRefusedException.class,
                   () -> service.enforce("submit_work_request", tools.get("submit_work_request"), refused, proposal));
    }
    for (String name : List.of("cancel_work_request", "get_request_details")) {
      ActingIdentity identity = WRITES.contains(name) ? proposal : run;
      for (String refused : List.of("{\"request_id\":60}", "{\"request_id\":70}", "{}", "{\"request_id\":50,\"status\":\"Canceled\"}")) {
        assertThrows(name + " " + refused, SpaceScopeRefusedException.class,
                     () -> service.enforce(name, tools.get(name), refused, identity));
      }
    }
    SimpleToolDefinition list = tools.get("list_processes");
    assertEquals(12L, MAPPER.readTree(service.enforce("list_processes", list, "{}", run)).path("space_id").asLong());
    assertEquals(12L, MAPPER.readTree(service.enforce("list_processes", list, "{\"space_id\":null}", run)).path("space_id").asLong());
    assertEquals("{\"space_id\":12}", service.enforce("list_processes", list, "{\"space_id\":12}", run));
    for (String refused : List.of("{\"space_id\":13}", "{\"space_id\":12,\"status\":\"Request\"}")) {
      assertThrows(refused, SpaceScopeRefusedException.class, () -> service.enforce("list_processes", list, refused, run));
    }
    for (String listing : List.of("get_my_requests", "get_pending_approvals")) {
      assertThrows(listing, SpaceScopeRefusedException.class, () -> service.enforce(listing, tools.get(listing), "{}", run));
    }
  }

  /**
   * @param tool a tool declaring a space scope
   * @param argument the argument that must hold the object id
   * @param resolver the resolver that must tell the object's space
   * @param allowed the other arguments it must accept, schema properties
   */
  private static void assertObject(SimpleToolDefinition tool, String argument, String resolver, List<String> allowed) {
    SpaceScopeDeclaration scope = tool.getSpaceScope();
    assertTrue(tool.getName(), scope.isObjectKind());
    assertEquals(tool.getName(), argument, scope.getObjectArg());
    assertEquals(tool.getName(), resolver, scope.getResolver());
    assertEquals(tool.getName(), allowed, scope.getAllowedArgs() == null ? List.of() : scope.getAllowedArgs());
    JsonNode properties = MAPPER.readTree(tool.getInputSchema()).path("properties");
    assertTrue(tool.getName(), properties.has(argument));
    allowed.forEach(name -> assertTrue(tool.getName() + " takes " + name, properties.has(name)));
  }

  /**
   * @param name the resolver's name
   * @param spaces the space of each object id it knows
   * @return a resolver telling those spaces, none for any other id
   */
  private static SpaceScopeResolver resolver(String name, Map<String, Long> spaces) {
    return new SpaceScopeResolver() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public Long spaceOf(String objectId) {
        return spaces.get(objectId);
      }
    };
  }

  /**
   * @return the shipped processes tools, by name
   */
  private static Map<String, SimpleToolDefinition> tools() {
    try {
      URL classes = ProcessesMcpTool.class.getProtectionDomain().getCodeSource().getLocation();
      URL url = new File(new File(classes.toURI()), "ai-tool-definitions.json").toURI().toURL();
      return McpToolUtils.parseToolDefinitions(url)
                         .stream()
                         .collect(Collectors.toMap(SimpleToolDefinition::getName, Function.identity()));
    } catch (Exception e) {
      throw new IllegalStateException("The processes tool definitions couldn't be read", e);
    }
  }

  /**
   * Each space-scoped write declares in {@code space_role} the role the member
   * who asks a digital worker for it must hold in the call's space
   * (EXO-91405), and no space-scoped read declares one: who may submit a
   * request is the process's own list of requesters, and a request is
   * cancelled by its requester, no space role, so each of them is
   * {@code member}.
   * Mutants: a write's role dropped or changed, or a read given one.
   *
   * @throws Exception when the shipped definitions can't be read
   */
  @Test
  public void spaceScopedWritesDeclareTheAskersSpaceRole() throws Exception {
    Map<String, String> roles = new HashMap<>();
    URL classes = ProcessesMcpTool.class.getProtectionDomain().getCodeSource().getLocation();
    JsonNode definitions = MAPPER.readTree(new File(new File(classes.toURI()), "ai-tool-definitions.json"));
    for (JsonNode tool : definitions.path("tools")) {
      String name = tool.path("name").asString();
      JsonNode scope = tool.path("space_scope");
      if (!scope.isObject()) {
        continue;
      }
      if (tool.path("annotations").path("readOnlyHint").asBoolean(false)) {
        assertFalse(name, scope.has("space_role"));
      } else {
        assertTrue(name, scope.has("space_role"));
        roles.put(name, scope.path("space_role").asString());
        if (scope.path("space_admin").asBoolean(false)) {
          assertEquals(name, "manager", roles.get(name));
        }
      }
    }
    assertEquals(SPACE_ROLES, roles);
  }

}
