package org.exoplatform.processes.service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.storage.ProcessesStorage;
import org.exoplatform.social.core.manager.IdentityManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
public class ProcessesServiceImplTest {

  @Mock
  private IdentityManager  identityManager;

  @Mock
  private ProcessesStorage processesStorage;

  private ProcessesService processesService;

  private WorkFlow         disabledWorkFlow, enabledWorkFlow;

  private List<WorkFlow>   enabledWorkFlowList  = new ArrayList<>();;

  private List<WorkFlow>   disabledWorkFlowList = new ArrayList<>();

  private List<WorkFlow>   allWorkFlowList      = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    this.processesService = new ProcessesServiceImpl(processesStorage, identityManager);
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

    when(processesStorage.findAllWorkFlows(0, 10)).thenReturn(allWorkFlowList);
    when(processesStorage.findEnabledWorkFlows(0, 10)).thenReturn(enabledWorkFlowList);
    when(processesStorage.findDisabledWorkFlows(0, 10)).thenReturn(disabledWorkFlowList);

    List<WorkFlow> allResult = processesService.getWorkFlows(processesFilter, 0, 10, 0L);
    assertEquals(allWorkFlowList, allResult);
    assertEquals(2, allResult.size());

    processesFilter.setEnabled(true);
    List<WorkFlow> enabledResult = processesService.getWorkFlows(processesFilter, 0, 10, 0L);
    assertEquals(enabledWorkFlowList, enabledResult);
    assertEquals(1, enabledResult.size());
    assertTrue(enabledResult.get(0).isEnabled());

    processesFilter.setEnabled(false);
    List<WorkFlow> disabledResult = processesService.getWorkFlows(processesFilter, 0, 10, 0L);
    assertEquals(disabledWorkFlowList, disabledResult);
    assertEquals(1, disabledResult.size());
    assertFalse(disabledResult.get(0).isEnabled());

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
}
