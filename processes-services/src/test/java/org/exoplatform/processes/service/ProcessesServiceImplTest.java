package org.exoplatform.processes.service;

import static org.exoplatform.processes.Utils.ProcessesUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

import java.util.*;

import org.exoplatform.processes.model.*;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.storage.ProcessStorage;
import org.exoplatform.processes.storage.RequestStorage;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ProcessesServiceImplTest {

  private static final MockedStatic<ProcessesUtils>    PROCESS_UTILS       = mockStatic(ProcessesUtils.class);

  private static final MockedStatic<ConversationState> CONVERSATION_STATE  = mockStatic(ConversationState.class);

  private final List<WorkFlow>                         enabledWorkFlowList = new ArrayList<>();

  private final List<WorkFlow>                         allWorkFlowList     = new ArrayList<>();

  private final List<Work>                             allWorkList         = new ArrayList<>();

  private final String                                 userName            = "testuser";

  @Mock
  private ProcessStorage                               processStorage;

  private ProcessService                               processService;

  @Mock
  private RequestStorage                               requestStorage;

  private RequestService                               requestService;

  @Mock
  private IdentityManager                              identityManager;

  @Mock
  private UserACL                                      userAcl;

  @Mock
  private org.exoplatform.services.security.Identity   identity;

  private WorkFlow                                     enabledWorkFlow;

  @AfterClass
  public static void afterRunBare() { // NOSONAR
    PROCESS_UTILS.close();
    CONVERSATION_STATE.close();
  }

  @Before
  public void setUp() {
    this.processService = new ProcessServiceImpl(processStorage, userAcl);
    this.requestService = new RequestServiceImpl(requestStorage, processService, userAcl, identityManager);
    WorkFlow disabledWorkFlow = new WorkFlow();
    disabledWorkFlow.setEnabled(false);
    enabledWorkFlow = new WorkFlow();
    enabledWorkFlow.setId(1L);
    enabledWorkFlow.setEnabled(true);

    allWorkFlowList.add(disabledWorkFlow);
    allWorkFlowList.add(disabledWorkFlow);

    enabledWorkFlowList.add(enabledWorkFlow);
    ConversationState conversationState = mock(ConversationState.class);
    CONVERSATION_STATE.when(ConversationState::getCurrent).thenReturn(conversationState);
  }

  @Test
  public void getWorkFlows() {

    ProcessesFilter processesFilter = new ProcessesFilter();
    processesFilter.setEnabled(true);
    processesFilter.setQuery("test");
    when(processStorage.findWorkFlows(processesFilter, "user", 0, 10)).thenReturn(enabledWorkFlowList);
    Identity identity = mock(Identity.class);
    when(identityManager.getIdentity(1)).thenReturn(null);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    processService.getWorkFlows(processesFilter, 0, 10, userName);
    verify(processStorage, times(1)).findWorkFlows(processesFilter, userName, 0, 10);
  }

  @Test
  public void getWorks() {

    WorkFilter workFilter = new WorkFilter();
    workFilter.setQuery("test");
    when(requestStorage.getWorks("user", workFilter, 0, 10)).thenReturn(allWorkList);
    assertEquals(requestService.getWorks("user", workFilter, 0, 10), allWorkList);
  }

  @Test
  public void getWorkFlowByProjectId() throws Exception {

    when(processStorage.getWorkFlowByProjectId(0L)).thenReturn(enabledWorkFlow);
    assertEquals(processService.getWorkFlowByProjectId(0L).getId(), 1L);
  }

  @Test
  public void getWorkFlow() throws IllegalAccessException, ObjectNotFoundException {
    Identity identity = mock(Identity.class);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(processStorage.getWorkFlowById(1L)).thenReturn(enabledWorkFlow);
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(false);
    PROCESS_UTILS.when(() -> isPlatformAdmin(any())).thenReturn(false);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    Throwable exception = assertThrows(IllegalAccessException.class, () -> this.processService.getWorkFlow(1L, userName));
    assertEquals("User testuser  does not have the rights to access Process", exception.getMessage());
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(true);
    this.processService.getWorkFlow(1L, userName);
    verify(processStorage, times(2)).getWorkFlowById(1L);
  }

  @Test
  public void countWorkFlows() {

    ProcessesFilter processesFilter = new ProcessesFilter();
    processesFilter.setEnabled(true);
    processesFilter.setQuery("test");
    when(processStorage.countWorkFlows(processesFilter)).thenReturn(enabledWorkFlowList.size());
    assertEquals(processService.countWorkFlows(processesFilter, "user"), enabledWorkFlowList.size());
  }

  @Test
  public void updateWorkflow() throws ObjectNotFoundException, IllegalAccessException {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setTitle("test");
    workFlow.setDescription("test description");
    workFlow.setAcl(new ProcessPermission());
    workFlow.setManager(new HashSet<>());
    workFlow.setCanShowPending(false);
    workFlow.setEnabled(true);
    workFlow.setIllustrativeAttachment(new IllustrativeAttachment());
    workFlow.setModifiedDate(new Date());
    workFlow.setModifierId(1L);
    workFlow.setProjectId(1L);
    workFlow.setRequestsCreators(new ArrayList<>());
    workFlow.setSummary("test summary");
    WorkFlow updatedWorkflow = new WorkFlow();
    updatedWorkflow.setId(1L);
    updatedWorkflow.setDescription("anything");
    updatedWorkflow.setTitle("test");
    updatedWorkflow.setAcl(new ProcessPermission());
    updatedWorkflow.setManager(new HashSet<>());
    updatedWorkflow.setCanShowPending(false);
    updatedWorkflow.setEnabled(true);
    updatedWorkflow.setIllustrativeAttachment(new IllustrativeAttachment());
    updatedWorkflow.setModifiedDate(new Date());
    updatedWorkflow.setModifierId(1L);
    updatedWorkflow.setProjectId(1L);
    updatedWorkflow.setRequestsCreators(new ArrayList<>());
    updatedWorkflow.setSummary("test summary");
    workFlow.setId(0L);
    Identity identity = mock(Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.processService.updateWorkFlow(null, userName));
    assertEquals("Workflow Type is mandatory and its id must not be equal to 0", exception.getMessage());
    verify(requestStorage, times(0)).getWorkById(1L);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processService.updateWorkFlow(workFlow, userName));
    assertEquals("Workflow Type is mandatory and its id must not be equal to 0", exception.getMessage());
    verify(requestStorage, times(0)).getWorkById(1L);
    workFlow.setId(1L);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(processStorage.getWorkFlowById(workFlow.getId())).thenReturn(null);
    exception = assertThrows(ObjectNotFoundException.class, () -> this.processService.updateWorkFlow(workFlow, userName));
    assertEquals("Workflow does not exist", exception.getMessage());
    verify(requestStorage, times(0)).getWorkById(1L);
    when(processStorage.getWorkFlowById(workFlow.getId())).thenReturn(workFlow);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processService.updateWorkFlow(workFlow, userName));
    assertEquals("there are no changes to save", exception.getMessage());
    when(processStorage.getWorkFlowById(workFlow.getId())).thenReturn(updatedWorkflow);
    when(identity.getRemoteId()).thenReturn(userName);
    Set<String> manager = new HashSet<>();
    updatedWorkflow.setManager(manager);
    updatedWorkflow.setId(1L);
    when(processStorage.getWorkFlowById(workFlow.getId())).thenReturn(workFlow);
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(false);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processService.updateWorkFlow(updatedWorkflow, userName));
    assertEquals("User  testuser does not have the rights to update this Process", exception.getMessage());
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(true);
    this.processService.updateWorkFlow(updatedWorkflow, userName);
  }

  @Test
  public void createWorkflow() throws IllegalAccessException {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(1L);
    Identity identity = mock(Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.processService.createWorkFlow(null, userName));
    assertEquals("workFlow is mandatory", exception.getMessage());
    verify(processStorage, times(0)).saveWorkFlow(workFlow, userName);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processService.createWorkFlow(workFlow, userName));
    assertEquals("workFlow id must be equal to 0", exception.getMessage());
    verify(processStorage, times(0)).saveWorkFlow(workFlow, userName);
    workFlow.setId(0L);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(identity.getRemoteId()).thenReturn(userName);
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(false);
    PROCESS_UTILS.when(() -> isPlatformAdmin(any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processService.createWorkFlow(workFlow, userName));
    assertEquals("User  testuser does not have the rights to add Process", exception.getMessage());
    verify(processStorage, times(0)).saveWorkFlow(workFlow, userName);
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processService.createWorkFlow(workFlow, userName));
    assertEquals("User  testuser does not have the rights to add Process", exception.getMessage());
    verify(processStorage, times(0)).saveWorkFlow(workFlow, userName);
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(true);
    processService.createWorkFlow(workFlow, userName);
    verify(processStorage, times(1)).saveWorkFlow(workFlow, userName);
  }

  @Test
  public void createWork() throws IllegalAccessException, ObjectNotFoundException {
    Work work = new Work();
    WorkFlow workFlow = new WorkFlow();
    work.setId(1L);
    Identity identity = mock(Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.requestService.createWork(null, userName));
    assertEquals("work is mandatory", exception.getMessage());
    verify(requestStorage, times(0)).saveWork(work, userName);
    exception = assertThrows(IllegalArgumentException.class, () -> this.requestService.createWork(work, userName));
    assertEquals("work id must be equal to 0", exception.getMessage());
    verify(requestStorage, times(0)).saveWork(work, userName);
    work.setId(0L);
    when(processStorage.getWorkFlowByProjectId(anyLong())).thenReturn(workFlow);
    org.exoplatform.services.security.Identity userIdentity = mock(org.exoplatform.services.security.Identity.class);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(identity.getRemoteId()).thenReturn(userName);
    when(userAcl.getUserIdentity(userName)).thenReturn(userIdentity);
    when(userAcl.isMemberOf(userIdentity, PROCESSES_GROUP)).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.requestService.createWork(work, userName));
    assertEquals("User  testuser does not have the rights to create requests", exception.getMessage());
    verify(requestStorage, times(0)).saveWork(work, userName);
    when(userAcl.isMemberOf(userIdentity, PROCESSES_GROUP)).thenReturn(true);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.requestService.createWork(work, userName));
    assertEquals("User  testuser does not have the rights to create requests", exception.getMessage());
    verify(requestStorage, times(0)).saveWork(work, userName);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(true);
    requestService.createWork(work, userName);
    verify(requestStorage, times(1)).saveWork(work, userName);
  }

  @Test
  public void updateWork() throws ObjectNotFoundException, IllegalAccessException {
    Work work = new Work();
    WorkFlow workFlow = new WorkFlow();
    work.setId(0L);
    work.setTitle("title");
    work.setDescription("description");
    work.setStatus("status");
    work.setCompleted(false);
    Identity identity = mock(Identity.class);
    org.exoplatform.services.security.Identity userIdentity = mock(org.exoplatform.services.security.Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.requestService.updateWork(null, userName));
    assertEquals("Work is mandatory", exception.getMessage());
    verify(requestStorage, times(0)).saveWork(work, userName);

    exception = assertThrows(IllegalArgumentException.class, () -> this.requestService.updateWork(work, userName));
    assertEquals("work id must not be equal to 0", exception.getMessage());
    verify(requestStorage, times(0)).saveWork(work, userName);
    work.setId(1L);
    when(processStorage.getWorkFlowByProjectId(anyLong())).thenReturn(workFlow);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(identity.getRemoteId()).thenReturn(userName);
    when(userAcl.getUserIdentity(userName)).thenReturn(userIdentity);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.requestService.updateWork(work, userName));
    assertEquals("User  testuser  does not have the rights to update the request", exception.getMessage());
    verify(requestStorage, times(0)).saveWork(work, userName);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(true);
    when(requestStorage.getWorkById(work.getId())).thenReturn(null);
    exception = assertThrows(ObjectNotFoundException.class, () -> this.requestService.updateWork(work, userName));
    assertEquals("oldWork does not exist", exception.getMessage());
    verify(requestStorage, times(0)).saveWork(work, userName);
    when(requestStorage.getWorkById(work.getId())).thenReturn(work);
    exception = assertThrows(IllegalArgumentException.class, () -> this.requestService.updateWork(work, userName));
    assertEquals("there are no changes to save", exception.getMessage());
    verify(requestStorage, times(0)).saveWork(work, userName);

    Work newWork = new Work();
    when(requestStorage.getWorkById(newWork.getId())).thenReturn(newWork);
    newWork.setId(work.getId());
    newWork.setDescription("anything");
    requestService.updateWork(newWork, userName);
    verify(requestStorage, times(1)).saveWork(newWork, userName);
  }

  @Test
  public void countWorksByWorkflow() throws Exception {
    Identity identity = mock(Identity.class);
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(1L);
    Throwable exception = assertThrows(IllegalArgumentException.class,
                                       () -> this.processService.countWorksByWorkflow(null, userName, false));
    assertEquals("Project Id is mandatory", exception.getMessage());
    verify(processStorage, times(0)).countWorksByWorkflow(1L, false);

    exception = assertThrows(IllegalArgumentException.class, () -> this.processService.countWorksByWorkflow(1L, userName, null));
    assertEquals("isCompleted should not be null", exception.getMessage());
    verify(processStorage, times(0)).countWorksByWorkflow(1L, false);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    exception = assertThrows(ObjectNotFoundException.class, () -> this.processService.countWorksByWorkflow(1L, userName, false));
    assertEquals("Workflow does not exist", exception.getMessage());
    verify(processStorage, times(0)).countWorksByWorkflow(1L, false);
    when(processStorage.getWorkFlowByProjectId(1)).thenReturn(workFlow);
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(false);
    PROCESS_UTILS.when(() -> isPlatformAdmin(any())).thenReturn(false);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processService.countWorksByWorkflow(1L, userName, false));
    assertEquals("User  testuser does not have the rights to count requests for the process", exception.getMessage());
    verify(processStorage, times(0)).countWorksByWorkflow(1L, false);
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(true);
    processService.countWorksByWorkflow(1L, userName, false);
    verify(processStorage, times(1)).countWorksByWorkflow(1L, false);
  }

  @Test
  public void deleteWorkById() throws ObjectNotFoundException, IllegalAccessException {
    Identity identity = mock(Identity.class);
    Work work = new Work();
    WorkFlow workFlow = new WorkFlow();
    work.setId(1L);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.requestService.deleteWorkById(null, userName));
    assertEquals("Work id is mandatory", exception.getMessage());
    verify(requestStorage, times(0)).deleteWorkById(anyLong());
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(requestStorage.getWorkById(1L)).thenReturn(null);
    exception = assertThrows(ObjectNotFoundException.class, () -> this.requestService.deleteWorkById(1L, userName));
    assertEquals("Work not found", exception.getMessage());
    verify(requestStorage, times(0)).deleteWorkById(1L);
    work.setCreatedBy("user2");
    when(requestStorage.getWorkById(1L)).thenReturn(work);
    when(processStorage.getWorkFlowByProjectId(anyLong())).thenReturn(workFlow);
    when(identity.getRemoteId()).thenReturn("user1");
    exception = assertThrows(IllegalAccessException.class, () -> this.requestService.deleteWorkById(1L, userName));
    assertEquals("User  testuser does not have the rights to access the request", exception.getMessage());
    verify(requestStorage, times(0)).deleteWorkById(1L);
    when(identity.getRemoteId()).thenReturn("user2");
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.requestService.deleteWorkById(1L, userName));
    assertEquals("User  testuser does not have the rights to access the request", exception.getMessage());
    verify(requestStorage, times(0)).deleteWorkById(1L);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(true);
    requestService.deleteWorkById(1L, userName);
    verify(requestStorage, times(1)).deleteWorkById(1L);
  }

  @Test
  public void createWorkDraft() throws IllegalAccessException {
    Work work = new Work();
    work.setId(1L);
    Identity identity = mock(Identity.class);
    org.exoplatform.services.security.Identity userIdentity = mock(org.exoplatform.services.security.Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.requestService.createWorkDraft(null, userName));
    assertEquals("WorkDraft is mandatory and it's id must be equal to 0", exception.getMessage());
    verify(requestStorage, times(0)).saveWorkDraft(work, userName);
    exception = assertThrows(IllegalArgumentException.class, () -> this.requestService.createWorkDraft(work, userName));
    assertEquals("WorkDraft is mandatory and it's id must be equal to 0", exception.getMessage());
    verify(requestStorage, times(0)).saveWorkDraft(work, userName);
    work.setId(0L);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(identity.getRemoteId()).thenReturn(userName);
    when(userAcl.getUserIdentity(userName)).thenReturn(userIdentity);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.requestService.createWorkDraft(work, userName));
    assertEquals("User  testuser does not have the rights to create requests", exception.getMessage());
    verify(requestStorage, times(0)).saveWorkDraft(work, userName);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(true);
    requestService.createWorkDraft(work, userName);
    verify(requestStorage, times(1)).saveWorkDraft(work, userName);
  }

  @Test
  public void updateWorkDraft() throws ObjectNotFoundException, IllegalAccessException {
    Identity socIdentity = mock(Identity.class);
    Work work = new Work();
    work.setId(0L);
    work.setTitle("title");
    work.setDescription( "description");
    work.setIsDraft(true);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.requestService.updateWorkDraft(null, userName));
    assertEquals("WorkDraft Type is mandatory", exception.getMessage());
    exception = assertThrows(IllegalArgumentException.class, () -> this.requestService.updateWorkDraft(work, userName));
    assertEquals("WorkDraft type id must not be equal to 0", exception.getMessage());
    work.setId(1L);
    when(socIdentity.getRemoteId()).thenReturn(userName);
    when(socIdentity.getId()).thenReturn("1");
    when(identity.getUserId()).thenReturn(userName);
    when(userAcl.getUserIdentity(userName)).thenReturn(identity);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    when(identityManager.getOrCreateUserIdentity(userName)).thenReturn(null);
    exception = assertThrows(IllegalArgumentException.class, () -> this.requestService.updateWorkDraft(work, userName));
    assertEquals("identity does not exist", exception.getMessage());
    verify(requestStorage, times(0)).saveWorkDraft(work, userName);
    when(identityManager.getOrCreateUserIdentity(userName)).thenReturn(socIdentity);
    when(requestStorage.getWorkDraftyId(1L)).thenReturn(null);
    exception = assertThrows(ObjectNotFoundException.class, () -> this.requestService.updateWorkDraft(work, userName));
    assertEquals("oldWorkDraft is not exist", exception.getMessage());
    verify(requestStorage, times(0)).saveWorkDraft(work, userName);
    when(requestStorage.getWorkDraftyId(1L)).thenReturn(work);
    Work newWork = new Work();
    newWork.setId(work.getId());
    newWork.setDescription("test");
    newWork.setTitle("title");
    newWork.setDescription( "description1");
    newWork.setIsDraft(true);
    exception = assertThrows(IllegalArgumentException.class, () -> this.requestService.updateWorkDraft(work, userName));
    assertEquals("there are no changes to save", exception.getMessage());
    verify(requestStorage, times(0)).saveWorkDraft(newWork, userName);
    work.setCreatedBy("2");
    exception = assertThrows(IllegalAccessException.class, () -> this.requestService.updateWorkDraft(newWork, userName));
    assertEquals("User  testuser does not have the rights to update this draft", exception.getMessage());
    verify(requestStorage, times(0)).saveWorkDraft(newWork, userName);
    work.setCreatorId(1);
    when(socIdentity.getId()).thenReturn("1");
    when(requestStorage.getWorkDraftyId(1L)).thenReturn(work);
    requestService.updateWorkDraft(newWork, userName);
    verify(requestStorage, times(1)).saveWorkDraft(work, userName);
  }

  @Test
  public void getWorkDrafts() {
    Identity socIdentity = mock(Identity.class);
    List<Work> workList = new ArrayList<>();
    WorkFilter workFilter = new WorkFilter();
    workFilter.setIsDraft(true);
    workFilter.setQuery("test");
    workList.add(new Work());
    when(identityManager.getIdentity(1)).thenReturn(null);
    Throwable exception = assertThrows(IllegalArgumentException.class,
                                       () -> requestService.getWorkDrafts(userName, workFilter, 0, 10));
    assertEquals("identity does not exist", exception.getMessage());
    verify(requestStorage, times(0)).findAllWorkDraftsByUser(workFilter, 0, 10, 1L);
    when(identityManager.getOrCreateUserIdentity(userName)).thenReturn(socIdentity);
    when(socIdentity.getRemoteId()).thenReturn(userName);
    when(socIdentity.getId()).thenReturn("1");
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(identity);
    when(identity.getUserId()).thenReturn(userName);
    when(userAcl.getUserIdentity(userName)).thenReturn(identity);
    when(requestStorage.findAllWorkDraftsByUser(workFilter, 0, 10, 1L)).thenReturn(workList);
    List<Work> list = requestService.getWorkDrafts(userName, workFilter, 0, 10);
    verify(requestStorage, times(1)).findAllWorkDraftsByUser(workFilter, 0, 10, 1L);
    assertEquals(list, workList);
  }

  @Test
  public void deleteWorkDraftById() throws IllegalAccessException, ObjectNotFoundException {
    Identity identity = mock(Identity.class);
    Work work = new Work();
    org.exoplatform.services.security.Identity userIdentity = mock(org.exoplatform.services.security.Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class,
                                       () -> this.requestService.deleteWorkDraftById(null, userName));
    assertEquals("WorkDraft id is mandatory", exception.getMessage());
    verify(requestStorage, times(0)).deleteWorkDraftById(1L);
    when(userAcl.getUserIdentity(userName)).thenReturn(userIdentity);
    when(userAcl.isMemberOf(userIdentity, PROCESSES_GROUP)).thenReturn(false);
    when(identityManager.getOrCreateUserIdentity(userName)).thenReturn(null);
    exception = assertThrows(IllegalArgumentException.class, () -> this.requestService.deleteWorkDraftById(1L, userName));
    assertEquals("identity does not exist", exception.getMessage());
    verify(requestStorage, times(0)).deleteWorkDraftById(1L);
    when(identityManager.getOrCreateUserIdentity(userName)).thenReturn(identity);
    when(identity.getRemoteId()).thenReturn(userName);
    when(identity.getId()).thenReturn("1");
    exception = assertThrows(ObjectNotFoundException.class, () -> this.requestService.deleteWorkDraftById(1L, userName));
    assertEquals("WorkDraft is not found", exception.getMessage());
    verify(requestStorage, times(0)).deleteWorkDraftById(1L);
    work.setCreatorId(2L);
    when(requestStorage.getWorkDraftyId(1L)).thenReturn(work);
    exception = assertThrows(IllegalAccessException.class, () -> this.requestService.deleteWorkDraftById(1L, userName));
    assertEquals("User  testuser does not have the rights to delete the draft", exception.getMessage());
    verify(requestStorage, times(0)).deleteWorkDraftById(1L);
    work.setCreatorId(1L);
    when(requestStorage.getWorkDraftyId(1L)).thenReturn(work);
    requestService.deleteWorkDraftById(1L, userName);
    verify(requestStorage, times(1)).deleteWorkDraftById(1L);
  }

  @Test
  public void getWorkById() throws IllegalAccessException, ObjectNotFoundException {
    Work work = new Work();
    work.setId(1L);
    Identity identity = mock(Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.requestService.getWorkById(userName, null));
    assertEquals("Work id is mandatory", exception.getMessage());
    verify(requestStorage, times(0)).getWorkById(1L);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    exception = assertThrows(ObjectNotFoundException.class, () -> this.requestService.getWorkById(userName, 1L));
    assertEquals("Work not found", exception.getMessage());
    verify(requestStorage, times(1)).getWorkById(1L);
    work.setCreatedBy(userName);
    when(requestStorage.getWorkById(1L)).thenReturn(work);
    requestService.getWorkById(userName, 1L);
    verify(requestStorage, times(2)).getWorkById(1L);
  }

  @Test
  public void updateWorkCompleted() throws ObjectNotFoundException, IllegalAccessException {
    Work work = new Work();
    WorkFlow workFlow = new WorkFlow();
    work.setId(1L);
    Identity identity = mock(Identity.class);
    org.exoplatform.services.security.Identity userIdentity = mock(org.exoplatform.services.security.Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class,
                                       () -> this.requestService.updateWorkCompleted(null, userName, true));
    assertEquals("Work id is mandatory", exception.getMessage());
    verify(requestStorage, times(0)).updateWorkCompleted(1L, true);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    exception = assertThrows(ObjectNotFoundException.class, () -> this.requestService.updateWorkCompleted(1L, userName, true));
    assertEquals("Work not found", exception.getMessage());
    verify(requestStorage, times(0)).updateWorkCompleted(1L, true);
    when(requestStorage.getWorkById(1L)).thenReturn(work);
    when(processStorage.getWorkFlowByProjectId(anyLong())).thenReturn(workFlow);
    work.setCreatedBy("user2");
    when(requestStorage.getWorkById(1L)).thenReturn(work);
    when(identity.getRemoteId()).thenReturn("user1");
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.requestService.updateWorkCompleted(1L, userName, true));
    assertEquals("User  testuser does not have the rights to access the request", exception.getMessage());
    verify(requestStorage, times(0)).updateWorkCompleted(1L, true);
    when(identity.getRemoteId()).thenReturn("user1");
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.requestService.updateWorkCompleted(1L, userName, true));
    assertEquals("User  testuser does not have the rights to access the request", exception.getMessage());
    verify(requestStorage, times(0)).updateWorkCompleted(1L, true);
    when(identity.getRemoteId()).thenReturn("user2");
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(true);
    when(userAcl.getUserIdentity(userName)).thenReturn(userIdentity);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent().getIdentity()).thenReturn(userIdentity);
    when(userIdentity.getUserId()).thenReturn(userName);
    requestService.updateWorkCompleted(1L, userName, true);
    verify(requestStorage, times(1)).updateWorkCompleted(1L, true);
  }

  @Test
  public void getIllustrationImageById() {
    Throwable exception = assertThrows(IllegalArgumentException.class,
                                       () -> this.processService.getIllustrationImageById(null, userName));
    assertEquals("IllustrationId id is mandatory", exception.getMessage());
    verify(processStorage, times(0)).getIllustrationImageById(1L);
    processService.getIllustrationImageById(1L, userName);
    verify(processStorage, times(1)).getIllustrationImageById(1L);
  }

  @Test
  public void deleteWorkflowById() throws ObjectNotFoundException, IllegalAccessException {
    WorkFlow workFlow = new WorkFlow();
    Identity identity = mock(Identity.class);
    workFlow.setId(1L);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(processStorage.getWorkFlowById(workFlow.getId())).thenReturn(null);
    Throwable exception = assertThrows(ObjectNotFoundException.class, () -> this.processService.deleteWorkflowById(1L, userName));
    assertEquals("Workflow does not exist", exception.getMessage());
    verify(processStorage, times(0)).deleteWorkflowById(1L);
    when(processStorage.getWorkFlowById(workFlow.getId())).thenReturn(workFlow);
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(false);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    PROCESS_UTILS.when(() -> isPlatformAdmin(any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processService.deleteWorkflowById(1L, userName));
    assertEquals("User testuser  does not have the rights to access Process", exception.getMessage());
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(true);
    this.processService.deleteWorkflowById(1L, userName);
    verify(processStorage, times(1)).deleteWorkflowById(1L);
  }

  @Test
  public void getWorkFlowByProjectIdAndIdentity() throws ObjectNotFoundException, IllegalAccessException {
    WorkFlow workFlow = new WorkFlow();
    Identity identity = mock(Identity.class);
    workFlow.setId(1L);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(processStorage.getWorkFlowByProjectId(1L)).thenReturn(null);
    Throwable exception = assertThrows(ObjectNotFoundException.class,
                                       () -> this.processService.getWorkFlowByProjectId(1L, userName));
    assertEquals("Workflow does not exist", exception.getMessage());
    when(processStorage.getWorkFlowByProjectId(1L)).thenReturn(workFlow);
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(false);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processService.getWorkFlowByProjectId(1L, userName));
    assertEquals("User  testuser does not have the rights to access Process", exception.getMessage());
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(true);
    this.processService.getWorkFlowByProjectId(1L, userName);
  }
}
