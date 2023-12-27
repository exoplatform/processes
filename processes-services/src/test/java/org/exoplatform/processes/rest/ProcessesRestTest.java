package org.exoplatform.processes.rest;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.ItemExistsException;
import jakarta.persistence.EntityNotFoundException;
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
import org.exoplatform.processes.model.IllustrativeAttachment;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFilter;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.model.WorkStatus;
import org.exoplatform.processes.rest.model.WorkEntity;
import org.exoplatform.processes.rest.model.WorkFlowEntity;
import org.exoplatform.processes.rest.util.EntityBuilder;
import org.exoplatform.processes.rest.util.RestUtils;
import org.exoplatform.processes.service.ProcessesAttachmentService;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.attachments.model.Attachment;
import org.exoplatform.services.attachments.rest.model.AttachmentEntity;
import org.exoplatform.services.rest.impl.RuntimeDelegateImpl;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ProcessesRestTest {

  private static final MockedStatic<CommonsUtils>                                             COMMONS_UTILS             =
                                                                                                            mockStatic(CommonsUtils.class);

  private static final MockedStatic<RestUtils>                                                REST_UTILS                =
                                                                                                         mockStatic(RestUtils.class);

  private static final MockedStatic<EntityBuilder>                                            ENTITY_BUILDER            =
                                                                                                             mockStatic(EntityBuilder.class);

  private static final MockedStatic<org.exoplatform.services.attachments.utils.EntityBuilder> ATTACHMENT_ENTITY_BUILDER =
                                                                                                                        mockStatic(org.exoplatform.services.attachments.utils.EntityBuilder.class);

  private static final MockedStatic<ConversationState>                                        CONVERSATION_STATE        =
                                                                                                                 mockStatic(ConversationState.class);

  @Mock
  private IdentityManager                                                                     identityManager;

  @Mock
  private ProcessesService                                                                    processesService;

  @Mock
  private ProcessesAttachmentService                                                          processesAttachmentService;

  private ProcessesRest                                                                       processesRest;

  @Mock
  private Identity                                                                            identity;

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    COMMONS_UTILS.close();
    REST_UTILS.close();
    ENTITY_BUILDER.close();
    ATTACHMENT_ENTITY_BUILDER.close();
    CONVERSATION_STATE.close();
  }

  @Before
  public void setUp() {
    RuntimeDelegate.setInstance(new RuntimeDelegateImpl());
    this.processesRest = new ProcessesRest(processesService, identityManager, processesAttachmentService);

    ConversationState conversationState = mock(ConversationState.class);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent()).thenReturn(conversationState);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);

  }

  @Test
  public void getWorkFlows() throws Exception {
    List<WorkFlow> workFlows = new ArrayList<>();
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(1L);
    workFlow.setProjectId(1L);
    workFlows.add(workFlow);
    List<WorkFlowEntity> workFlowEntities = new ArrayList<>();
    ProcessesFilter processesFilter = new ProcessesFilter();
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response1 = processesRest.getWorkFlows(1L, null, null, null, null, 0, 10);
    assertEquals(response1.getStatus(), Response.Status.UNAUTHORIZED.getStatusCode());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(processesService.getWorkFlows(processesFilter, 0, 10, 1L)).thenReturn(workFlows);
    ENTITY_BUILDER.when(() -> EntityBuilder.toRestEntities(workFlows, null)).thenReturn(workFlowEntities);
    Response response2 = processesRest.getWorkFlows(1L, true, null, "test", null, 0, 10);
    assertEquals(response2.getStatus(), Response.Status.OK.getStatusCode());
    when(processesService.getWorkFlows(processesFilter, 0, 10, 1L)).thenThrow(RuntimeException.class);
    Response response3 = processesRest.getWorkFlows(1L, null, null, null, null, 0, 10);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response3.getStatus());

  }

  @Test
  public void isProcessesManager() {
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response1 = processesRest.isProcessesManager();
    assertEquals(response1.getStatus(), Response.Status.UNAUTHORIZED.getStatusCode());
    REST_UTILS.when(() -> RestUtils.isProcessesGroupMember(identity)).thenReturn(true);
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    Response response2 = processesRest.isProcessesManager();
    assertEquals(response2.getStatus(), Response.Status.OK.getStatusCode());
    assertEquals("true", response2.getEntity());
    REST_UTILS.when(() -> RestUtils.isProcessesGroupMember(identity)).thenThrow(RuntimeException.class);
    Response response3 = processesRest.isProcessesManager();
    assertEquals(response3.getStatus(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void deleteWorkflow() {
    Response response = processesRest.deleteWorkflow(null);
    assertEquals(response.getStatus(), Response.Status.BAD_REQUEST.getStatusCode());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response1 = processesRest.deleteWorkflow(1l);
    assertEquals(response1.getStatus(), Response.Status.UNAUTHORIZED.getStatusCode());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    REST_UTILS.when(() -> RestUtils.isProcessesGroupMember(identity)).thenReturn(false);
    Response response2 = processesRest.deleteWorkflow(1l);
    assertEquals(response2.getStatus(), Response.Status.UNAUTHORIZED.getStatusCode());
    REST_UTILS.when(() -> RestUtils.isProcessesGroupMember(identity)).thenReturn(true);
    doNothing().when(processesService).deleteWorkflowById(1l);
    Response response3 = processesRest.deleteWorkflow(1l);
    assertEquals(response3.getStatus(), Response.Status.OK.getStatusCode());
    doThrow(new EntityNotFoundException()).when(processesService).deleteWorkflowById(1l);
    Response response4 = processesRest.deleteWorkflow(1l);
    assertEquals(response4.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    doThrow(new RuntimeException()).when(processesService).deleteWorkflowById(1l);
    Response response5 = processesRest.deleteWorkflow(1l);
    assertEquals(response5.getStatus(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void updateWorkflow() throws ObjectNotFoundException, IllegalAccessException {
    WorkFlow workFlow = mock(WorkFlow.class);
    WorkFlowEntity workFlowEntity = mock(WorkFlowEntity.class);
    Response response1 = processesRest.updateWorkFlow(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response2 = processesRest.updateWorkFlow(workFlowEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response2.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    ENTITY_BUILDER.when(() -> EntityBuilder.fromEntity(workFlowEntity)).thenReturn(workFlow);
    when(processesService.updateWorkFlow(workFlow, 1l)).thenReturn(workFlow);
    Response response3 = processesRest.updateWorkFlow(workFlowEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());

    when(processesService.updateWorkFlow(workFlow, 1L)).thenThrow(ObjectNotFoundException.class);
    Response response4 = processesRest.updateWorkFlow(workFlowEntity);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response4.getStatus());
  }

  @Test
  public void shouldReturnUnauthorizedErrorWhenUpdateWorkflow() throws ObjectNotFoundException, IllegalAccessException {
    WorkFlow workFlow = new WorkFlow();
    WorkFlowEntity workFlowEntity = new WorkFlowEntity();
    Date createdDate = new Date();
    Date modifiedDate = new Date();
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    ENTITY_BUILDER.when(() -> EntityBuilder.fromEntity(workFlowEntity)).thenReturn(workFlow);
    when(processesService.updateWorkFlow(workFlow, 1L)).thenThrow(IllegalAccessException.class);
    Response response = processesRest.updateWorkFlow(workFlowEntity);
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
    workFlowEntity1.hashCode();
    workFlowEntity1.equals(workFlowEntity1);
    workFlowEntity1.toString();
    Response response1 = processesRest.updateWorkFlow(workFlowEntity1);
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
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    ENTITY_BUILDER.when(() -> EntityBuilder.fromEntity(workFlowEntity)).thenReturn(workFlow);
    when(processesService.updateWorkFlow(workFlow, 1L)).thenThrow(RuntimeException.class);
    Response response = processesRest.updateWorkFlow(workFlowEntity);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  public void createWorkflow() throws IllegalAccessException {
    WorkFlow workFlow = mock(WorkFlow.class);
    WorkFlowEntity workFlowEntity = mock(WorkFlowEntity.class);
    Response response1 = processesRest.createWorkFlow(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response2 = processesRest.createWorkFlow(workFlowEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response2.getStatus());
    ENTITY_BUILDER.when(() -> EntityBuilder.fromEntity(workFlowEntity)).thenReturn(workFlow);
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(processesService.createWorkFlow(workFlow, 1L)).thenReturn(workFlow);
    Response response3 = processesRest.createWorkFlow(workFlowEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());
    when(processesService.createWorkFlow(workFlow, 1L)).thenThrow(IllegalAccessException.class);
    Response response4 = processesRest.createWorkFlow(workFlowEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response4.getStatus());
  }

  @Test
  public void shouldReturnServerErrorWhenCreateWorkflow() throws Exception {
    WorkFlow workFlow = mock(WorkFlow.class);
    WorkFlowEntity workFlowEntity = mock(WorkFlowEntity.class);
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    ENTITY_BUILDER.when(() -> EntityBuilder.fromEntity(workFlowEntity)).thenReturn(workFlow);
    when(processesService.createWorkFlow(workFlow, 1L)).thenThrow(RuntimeException.class);
    Response response = processesRest.createWorkFlow(workFlowEntity);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  public void getWorks() throws Exception {
    List<Work> works = new ArrayList<>();
    WorkFilter workFilter = new WorkFilter();
    workFilter.setStatus("ToDo");
    workFilter.setQuery("test");
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response1 = processesRest.getWorks(0L, "", false, "ToDo", "test", 0, 10);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response1.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(processesService.getWorks(1L, workFilter, 0, 10)).thenReturn(works);
    Response response2 = processesRest.getWorks(null, "", false, "ToDo", "test", 0, 10);
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());
    workFilter.setCompleted(true);
    when(processesService.getWorks(1L, workFilter, 0, 10)).thenThrow(RuntimeException.class);
    Response response3 = processesRest.getWorks(1L, "", true, "ToDo", "test", 0, 10);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response3.getStatus());
  }

  @Test
  public void createWork() throws IllegalAccessException {
    WorkEntity workEntity = new WorkEntity();
    Work work = mock(Work.class);
    WorkFlowEntity workFlowEntity = new WorkFlowEntity();
    Response response1 = processesRest.createWork(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    workEntity.setProjectId(0L);
    workFlowEntity.setProjectId(0L);
    workEntity.setWorkFlow(workFlowEntity);
    workEntity.hashCode();
    workEntity.equals(workEntity);
    workEntity.toString();
    Response response2 = processesRest.createWork(workEntity);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response2.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    workEntity.setProjectId(1L);
    workEntity.getWorkFlow().setProjectId(1L);
    workEntity.getWorkFlow().setEnabled(false);
    workEntity.setCompleted(true);
    Response response6 = processesRest.createWork(workEntity);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response6.getStatus());
    workEntity.getWorkFlow().setEnabled(true);
    Response response3 = processesRest.createWork(workEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response3.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWork(processesService, workEntity)).thenReturn(work);
    when(processesService.createWork(work, 1L)).thenReturn(work);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWorkEntity(processesService, work, "")).thenReturn(workEntity);
    Response response4 = processesRest.createWork(workEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response4.getStatus());
    when(processesService.createWork(work, 1L)).thenThrow(IllegalAccessException.class);
    Response response5 = processesRest.createWork(workEntity);
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
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWork(processesService, workEntity)).thenReturn(work);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWorkEntity(processesService, work, "")).thenReturn(workEntity);
    when(processesService.createWork(work, 1L)).thenThrow(RuntimeException.class);
    Response response5 = processesRest.createWork(workEntity);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response5.getStatus());
  }

  @Test
  public void updateWork() throws ObjectNotFoundException, IllegalAccessException {
    WorkEntity workEntity = new WorkEntity();
    Work work = mock(Work.class);
    Response response1 = processesRest.updateWork(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response3 = processesRest.updateWork(workEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response3.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWork(processesService, workEntity)).thenReturn(work);
    when(processesService.updateWork(work, 1L)).thenReturn(work);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWorkEntity(processesService, work, "")).thenReturn(workEntity);
    Response response4 = processesRest.updateWork(workEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response4.getStatus());
    when(processesService.updateWork(work, 1L)).thenThrow(ObjectNotFoundException.class);
    Response response5 = processesRest.updateWork(workEntity);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response5.getStatus());

  }

  @Test
  public void shouldReturnUnauthorizedErrorWWhenUpdateWork() throws Exception {
    WorkEntity workEntity = new WorkEntity();
    Work work = mock(Work.class);
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWork(processesService, workEntity)).thenReturn(work);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWorkEntity(processesService, work, "")).thenReturn(workEntity);
    when(processesService.updateWork(work, 1L)).thenThrow(IllegalAccessException.class);
    Response response6 = processesRest.updateWork(workEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response6.getStatus());
  }

  @Test
  public void shouldReturnServerErrorWhenUpdateWork() throws Exception {
    WorkEntity workEntity = new WorkEntity();
    Work work = mock(Work.class);
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWork(processesService, workEntity)).thenReturn(work);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWorkEntity(processesService, work, "")).thenReturn(workEntity);
    when(processesService.updateWork(work, 1L)).thenThrow(RuntimeException.class);
    Response response6 = processesRest.updateWork(workEntity);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response6.getStatus());
  }

  @Test
  public void countWorksByWorkflow() throws Exception {
    WorkFlow workFlow = mock(WorkFlow.class);
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response = processesRest.countWorksByWorkflow(null, null);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    Response response1 = processesRest.countWorksByWorkflow(null, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    when(processesService.getWorkFlowByProjectId(1L)).thenReturn(null);
    Response response2 = processesRest.countWorksByWorkflow(1L, null);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response2.getStatus());
    when(processesService.getWorkFlowByProjectId(1L)).thenReturn(workFlow);
    when(processesService.countWorksByWorkflow(1L, false)).thenReturn(2);
    Response response3 = processesRest.countWorksByWorkflow(1L, false);
    assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());
    when(processesService.countWorksByWorkflow(1L, false)).thenThrow(RuntimeException.class);
    Response response4 = processesRest.countWorksByWorkflow(1L, false);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response4.getStatus());
  }

  @Test
  public void deleteWorkById() {
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response = processesRest.deleteWork(1L);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    Response response1 = processesRest.deleteWork(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    Response response2 = processesRest.deleteWork(1L);
    verify(processesService, times(1)).deleteWorkById(1L);
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());
    doThrow(new RuntimeException()).when(processesService).deleteWorkById(1l);
    Response response3 = processesRest.deleteWork(1L);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response3.getStatus());
  }

  @Test
  public void createWorkDraft() {
    WorkEntity WorkEntity = new WorkEntity();
    Work work = mock(Work.class);
    ENTITY_BUILDER.when(() -> EntityBuilder.fromEntity(WorkEntity)).thenReturn(work);
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response = processesRest.createWorkDraft(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    Response response1 = processesRest.createWorkDraft(WorkEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response1.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(processesService.createWorkDraft(work, 1L)).thenReturn(work);
    Response response2 = processesRest.createWorkDraft(WorkEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());
    doThrow(new RuntimeException()).when(processesService).createWorkDraft(work, 1L);
    Response response3 = processesRest.createWorkDraft(WorkEntity);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response3.getStatus());
  }

  @Test
  public void getWorkDrafts() {
    List<Work> works = new ArrayList<>();
    WorkFilter workFilter = new WorkFilter();
    workFilter.setIsDraft(true);
    workFilter.setQuery("test");
    List<WorkEntity> WorkEntityList = new ArrayList<>();
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response = processesRest.getWorkDrafts(0L, "", "test", 0, 10);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(processesService.getWorkDrafts(1L, workFilter, 0, 10)).thenReturn(works);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWorkEntityList(works)).thenReturn(WorkEntityList);
    Response response1 = processesRest.getWorkDrafts(null, "", "test", 0, 10);
    assertEquals(Response.Status.OK.getStatusCode(), response1.getStatus());
    doThrow(new RuntimeException()).when(processesService).getWorkDrafts(1L, workFilter, 0, 10);
    Response response2 = processesRest.getWorkDrafts(1L, "", "test", 0, 10);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response2.getStatus());
  }

  @Test
  public void updateWorkDraft() throws ObjectNotFoundException {
    WorkEntity WorkEntity = new WorkEntity();
    Work work = mock(Work.class);
    ENTITY_BUILDER.when(() -> EntityBuilder.fromEntity(WorkEntity)).thenReturn(work);
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response = processesRest.updateWorkDraft(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    Response response1 = processesRest.updateWorkDraft(WorkEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response1.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    ENTITY_BUILDER.when(() -> EntityBuilder.fromEntity(WorkEntity)).thenReturn(work);
    ENTITY_BUILDER.when(() -> EntityBuilder.toEntity(work)).thenReturn(WorkEntity);
    when(processesService.updateWorkDraft(work, 1L)).thenReturn(work);
    Response response2 = processesRest.updateWorkDraft(WorkEntity);
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());
    doThrow(new ObjectNotFoundException("oldWorkDraft is not exist")).when(processesService).updateWorkDraft(work, 1L);
    Response response4 = processesRest.updateWorkDraft(WorkEntity);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response4.getStatus());
    doThrow(new RuntimeException()).when(processesService).updateWorkDraft(work, 1L);
    Response response3 = processesRest.updateWorkDraft(WorkEntity);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response3.getStatus());
  }

  @Test
  public void deleteWorkDraft() {
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response = processesRest.deleteWorkDraft(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    Response response1 = processesRest.deleteWorkDraft(1L);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response1.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    Response response2 = processesRest.deleteWorkDraft(1L);
    verify(processesService, times(1)).deleteWorkDraftById(1L);
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());
    doThrow(new EntityNotFoundException()).when(processesService).deleteWorkDraftById(1L);
    Response response3 = processesRest.deleteWorkDraft(1L);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response3.getStatus());
    doThrow(new RuntimeException()).when(processesService).deleteWorkDraftById(1L);
    Response response4 = processesRest.deleteWorkDraft(1L);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response4.getStatus());
  }

  @Test
  public void getWorkById() {
    Work work = mock(Work.class);
    WorkEntity workEntity = mock(WorkEntity.class);
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response = processesRest.getWorkById(null, "");
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    Response response1 = processesRest.getWorkById(null, "");
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    when(processesService.getWorkById(1L, 1L)).thenReturn(work);
    ENTITY_BUILDER.when(() -> EntityBuilder.toWorkEntity(processesService, work, "workFlow")).thenReturn(workEntity);
    Response response2 = processesRest.getWorkById(1L, "");
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());
    doThrow(new EntityNotFoundException()).when(processesService).getWorkById(1L, 1L);
    Response response3 = processesRest.getWorkById(1L, "");
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response3.getStatus());
    doThrow(new RuntimeException()).when(processesService).getWorkById(1L, 1L);
    Response response4 = processesRest.getWorkById(1L, "");
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response4.getStatus());
  }

  @Test
  public void getWorkflowById() throws IllegalAccessException {
    WorkFlow workFlow = mock(WorkFlow.class);
    WorkFlowEntity workFlowEntity = mock(WorkFlowEntity.class);
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response = processesRest.getWorkFlowById(null, "");
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    Response response1 = processesRest.getWorkFlowById(null, "");
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    when(processesService.getWorkFlow(1L)).thenReturn(null);
    Response response2 = processesRest.getWorkFlowById(1L, "");
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response2.getStatus());
    when(processesService.getWorkFlow(1L)).thenReturn(workFlow);
    ENTITY_BUILDER.when(() -> EntityBuilder.toEntity(workFlow, "")).thenReturn(workFlowEntity);
    Response response3 = processesRest.getWorkFlowById(1L, "");
    assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());
    doThrow(new RuntimeException()).when(processesService).getWorkFlow(1L);
    Response response4 = processesRest.getWorkFlowById(1L, "");
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response4.getStatus());
  }

  @Test
  public void createNewFormDocument() throws Exception {
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Attachment attachment = mock(Attachment.class);
    AttachmentEntity attachmentEntity = mock(AttachmentEntity.class);
    Response response = processesRest.createNewFormDocument(null, null, null, null, null, null);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    Response response1 = processesRest.createNewFormDocument(null, "any", "any", "any", null, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    Response response2 = processesRest.createNewFormDocument("any", null, "any", "any", null, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response2.getStatus());
    Response response3 = processesRest.createNewFormDocument("any", "any", null, "any", null, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response3.getStatus());
    Response response4 = processesRest.createNewFormDocument("any", "any", "any", null, null, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response4.getStatus());
    ATTACHMENT_ENTITY_BUILDER.when(() -> org.exoplatform.services.attachments.utils.EntityBuilder.fromAttachment(identityManager,
                                                                                                                 attachment))
                             .thenReturn(attachmentEntity);
    Response response7 = processesRest.createNewFormDocument("any", "any", "any", "any", "workflow", 1L);
    assertEquals(Response.Status.OK.getStatusCode(), response7.getStatus());
    doThrow(new ItemExistsException()).when(processesAttachmentService)
                                      .createNewFormDocument(anyLong(),
                                                             anyString(),
                                                             anyString(),
                                                             anyString(),
                                                             anyString(),
                                                             anyString(),
                                                             anyLong());
    Response response5 = processesRest.createNewFormDocument("any", "any", "any", "any", "workflow", 1L);
    assertEquals(Response.Status.CONFLICT.getStatusCode(), response5.getStatus());
    doThrow(new RuntimeException()).when(processesAttachmentService)
                                   .createNewFormDocument(anyLong(),
                                                          anyString(),
                                                          anyString(),
                                                          anyString(),
                                                          anyString(),
                                                          anyString(),
                                                          anyLong());
    Response response6 = processesRest.createNewFormDocument("any", "any", "any", "any", "workflow", 1L);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response6.getStatus());
  }

  @Test
  public void updateWorkCompleted() {
    Map<String, Boolean> completed = new HashMap<>();
    completed.put("value", null);
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response = processesRest.updateWorkCompleted(null, null);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    Response response1 = processesRest.updateWorkCompleted(null, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    Response response7 = processesRest.updateWorkCompleted(null, 1L);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response7.getStatus());
    Response response2 = processesRest.updateWorkCompleted(completed, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response2.getStatus());
    Response response3 = processesRest.updateWorkCompleted(completed, 1L);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response3.getStatus());
    completed.put("value", true);
    Response response5 = processesRest.updateWorkCompleted(completed, 1L);
    assertEquals(Response.Status.OK.getStatusCode(), response5.getStatus());
    doThrow(new EntityNotFoundException()).when(processesService).updateWorkCompleted(1L, true);
    Response response4 = processesRest.updateWorkCompleted(completed, 1L);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response4.getStatus());
    doThrow(new RuntimeException()).when(processesService).updateWorkCompleted(1L, true);
    Response response6 = processesRest.updateWorkCompleted(completed, 1L);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response6.getStatus());
  }

  @Test
  public void getAvailableWorkStatuses() {
    List<WorkStatus> statuses = new ArrayList<>();
    statuses.add(new WorkStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    Response response = processesRest.getAvailableWorkStatuses();
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    REST_UTILS.when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(processesService.getAvailableWorkStatuses()).thenReturn(statuses);
    Response response1 = processesRest.getAvailableWorkStatuses();
    assertEquals(Response.Status.OK.getStatusCode(), response1.getStatus());
    doThrow(new RuntimeException()).when(processesService).getAvailableWorkStatuses();
    Response response2 = processesRest.getAvailableWorkStatuses();
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response2.getStatus());
  }

  @Test
  public void getImageIllustration() throws Exception {
    Request request = mock(Request.class);
    IllustrativeAttachment illustrativeAttachment =
                                                  new IllustrativeAttachment(1L, "file.png", null, "image/png", 12654L, 1234577L);
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(1L);
    workFlow.setIllustrativeAttachment(illustrativeAttachment);
    Response response = processesRest.getImageIllustration(request, null, 0);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    when(processesService.getWorkFlow(1L)).thenReturn(null);
    Response response1 = processesRest.getImageIllustration(request, 1L, 0);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response1.getStatus());
    when(processesService.getWorkFlow(1L)).thenReturn(workFlow);
    when(processesService.getIllustrationImageById(1L)).thenReturn(illustrativeAttachment);
    when(request.evaluatePreconditions(any(EntityTag.class))).thenReturn(null);
    Response response2 = processesRest.getImageIllustration(request, 1L, 0);
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());
    Response response3 = processesRest.getImageIllustration(request, 1L, 133584);
    assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());
    doThrow(new ObjectNotFoundException("Illustration image not found")).when(processesService).getIllustrationImageById(1L);
    Response response4 = processesRest.getImageIllustration(request, 1L, 133584);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response4.getStatus());
    doThrow(new RuntimeException()).when(processesService).getIllustrationImageById(1L);
    Response response5 = processesRest.getImageIllustration(request, 1L, 133584);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response5.getStatus());
  }
}
