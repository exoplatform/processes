package org.exoplatform.processes.service;

import static org.exoplatform.processes.Utils.ProcessesUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFilter;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ProcessesServiceImplTest {

  @Mock
  private ProcessesStorage processesStorage;

  private ProcessesService processesService;

  private static final MockedStatic<ProcessesUtils> PROCESS_UTILS        = mockStatic(ProcessesUtils.class);

  @Mock
  private IdentityManager                           identityManager;

  @Mock
  private UserACL                                   userAcl;

  private WorkFlow         disabledWorkFlow, enabledWorkFlow;

  private Work             work1, work2;

  private final List<WorkFlow>   enabledWorkFlowList  = new ArrayList<>();

  private final List<WorkFlow>   disabledWorkFlowList = new ArrayList<>();

  private final List<WorkFlow>   allWorkFlowList      = new ArrayList<>();

  private final List<Work>       allWorkList          = new ArrayList<>();

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    PROCESS_UTILS.close();
  }
  @Before
  public void setUp() throws Exception {
    this.processesService = new ProcessesServiceImpl(processesStorage, userAcl, identityManager);
    disabledWorkFlow = new WorkFlow();
    disabledWorkFlow.setEnabled(false);
    enabledWorkFlow = new WorkFlow();
    enabledWorkFlow.setId(1L);
    enabledWorkFlow.setEnabled(true);

    allWorkFlowList.add(disabledWorkFlow);
    allWorkFlowList.add(disabledWorkFlow);

    enabledWorkFlowList.add(enabledWorkFlow);
    disabledWorkFlowList.add(disabledWorkFlow);

    allWorkList.add(work1);
    allWorkList.add(work2);
  }

  @Test
  public void getWorkFlows() throws IllegalAccessException {

    ProcessesFilter processesFilter = new ProcessesFilter();
    processesFilter.setEnabled(true);
    processesFilter.setQuery("test");
    when(processesStorage.findWorkFlows(processesFilter, 0, 0, 10)).thenReturn(enabledWorkFlowList);
    Identity identity = mock(Identity.class);
    when(identityManager.getIdentity(1)).thenReturn(null);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.getWorkFlows(processesFilter, 0, 10, 1L));
    assertEquals("identity does not exist", exception.getMessage());
    verify(processesStorage, times(0)).findWorkFlows(processesFilter, 1L, 0, 10);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    processesService.getWorkFlows(processesFilter, 0, 10, 1L);
    verify(processesStorage, times(1)).findWorkFlows(processesFilter, 1L, 0, 10);
  }

  @Test
  public void getWorks() throws Exception {

    WorkFilter workFilter = new WorkFilter();
    workFilter.setQuery("test");
    when(processesStorage.getWorks(0L, workFilter, 0, 10)).thenReturn(allWorkList);
    assertEquals(processesService.getWorks(0L, workFilter, 0, 10), allWorkList);
  }

  @Test
  public void getWorkFlowByProjectId() throws Exception {

    when(processesStorage.getWorkFlowByProjectId(0L)).thenReturn(enabledWorkFlow);
    assertEquals(processesService.getWorkFlowByProjectId(0L).getId(), 1L);
  }

  @Test
  public void getWorkFlow() throws IllegalAccessException {
    Identity identity = mock(Identity.class);
    when(identityManager.getIdentity(1)).thenReturn(null);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.getWorkFlow(1L, 1L));
    assertEquals("identity does not exist", exception.getMessage());
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(processesStorage.getWorkFlowById(1L)).thenReturn(enabledWorkFlow);
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(false);
    PROCESS_UTILS.when(() -> isPlatformAdmin(any())).thenReturn(false);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processesService.getWorkFlow(1L, 1L));
    assertEquals("User with identity Id = 1  does not have the rights to access Process", exception.getMessage());
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(true);
    this.processesService.getWorkFlow(1L, 1L);
    verify(processesStorage, times(2)).getWorkFlowById(1l);
  }

  @Test
  public void countWorkFlows() throws IllegalAccessException {

    ProcessesFilter processesFilter = new ProcessesFilter();
    processesFilter.setEnabled(true);
    processesFilter.setQuery("test");
    when(processesStorage.countWorkFlows(processesFilter)).thenReturn(enabledWorkFlowList.size());
    assertEquals(processesService.countWorkFlows(processesFilter, 0L), enabledWorkFlowList.size());
  }

  @Test
  public void updateWorkflow() throws ObjectNotFoundException, IllegalAccessException {
    WorkFlow workFlow = new WorkFlow();
    WorkFlow updatedWorkflow = new WorkFlow();
    updatedWorkflow.setId(1L);
    updatedWorkflow.setDescription("anything");
    workFlow.setId(0L);
    Identity identity = mock(Identity.class);
    org.exoplatform.services.security.Identity userIdentity = mock(org.exoplatform.services.security.Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWorkFlow(null, 1l));
    assertEquals("Workflow Type is mandatory", exception.getMessage());
    verify(processesStorage, times(0)).getWorkById(1L);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWorkFlow(workFlow, 1l));
    assertEquals("workflow type id must not be equal to 0", exception.getMessage());
    verify(processesStorage, times(0)).getWorkById(1L);
    workFlow.setId(1L);
    when(identityManager.getIdentity(1)).thenReturn(null);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWorkFlow(workFlow, 1l));
    assertEquals("identity does not exist", exception.getMessage());
    verify(processesStorage, times(0)).getWorkById(1L);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(processesStorage.getWorkFlowById(workFlow.getId())).thenReturn(null);
    exception = assertThrows(ObjectNotFoundException.class, () -> this.processesService.updateWorkFlow(workFlow, 1l));
    assertEquals("oldWorkFlow does not exist", exception.getMessage());
    verify(processesStorage, times(0)).getWorkById(1L);
    when(processesStorage.getWorkFlowById(workFlow.getId())).thenReturn(workFlow);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWorkFlow(workFlow, 1l));
    assertEquals("there are no changes to save", exception.getMessage());
    when(processesStorage.getWorkFlowById(workFlow.getId())).thenReturn(updatedWorkflow);
    when(identity.getRemoteId()).thenReturn("userName");
    Set<String> manager = new HashSet<>();
    updatedWorkflow.setManager(manager);
    when(processesStorage.getWorkFlowById(workFlow.getId())).thenReturn(updatedWorkflow);
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(false);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processesService.updateWorkFlow(workFlow, 1l));
    assertEquals("User with identity Id = 1 does not have the rights to update this Process", exception.getMessage());
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(true);
    this.processesService.updateWorkFlow(workFlow, 1l);
    verify(processesStorage, times(1)).saveWorkFlow(workFlow, identity);
  }

  @Test
  public void createWorkflow() throws IllegalAccessException {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(1L);
    Identity identity = mock(Identity.class);
    org.exoplatform.services.security.Identity userIdentity = mock(org.exoplatform.services.security.Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.createWorkFlow(null, 1L));
    assertEquals("workFlow is mandatory", exception.getMessage());
    verify(processesStorage, times(0)).saveWorkFlow(workFlow, identity);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.createWorkFlow(workFlow, 1L));
    assertEquals("workFlow id must be equal to 0", exception.getMessage());
    verify(processesStorage, times(0)).saveWorkFlow(workFlow, identity);
    workFlow.setId(0L);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.createWorkFlow(workFlow, 1L));
    assertEquals("identity does not exist", exception.getMessage());
    verify(processesStorage, times(0)).saveWorkFlow(workFlow, identity);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(identity.getRemoteId()).thenReturn("userName");
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(false);
    PROCESS_UTILS.when(() -> isPlatformAdmin(any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processesService.createWorkFlow(workFlow, 1L));
    assertEquals("User with identity Id = 1 does not have the rights to add Process", exception.getMessage());
    verify(processesStorage, times(0)).saveWorkFlow(workFlow, identity);
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processesService.createWorkFlow(workFlow, 1L));
    assertEquals("User with identity Id = 1 does not have the rights to add Process", exception.getMessage());
    verify(processesStorage, times(0)).saveWorkFlow(workFlow, identity);
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(true);
    processesService.createWorkFlow(workFlow, 1L);
    verify(processesStorage, times(1)).saveWorkFlow(workFlow, identity);
  }

  @Test
  public void createWork() throws IllegalAccessException {
    Work work = new Work();
    work.setId(1L);
    Identity identity = mock(Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.createWork(null, 1L));
    assertEquals("work is mandatory", exception.getMessage());
    verify(processesStorage, times(0)).saveWork(work, identity);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.createWork(work, 1L));
    assertEquals("work id must be equal to 0", exception.getMessage());
    verify(processesStorage, times(0)).saveWork(work, identity);
    work.setId(0L);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.createWork(work, 1L));
    assertEquals("identity does not exist", exception.getMessage());
    verify(processesStorage, times(0)).saveWork(work, identity);
    org.exoplatform.services.security.Identity userIdentity = mock(org.exoplatform.services.security.Identity.class);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(identity.getRemoteId()).thenReturn("userName");
    when(userAcl.getUserIdentity("userName")).thenReturn(userIdentity);
    when(userAcl.isMemberOf(userIdentity, PROCESSES_GROUP)).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processesService.createWork(work, 1L));
    assertEquals("User with identity Id = 1 does not have the rights to create requests", exception.getMessage());
    verify(processesStorage, times(0)).saveWork(work, identity);
    when(userAcl.isMemberOf(userIdentity, PROCESSES_GROUP)).thenReturn(true);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processesService.createWork(work, 1L));
    assertEquals("User with identity Id = 1 does not have the rights to create requests", exception.getMessage());
    verify(processesStorage, times(0)).saveWork(work, identity);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(true);
    processesService.createWork(work, 1L);
    verify(processesStorage, times(1)).saveWork(work, identity);
  }

  @Test
  public void updateWork() throws ObjectNotFoundException, IllegalAccessException {
    Work work = new Work();
    work.setId(0L);
    Identity identity = mock(Identity.class);
    org.exoplatform.services.security.Identity userIdentity = mock(org.exoplatform.services.security.Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWork(null, 1L));
    assertEquals("Work is mandatory", exception.getMessage());
    verify(processesStorage, times(0)).saveWork(work, identity);

    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWork(work, 1L));
    assertEquals("work id must not be equal to 0", exception.getMessage());
    verify(processesStorage, times(0)).saveWork(work, identity);
    work.setId(1L);
    when(identityManager.getIdentity(1)).thenReturn(null);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWork(work, 1L));
    assertEquals("identity does not exist", exception.getMessage());
    verify(processesStorage, times(0)).saveWork(work, identity);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(identity.getRemoteId()).thenReturn("userName");
    when(userAcl.getUserIdentity("userName")).thenReturn(userIdentity);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processesService.updateWork(work, 1L));
    assertEquals("User with identity Id = 1  does not have the rights to update the request", exception.getMessage());
    verify(processesStorage, times(0)).saveWork(work, identity);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(true);
    when(processesStorage.getWorkById(work.getId())).thenReturn(null);
    exception = assertThrows(ObjectNotFoundException.class, () -> this.processesService.updateWork(work, 1L));
    assertEquals("oldWork does not exist", exception.getMessage());
    verify(processesStorage, times(0)).saveWork(work, identity);
    when(processesStorage.getWorkById(work.getId())).thenReturn(work);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWork(work, 1L));
    assertEquals("there are no changes to save", exception.getMessage());
    verify(processesStorage, times(0)).saveWork(work, identity);

    Work newWork = new Work();
    when(processesStorage.getWorkById(newWork.getId())).thenReturn(newWork);
    newWork.setId(work.getId());
    newWork.setDescription("anything");
    processesService.updateWork(newWork, 1L);
    verify(processesStorage, times(1)).saveWork(newWork, identity);
  }

  @Test
  public void countWorksByWorkflow() throws Exception {
    Identity identity = mock(Identity.class);
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(1L);
    Throwable exception = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.countWorksByWorkflow(null, 1L, false));
    assertEquals("Project Id is mandatory", exception.getMessage());
    verify(processesStorage, times(0)).countWorksByWorkflow(1L, false);

    exception =
            assertThrows(IllegalArgumentException.class, () -> this.processesService.countWorksByWorkflow(1L, 1L, null));
    assertEquals("isCompleted should not be null", exception.getMessage());
    verify(processesStorage, times(0)).countWorksByWorkflow(1L, false);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.countWorksByWorkflow(1L, 1L, false));
    assertEquals("identity does not exist", exception.getMessage());
    verify(processesStorage, times(0)).countWorksByWorkflow(1L, false);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    exception = assertThrows(ObjectNotFoundException.class, () -> this.processesService.countWorksByWorkflow(1L, 1L, false));
    assertEquals("Workflow related to the project Id 1 not found", exception.getMessage());
    verify(processesStorage, times(0)).countWorksByWorkflow(1L, false);
    when(processesStorage.getWorkFlowByProjectId(1)).thenReturn(workFlow);
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(false);
    PROCESS_UTILS.when(() -> isPlatformAdmin(any())).thenReturn(false);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processesService.countWorksByWorkflow(1L, 1L, false));
    assertEquals("User with identity Id = 1 does not have the rights to count requests for the process", exception.getMessage());
    verify(processesStorage, times(0)).countWorksByWorkflow(1L, false);
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(true);
    processesService.countWorksByWorkflow(1L, 1L, false);
    verify(processesStorage, times(1)).countWorksByWorkflow(1L, false);
  }

  @Test
  public void deleteWorkById() throws ObjectNotFoundException, IllegalAccessException {
    Identity identity = mock(Identity.class);
    Work work = new Work();
    work.setId(1L);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.deleteWorkById(null, 1L));
    assertEquals("Work id is mandatory", exception.getMessage());
    verify(processesStorage, times(0)).deleteWorkById(anyLong());
    when(identityManager.getIdentity(1)).thenReturn(null);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.deleteWorkById(1L, 1L));
    assertEquals("identity does not exist", exception.getMessage());
    verify(processesStorage, times(0)).deleteWorkById(1L);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(processesStorage.getWorkById(1L, 1L)).thenReturn(null);
    exception = assertThrows(ObjectNotFoundException.class, () -> this.processesService.deleteWorkById(1L, 1L));
    assertEquals("Work is not found", exception.getMessage());
    verify(processesStorage, times(0)).deleteWorkById(1L);
    work.setCreatedBy("user2");
    when(processesStorage.getWorkById(1L, 1L)).thenReturn(work);
    when(identity.getRemoteId()).thenReturn("user1");
    exception = assertThrows(IllegalAccessException.class, () -> this.processesService.deleteWorkById(1L, 1L));
    assertEquals("User with identity Id = 1 does not have the rights to access the request", exception.getMessage());
    verify(processesStorage, times(0)).deleteWorkById(1L);
    when(identity.getRemoteId()).thenReturn("user2");
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processesService.deleteWorkById(1L, 1L));
    assertEquals("User with identity Id = 1 does not have the rights to delete the request", exception.getMessage());
    verify(processesStorage, times(0)).deleteWorkById(1L);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(true);
    processesService.deleteWorkById(1L, 1L);
    verify(processesStorage, times(1)).deleteWorkById(1L);
  }

  @Test
  public void createWorkDraft() throws IllegalAccessException {
    Work work = new Work();
    work.setId(1L);
    Identity identity = mock(Identity.class);
    org.exoplatform.services.security.Identity userIdentity = mock(org.exoplatform.services.security.Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.createWorkDraft(null, 1L));
    assertEquals("WorkDraft is mandatory", exception.getMessage());
    verify(processesStorage, times(0)).saveWorkDraft(work, 1L);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.createWorkDraft(work, 1L));
    assertEquals("WorkDraft id must be equal to 0", exception.getMessage());
    verify(processesStorage, times(0)).saveWorkDraft(work, 1L);
    work.setId(0L);
    when(identityManager.getIdentity(1)).thenReturn(null);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.createWorkDraft(work, 1L));
    assertEquals("identity does not exist", exception.getMessage());
    verify(processesStorage, times(0)).saveWorkDraft(work, 1L);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(identity.getRemoteId()).thenReturn("userName");
    when(userAcl.getUserIdentity("userName")).thenReturn(userIdentity);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processesService.createWorkDraft(work, 1L));
    assertEquals("User with identity Id = 1 does not have the rights to create requests", exception.getMessage());
    verify(processesStorage, times(0)).saveWorkDraft(work, 1L);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(true);
    processesService.createWorkDraft(work, 1L);
    verify(processesStorage, times(1)).saveWorkDraft(work, 1L);
  }

  @Test
  public void updateWorkDraft() throws ObjectNotFoundException, IllegalAccessException {
    Work work = new Work();
    work.setId(0L);
    Identity identity = mock(Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWorkDraft(null, 1L));
    assertEquals("WorkDraft Type is mandatory", exception.getMessage());
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWorkDraft(work, 1L));
    assertEquals("WorkDraft type id must not be equal to 0", exception.getMessage());
    work.setId(1L);
    when(identityManager.getIdentity(1)).thenReturn(null);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWorkDraft(work, 1L));
    assertEquals("identity does not exist", exception.getMessage());
    verify(processesStorage, times(0)).saveWorkDraft(work, 1L);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(processesStorage.getWorkDraftyId(1L)).thenReturn(null);
    exception = assertThrows(ObjectNotFoundException.class, () -> this.processesService.updateWorkDraft(work, 1L));
    assertEquals("oldWorkDraft is not exist", exception.getMessage());
    verify(processesStorage, times(0)).saveWorkDraft(work, 1L);
    when(processesStorage.getWorkDraftyId(1L)).thenReturn(work);
    Work newWork = new Work();
    newWork.setId(work.getId());
    newWork.setDescription("test");
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWorkDraft(work, 1L));
    assertEquals("there are no changes to save", exception.getMessage());
    verify(processesStorage, times(0)).saveWorkDraft(newWork, 1L);
    work.setCreatedBy("2");
    exception = assertThrows(IllegalAccessException.class, () -> this.processesService.updateWorkDraft(newWork, 1L));
    assertEquals("User with identity Id = 1 does not have the rights to update this draft", exception.getMessage());
    verify(processesStorage, times(0)).saveWorkDraft(newWork, 1L);
    work.setCreatorId(1);
    when(processesStorage.getWorkDraftyId(1L)).thenReturn(work);
    processesService.updateWorkDraft(newWork, 1L);
    verify(processesStorage, times(1)).saveWorkDraft(newWork, 1L);
  }

  @Test
  public void getWorkDrafts() {
    List<Work> workList = new ArrayList<>();
    WorkFilter workFilter = new WorkFilter();
    workFilter.setIsDraft(true);
    workFilter.setQuery("test");
    workList.add(new Work());
    when(processesStorage.findAllWorkDraftsByUser(workFilter, 0, 10, 1L)).thenReturn(workList);
    List<Work> list = processesService.getWorkDrafts(1L, workFilter, 0, 10);
    verify(processesStorage, times(1)).findAllWorkDraftsByUser(workFilter, 0, 10, 1L);
    assertEquals(list, workList);
  }

  @Test
  public void deleteWorkDraftById() throws IllegalAccessException, ObjectNotFoundException {
    Identity identity = mock(Identity.class);
    Work work = new Work();
    org.exoplatform.services.security.Identity userIdentity = mock(org.exoplatform.services.security.Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.deleteWorkDraftById(null, 1L));
    assertEquals("WorkDraft id is mandatory", exception.getMessage());
    verify(processesStorage, times(0)).deleteWorkDraftById(1L);
    when(identityManager.getIdentity(1)).thenReturn(null);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.deleteWorkDraftById(1L, 1L));
    assertEquals("identity does not exist", exception.getMessage());
    verify(processesStorage, times(0)).deleteWorkDraftById(1L);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(identity.getRemoteId()).thenReturn("userName");
    when(userAcl.getUserIdentity("userName")).thenReturn(userIdentity);
    when(userAcl.isMemberOf(userIdentity, PROCESSES_GROUP)).thenReturn(false);
    exception = assertThrows(ObjectNotFoundException.class, () -> this.processesService.deleteWorkDraftById(1L, 1L));
    assertEquals("WorkDraft is not found", exception.getMessage());
    verify(processesStorage, times(0)).deleteWorkDraftById(1L);
    work.setCreatorId(2L);
    when(processesStorage.getWorkDraftyId(1L)).thenReturn(work);
    exception = assertThrows(IllegalAccessException.class, () -> this.processesService.deleteWorkDraftById(1L, 1L));
    assertEquals("User with identity Id = 1 does not have the rights to delete the draft", exception.getMessage());
    verify(processesStorage, times(0)).deleteWorkDraftById(1L);
    work.setCreatorId(1L);
    when(processesStorage.getWorkDraftyId(1L)).thenReturn(work);
    processesService.deleteWorkDraftById(1L, 1L);
    verify(processesStorage, times(1)).deleteWorkDraftById(1L);
  }

  @Test
  public void getWorkById() throws IllegalAccessException {
    Identity identity = mock(Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.getWorkById(1L, null));
    assertEquals("Work id is mandatory", exception.getMessage());
    verify(processesStorage, times(0)).getWorkById(1L, 1L);
    when(identityManager.getIdentity(1)).thenReturn(null);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.getWorkById(1L, 1L));
    assertEquals("identity does not exist", exception.getMessage());
    verify(processesStorage, times(0)).getWorkById(1L, 1L);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    processesService.getWorkById(1L, 1L);
    verify(processesStorage, times(1)).getWorkById(1L, 1L);
  }

  @Test
  public void updateWorkCompleted() throws ObjectNotFoundException, IllegalAccessException {
    Work work = new Work();
    work.setId(1L);
    Identity identity = mock(Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.updateWorkCompleted(null, 1L, true));
    assertEquals("Work id is mandatory", exception.getMessage());
    verify(processesStorage, times(0)).updateWorkCompleted(1L, true);
    when(identityManager.getIdentity(1)).thenReturn(null);
    exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.updateWorkCompleted(1L, 1L, true));
    assertEquals("identity does not exist", exception.getMessage());
    verify(processesStorage, times(0)).updateWorkCompleted(1L, true);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    exception = assertThrows(ObjectNotFoundException.class, () -> this.processesService.updateWorkCompleted(1L, 1L, true));
    assertEquals("Work is not found", exception.getMessage());
    verify(processesStorage, times(0)).updateWorkCompleted(1L, true);
    when(processesStorage.getWorkById(1L, 1L)).thenReturn(work);
    work.setCreatedBy("user2");
    when(processesStorage.getWorkById(1L, 1L)).thenReturn(work);
    when(identity.getRemoteId()).thenReturn("user1");
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processesService.updateWorkCompleted(1L, 1L, true));
    assertEquals("User with identity Id = 1 does not have the rights to access the request", exception.getMessage());
    verify(processesStorage, times(0)).updateWorkCompleted(1L, true);
    when(identity.getRemoteId()).thenReturn("user1");
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processesService.updateWorkCompleted(1L, 1L, true));
    assertEquals("User with identity Id = 1 does not have the rights to access the request", exception.getMessage());
    verify(processesStorage, times(0)).updateWorkCompleted(1L, true);
    when(identity.getRemoteId()).thenReturn("user2");
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(true);
    processesService.updateWorkCompleted(1L, 1L, true);
    verify(processesStorage, times(1)).updateWorkCompleted(1L, true);
  }

  @Test
  public void getIllustrationImageById() throws ObjectNotFoundException, IOException, FileStorageException {
    Throwable exception =
            assertThrows(IllegalArgumentException.class, () -> this.processesService.getIllustrationImageById(null, 1L));
    assertEquals("IllustrationId id is mandatory", exception.getMessage());
    verify(processesStorage, times(0)).getIllustrationImageById(1L);
    processesService.getIllustrationImageById(1L, 1L);
    verify(processesStorage, times(1)).getIllustrationImageById(1L);
  }

  @Test
  public void deleteWorkflowById() throws ObjectNotFoundException, IOException, FileStorageException, IllegalAccessException {
    WorkFlow workFlow = new WorkFlow();
    Identity identity = mock(Identity.class);
    workFlow.setId(1L);
    when(identityManager.getIdentity(1)).thenReturn(null);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> this.processesService.deleteWorkflowById(1L, 1l));
    assertEquals("identity does not exist", exception.getMessage());
    verify(processesStorage, times(0)).deleteWorkflowById(1L);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(processesStorage.getWorkFlowById(workFlow.getId())).thenReturn(null);
    exception = assertThrows(ObjectNotFoundException.class, () -> this.processesService.deleteWorkflowById(1L, 1l));
    assertEquals("Workflow does not exist", exception.getMessage());
    verify(processesStorage, times(0)).deleteWorkflowById(1L);
    when(processesStorage.getWorkFlowById(workFlow.getId())).thenReturn(workFlow);
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(false);
    PROCESS_UTILS.when(() -> isProcessManager(any(), any())).thenReturn(false);
    PROCESS_UTILS.when(() -> isPlatformAdmin(any())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> this.processesService.deleteWorkflowById(1L, 1l));
    assertEquals("User with identity Id = 1  does not have the rights to access Process", exception.getMessage());
    PROCESS_UTILS.when(() -> isProcessAdmin(any())).thenReturn(true);
    this.processesService.deleteWorkflowById(1L, 1l);
    verify(processesStorage, times(1)).deleteWorkflowById(1L);
  }
}
