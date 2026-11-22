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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.processes.mcp.model.ProcessModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.Utils.RequestFormValues;
import org.exoplatform.processes.mcp.model.PendingApprovalModel;
import org.exoplatform.processes.mcp.model.RequestModel;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFilter;
import org.exoplatform.processes.model.ProcessPermission;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.service.ProcessTranslationService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.attachments.model.Attachment;
import org.exoplatform.services.attachments.service.AttachmentService;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.LocaleConfigService;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.task.dao.TaskQuery;
import org.exoplatform.task.dao.condition.AggregateCondition;
import org.exoplatform.task.dao.condition.PredicateCondition;
import org.exoplatform.task.domain.Task;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.StatusDto;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.service.TaskService;

import io.meeds.social.translation.service.TranslationService;

/**
 * Exercises the real {@code ProcessesMcpTool} (only its collaborators are
 * mocked): the enabled processes filter of its listings; the ACL gate
 * on {@code submitWorkRequest} ({@code WorkFlow.acl.canAddRequest}); the
 * refusal to cancel an already-decided request; and the read/cancel/pending
 * paths' mapping and self-scoping.
 */
public class ProcessesMcpToolTest {

  private ProcessesService processesService;

  private TaskService      taskService;

  private IdentityManager  identityManager;

  private SpaceService     spaceService;

  private ProcessSpaceScopeResolver processSpaceScopeResolver;

  private TranslationService translationService;

  private ProcessTranslationService processTranslationService;

  private AttachmentService attachmentService;

  private ProcessesMcpTool tool;

  @Before
  public void setUp() {
    processesService = mock(ProcessesService.class);
    taskService = mock(TaskService.class);
    identityManager = mock(IdentityManager.class);
    spaceService = mock(SpaceService.class);
    processSpaceScopeResolver = mock(ProcessSpaceScopeResolver.class);
    translationService = mock(TranslationService.class);
    LocaleConfigService localeConfigService = mock(LocaleConfigService.class);
    LocaleConfig defaultLocaleConfig = mock(LocaleConfig.class);
    when(localeConfigService.getDefaultLocaleConfig()).thenReturn(defaultLocaleConfig);
    when(defaultLocaleConfig.getLocale()).thenReturn(Locale.ENGLISH);
    processTranslationService = new ProcessTranslationService(translationService, localeConfigService);
    attachmentService = mock(AttachmentService.class);
    tool = spy(new ProcessesMcpTool(processesService,
                                    taskService,
                                    identityManager,
                                    spaceService,
                                    processSpaceScopeResolver,
                                    processTranslationService,
                                    attachmentService));
    // The user reads the platform in its default language unless a test says otherwise
    doReturn(Locale.ENGLISH).when(tool).getCurrentUserLocale();

    Identity socialIdentity = mock(Identity.class);
    when(socialIdentity.getId()).thenReturn("1");
    when(identityManager.getOrCreateUserIdentity("testuser")).thenReturn(socialIdentity);

    ConversationState.setCurrent(new ConversationState(new org.exoplatform.services.security.Identity("testuser")));
  }

  @After
  public void tearDown() {
    ConversationState.setCurrent(null);
  }

  /**
   * With a space, the listing names only the processes whose project the
   * process resolver puts in that space, across pages of the user's
   * processes; a space the user isn't a member of, or an unknown one, lists
   * none, and no space keeps every process the user sees.
   * Mutants: the space filter, the membership or the unknown-space check
   * removed, the paging stopped after the first page, or the unscoped path
   * filtered too.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  public void listProcessesOfASpaceNamesOnlyThatSpacesProcesses() throws Exception {
    List<WorkFlow> firstPage = new java.util.ArrayList<>();
    for (int i = 1; i <= 200; i++) {
      firstPage.add(workFlow(i, 100 + i));
    }
    List<WorkFlow> secondPage = List.of(workFlow(201, 7));
    when(processesService.getWorkFlows(any(ProcessesFilter.class), eq(0), eq(200), anyLong())).thenReturn(firstPage);
    when(processesService.getWorkFlows(any(ProcessesFilter.class), eq(200), eq(200), anyLong())).thenReturn(secondPage);
    java.util.Map<Long, Long> spaceOfProject = java.util.Map.of(101L, 12L, 102L, 13L, 7L, 12L, 103L, 14L);
    when(processSpaceScopeResolver.spaceOfWorkFlow(any(WorkFlow.class))).thenAnswer(invocation -> spaceOfProject.get(((WorkFlow) invocation.getArgument(0)).getProjectId()));
    // An unknown space is refused by its own check, whatever a membership
    // lookup on a null space would answer.
    when(spaceService.isMember(isNull(Space.class), eq("testuser"))).thenReturn(true);
    Space space = mock(Space.class);
    when(spaceService.getSpaceById(12L)).thenReturn(space);
    when(spaceService.isMember(space, "testuser")).thenReturn(true);
    Space other = mock(Space.class);
    when(spaceService.getSpaceById(13L)).thenReturn(other);
    when(spaceService.isMember(other, "testuser")).thenReturn(false);

    assertEquals(List.of(1L, 201L), tool.listProcesses(12L).stream().map(ProcessModel::id).toList());
    assertTrue(tool.listProcesses(13L).isEmpty());
    assertTrue(tool.listProcesses(14L).isEmpty());
    assertEquals(200, tool.listProcesses(null).size());
    assertEquals(200, tool.listProcesses(0L).size());
  }

  /**
   * A space's listing stops at 200 processes: it keeps the first 200 of the
   * space across pages, even when a page holds more than the cap leaves room
   * for, and never reads the page after the one that fills the cap. A process
   * met again on a later page, as an unordered query may return it, is
   * listed once.
   * Mutants: either cap clause removed (the page loop's or the add's), or the
   * repeat check removed.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  public void listProcessesOfASpaceStopsAtTheCapAndListsAProcessOnce() throws Exception {
    List<WorkFlow> firstPage = new java.util.ArrayList<>();
    for (int i = 1; i <= 200; i++) {
      firstPage.add(workFlow(i, i <= 150 ? i : 10000 + i));
    }
    List<WorkFlow> secondPage = new java.util.ArrayList<>();
    secondPage.add(workFlow(1, 1));
    for (int i = 201; i <= 399; i++) {
      secondPage.add(workFlow(i, i));
    }
    when(processesService.getWorkFlows(any(ProcessesFilter.class), eq(0), eq(200), anyLong())).thenReturn(firstPage);
    when(processesService.getWorkFlows(any(ProcessesFilter.class), eq(200), eq(200), anyLong())).thenReturn(secondPage);
    lenient().when(processesService.getWorkFlows(any(ProcessesFilter.class), eq(400), eq(200), anyLong()))
             .thenReturn(List.of(workFlow(401, 401)));
    when(processSpaceScopeResolver.spaceOfWorkFlow(any(WorkFlow.class))).thenAnswer(invocation -> {
      long projectId = ((WorkFlow) invocation.getArgument(0)).getProjectId();
      return projectId < 10000 ? 12L : 13L;
    });
    Space space = mock(Space.class);
    when(spaceService.getSpaceById(12L)).thenReturn(space);
    when(spaceService.isMember(space, "testuser")).thenReturn(true);

    List<Long> ids = tool.listProcesses(12L).stream().map(ProcessModel::id).toList();

    assertEquals(200, ids.size());
    assertEquals(200, new java.util.HashSet<>(ids).size());
    assertEquals(Long.valueOf(150L), ids.get(149));
    assertEquals(Long.valueOf(250L), ids.get(199));
    verify(processesService, never()).getWorkFlows(any(ProcessesFilter.class), eq(400), eq(200), anyLong());
  }

  /**
   * A user reading the platform in another language than the default one
   * gets each process text in that language where a translation is written,
   * and the stored text otherwise; the refusal of a disabled process names it
   * the same way.
   * Mutants: the listing or the refusal reading the stored title, or a blank
   * translation replacing the stored text.
   *
   * @throws Exception never, the services are mocks
   */
  @Test
  public void listProcessesAndRefusalsNameAProcessInTheUsersLanguage() throws Exception {
    doReturn(Locale.FRENCH).when(tool).getCurrentUserLocale();
    WorkFlow workFlow = workFlow(5, 50);
    workFlow.setTitle("Leave");
    workFlow.setDescription("Ask for leave");
    workFlow.setSummary("<p>Leave rules</p>");
    when(processesService.getWorkFlows(any(ProcessesFilter.class), anyInt(), anyInt(), anyLong())).thenReturn(List.of(workFlow));
    when(translationService.getTranslationLabel("process", "5", "title", Locale.FRENCH)).thenReturn("Congés");
    when(translationService.getTranslationLabel("process", "5", "description", Locale.FRENCH)).thenReturn(" ");
    when(translationService.getTranslationLabel("process", "5", "summary", Locale.FRENCH)).thenReturn("<p>Règles</p>");

    ProcessModel model = tool.listProcesses(null).get(0);

    assertEquals("Congés", model.title());
    assertEquals("Ask for leave", model.description());
    assertEquals("<p>Règles</p>", model.summary());

    workFlow.setEnabled(false);
    when(processesService.getWorkFlow(5L, 1L)).thenReturn(workFlow);
    IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, () -> tool.submitWorkRequest(5L, null, null, null));
    assertTrue(refusal.getMessage().contains("'Congés'"));
  }

  /**
   * @param id a process id
   * @param projectId its project id
   * @return an enabled process of that project
   */
  private static WorkFlow workFlow(long id, long projectId) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(id);
    workFlow.setProjectId(projectId);
    workFlow.setEnabled(true);
    return workFlow;
  }

  @Test
  public void listProcessesListsEnabledProcesses() throws Exception {
    when(processesService.getWorkFlows(any(ProcessesFilter.class),
                                       anyInt(),
                                       anyInt(),
                                       anyLong())).thenReturn(Collections.emptyList());

    tool.listProcesses(null);

    ArgumentCaptor<ProcessesFilter> captor = ArgumentCaptor.forClass(ProcessesFilter.class);
    verify(processesService).getWorkFlows(captor.capture(), anyInt(), anyInt(), anyLong());
    assertEquals(Boolean.TRUE, captor.getValue().getEnabled());
  }

  @Test
  public void getPendingApprovalsListsEnabledProcesses() throws Exception {
    when(processesService.getWorkFlows(any(ProcessesFilter.class),
                                       anyInt(),
                                       anyInt(),
                                       anyLong())).thenReturn(Collections.emptyList());

    tool.getPendingApprovals();

    ArgumentCaptor<ProcessesFilter> captor = ArgumentCaptor.forClass(ProcessesFilter.class);
    verify(processesService).getWorkFlows(captor.capture(), anyInt(), anyInt(), anyLong());
    assertEquals(Boolean.TRUE, captor.getValue().getEnabled());
  }

  @Test
  public void getMyRequestsReturnsMappedRequestsForCurrentUser() throws Exception {
    Work work = new Work();
    work.setId(10L);
    work.setTitle("My request");
    work.setStatus("Request");
    when(processesService.getWorks(eq(1L), any(WorkFilter.class), anyInt(), anyInt())).thenReturn(Collections.singletonList(work));

    List<RequestModel> requests = tool.getMyRequests("Request");

    assertEquals(1, requests.size());
    assertEquals(10L, requests.get(0).id());
    assertEquals("My request", requests.get(0).title());

    ArgumentCaptor<WorkFilter> captor = ArgumentCaptor.forClass(WorkFilter.class);
    verify(processesService).getWorks(eq(1L), captor.capture(), anyInt(), anyInt());
    assertEquals("Request", captor.getValue().getStatus());
  }

  @Test
  public void getMyRequestsLeavesStatusUnsetWhenBlank() throws Exception {
    when(processesService.getWorks(anyLong(), any(WorkFilter.class), anyInt(), anyInt())).thenReturn(Collections.emptyList());

    tool.getMyRequests("  ");

    ArgumentCaptor<WorkFilter> captor = ArgumentCaptor.forClass(WorkFilter.class);
    verify(processesService).getWorks(anyLong(), captor.capture(), anyInt(), anyInt());
    assertNull(captor.getValue().getStatus());
  }

  @Test(expected = IllegalArgumentException.class)
  public void getRequestDetailsRejectsNonPositiveId() throws Exception {
    tool.getRequestDetails(0L);
  }

  @Test(expected = ObjectNotFoundException.class)
  public void getRequestDetailsThrowsWhenRequestNotFound() throws Exception {
    when(processesService.getWorkById(1L, 99L)).thenReturn(null);

    tool.getRequestDetails(99L);
  }

  @Test
  public void getRequestDetailsReturnsMappedRequest() throws Exception {
    Work work = new Work();
    work.setId(7L);
    work.setTitle("Details request");
    when(processesService.getWorkById(1L, 7L)).thenReturn(work);

    RequestModel request = tool.getRequestDetails(7L);

    assertEquals(7L, request.id());
    assertEquals("Details request", request.title());
  }

  @Test(expected = IllegalArgumentException.class)
  public void submitWorkRequestRejectsNonPositiveProcessId() throws Exception {
    tool.submitWorkRequest(0L, "title", "description", null);
  }

  /**
   * The agent's title is optional: a request without one is created, named by
   * the fixed rule only. Mutation: refuse a blank title again and this test
   * fails.
   */
  @Test
  public void submitWorkRequestTakesNoTitle() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(5L);
    workFlow.setEnabled(true);
    workFlow.setAcl(new ProcessPermission(false, false, false, true));
    when(processesService.getWorkFlow(5L, 1L)).thenReturn(workFlow);
    Work created = new Work();
    created.setId(12L);
    when(processesService.createWork(any(Work.class), eq(1L), any())).thenReturn(created);

    assertEquals(12L, tool.submitWorkRequest(5L, null, "description", null).id());

    verify(processesService).createWork(any(Work.class), eq(1L), isNull());
  }

  @Test(expected = ObjectNotFoundException.class)
  public void submitWorkRequestThrowsWhenProcessNotFound() throws Exception {
    when(processesService.getWorkFlow(5L, 1L)).thenReturn(null);

    tool.submitWorkRequest(5L, "title", "description", null);
  }

  /**
   * A process the user can't see is refused in the tool's own words, naming
   * the process by its id, not by the Service's message. Mutation: let the
   * Service's exception through and this test fails on the message.
   */
  @Test
  public void submitWorkRequestRefusesAProcessTheUserCannotSee() throws Exception {
    when(processesService.getWorkFlow(5L, 1L)).thenThrow(new IllegalAccessException("User 1 can't see the process 5"));

    IllegalAccessException exception = assertThrows(IllegalAccessException.class,
                                                    () -> tool.submitWorkRequest(5L, "title", "description", null));

    assertEquals("You are not allowed to submit a request under the process 5. Call list_processes to see the process types "
        + "available to you.", exception.getMessage());
    verify(processesService, never()).createWork(any(Work.class), anyLong(), any());
  }

  @Test(expected = IllegalArgumentException.class)
  public void submitWorkRequestRejectsDisabledProcess() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(5L);
    workFlow.setTitle("Disabled process");
    workFlow.setEnabled(false);
    when(processesService.getWorkFlow(5L, 1L)).thenReturn(workFlow);

    tool.submitWorkRequest(5L, "title", "description", null);
  }

  @Test
  public void submitWorkRequestCreatesWorkUnderProcessProject() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(5L);
    workFlow.setEnabled(true);
    workFlow.setProjectId(42L);
    workFlow.setAcl(new ProcessPermission(false, false, false, true));
    when(processesService.getWorkFlow(5L, 1L)).thenReturn(workFlow);

    Work created = new Work();
    created.setId(11L);
    created.setTitle("New request");
    when(processesService.createWork(any(Work.class), eq(1L), any())).thenReturn(created);

    RequestModel request = tool.submitWorkRequest(5L, "New request", null, null);

    assertEquals(11L, request.id());
    ArgumentCaptor<Work> captor = ArgumentCaptor.forClass(Work.class);
    // The agent's title goes as the agent title, never as the work's own
    // title, which the Service ignores
    verify(processesService).createWork(captor.capture(), eq(1L), eq("New request"));
    verify(processesService, never()).createWork(any(Work.class), anyLong());
    assertNull(captor.getValue().getTitle());
    assertEquals("the request names its process, which decides its project", 5L, captor.getValue().getWorkFlowId());
    assertEquals("", captor.getValue().getDescription());
  }

  /**
   * A process with documents to fill is refused before any request is
   * created, its documents read for the requester, and a process without any
   * is submitted. Mutation: drop the check, or create the request
   * before it, and this test fails.
   *
   * @throws Exception on an unexpected refusal
   */
  @Test
  public void submitWorkRequestRefusesAProcessWithDocumentsToFill() throws Exception {
    WorkFlow workFlow = workFlow(5L, 42L);
    workFlow.setAcl(new ProcessPermission(false, false, false, true));
    when(processesService.getWorkFlow(5L, 1L)).thenReturn(workFlow);
    when(attachmentService.getAttachmentsByEntity(1L, 5L, "workflow")).thenReturn(List.of(new Attachment()));

    assertEquals(ProcessesMcpTool.DOCUMENT_TO_FILL,
                 assertThrows(IllegalArgumentException.class, () -> tool.submitWorkRequest(5L, null, "details", null)).getMessage());
    verify(processesService, never()).createWork(any(Work.class), anyLong(), any());

    when(attachmentService.getAttachmentsByEntity(1L, 5L, "workflow")).thenReturn(List.of());
    when(processesService.createWork(any(Work.class), eq(1L), any())).thenReturn(new Work());

    tool.submitWorkRequest(5L, null, "details", null);

    verify(processesService).createWork(any(Work.class), eq(1L), isNull());
  }

  /**
   * A listed process carries the link that opens its request form in the
   * Process app, where a process with documents to fill is submitted.
   * Mutation: leave the link out and this test fails.
   *
   * @throws Exception never
   */
  @Test
  public void aListedProcessCarriesItsRequestUrl() throws Exception {
    when(processesService.getWorkFlows(any(ProcessesFilter.class), anyInt(), anyInt(), anyLong())).thenReturn(List.of(workFlow(5L, 42L)));
    doReturn("http://localhost:8080/portal/dw/processes/5/createRequest").when(tool).requestUrl(5L);

    assertEquals("http://localhost:8080/portal/dw/processes/5/createRequest", tool.listProcesses(null).get(0).requestUrl());
  }

  /**
   * The link opens the Process app's create-request route on the site the
   * platform names, its main site for the assistant's calls, and is null when
   * no site is known rather than naming a null site. Mutation: build the link
   * whatever the site, and this test fails.
   */
  @Test
  public void theRequestUrlOpensTheCreateRequestRouteOnAKnownSiteOnly() {
    try (MockedStatic<CommonsUtils> commonsUtils = mockStatic(CommonsUtils.class);
         MockedStatic<PortalContainer> portalContainer = mockStatic(PortalContainer.class)) {
      commonsUtils.when(CommonsUtils::getCurrentDomain).thenReturn("http://localhost:8080");
      portalContainer.when(PortalContainer::getCurrentPortalContainerName).thenReturn("portal");
      commonsUtils.when(CommonsUtils::getCurrentPortalOwner).thenReturn("dw");

      assertEquals("http://localhost:8080/portal/dw/processes/5/createRequest", tool.requestUrl(5L));

      commonsUtils.when(CommonsUtils::getCurrentPortalOwner).thenReturn(null);

      assertNull(tool.requestUrl(5L));
    }
  }

  /**
   * The refusal of a process with documents to fill reads, in the chat, as
   * its reason: the chat names a coded failure by the text of
   * AiAgentChat.answer.refused. followed by its code, which processes ships in
   * its own bundle of the chat's name, and by the step's name alone when no
   * bundle has it. The text holds its reason, no code and no final stop, the
   * chat adding its own. Mutation: the key removed or renamed, or the code
   * changed without it, and this test fails.
   *
   * @throws Exception when the bundle cannot be read
   */
  @Test
  public void theDocumentToFillRefusalHasItsReasonInTheChatBundle() throws Exception {
    Properties bundle = new Properties();
    try (InputStream input = Files.newInputStream(Path.of("..", "processes-webapp", "src", "main", "resources", "locale", "portlet",
                                                          "AiAgentChat_en.properties"))) {
      bundle.load(new InputStreamReader(input, StandardCharsets.UTF_8));
    }
    String reason = bundle.getProperty("AiAgentChat.answer.refused." + ProcessesMcpTool.DOCUMENT_TO_FILL);

    assertTrue(String.valueOf(reason), reason != null && reason.contains("Process app") && !reason.contains("processes.")
        && !reason.endsWith("."));
  }

  /**
   * The agent's form values reach createWork as sent, a field sent null and a
   * null inside a field's values kept, so that Forms refuses them
   * (forms.value.nullNotAllowed) rather than processes dropping them, and the
   * refusal reaches the agent with its code and field key (spec 55633 §3.3,
   * P16). Mutation: drop the null entries in the copy, or catch the refusal,
   * and this test fails.
   */
  @Test
  public void submitWorkRequestPassesTheFormValuesWithTheirNulls() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(5L);
    workFlow.setEnabled(true);
    workFlow.setAcl(new ProcessPermission(false, false, false, true));
    when(processesService.getWorkFlow(5L, 1L)).thenReturn(workFlow);
    when(processesService.createWork(any(Work.class), eq(1L), any()))
        .thenThrow(new IllegalArgumentException("forms.value.nullNotAllowed:reason"));
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("amount", List.of(150));
    fields.put("reason", null);
    fields.put("note", Arrays.asList((Object) null));
    Map<String, Object> formValues = new LinkedHashMap<>();
    formValues.put("12", fields);

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> tool.submitWorkRequest(5L, null, "description", formValues));

    assertEquals("forms.value.nullNotAllowed:reason", exception.getMessage());
    ArgumentCaptor<Work> captor = ArgumentCaptor.forClass(Work.class);
    verify(processesService).createWork(captor.capture(), eq(1L), isNull());
    Map<String, List<Object>> sent = captor.getValue().getFormValues().get(12L);
    assertEquals(List.of("amount", "reason", "note"), List.copyOf(sent.keySet()));
    assertEquals(1, sent.get("amount").size());
    assertNull(sent.get("reason"));
    assertEquals(Arrays.asList((Object) null), sent.get("note"));
  }

  /**
   * Form values that are not form id → field key → array are refused before
   * any request is created; no form values leave the work's map unset.
   * Mutation: skip the parse, or set an empty map when none is sent, and this
   * test fails.
   */
  @Test
  public void submitWorkRequestRefusesUnreadableFormValues() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(5L);
    workFlow.setEnabled(true);
    workFlow.setAcl(new ProcessPermission(false, false, false, true));
    when(processesService.getWorkFlow(5L, 1L)).thenReturn(workFlow);
    Work created = new Work();
    created.setId(12L);
    when(processesService.createWork(any(Work.class), eq(1L), any())).thenReturn(created);

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> tool.submitWorkRequest(5L, null, null, Map.of("form", Map.of())));

    assertEquals(RequestFormValues.FORM_VALUES_INVALID, exception.getMessage());
    verify(processesService, never()).createWork(any(Work.class), anyLong(), any());

    tool.submitWorkRequest(5L, null, null, null);
    ArgumentCaptor<Work> captor = ArgumentCaptor.forClass(Work.class);
    verify(processesService).createWork(captor.capture(), eq(1L), isNull());
    assertNull(captor.getValue().getFormValues());
  }

  @Test
  public void submitWorkRequestRejectsProcessWithoutAddRequestAcl() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(5L);
    workFlow.setTitle("Restricted process");
    workFlow.setEnabled(true);
    workFlow.setProjectId(42L);
    workFlow.setAcl(new ProcessPermission(true, false, false, false));
    when(processesService.getWorkFlow(5L, 1L)).thenReturn(workFlow);

    assertThrows(IllegalAccessException.class, () -> tool.submitWorkRequest(5L, "title", "description", null));

    verify(processesService, never()).createWork(any(Work.class), anyLong(), any());
  }

  @Test
  public void submitWorkRequestRejectsProcessWithNoAcl() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(5L);
    workFlow.setTitle("No-acl process");
    workFlow.setEnabled(true);
    workFlow.setProjectId(42L);
    when(processesService.getWorkFlow(5L, 1L)).thenReturn(workFlow);

    assertThrows(IllegalAccessException.class, () -> tool.submitWorkRequest(5L, "title", "description", null));

    verify(processesService, never()).createWork(any(Work.class), anyLong(), any());
  }

  @Test(expected = IllegalArgumentException.class)
  public void cancelWorkRequestRejectsNonPositiveId() throws Exception {
    tool.cancelWorkRequest(0L);
  }

  @Test(expected = ObjectNotFoundException.class)
  public void cancelWorkRequestThrowsWhenRequestNotFound() throws Exception {
    when(processesService.getWorkById(1L, 99L)).thenReturn(null);

    tool.cancelWorkRequest(99L);
  }

  /**
   * A request its requester already cancelled is answered as it is, whatever
   * its column. Mutation: cancel it again and the service is called.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  public void cancelWorkRequestReturnsAsIsWhenAlreadyCanceled() throws Exception {
    Work work = new Work();
    work.setId(3L);
    work.setStatus("Any column");
    work.setCompleted(true);
    work.setCanceled(true);
    when(processesService.getWorkById(1L, 3L)).thenReturn(work);

    assertTrue(tool.cancelWorkRequest(3L).canceled());

    verify(processesService, never()).cancelWork(anyLong(), anyLong());
    verify(processesService, never()).updateWork(any(Work.class), anyLong());
  }

  /**
   * A pending request is cancelled through the service's cancel, its column
   * left as it is: no column name is written (EXO-94923). Mutation: write a
   * « Canceled » column through updateWork again and the never() fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  public void cancelWorkRequestCancelsPendingRequest() throws Exception {
    Work work = new Work();
    work.setId(3L);
    work.setStatus("New requests");
    work.setCompleted(false);
    when(processesService.getWorkById(1L, 3L)).thenReturn(work);
    Work canceled = new Work();
    canceled.setId(3L);
    canceled.setStatus("New requests");
    canceled.setCompleted(true);
    canceled.setCanceled(true);
    when(processesService.cancelWork(3L, 1L)).thenReturn(canceled);

    RequestModel result = tool.cancelWorkRequest(3L);

    assertEquals("New requests", result.status());
    assertTrue(result.completed());
    assertTrue(result.canceled());
    verify(processesService, never()).updateWork(any(Work.class), anyLong());
  }

  /**
   * A closed request, decided by a manager, is refused whatever its column is
   * named. Mutation: read the column name again and a request closed in a
   * column named otherwise is cancelled.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  public void cancelWorkRequestRejectsAClosedRequest() throws Exception {
    Work work = new Work();
    work.setId(3L);
    work.setStatus("Approved by HR");
    work.setCompleted(true);
    when(processesService.getWorkById(1L, 3L)).thenReturn(work);

    assertThrows(IllegalArgumentException.class, () -> tool.cancelWorkRequest(3L));

    verify(processesService, never()).cancelWork(anyLong(), anyLong());
  }

  @Test
  public void getPendingApprovalsReturnsEmptyWhenNoManagedProcess() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setProjectId(5L);
    workFlow.setCanShowPending(false);
    when(processesService.getWorkFlows(any(ProcessesFilter.class), anyInt(), anyInt(), anyLong())).thenReturn(Collections.singletonList(workFlow));

    List<PendingApprovalModel> pending = tool.getPendingApprovals();

    assertTrue(pending.isEmpty());
    verify(taskService, never()).findTasks(any(TaskQuery.class), anyInt(), anyInt());
  }

  /**
   * The pending requests are the tasks of the shown processes' requests only,
   * not completed, read in one query that names no column (EXO-94923): the
   * task query is restricted to their request records, and each request is
   * named by the process its record names, even when two processes share its
   * project. Mutation: drop the restriction, restrict it to another list, name
   * the request by its project, or query by a column name again, and this
   * test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  @SuppressWarnings("unchecked")
  public void getPendingApprovalsListsOnlyTheRequestsOfTheShownProcesses() throws Exception {
    WorkFlow onboarding = workFlow(7L, 5L);
    onboarding.setTitle("Onboarding");
    onboarding.setCanShowPending(true);
    WorkFlow leave = workFlow(8L, 5L);
    leave.setTitle("Leave");
    leave.setCanShowPending(true);
    WorkFlow hidden = workFlow(9L, 5L);
    hidden.setTitle("Hidden");
    hidden.setCanShowPending(false);
    when(processesService.getWorkFlows(any(ProcessesFilter.class), anyInt(), anyInt(), anyLong())).thenReturn(List.of(onboarding,
                                                                                                                       leave,
                                                                                                                       hidden));
    TaskDto onboardingRequest = pendingTask(21L, 5L);
    TaskDto leaveRequest = pendingTask(22L, 5L);
    when(taskService.findTasks(any(TaskQuery.class), anyInt(), anyInt())).thenReturn(List.of(onboardingRequest, leaveRequest));
    when(processesService.getRequestWorkFlowIds(List.of(21L, 22L))).thenReturn(Map.of(21L, 7L, 22L, 8L));

    List<PendingApprovalModel> pending = tool.getPendingApprovals();

    assertEquals(2, pending.size());
    assertEquals("Onboarding", pending.get(0).processTitle());
    assertEquals("Leave", pending.get(1).processTitle());
    assertEquals(5L, pending.get(0).projectId());

    ArgumentCaptor<TaskQuery> query = ArgumentCaptor.forClass(TaskQuery.class);
    verify(taskService, times(1)).findTasks(query.capture(), anyInt(), anyInt());
    java.util.List<String> fields = new java.util.ArrayList<>();
    collectFields(query.getValue().getCondition(), fields);
    assertTrue("the pending requests are the open tasks: " + fields, fields.contains("completed"));
    assertTrue("no column name is read: " + fields, fields.stream().noneMatch(field -> field.startsWith("status")));
    PredicateCondition<Task> restriction = ((AggregateCondition) query.getAllValues().get(0).getCondition()).getConditions()
                                                                                                    .stream()
                                                                                                    .filter(PredicateCondition.class::isInstance)
                                                                                                    .map(condition -> (PredicateCondition<Task>) condition)
                                                                                                    .findFirst()
                                                                                                    .orElseThrow();
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    CriteriaQuery<?> criteria = mock(CriteriaQuery.class);
    Root<Task> root = mock(Root.class);
    jakarta.persistence.criteria.Path<Object> id = mock(jakarta.persistence.criteria.Path.class);
    when(root.get("id")).thenReturn(id);
    restriction.getBuilder().build(cb, criteria, root);
    verify(processesService).requestOf(eq(cb), eq(criteria), eq((jakarta.persistence.criteria.Path) id), eq(List.of(7L, 8L)));
  }

  /**
   * Collects the fields of a Task query's single conditions, its aggregates
   * walked.
   *
   * @param condition the condition
   * @param fields the fields, appended
   */
  private static void collectFields(org.exoplatform.task.dao.condition.Condition condition, java.util.List<String> fields) {
    if (condition instanceof AggregateCondition aggregate) {
      aggregate.getConditions().forEach(child -> collectFields(child, fields));
    } else if (condition instanceof org.exoplatform.task.dao.condition.SingleCondition<?> single) {
      fields.add(single.getField());
    }
  }

  /**
   * @param id the task id
   * @param projectId its project id
   * @return a pending request task
   */
  private static TaskDto pendingTask(long id, long projectId) {
    ProjectDto project = new ProjectDto();
    project.setId(projectId);
    StatusDto status = new StatusDto();
    status.setName("To Do");
    status.setProject(project);
    TaskDto task = new TaskDto();
    task.setId(id);
    task.setTitle("Pending task " + id);
    task.setStatus(status);
    task.setCreatedBy("john");
    return task;
  }

}
