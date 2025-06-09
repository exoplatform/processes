package org.exoplatform.processes.rest;

import static org.exoplatform.processes.Utils.ProcessesUtils.isProcessAdmin;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import java.util.*;

import javax.jcr.ItemExistsException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.RuntimeDelegate;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.model.*;
import org.exoplatform.processes.rest.model.WorkEntity;
import org.exoplatform.processes.rest.model.WorkFlowEntity;
import org.exoplatform.processes.rest.util.RestUtils;
import org.exoplatform.processes.service.ProcessAttachmentService;
import org.exoplatform.processes.service.ProcessService;
import org.exoplatform.processes.service.RequestService;
import org.exoplatform.services.attachments.model.Attachment;
import org.exoplatform.services.attachments.rest.model.AttachmentEntity;
import org.exoplatform.services.rest.impl.RuntimeDelegateImpl;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ProcessRestTest {

  private static final MockedStatic<CommonsUtils>                                             COMMONS_UTILS             =
                                                                                                            mockStatic(CommonsUtils.class);

  private static final MockedStatic<RestUtils>                                                REST_UTILS                =
                                                                                                         mockStatic(RestUtils.class);

  private static final MockedStatic<ProcessesUtils>                                           PROCESS_UTILS             =
                                                                                                            mockStatic(ProcessesUtils.class);

  private static final MockedStatic<EntityBuilder>                                            ENTITY_BUILDER            =
                                                                                                             mockStatic(EntityBuilder.class);

  private static final MockedStatic<org.exoplatform.services.attachments.utils.EntityBuilder> ATTACHMENT_ENTITY_BUILDER =
                                                                                                                        mockStatic(org.exoplatform.services.attachments.utils.EntityBuilder.class);

  private static final MockedStatic<ConversationState>                                        CONVERSATION_STATE        =
                                                                                                                 mockStatic(ConversationState.class);

  private final String                                                                        userName                  =
                                                                                                       "testuser";

  @Mock
  private IdentityManager                                                                     identityManager;

  @Mock
  private ProcessService                                                                      processService;

  @Mock
  private RequestService                                                                      requestService;

  @Mock
  private ProcessAttachmentService                                                            processAttachmentService;

  private ProcessRest                                                                         processRest;

  private AttachmentRest                                                                      attachmentRest;

  private RequestRest                                                                         requestRest;

  private DraftRest                                                                           draftRest;

  @Mock
  private Identity                                                                            identity;

  @Mock
  private UserACL                                                                             userAcl;

  @AfterClass
  public static void afterRunBare() { // NOSONAR
    COMMONS_UTILS.close();
    REST_UTILS.close();
    ENTITY_BUILDER.close();
    ATTACHMENT_ENTITY_BUILDER.close();
    CONVERSATION_STATE.close();
    PROCESS_UTILS.close();
  }

  @Before
  public void setUp() {
    RuntimeDelegate.setInstance(new RuntimeDelegateImpl());
    this.processRest = new ProcessRest(processService);
    this.requestRest = new RequestRest(processService, requestService);
    this.draftRest = new DraftRest(requestService);
    this.attachmentRest = new AttachmentRest(processService, identityManager, processAttachmentService);
    ConversationState conversationState = mock(ConversationState.class);
    CONVERSATION_STATE.when(ConversationState::getCurrent).thenReturn(conversationState);
  }

  @Test
  public void getWorkFlows() {
    List<WorkFlow> workFlows = new ArrayList<>();
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(1L);
    workFlow.setProjectId(1L);
    workFlows.add(workFlow);
    List<WorkFlowEntity> workFlowEntities = new ArrayList<>();
    ProcessesFilter processesFilter = new ProcessesFilter();
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    Response response1 = processRest.getWorkFlows(userName, null, null, null, null, 0, 10);
    assertEquals(response1.getStatus(), Response.Status.UNAUTHORIZED.getStatusCode());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    when(processService.getWorkFlows(processesFilter, 0, 10, userName)).thenReturn(workFlows);
    ENTITY_BUILDER.when(() -> EntityBuilder.toRestEntities(workFlows, null)).thenReturn(workFlowEntities);
    Response response2 = processRest.getWorkFlows(userName, true, null, "test", null, 0, 10);
    assertEquals(response2.getStatus(), Response.Status.OK.getStatusCode());
  }

  @Test
  public void isProcessesManager() {
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    Response response1 = processRest.isProcessesManager();
    assertEquals(response1.getStatus(), Response.Status.UNAUTHORIZED.getStatusCode());
    PROCESS_UTILS.when(() -> isProcessAdmin(identity)).thenReturn(true);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    Response response2 = processRest.isProcessesManager();
    assertEquals(response2.getStatus(), Response.Status.OK.getStatusCode());
    assertEquals("true", response2.getEntity());
    PROCESS_UTILS.when(() -> isProcessAdmin(identity)).thenReturn(true);
    Response response3 = processRest.isProcessesManager();
    assertEquals(response3.getStatus(), Response.Status.OK.getStatusCode());
  }

  @Test
  public void deleteWorkflow() throws IllegalAccessException, ObjectNotFoundException {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(1L);
    workFlow.setProjectId(1L);
    Response response = processRest.deleteWorkflow(null);
    assertEquals(response.getStatus(), Response.Status.BAD_REQUEST.getStatusCode());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    Response response1 = processRest.deleteWorkflow(1L);
    assertEquals(response1.getStatus(), Response.Status.UNAUTHORIZED.getStatusCode());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    doNothing().when(processService).deleteWorkflowById(1L, userName);
    Response response4 = processRest.deleteWorkflow(1L);
    assertEquals(response4.getStatus(), Response.Status.OK.getStatusCode());
    doThrow(new ObjectNotFoundException("Workflow does not exist")).when(processService).deleteWorkflowById(1L, userName);
    Response response5 = processRest.deleteWorkflow(1L);
    assertEquals(response5.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void updateWorkflow() throws ObjectNotFoundException, IllegalAccessException {
    WorkFlow workFlow = mock(WorkFlow.class);
    WorkFlowEntity workFlowEntity = mock(WorkFlowEntity.class);
    Response response1 = processRest.updateWorkFlow(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    Response response2 = processRest.updateWorkFlow(workFlowEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response2.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    ENTITY_BUILDER.when(() -> EntityBuilder.fromEntity(workFlowEntity)).thenReturn(workFlow);
    when(processService.updateWorkFlow(workFlow, userName)).thenReturn(workFlow);
    Response response3 = processRest.updateWorkFlow(workFlowEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());

    when(processService.updateWorkFlow(workFlow, userName)).thenThrow(ObjectNotFoundException.class);
    Response response4 = processRest.updateWorkFlow(workFlowEntity);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response4.getStatus());
  }

  @Test
  public void shouldReturnUnauthorizedErrorWhenUpdateWorkflow() throws ObjectNotFoundException, IllegalAccessException {
    WorkFlow workFlow = new WorkFlow();
    WorkFlowEntity workFlowEntity = new WorkFlowEntity();
    Date createdDate = new Date();
    Date modifiedDate = new Date();
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    ENTITY_BUILDER.when(() -> EntityBuilder.fromEntity(workFlowEntity)).thenReturn(workFlow);
    when(processService.updateWorkFlow(workFlow, userName)).thenThrow(IllegalAccessException.class);
    Response response = processRest.updateWorkFlow(workFlowEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    WorkFlowEntity workFlowEntity1 = new WorkFlowEntity();
    workFlowEntity1.setId(1L);
    workFlowEntity1.setTitle("workFlow");
    workFlowEntity1.setCreatorId(1);
    workFlowEntity1.setSummary("workFlow summary");
    workFlowEntity1.setModifierId(1);
    workFlowEntity1.setTitle("workFlow");
    workFlowEntity1.setTitle("workFlow");
    workFlowEntity1.setEnabled(true);
    workFlowEntity1.setDescription("test");
    Response response1 = processRest.updateWorkFlow(workFlowEntity1);
    WorkFlowEntity workFlowEntity2 = new WorkFlowEntity(1,
                                                        "title",
                                                        "description",
                                                        "summary",
                                                        "image",
                                                        "helpLink",
                                                        true,
                                                        1,
                                                        createdDate,
                                                        1,
                                                        modifiedDate,
                                                        1,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        false,
                                                        null,
                                                        null);
  }

  @Test
  public void shouldReturnServerErrorWhenUpdateWorkflow() throws Exception {
    WorkFlow workFlow = mock(WorkFlow.class);
    WorkFlowEntity workFlowEntity = mock(WorkFlowEntity.class);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    ENTITY_BUILDER.when(() -> EntityBuilder.fromEntity(workFlowEntity)).thenReturn(workFlow);
    when(processService.updateWorkFlow(workFlow, userName)).thenReturn(workFlow);
    Response response = processRest.updateWorkFlow(workFlowEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  public void createWorkflow() throws IllegalAccessException {
    WorkFlow workFlow = mock(WorkFlow.class);
    WorkFlowEntity workFlowEntity = mock(WorkFlowEntity.class);
    Response response1 = processRest.createWorkFlow(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    Response response2 = processRest.createWorkFlow(workFlowEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response2.getStatus());
    ENTITY_BUILDER.when(() -> EntityBuilder.fromEntity(workFlowEntity)).thenReturn(workFlow);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    when(processService.createWorkFlow(workFlow, userName)).thenReturn(workFlow);
    Response response3 = processRest.createWorkFlow(workFlowEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());
    when(processService.createWorkFlow(workFlow, userName)).thenThrow(IllegalAccessException.class);
    Response response4 = processRest.createWorkFlow(workFlowEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response4.getStatus());
  }

  @Test
  public void shouldReturnServerErrorWhenCreateWorkflow() throws Exception {
    WorkFlow workFlow = mock(WorkFlow.class);
    WorkFlowEntity workFlowEntity = mock(WorkFlowEntity.class);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    ENTITY_BUILDER.when(() -> EntityBuilder.fromEntity(workFlowEntity)).thenReturn(workFlow);
    when(processService.createWorkFlow(workFlow, userName)).thenReturn(workFlow);
    Response response = processRest.createWorkFlow(workFlowEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  public void getWorks() {
    List<Work> works = new ArrayList<>();
    WorkFilter workFilter = new WorkFilter();
    workFilter.setStatus("ToDo");
    workFilter.setQuery("test");
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    Response response1 = requestRest.getWorks("user2", "", false, "ToDo", "test", 0, 10);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response1.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    when(requestService.getWorks(userName, workFilter, 0, 10)).thenReturn(works);
    Response response2 = requestRest.getWorks(null, "", false, "ToDo", "test", 0, 10);
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());
  }

  @Test
  public void createWork() throws IllegalAccessException, ObjectNotFoundException {
    WorkEntity workEntity = new WorkEntity();
    Work work = mock(Work.class);
    WorkFlowEntity workFlowEntity = new WorkFlowEntity();
    Response response1 = requestRest.createWork(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    workEntity.setProjectId(0L);
    workFlowEntity.setProjectId(0L);
    workEntity.setWorkFlow(workFlowEntity);
    Response response2 = requestRest.createWork(workEntity);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response2.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    workEntity.setProjectId(1L);
    workEntity.getWorkFlow().setProjectId(1L);
    workEntity.getWorkFlow().setEnabled(false);
    workEntity.setCompleted(true);
    Response response6 = requestRest.createWork(workEntity);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response6.getStatus());
    workEntity.getWorkFlow().setEnabled(true);
    Response response3 = requestRest.createWork(workEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response3.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWork(processService, workEntity)).thenReturn(work);
    when(requestService.createWork(work, userName)).thenReturn(work);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWorkEntity(processService, work, "")).thenReturn(workEntity);
    Response response4 = requestRest.createWork(workEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response4.getStatus());
    when(requestService.createWork(work, userName)).thenThrow(IllegalAccessException.class);
    Response response5 = requestRest.createWork(workEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response5.getStatus());
  }

  @Test
  public void shouldReturnServerErrorWhenCreateWork() throws Exception {
    WorkEntity workEntity = new WorkEntity();
    Work work = mock(Work.class);
    WorkFlowEntity workFlowEntity = new WorkFlowEntity();
    workFlowEntity.setCanShowPending(true);
    workFlowEntity.setParentSpace(null);
    workEntity.setWorkFlow(workFlowEntity);
    workEntity.setProjectId(1L);
    workEntity.getWorkFlow().setProjectId(1L);
    workEntity.getWorkFlow().setEnabled(true);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWork(processService, workEntity)).thenReturn(work);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWorkEntity(processService, work, "")).thenReturn(workEntity);
    when(requestService.createWork(work, userName)).thenReturn(work);
    Response response5 = requestRest.createWork(workEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response5.getStatus());
  }

  @Test
  public void updateWork() throws ObjectNotFoundException, IllegalAccessException {
    WorkEntity workEntity = new WorkEntity();
    Work work = mock(Work.class);
    Response response1 = requestRest.updateWork(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    Response response3 = requestRest.updateWork(workEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response3.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWork(processService, workEntity)).thenReturn(work);
    when(requestService.updateWork(work, userName)).thenReturn(work);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWorkEntity(processService, work, "")).thenReturn(workEntity);
    Response response4 = requestRest.updateWork(workEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response4.getStatus());
    when(requestService.updateWork(work, userName)).thenThrow(ObjectNotFoundException.class);
    Response response5 = requestRest.updateWork(workEntity);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response5.getStatus());
  }

  @Test
  public void shouldReturnUnauthorizedErrorWWhenUpdateWork() throws Exception {
    WorkEntity workEntity = new WorkEntity();
    Work work = mock(Work.class);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWork(processService, workEntity)).thenReturn(work);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWorkEntity(processService, work, "")).thenReturn(workEntity);
    when(requestService.updateWork(work, userName)).thenThrow(IllegalAccessException.class);
    Response response6 = requestRest.updateWork(workEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response6.getStatus());
    when(identity.getUserId()).thenReturn(userName);
    when(requestService.updateWork(any(), anyString())).thenReturn(work);
    Response response7 = requestRest.updateWork(workEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response7.getStatus());
  }

  @Test
  public void shouldReturnServerErrorWhenUpdateWork() throws Exception {
    WorkEntity workEntity = new WorkEntity();
    Work work = mock(Work.class);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWork(processService, workEntity)).thenReturn(work);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWorkEntity(processService, work, "")).thenReturn(workEntity);
    when(requestService.updateWork(work, userName)).thenReturn(work);
    Response response6 = requestRest.updateWork(workEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response6.getStatus());
  }

  @Test
  public void countWorksByWorkflow() throws Exception {
    WorkFlow workFlow = mock(WorkFlow.class);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    Response response = processRest.countWorksByWorkflow(null, null);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    Response response1 = processRest.countWorksByWorkflow(null, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    when(processService.getWorkFlowByProjectId(1L, userName)).thenReturn(workFlow);
    when(processService.countWorksByWorkflow(1L, userName, false)).thenReturn(2);
    Response response3 = processRest.countWorksByWorkflow(1L, false);
    assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());
  }

  @Test
  public void deleteWorkById() throws ObjectNotFoundException, IllegalAccessException {
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    Response response = requestRest.deleteWork(1L);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    Response response1 = requestRest.deleteWork(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    Response response2 = requestRest.deleteWork(1L);
    verify(requestService, times(1)).deleteWorkById(1L, userName);
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());
  }

  @Test
  public void createWorkDraft() throws IllegalAccessException {
    WorkEntity WorkEntity = new WorkEntity();
    WorkFlowEntity workFlowEntity = new WorkFlowEntity();
    workFlowEntity.setId(1L);
    WorkEntity.setWorkFlow(workFlowEntity);
    Work work = mock(Work.class);
    ENTITY_BUILDER.when(() -> EntityBuilder.fromEntity(WorkEntity)).thenReturn(work);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    Response response = draftRest.createWorkDraft(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    Response response1 = draftRest.createWorkDraft(WorkEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response1.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    when(requestService.createWorkDraft(work, userName)).thenReturn(work);
    Response response2 = draftRest.createWorkDraft(WorkEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());
    doThrow(new IllegalAccessException()).when(requestService).createWorkDraft(work, userName);
    Response response5 = draftRest.createWorkDraft(WorkEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response5.getStatus());
  }

  @Test
  public void getWorkDrafts() {
    List<Work> works = new ArrayList<>();
    WorkFilter workFilter = new WorkFilter();
    workFilter.setIsDraft(true);
    workFilter.setQuery("test");
    List<WorkEntity> WorkEntityList = new ArrayList<>();
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    Response response = draftRest.getWorkDrafts("", "test", 0, 10);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    when(requestService.getWorkDrafts(userName, workFilter, 0, 10)).thenReturn(works);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWorkEntityList(works)).thenReturn(WorkEntityList);
    Response response1 = draftRest.getWorkDrafts("", "test", 0, 10);
    assertEquals(Response.Status.OK.getStatusCode(), response1.getStatus());
  }

  @Test
  public void updateWorkDraft() throws ObjectNotFoundException, IllegalAccessException {
    WorkEntity WorkEntity = new WorkEntity();
    Work work = mock(Work.class);
    when(work.getId()).thenReturn(1L);
    when(work.getDraftId()).thenReturn(1L);
    ENTITY_BUILDER.when(() -> EntityBuilder.fromEntity(WorkEntity)).thenReturn(work);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    Response response = draftRest.updateWorkDraft(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    Response response1 = draftRest.updateWorkDraft(WorkEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response1.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    ENTITY_BUILDER.when(() -> EntityBuilder.fromEntity(WorkEntity)).thenReturn(work);
    ENTITY_BUILDER.when(() -> EntityBuilder.toEntity(work)).thenReturn(WorkEntity);
    when(requestService.updateWorkDraft(work, userName)).thenReturn(work);
    Response response3 = draftRest.updateWorkDraft(WorkEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());
    when(identity.getUserId()).thenReturn(userName);
    doThrow(new ObjectNotFoundException("oldWorkDraft is not exist")).when(requestService).updateWorkDraft(work, userName);
    Response response4 = draftRest.updateWorkDraft(WorkEntity);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response4.getStatus());
    doThrow(new IllegalAccessException()).when(requestService).updateWorkDraft(work, userName);
    Response response5 = draftRest.updateWorkDraft(WorkEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response5.getStatus());
  }

  @Test
  public void deleteWorkDraft() throws IllegalAccessException, ObjectNotFoundException {
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    Response response = draftRest.deleteWorkDraft(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    Response response1 = draftRest.deleteWorkDraft(1L);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response1.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    when(identity.getUserId()).thenReturn(userName);
    Response response2 = draftRest.deleteWorkDraft(1L);
    verify(requestService, times(1)).deleteWorkDraftById(1L, userName);
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());
    doThrow(new ObjectNotFoundException("identity does not exist")).when(requestService).deleteWorkDraftById(1L, userName);
    Response response3 = draftRest.deleteWorkDraft(1L);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response3.getStatus());
    doThrow(new IllegalAccessException()).when(requestService).deleteWorkDraftById(1L, userName);
    Response response4 = draftRest.deleteWorkDraft(1L);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response4.getStatus());
  }

  @Test
  public void getWorkById() throws IllegalAccessException, ObjectNotFoundException {
    Work work = mock(Work.class);
    WorkEntity workEntity = mock(WorkEntity.class);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    Response response = requestRest.getWorkById(null, "");
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    Response response1 = requestRest.getWorkById(null, "");
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    when(requestService.getWorkById(userName, 1L)).thenReturn(work);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWorkEntity(processService, work, "workFlow")).thenReturn(workEntity);
    Response response2 = requestRest.getWorkById(1L, "");
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());
    doThrow(new ObjectNotFoundException("Work not found")).when(requestService).getWorkById(userName, 1L);
    Response response3 = requestRest.getWorkById(1L, "");
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response3.getStatus());
  }

  @Test
  public void getWorkflowById() throws IllegalAccessException, ObjectNotFoundException {
    WorkFlow workFlow = mock(WorkFlow.class);
    WorkFlowEntity workFlowEntity = mock(WorkFlowEntity.class);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    Response response = processRest.getWorkFlowById(null, "");
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    Response response1 = processRest.getWorkFlowById(null, "");
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    when(processService.getWorkFlow(1L, userName)).thenReturn(null);
    Response response2 = processRest.getWorkFlowById(1L, "");
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response2.getStatus());
    when(processService.getWorkFlow(1L, userName)).thenReturn(workFlow);
    ENTITY_BUILDER.when(() -> EntityBuilder.toEntity(workFlow, "")).thenReturn(workFlowEntity);
    Response response3 = processRest.getWorkFlowById(1L, "");
    assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());
  }

  @Test
  public void createNewFormDocument() throws Exception {
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    Attachment attachment = mock(Attachment.class);
    AttachmentEntity attachmentEntity = mock(AttachmentEntity.class);
    Response response = attachmentRest.createNewFormDocument(null, null, null, null, null, null);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    Response response1 = attachmentRest.createNewFormDocument(null, "any", "any", "any", null, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    Response response2 = attachmentRest.createNewFormDocument("any", null, "any", "any", null, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response2.getStatus());
    Response response3 = attachmentRest.createNewFormDocument("any", "any", null, "any", null, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response3.getStatus());
    Response response4 = attachmentRest.createNewFormDocument("any", "any", "any", null, null, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response4.getStatus());
    ATTACHMENT_ENTITY_BUILDER.when(() -> org.exoplatform.services.attachments.utils.EntityBuilder.fromAttachment(identityManager,
                                                                                                                 attachment))
                             .thenReturn(attachmentEntity);
    Response response7 = attachmentRest.createNewFormDocument("any", "any", "any", "any", "workflow", 1L);
    assertEquals(Response.Status.OK.getStatusCode(), response7.getStatus());
    doThrow(new ItemExistsException()).when(processAttachmentService)
                                      .createNewFormDocument(anyString(),
                                                             anyString(),
                                                             anyString(),
                                                             anyString(),
                                                             anyString(),
                                                             anyString(),
                                                             anyLong());
    Response response5 = attachmentRest.createNewFormDocument("any", "any", "any", "any", "workflow", 1L);
    assertEquals(Response.Status.CONFLICT.getStatusCode(), response5.getStatus());
    doThrow(new RuntimeException()).when(processAttachmentService)
                                   .createNewFormDocument(anyString(),
                                                          anyString(),
                                                          anyString(),
                                                          anyString(),
                                                          anyString(),
                                                          anyString(),
                                                          anyLong());
    Response response6 = attachmentRest.createNewFormDocument("any", "any", "any", "any", "workflow", 1L);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response6.getStatus());
  }

  @Test
  public void updateWorkCompleted() throws ObjectNotFoundException, IllegalAccessException {
    Map<String, Boolean> completed = new HashMap<>();
    completed.put("value", null);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    Response response = requestRest.updateWorkCompleted(null, null);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    Response response1 = requestRest.updateWorkCompleted(null, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    Response response7 = requestRest.updateWorkCompleted(null, 1L);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response7.getStatus());
    Response response2 = requestRest.updateWorkCompleted(completed, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response2.getStatus());
    Response response3 = requestRest.updateWorkCompleted(completed, 1L);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response3.getStatus());
    completed.put("value", true);
    Response response5 = requestRest.updateWorkCompleted(completed, 1L);
    assertEquals(Response.Status.OK.getStatusCode(), response5.getStatus());
    doThrow(new ObjectNotFoundException("Work is not found")).when(requestService).updateWorkCompleted(1L, userName, true);
    Response response4 = requestRest.updateWorkCompleted(completed, 1L);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response4.getStatus());
  }

  @Test
  public void getAvailableWorkStatuses() {
    List<WorkStatus> statuses = new ArrayList<>();
    statuses.add(new WorkStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(null);
    Response response = processRest.getAvailableWorkStatuses();
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    when(processService.getAvailableWorkStatuses()).thenReturn(statuses);
    Response response1 = processRest.getAvailableWorkStatuses();
    assertEquals(Response.Status.OK.getStatusCode(), response1.getStatus());
  }

  @Test
  public void getImageIllustration() throws Exception {
    Request request = mock(Request.class);
    IllustrativeAttachment illustrativeAttachment =
                                                  new IllustrativeAttachment(1L, "file.png", null, "image/png", 12654L, 1234577L);
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(1L);
    workFlow.setIllustrativeAttachment(illustrativeAttachment);
    Response response = attachmentRest.getImageIllustration(request, null, 0);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    response = attachmentRest.getImageIllustration(request, null, 0);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    when(processService.getWorkFlow(1L, userName)).thenReturn(null);
    Response response1 = attachmentRest.getImageIllustration(request, 1L, 0);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response1.getStatus());
    when(processService.getWorkFlow(1L, userName)).thenReturn(workFlow);
    when(processService.getIllustrationImageById(1L, userName)).thenReturn(illustrativeAttachment);
    when(request.evaluatePreconditions(any(EntityTag.class))).thenReturn(null);
    Response response2 = attachmentRest.getImageIllustration(request, 1L, 0);
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());
    Response response3 = attachmentRest.getImageIllustration(request, 1L, 133584);
    assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());
  }
}
