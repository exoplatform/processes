package org.exoplatform.processes.storage;

import org.exoplatform.processes.Utils.EntityMapper;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.rest.util.EntityBuilder;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.StatusService;
import org.exoplatform.task.service.TaskService;
import org.exoplatform.task.util.ProjectUtil;
import org.exoplatform.task.util.UserUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({EntityMapper.class, UserUtil.class, ProjectUtil.class})
public class ProcessesStorageImplTest {

  @Mock
  private WorkFlowDAO      workFlowDAO;

  @Mock
  private IdentityManager  identityManager;

  @Mock
  private TaskService      taskService;

  @Mock
  private ProjectService   projectService;

  @Mock
  private StatusService    statusService;

  @Mock
  private SpaceService spaceService;

  private ProcessesStorage processesStorage;

  @Before
  public void setUp() throws Exception {
    this.processesStorage = new ProcessesStorageImpl(workFlowDAO, taskService, projectService, statusService, identityManager, spaceService);
    PowerMockito.mockStatic(EntityMapper.class);
    PowerMockito.mockStatic(UserUtil.class);
    PowerMockito.mockStatic(ProjectUtil.class);
  }

  @Test
  public void deleteWorkflowById() throws EntityNotFoundException {
    WorkFlowEntity workFlowEntity = mock(WorkFlowEntity.class);
    ProjectDto projectDto = mock(ProjectDto.class);
    when(workFlowDAO.find(1l)).thenReturn(null);
    Throwable exception = assertThrows(javax.persistence.EntityNotFoundException.class,
                                       () -> this.processesStorage.deleteWorkflowById(1l));
    assertEquals("Workflow not found", exception.getMessage());
    verify(projectService, times(0)).getProject(0l);
    verify(workFlowDAO, times(0)).delete(workFlowEntity);
    when(workFlowDAO.find(1l)).thenReturn(workFlowEntity);
    when(projectService.getProject(1l)).thenReturn(projectDto);
    when(projectDto.getId()).thenReturn(0l);
    this.processesStorage.deleteWorkflowById(1l);
    verify(projectService, times(1)).getProject(0l);
    verify(workFlowDAO, times(1)).delete(workFlowEntity);
  }

  @Test
  public void saveWorkflow() throws Exception {

    Identity identity = mock(Identity.class);
    WorkFlow workFlow = mock(WorkFlow.class);
    List<String > memberships = new ArrayList<>();
    memberships.add("manager");
    memberships.add("participator");
    Set<String> managers = new HashSet<String>(Arrays.asList(memberships.get(0)));
    Set<String> participators = new HashSet<String>(Arrays.asList(memberships.get(1)));
    ProjectDto projectDto = mock(ProjectDto.class);
    Space space = mock(Space.class);
    WorkFlowEntity workFlowEntity = mock(WorkFlowEntity.class);
    Throwable exception1 = assertThrows(IllegalArgumentException.class,
            () -> this.processesStorage.saveWorkFlow(null, 1l));
    assertEquals("workflow argument is null", exception1.getMessage());

    when(identityManager.getIdentity("1")).thenReturn(null);
    Throwable exception2 = assertThrows(IllegalArgumentException.class,
            () -> this.processesStorage.saveWorkFlow(workFlow, 1l));
    assertEquals("identity is not exist", exception2.getMessage());

    when(EntityMapper.toEntity(workFlow)).thenReturn(workFlowEntity);
    when(workFlow.getId()).thenReturn(0L);
    when(workFlow.getProjectId()).thenReturn(0L);

    when(identityManager.getIdentity("1")).thenReturn(identity);
    when(space.getGroupId()).thenReturn("/spaces/processes_space");
    when(spaceService.getSpaceByGroupId("/spaces/processes_space")).thenReturn(space);
    when(UserUtil.getSpaceMemberships(space.getGroupId())).thenReturn(memberships);
    workFlow.setDescription("anything");
    workFlow.setTitle("title");
    when(ProjectUtil.newProjectInstanceDto(workFlow.getTitle(), workFlow.getDescription(), managers, participators)).thenReturn(projectDto);
    projectDto.setId(1L);
    when(projectService.createProject(projectDto)).thenReturn(projectDto);
    PowerMockito.doNothing().when(statusService).createInitialStatuses(projectDto);
    this.processesStorage.saveWorkFlow(workFlow, 1L);
    verify(workFlowDAO, times(1)).create(workFlowEntity);
    when(workFlow.getId()).thenReturn(1L);
    this.processesStorage.saveWorkFlow(workFlow, 1L);
    verify(workFlowDAO, times(1)).update(workFlowEntity);
  }

  @Test
  public void countWorksByWorkflow() throws Exception {
    processesStorage.countWorksByWorkflow(1L, false);
    verify(taskService, times(1)).countTasks(any());
    processesStorage.countWorksByWorkflow(1L, true);
    verify(taskService, times(2)).countTasks(any());

  }
}
