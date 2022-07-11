package org.exoplatform.processes.service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFilter;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ExoContainerContext.class, PortalContainer.class, PropertyManager.class, RequestLifeCycle.class})
public class ProcessesServiceImplTest {



  @Mock
  private ProcessesStorage processesStorage;

  private ProcessesService processesService;

  private WorkFlow         disabledWorkFlow, enabledWorkFlow;

  private List<WorkFlow>   enabledWorkFlowList  = new ArrayList<>();;

  private List<WorkFlow>   disabledWorkFlowList = new ArrayList<>();

  private List<WorkFlow>   allWorkFlowList      = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    this.processesService = new ProcessesServiceImpl(processesStorage);
    disabledWorkFlow = new WorkFlow();
    disabledWorkFlow.setEnabled(false);
    enabledWorkFlow = new WorkFlow();
    enabledWorkFlow.setEnabled(true);

    allWorkFlowList.add(disabledWorkFlow);
    allWorkFlowList.add(disabledWorkFlow);

    enabledWorkFlowList.add(enabledWorkFlow);
    disabledWorkFlowList.add(disabledWorkFlow);
  }

  @Test
  public void getWorkFlows() throws IllegalAccessException {

    ProcessesFilter processesFilter = new ProcessesFilter();
    processesFilter.setEnabled(true);
    processesFilter.setQuery("test");
    when(processesStorage.findWorkFlows(processesFilter, 0,0, 10)).thenReturn(enabledWorkFlowList);

    List<WorkFlow> enabledResult = processesService.getWorkFlows(processesFilter, 0, 10, 0L);
    assertEquals(enabledWorkFlowList, enabledResult);
    assertEquals(1, enabledResult.size());
    assertTrue(enabledResult.get(0).isEnabled());

  }

  @Test
  public void updateWorkflow() throws ObjectNotFoundException, IllegalAccessException {
    WorkFlow workFlow = new WorkFlow();
    WorkFlow updatedWorkflow = new WorkFlow();
    updatedWorkflow.setId(1L);
    updatedWorkflow.setDescription("anything");
    workFlow.setId(0L);
    Throwable exception1 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.updateWorkFlow(null, 1l));
    assertEquals("Workflow Type is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).getWorkById(1L);

    Throwable exception2 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.updateWorkFlow(workFlow, 1l));
    assertEquals("workflow type id must not be equal to 0", exception2.getMessage());
    verify(processesStorage, times(0)).getWorkById(1L);

    workFlow.setId(1L);
    when(processesStorage.getWorkFlowById(workFlow.getId())).thenReturn(null);
    Throwable exception3 = assertThrows(ObjectNotFoundException.class,
            () -> this.processesService.updateWorkFlow(workFlow, 1l));
    assertEquals("oldWorkFlow is not exist", exception3.getMessage());

    when(processesStorage.getWorkFlowById(workFlow.getId())).thenReturn(workFlow);
    Throwable exception4 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.updateWorkFlow(workFlow, 1l));
    assertEquals("there are no changes to save", exception4.getMessage());


    when(processesStorage.getWorkFlowById(workFlow.getId())).thenReturn(updatedWorkflow);
    this.processesService.updateWorkFlow(workFlow, 1l);
    verify(processesStorage, times(1)).saveWorkFlow(workFlow, 1L);
  }

  @Test
  public void createWorkflow() throws IllegalAccessException {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(1L);
    Throwable exception1 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.createWorkFlow(null, 1L));
    assertEquals("workFlow is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).saveWorkFlow(workFlow, 1L);

    Throwable exception2 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.createWorkFlow(workFlow, 1L));
    assertEquals("workFlow id must be equal to 0", exception2.getMessage());
    verify(processesStorage, times(0)).saveWorkFlow(workFlow, 1L);

    workFlow.setId(0L);
    processesService.createWorkFlow(workFlow, 1L);
    verify(processesStorage, times(1)).saveWorkFlow(workFlow, 1L);
  }

  @Test
  public void createWork() throws IllegalAccessException {
    Work work = new Work();
    work.setId(1L);
    Throwable exception1 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.createWork(null, 1L));
    assertEquals("work is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).saveWork(work, 1L);

    Throwable exception2 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.createWork(work, 1L));
    assertEquals("work id must be equal to 0", exception2.getMessage());
    verify(processesStorage, times(0)).saveWork(work, 1L);

    work.setId(0L);
    processesService.createWork(work, 1L);
    verify(processesStorage, times(1)).saveWork(work, 1L);
  }

  @Test
  public void updateWork() throws ObjectNotFoundException, IllegalAccessException {
    Work work = new Work();
    work.setId(0L);
    Throwable exception1 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.updateWork(null, 1L));
    assertEquals("Work is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).saveWork(work, 1L);

    Throwable exception2 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.updateWork(work, 1L));
    assertEquals("work id must not be equal to 0", exception2.getMessage());
    verify(processesStorage, times(0)).saveWork(work, 1L);

    work.setId(1L);
    when(processesStorage.getWorkById(work.getId())).thenReturn(null);
    Throwable exception3 = assertThrows(ObjectNotFoundException.class,
            () -> this.processesService.updateWork(work, 1L));
    assertEquals("oldWork is not exist", exception3.getMessage());
    verify(processesStorage, times(0)).saveWork(work, 1L);

    when(processesStorage.getWorkById(work.getId())).thenReturn(work);
    Throwable exception4 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.updateWork(work, 1L));
    assertEquals("there are no changes to save", exception4.getMessage());
    verify(processesStorage, times(0)).saveWork(work, 1L);

    Work newWork = new Work();
    when(processesStorage.getWorkById(newWork.getId())).thenReturn(newWork);
    newWork.setId(work.getId());
    newWork.setDescription("anything");
    processesService.updateWork(newWork, 1L);
    verify(processesStorage, times(1)).saveWork(newWork, 1L);
  }

  @Test
  public void countWorksByWorkflow() throws Exception {
    Throwable exception1 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.countWorksByWorkflow(null, false));
    assertEquals("Project Id is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).countWorksByWorkflow(1L, false);

    Throwable exception2 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.countWorksByWorkflow(1L, null));
    assertEquals("isCompleted should not be null", exception2.getMessage());
    verify(processesStorage, times(0)).countWorksByWorkflow(1L, false);

    processesService.countWorksByWorkflow(1L, false);
    verify(processesStorage, times(1)).countWorksByWorkflow(1L, false);
  }

  @Test
  public void deleteWorkById() {
    Throwable exception1 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.deleteWorkById(null));
    assertEquals("Work Id is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).deleteWorkById(anyLong());
    processesService.deleteWorkById(1L);
    verify(processesStorage, times(1)).deleteWorkById(1L);
  }

  @Test
  public void createWorkDraft() {
    Work work = new Work();
    work.setId(1L);
    Throwable exception1 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.createWorkDraft(null, 1L));
    assertEquals("WorkDraft is mandatory", exception1.getMessage());
    Throwable exception2 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.createWorkDraft(work, 1L));
    assertEquals("WorkDraft id must be equal to 0", exception2.getMessage());
    work.setId(0L);
    processesService.createWorkDraft(work, 1L);
    verify(processesStorage, times(1)).saveWorkDraft(work, 1L);
  }

  @Test
  public void updateWorkDraft() throws ObjectNotFoundException {
    Work work = new Work();
    work.setId(0L);
    Throwable exception1 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.updateWorkDraft(null, 1L));
    assertEquals("WorkDraft Type is mandatory", exception1.getMessage());
    Throwable exception2 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.updateWorkDraft(work, 1L));
    assertEquals("WorkDraft type id must not be equal to 0", exception2.getMessage());
    work.setId(1L);
    when(processesStorage.getWorkDraftyId(1L)).thenReturn(null);
    Throwable exception3 = assertThrows(ObjectNotFoundException.class,
            () -> this.processesService.updateWorkDraft(work, 1L));
    assertEquals("oldWorkDraft is not exist", exception3.getMessage());
    when(processesStorage.getWorkDraftyId(1L)).thenReturn(work);
    Work newWork = new Work();
    newWork.setId(work.getId());
    newWork.setDescription("test");
    Throwable exception4 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.updateWorkDraft(work, 1L));
    assertEquals("there are no changes to save", exception4.getMessage());
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
  public void deleteWorkDraftById() {
    Throwable exception1 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.deleteWorkDraftById(null));
    assertEquals("WorkDraft id is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).deleteWorkDraftById(1L);
    processesService.deleteWorkDraftById(1L);
    verify(processesStorage, times(1)).deleteWorkDraftById(1L);
  }

  @Test
  public void getWorkById() {
    Throwable exception1 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.getWorkById(1L, null));
    assertEquals("Work id is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).getWorkById(1L, 1L);
    processesService.getWorkById(1L, 1L);
    verify(processesStorage, times(1)).getWorkById(1L, 1L);
  }

  @Test
  public void updateWorkCompleted() {
    Throwable exception1 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.updateWorkCompleted(null, false));
    assertEquals("Work id is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).updateWorkCompleted(1L, false);
    processesService.updateWorkCompleted(1L, true);
    verify(processesStorage, times(1)).updateWorkCompleted(1L, true);
  }

  @Test
  public void getIllustrationImageById() throws ObjectNotFoundException, IOException, FileStorageException {
    Throwable exception1 = assertThrows(IllegalArgumentException.class,
            () -> this.processesService.getIllustrationImageById(null));
    assertEquals("IllustrationId id is mandatory", exception1.getMessage());
    verify(processesStorage, times(0)).getIllustrationImageById(1L);
    processesService.getIllustrationImageById(1L);
    verify(processesStorage, times(1)).getIllustrationImageById(1L);
  }
}
