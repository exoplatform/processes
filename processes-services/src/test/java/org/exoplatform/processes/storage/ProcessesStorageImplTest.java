package org.exoplatform.processes.storage;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.*;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.spy;

import java.util.*;

import javax.ws.rs.ext.RuntimeDelegate;

import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.model.FileInfo;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.processes.Utils.EntityMapper;
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.dao.WorkDraftDAO;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.WorkEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.*;
import org.exoplatform.processes.rest.util.EntityBuilder;
import org.exoplatform.processes.service.ProcessesAttachmentService;
import org.exoplatform.services.attachments.model.Attachment;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.organization.Membership;
import org.exoplatform.services.organization.MembershipHandler;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.impl.MembershipImpl;
import org.exoplatform.services.rest.impl.RuntimeDelegateImpl;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dao.TaskQuery;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.dto.StatusDto;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.exception.EntityNotFoundException;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.StatusService;
import org.exoplatform.task.service.TaskService;
import org.exoplatform.task.util.ProjectUtil;
import org.exoplatform.task.util.UserUtil;

@RunWith(PowerMockRunner.class)
public class ProcessesStorageImplTest {

  @Mock
  private WorkFlowDAO                workFlowDAO;

  @Mock
  private WorkDraftDAO               workDraftDAO;

  @Mock
  private IdentityManager            identityManager;

  @Mock
  private TaskService                taskService;

  @Mock
  private ProjectService             projectService;

  @Mock
  private StatusService              statusService;

  @Mock
  private SpaceService               spaceService;

  @Mock
  private MembershipHandler          membershipHandler;

  private ListenerService            listenerService;

  @Mock
  private ProcessesAttachmentService processesAttachmentService;

  @Mock
  private FileService                fileService;

  @Mock
  private OrganizationService        organizationService;

  private ProcessesStorage           processesStorage;

  @Before
  public void setUp() throws Exception {
    RuntimeDelegate.setInstance(new RuntimeDelegateImpl());
    listenerService = new ListenerService(new ExoContainerContext(null));
    listenerService = spy(listenerService);
    this.processesStorage = new ProcessesStorageImpl(workFlowDAO,
                                                     workDraftDAO,
                                                     taskService,
                                                     projectService,
                                                     statusService,
                                                     identityManager,
                                                     spaceService,
                                                     listenerService,
                                                     processesAttachmentService,
                                                     fileService,
                                                     organizationService);

  }

  @Test
  public void deleteWorkflowById() throws EntityNotFoundException {
    WorkFlowEntity workFlowEntity = mock(WorkFlowEntity.class);
    ProjectDto projectDto = mock(ProjectDto.class);
    List<WorkEntity> drafts = new ArrayList<>();
    drafts.add(new WorkEntity());
    when(workFlowDAO.find(1l)).thenReturn(null);
    Throwable exception = assertThrows(javax.persistence.EntityNotFoundException.class,
                                       () -> this.processesStorage.deleteWorkflowById(1l));
    assertEquals("Workflow not found", exception.getMessage());
    verify(projectService, times(0)).getProject(0l);
    verify(workFlowDAO, times(0)).delete(workFlowEntity);
    when(workFlowDAO.find(1l)).thenReturn(workFlowEntity);
    when(projectService.getProject(1L)).thenReturn(projectDto);
    when(projectDto.getId()).thenReturn(1L);
    when(workFlowEntity.getProjectId()).thenReturn(1L);
    when(workDraftDAO.getDraftsByWorkflowId(1L)).thenReturn(drafts);
    this.processesStorage.deleteWorkflowById(1L);
    verify(workDraftDAO, times(1)).deleteAll(drafts);
    verify(projectService, times(1)).getProject(1L);
    verify(workFlowDAO, times(1)).delete(workFlowEntity);
    when(projectService.getProject(1L)).thenThrow(new EntityNotFoundException(1L, Object.class));
    this.processesStorage.deleteWorkflowById(1L);
    Throwable exception2 = assertThrows(EntityNotFoundException.class, () -> this.projectService.getProject(1L));
    assertEquals("Object does not exist with ID: 1", exception2.getMessage());
  }

  @Test
  @PrepareForTest({ EntityMapper.class, UserUtil.class, ProjectUtil.class, ProcessesUtils.class })
  public void saveWorkflow() throws EntityNotFoundException {
    PowerMockito.mockStatic(EntityMapper.class);
    PowerMockito.mockStatic(UserUtil.class);
    PowerMockito.mockStatic(ProjectUtil.class);
    PowerMockito.mockStatic(ProcessesUtils.class);

    IllustrativeAttachment illustrativeAttachment = new IllustrativeAttachment(null,
                                                                               "image.png",
                                                                               "image/png",
                                                                               1365L,
                                                                               new Date().getTime());
    List<Attachment> attachments = new ArrayList<>();
    Attachment attachment = new Attachment();
    attachment.setId("1");
    attachments.add(attachment);
    Identity identity = mock(Identity.class);
    WorkFlow workFlow = mock(WorkFlow.class);
    List<String> memberships = new ArrayList<>();
    memberships.add("manager");
    memberships.add("participator");
    Set<String> managers = new HashSet<>(Arrays.asList(memberships.get(0)));
    Set<String> participators = new HashSet<>(Arrays.asList(memberships.get(1)));
    ProjectDto projectDto = new ProjectDto();
    Space space = mock(Space.class);
    WorkFlowEntity workFlowEntity = new WorkFlowEntity();
    Throwable exception1 = assertThrows(IllegalArgumentException.class, () -> this.processesStorage.saveWorkFlow(null, 1l));
    assertEquals("workflow argument is null", exception1.getMessage());

    when(identityManager.getIdentity("1")).thenReturn(null);
    Throwable exception2 = assertThrows(IllegalArgumentException.class, () -> this.processesStorage.saveWorkFlow(workFlow, 1l));
    assertEquals("identity is not exist", exception2.getMessage());

    when(EntityMapper.toEntity(workFlow)).thenReturn(workFlowEntity);
    when(workFlow.getId()).thenReturn(0L);
    when(workFlow.getProjectId()).thenReturn(0L);
    when(workFlow.getSpaceId()).thenReturn("1");
    when(projectService.getProject(workFlow.getProjectId())).thenReturn(projectDto);
    when(identityManager.getIdentity("1")).thenReturn(identity);
    when(space.getGroupId()).thenReturn("/spaces/processes_space");
    when(space.getId()).thenReturn("2");
    when(spaceService.getSpaceByGroupId("/spaces/processes_space")).thenReturn(space);
    when(spaceService.getSpaceById("1")).thenReturn(space);
    when(UserUtil.getSpaceMemberships(space.getGroupId())).thenReturn(memberships);
    workFlow.setDescription("anything");
    workFlow.setTitle("title");
    workFlow.setProjectId(1L);
    workFlow.setSpaceId("1");
    List<CreatorIdentityEntity> identityEntities = new ArrayList<>();
    ProfileEntity profile =
                          new ProfileEntity(StringUtils.isNotEmpty(space.getAvatarUrl()) ? space.getAvatarUrl()
                                                                                         : "/portal/rest/v1/social/spaces/"
                                                                                             + space.getPrettyName() + "/avatar",
                                            space.getDisplayName());
    IdentityEntity identityEntity = new IdentityEntity("", space.getPrettyName(), "space", profile);
    identityEntities.add(new CreatorIdentityEntity(identityEntity));
    profile = new ProfileEntity(null, "users");
    identityEntity = new IdentityEntity("group:users", "platform/users", "group", profile);
    identityEntities.add(new CreatorIdentityEntity(identityEntity));
    workFlow.setRequestsCreators(identityEntities);
    when(ProjectUtil.newProjectInstanceDto(workFlow.getTitle(),
                                           workFlow.getDescription(),
                                           managers,
                                           participators)).thenReturn(projectDto);
    when(ProcessesUtils.getProjectParentSpace(workFlow.getProjectId())).thenReturn(space);
    projectDto.setId(1L);
    when(projectService.createProject(projectDto)).thenReturn(projectDto);
    PowerMockito.doNothing().when(statusService).createInitialStatuses(projectDto);
    when(workFlow.getAttachments()).thenReturn(attachments.toArray(new Attachment[attachments.size()]));
    when(EntityMapper.toEntity(workFlow)).thenReturn(workFlowEntity);
    WorkFlowEntity newWorkFlowEntity = new WorkFlowEntity();
    newWorkFlowEntity.setId(1L);
    newWorkFlowEntity.setProjectId(1L);
    newWorkFlowEntity.hashCode();
    newWorkFlowEntity.toString();
    newWorkFlowEntity.equals(workFlow);
    when(workFlowDAO.create(workFlowEntity)).thenReturn(newWorkFlowEntity);
    when(workFlowDAO.update(workFlowEntity)).thenReturn(newWorkFlowEntity);
    when(workFlow.getIllustrativeAttachment()).thenReturn(illustrativeAttachment);
    this.processesStorage.saveWorkFlow(workFlow, 1L);
    verify(workFlowDAO, times(1)).create(workFlowEntity);
    PowerMockito.verifyStatic(ProcessesUtils.class, times(1));
    ProcessesUtils.broadcast(listenerService,
                             "exo.process.created",
                             1L,
                             EntityMapper.fromEntity(workFlowEntity, illustrativeAttachment, memberships));
    verify(processesAttachmentService,
           times(1)).linkAttachmentsToEntity(attachments.toArray(new Attachment[0]), 1L, 1L, "workflow", 1L);
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

  @Test
  public void deleteWorkById() throws EntityNotFoundException {
    TaskDto taskDto = mock(TaskDto.class);
    ProjectDto projectDto = mock(ProjectDto.class);
    StatusDto statusDto = mock(StatusDto.class);
    when(taskService.getTask(1L)).thenReturn(taskDto);
    when(taskDto.getStatus()).thenReturn(statusDto);
    when(statusDto.getProject()).thenReturn(projectDto);
    processesStorage.deleteWorkById(1L);
    verify(taskService, times(1)).removeTask(1L);
    when(taskService.getTask(1L)).thenThrow(EntityNotFoundException.class);
    processesStorage.deleteWorkById(1L);
    verify(taskService, times(1)).removeTask(1L);
  }

  @Test
  @PrepareForTest({ EntityMapper.class, ProcessesUtils.class })
  public void saveWork() throws EntityNotFoundException, IllegalAccessException, ObjectNotFoundException {
    PowerMockito.mockStatic(EntityMapper.class);
    PowerMockito.mockStatic(ProcessesUtils.class);

    List<Attachment> attachments = new ArrayList<>();
    Attachment attachment = new Attachment();
    attachment.setId("1");
    attachments.add(attachment);
    Work work = new Work();
    ProjectDto projectDto = new ProjectDto();
    projectDto.setId(1L);
    StatusDto statusDto = mock(StatusDto.class);
    TaskDto taskDto = mock(TaskDto.class);
    Identity identity = mock(Identity.class);
    Profile profile = new Profile();
    profile.setProperty("fullName", "Root root");
    org.exoplatform.processes.entity.WorkEntity WorkEntity = mock(org.exoplatform.processes.entity.WorkEntity.class);
    when(identity.getProfile()).thenReturn(profile);
    when(taskDto.getStatus()).thenReturn(statusDto);
    when(statusDto.getProject()).thenReturn(projectDto);
    Throwable exception1 = assertThrows(IllegalArgumentException.class, () -> this.processesStorage.saveWork(null, 1l));
    assertEquals("work argument is null", exception1.getMessage());
    when(identityManager.getIdentity("1")).thenReturn(null);
    Throwable exception2 = assertThrows(IllegalArgumentException.class, () -> this.processesStorage.saveWork(work, 1l));
    assertEquals("identity is not exist", exception2.getMessage());
    work.setId(0L);
    work.setProjectId(1L);
    when(identityManager.getIdentity("1")).thenReturn(identity);
    when(projectService.getProject(work.getProjectId())).thenReturn(projectDto);
    when(EntityMapper.workToTask(work)).thenReturn(taskDto);
    when(EntityMapper.taskToWork(taskDto)).thenReturn(work);
    when(taskDto.getTitle()).thenReturn("");
    when(taskService.createTask(taskDto)).thenReturn(taskDto);
    processesStorage.saveWork(work, 1L);
    PowerMockito.verifyStatic(ProcessesUtils.class, times(1));
    ProcessesUtils.broadcast(listenerService, "exo.process.request.created", work, projectDto);

    work.setIsDraft(true);
    work.setId(0);
    work.setDraftId(1L);
    WorkFlow workFlow = new WorkFlow();
    workFlow.setProjectId(1L);
    when(taskDto.getId()).thenReturn(1L);
    when(workDraftDAO.find(1L)).thenReturn(WorkEntity);
    processesStorage.saveWork(work, 1L);
    verify(processesAttachmentService, times(1)).moveAttachmentsToEntity(1L, 1L, "workdraft", 1L, "task", 1L);
    verify(workDraftDAO, times(1)).delete(WorkEntity);
    when(projectService.getProject(work.getProjectId())).thenThrow(EntityNotFoundException.class);

    work.setId(1L);
    when(taskService.getTask(work.getId())).thenReturn(taskDto);
    TaskDto updatedTask = new TaskDto();
    updatedTask.setCompleted(true);
    StatusDto statusDto1 = new StatusDto();
    statusDto1.setName("Canceled");
    statusDto1.setProject(projectDto);
    updatedTask.setStatus(statusDto1);
    when(taskService.updateTask(taskDto)).thenReturn(updatedTask);
    List<StatusDto> statuses = new ArrayList<>();
    statuses.add(statusDto1);
    when(statusService.getStatuses(1L)).thenReturn(statuses);
    work.setStatus("Canceled");
    processesStorage.saveWork(work, 1L);
    PowerMockito.verifyStatic(ProcessesUtils.class, times(1));
    ProcessesUtils.broadcast(listenerService, "exo.process.request.canceled", updatedTask, updatedTask.getStatus().getProject());
    verify(taskService, times(1)).updateTask(taskDto);

    when(taskService.getTask(work.getId())).thenThrow(EntityNotFoundException.class);
    Throwable exception3 = assertThrows(IllegalArgumentException.class, () -> this.processesStorage.saveWork(work, 1l));
    assertEquals("Task not found", exception3.getMessage());

    work.setId(0L);
    Throwable exception4 = assertThrows(IllegalArgumentException.class, () -> this.processesStorage.saveWork(work, 1l));
    assertEquals("Task's project not found", exception4.getMessage());
  }

  @Test
  @PrepareForTest({ EntityMapper.class })
  public void saveWorkDraft() {
    PowerMockito.mockStatic(EntityMapper.class);
    Work work = new Work();
    work.setId(0L);
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(1L);
    workFlow.setProjectId(1L);
    org.exoplatform.processes.entity.WorkEntity workEntity = new org.exoplatform.processes.entity.WorkEntity();
    workEntity.setId(1L);
    workEntity.hashCode();
    workEntity.equals(workEntity);
    workEntity.toString();
    Identity identity = mock(Identity.class);
    when(identityManager.getIdentity("1")).thenReturn(null);
    Throwable exception1 = assertThrows(IllegalArgumentException.class, () -> this.processesStorage.saveWorkDraft(work, 1l));
    assertEquals("identity is not exist", exception1.getMessage());
    when(EntityMapper.toEntity(work)).thenReturn(workEntity);
    when(identityManager.getIdentity("1")).thenReturn(identity);
    work.setWorkFlow(workFlow);
    when(workDraftDAO.create(workEntity)).thenReturn(workEntity);
    processesStorage.saveWorkDraft(work, 1L);
    verify(processesAttachmentService,
           times(1)).copyAttachmentsToEntity(1L, work.getWorkFlow().getId(), "workflow", workEntity.getId(), "workdraft", 1L);
    verify(workDraftDAO, times(1)).create(workEntity);
    work.setId(1L);
    processesStorage.saveWorkDraft(work, 1L);
    verify(workDraftDAO, times(1)).update(workEntity);
  }

  @Test
  public void deleteWorkDraftById() {
    org.exoplatform.processes.entity.WorkEntity WorkEntity = new org.exoplatform.processes.entity.WorkEntity();
    when(workDraftDAO.find(1L)).thenReturn(null);
    Throwable exception1 = assertThrows(javax.persistence.EntityNotFoundException.class,
                                        () -> this.processesStorage.deleteWorkDraftById(1l));
    assertEquals("Work Draft not found", exception1.getMessage());
    when(workDraftDAO.find(1L)).thenReturn(WorkEntity);
    processesStorage.deleteWorkDraftById(1L);
    verify(workDraftDAO, times(1)).delete(WorkEntity);
  }

  @Test
  @PrepareForTest({ EntityMapper.class })
  public void getWorkById() throws Exception {
    PowerMockito.mockStatic(EntityMapper.class);
    TaskDto taskDto = mock(TaskDto.class);
    Identity identity = mock(Identity.class);
    List<TaskDto> list = new ArrayList<>();
    list.add(taskDto);
    when(identity.getRemoteId()).thenReturn("root");
    when(identityManager.getIdentity("1")).thenReturn(null);
    Throwable exception1 = assertThrows(IllegalArgumentException.class, () -> this.processesStorage.getWorkById(1L, 1L));
    assertEquals("identity is not exist", exception1.getMessage());
    when(identityManager.getIdentity("1")).thenReturn(identity);
    when(taskService.findTasks(any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(list);
    processesStorage.getWorkById(1L, 1L);
    verifyStatic(EntityMapper.class, times(1));
    EntityMapper.taskToWork(taskDto);
    when(taskService.findTasks(any(), anyInt(), anyInt())).thenThrow(new EntityNotFoundException(1L, Object.class));
    Throwable exception2 = assertThrows(javax.persistence.EntityNotFoundException.class,
                                        () -> this.processesStorage.getWorkById(1L, 1L));
    assertEquals("work not found", exception2.getMessage());
  }

  @Test
  @PrepareForTest({ CommonsUtils.class })
  public void updateWorkCompleted() throws EntityNotFoundException {
    PowerMockito.mockStatic(CommonsUtils.class);
    when(CommonsUtils.getService(OrganizationService.class)).thenReturn(organizationService);
    when(CommonsUtils.getService(SpaceService.class)).thenReturn(spaceService);
    TaskDto taskDto = new TaskDto();
    StatusDto statusDto = new StatusDto();
    statusDto.setId(1L);
    List<StatusDto> statusDtos = new ArrayList<>();
    List<TaskDto> taskDtos = new ArrayList<>();
    statusDto.setProject(new ProjectDto());
    taskDto.setStatus(statusDto);
    taskDto.setId(1);
    taskDto.setDescription("description");
    taskDto.setTitle("task");
    EntityMapper.toWorkStatuses(statusDtos);
    EntityMapper.tasksToWorkList(taskDtos);
    Work work = new Work();
    work.setId(1);
    List<Work> works = new ArrayList<>();
    works.add(work);
    WorkFlowEntity workFlowEntity = new WorkFlowEntity();
    workFlowEntity.setId(1L);
    workFlowEntity.setCreatorId(1L);
    workFlowEntity.setModifierId(1L);
    workFlowEntity.setProjectId(22L);
    WorkFlow workFlow1 = new WorkFlow();
    workFlow1.setProjectId(1L);
    WorkFlow workFlow2 = new WorkFlow();
    workFlow2.setProjectId(2L);
    List<WorkFlow> workFlows = new ArrayList<>();
    workFlows.add(workFlow1);
    workFlows.add(workFlow2);
    WorkEntity workEntity = new WorkEntity();
    workEntity.setId(11L);
    workEntity.setCreatorId(1L);
    workEntity.setWorkFlow(workFlowEntity);
    workEntity.setIsDraft(true);
    workEntity.setWorkFlow(workFlowEntity);
    List<WorkEntity> workEntities = new ArrayList<>();
    workEntities.add(workEntity);
    EntityMapper.toEntity(work);
    EntityMapper.toEntity(workFlow2);
    List<WorkFlowEntity> workFlowEntities2 = EntityMapper.fromWorkFlows(workFlows);
    assertNotNull(workFlowEntities2);
    EntityMapper.workToTask(work);
    EntityMapper.toWorkStatus(statusDto);
    EntityMapper.fromEntity(workEntity);
    List<Work> works1 = EntityMapper.fromWorkEntities(workEntities);

    assertNotNull(works1);

    when(taskService.getTask(1L)).thenReturn(taskDto);
    processesStorage.updateWorkCompleted(1L, true);
    verify(taskService, times(1)).updateTask(taskDto);
    when(taskService.getTask(1L)).thenThrow(new EntityNotFoundException(1L, Object.class));
    Throwable exception2 = assertThrows(javax.persistence.EntityNotFoundException.class,
                                        () -> this.processesStorage.updateWorkCompleted(1L, true));
    assertEquals("work not found", exception2.getMessage());
  }

  @Test
  @PrepareForTest({ EntityMapper.class, CommonsUtils.class })
  public void getWorks() throws Exception {
    PowerMockito.mockStatic(EntityMapper.class);
    PowerMockito.mockStatic(CommonsUtils.class);
    TaskQuery taskQuery = mock(TaskQuery.class);
    WorkFilter workFilter = new WorkFilter();
    TaskDto taskDto = new TaskDto();
    taskDto.setTitle("task");
    taskDto.setId(11);
    List<TaskDto> tasks = new ArrayList<>();
    tasks.add(taskDto);
    Work work = new Work();
    List<Work> works = new ArrayList<>();
    List<org.exoplatform.processes.rest.model.WorkEntity> workEntities = EntityBuilder.toWorkEntityList(works);
    Assert.assertEquals(0, workEntities.size());
    works.add(work);
    workEntities = EntityBuilder.toWorkEntityList(works);
    Assert.assertEquals(1, workEntities.size());
    workFilter.setStatus("Request");
    workFilter.setQuery("test");
    workFilter.setCompleted(true);
    WorkFlow workFlow1 = new WorkFlow();
    workFlow1.setProjectId(1L);
    WorkFlow workFlow2 = new WorkFlow();
    workFlow2.setProjectId(2L);
    List<WorkFlow> workFlows = new ArrayList<>();
    workFlows.add(workFlow1);
    workFlows.add(workFlow2);
    ProjectDto projectDto = mock(ProjectDto.class);
    projectDto.setId(1L);
    String username = "testuser";
    org.exoplatform.services.security.Identity root = new org.exoplatform.services.security.Identity(username);
    ConversationState.setCurrent(new ConversationState(root));
    long currentOwnerId = 2;
    org.exoplatform.social.core.identity.model.Identity currentIdentity =
                                                                        new org.exoplatform.social.core.identity.model.Identity(OrganizationIdentityProvider.NAME,
                                                                                                                                username);
    currentIdentity.setId(String.valueOf(currentOwnerId));
    Profile currentProfile = new Profile();
    currentProfile.setProperty(Profile.FULL_NAME, username);
    currentIdentity.setProfile(currentProfile);

    when(identityManager.getIdentity((String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);

    String user = ProcessesUtils.getUserNameByIdentityId(identityManager, 1l);
    assertNotNull(user);
    when(CommonsUtils.getService(ProjectService.class)).thenReturn(projectService);
    when(projectService.getProject(1l)).thenReturn(projectDto);
    Space space = ProcessesUtils.getProjectParentSpace(1l);
    assertNull(space);
    when(processesStorage.findWorkFlows(any(), anyLong(), anyInt(), anyInt())).thenReturn(workFlows);
    when(taskService.findTasks(taskQuery, 0, 0)).thenReturn(tasks);
    when(EntityMapper.tasksToWorkList(tasks)).thenReturn(works);
    processesStorage.getWorks(1L, workFilter, 0, 0);
    verify(taskService, times(1)).findTasks(any(), anyInt(), anyInt());
  }

  @Test
  @PrepareForTest({ EntityMapper.class })
  public void getAvailableWorkStatuses() {
    PowerMockito.mockStatic(EntityMapper.class);
    WorkFlow workFlow1 = new WorkFlow();
    workFlow1.setProjectId(1L);
    WorkFlow workFlow2 = new WorkFlow();
    workFlow2.setProjectId(2L);
    List<WorkFlow> workFlows = new ArrayList<>();
    workFlows.add(workFlow1);
    workFlows.add(workFlow2);
    StatusDto statusDto = mock(StatusDto.class);
    List<StatusDto> statuses = new ArrayList<>();
    statuses.add(statusDto);
    WorkStatus workStatus = new WorkStatus();
    workStatus.setName("Request");
    workStatus.setId(1L);
    workStatus.setRank(20);
    List<WorkStatus> workStatuses = new ArrayList<>();
    workStatuses.add(workStatus);
    when(statusService.getStatuses(1L)).thenReturn(statuses);
    when(processesStorage.findAllWorkFlows(anyInt(), anyInt())).thenReturn(workFlows);
    when(EntityMapper.toWorkStatuses(statuses)).thenReturn(workStatuses);

    List<WorkStatus> workStatusList = processesStorage.getAvailableWorkStatuses();
    assertEquals(1, workStatusList.size());
  }

  @Test
  public void getIllustrationImageById() throws Exception {
    FileInfo fileInfo = new FileInfo(1L, "file", "image/png", "processesApp", 1253L, new Date(), null, "", false);
    FileItem fileItem = new FileItem(fileInfo.getId(),
                                     fileInfo.getName(),
                                     fileInfo.getMimetype(),
                                     fileInfo.getNameSpace(),
                                     fileInfo.getSize(),
                                     fileInfo.getUpdatedDate(),
                                     fileInfo.getUpdater(),
                                     fileInfo.isDeleted(),
                                     null);
    IllustrativeAttachment illustrativeAttachment = processesStorage.getIllustrationImageById(null);
    assertNull(illustrativeAttachment);
    when(fileService.getFile(1L)).thenReturn(null);
    Throwable exception = assertThrows(ObjectNotFoundException.class, () -> this.processesStorage.getIllustrationImageById(1L));
    assertEquals("Illustration image not found", exception.getMessage());
    when(fileService.getFile(1L)).thenReturn(fileItem);
    IllustrativeAttachment illustrativeAttachment1 = processesStorage.getIllustrationImageById(1L);
    assertNotNull(illustrativeAttachment1);
  }

  @Test
  @PrepareForTest({ EntityMapper.class, UserUtil.class, ProjectUtil.class, ProcessesUtils.class })
  public void findWorkflow() throws Exception {
    PowerMockito.mockStatic(EntityMapper.class);
    PowerMockito.mockStatic(UserUtil.class);
    PowerMockito.mockStatic(ProjectUtil.class);
    PowerMockito.mockStatic(ProcessesUtils.class);

    IllustrativeAttachment illustrativeAttachment = new IllustrativeAttachment(null,
                                                                               "image.png",
                                                                               "image/png",
                                                                               1365L,
                                                                               new Date().getTime());
    List<Attachment> attachments = new ArrayList<>();
    Attachment attachment = new Attachment();
    attachment.setId("1");
    attachments.add(attachment);
    Identity identity = mock(Identity.class);
    WorkFlow workFlow = mock(WorkFlow.class);
    List<String> memberships = new ArrayList<>();
    memberships.add("manager");
    memberships.add("participator");
    Set<String> managers = new HashSet<>(Arrays.asList(memberships.get(0)));
    Set<String> participators = new HashSet<>(Arrays.asList(memberships.get(1)));
    ProjectDto projectDto = new ProjectDto();
    Space space = mock(Space.class);
    WorkFlowEntity workFlowEntity = new WorkFlowEntity();
    when(EntityMapper.toEntity(workFlow)).thenReturn(workFlowEntity);
    when(workFlow.getId()).thenReturn(0L);
    when(workFlow.getProjectId()).thenReturn(0L);
    when(workFlow.getSpaceId()).thenReturn("1");

    when(identityManager.getIdentity("1")).thenReturn(identity);
    when(identity.getRemoteId()).thenReturn("user");
    when(identity.getId()).thenReturn("1");
    when(space.getGroupId()).thenReturn("/spaces/processes_space");
    when(spaceService.getSpaceByGroupId("/spaces/processes_space")).thenReturn(space);
    when(spaceService.getSpaceById("1")).thenReturn(space);
    when(UserUtil.getSpaceMemberships(space.getGroupId())).thenReturn(memberships);
    workFlow.setDescription("anything");
    workFlow.setTitle("title");
    workFlow.setProjectId(1L);
    workFlow.setSpaceId("1");
    when(ProjectUtil.newProjectInstanceDto(workFlow.getTitle(),
                                           workFlow.getDescription(),
                                           managers,
                                           participators)).thenReturn(projectDto);
    projectDto.setId(1L);
    when(projectService.createProject(projectDto)).thenReturn(projectDto);
    PowerMockito.doNothing().when(statusService).createInitialStatuses(projectDto);
    when(workFlow.getAttachments()).thenReturn(attachments.toArray(new Attachment[attachments.size()]));
    when(EntityMapper.toEntity(workFlow)).thenReturn(workFlowEntity);
    WorkFlowEntity newWorkFlowEntity = new WorkFlowEntity();
    newWorkFlowEntity.setId(1L);
    newWorkFlowEntity.setProjectId(1L);
    newWorkFlowEntity.hashCode();
    newWorkFlowEntity.toString();
    newWorkFlowEntity.equals(workFlow);
    WorkFlowEntity newWorkFlowEntity1 = new WorkFlowEntity();
    newWorkFlowEntity1.setId(1L);
    newWorkFlowEntity1.setProjectId(1L);
    when(workFlowDAO.create(workFlowEntity)).thenReturn(newWorkFlowEntity);
    ProcessesFilter filter = new ProcessesFilter("", null);
    List<WorkFlowEntity> workFlowEntities = new ArrayList<>();
    workFlowEntities.add(newWorkFlowEntity);
    memberships = new ArrayList<>();
    memberships.add(identity.getRemoteId());
    memberships.add("member:/platform/administrators");
    memberships.add("*:/platform/administrators");
    memberships.add("*:/platform/users");
    when(workFlowDAO.findWorkFlows(filter, memberships, 0, 0)).thenReturn(workFlowEntities);
    when(workFlow.getIllustrativeAttachment()).thenReturn(illustrativeAttachment);
    this.processesStorage.saveWorkFlow(workFlow, 1L);
    when(organizationService.getMembershipHandler()).thenReturn(membershipHandler);

    Collection<Membership> memberships_ = new ArrayList();
    MembershipImpl membership;
    membership = new MembershipImpl();
    membership.setMembershipType("member");
    membership.setUserName("user");
    membership.setGroupId("/platform/administrators");
    memberships_.add(membership);
    MembershipImpl admin = new MembershipImpl();
    admin.setMembershipType("*");
    admin.setUserName("user");
    admin.setGroupId("/platform/administrators");
    memberships_.add(admin);

    membership = new MembershipImpl();
    membership.setMembershipType("*");
    membership.setUserName("user");
    membership.setGroupId("/platform/users");
    memberships_.add(membership);

    when(ProcessesUtils.getProjectParentSpace(workFlow.getProjectId())).thenReturn(space);
    when(EntityMapper.fromEntity(newWorkFlowEntity1, null)).thenReturn(workFlow);

    when(organizationService.getMembershipHandler().findMembershipsByUser(identity.getRemoteId())).thenReturn(memberships_);
    assertEquals(1, this.processesStorage.findWorkFlows(filter, Long.parseLong(identity.getId()), 0, 0).size());
  }

  @Test
  @PrepareForTest({ EntityMapper.class, UserUtil.class, ProjectUtil.class, ProcessesUtils.class })
  public void countWorkflow() {
    PowerMockito.mockStatic(EntityMapper.class);
    PowerMockito.mockStatic(UserUtil.class);
    PowerMockito.mockStatic(ProjectUtil.class);
    PowerMockito.mockStatic(ProcessesUtils.class);

    IllustrativeAttachment illustrativeAttachment = new IllustrativeAttachment(null,
                                                                               "image.png",
                                                                               "image/png",
                                                                               1365L,
                                                                               new Date().getTime());
    List<Attachment> attachments = new ArrayList<>();
    Attachment attachment = new Attachment();
    attachment.setId("1");
    attachments.add(attachment);
    Identity identity = mock(Identity.class);
    WorkFlow workFlow = mock(WorkFlow.class);
    List<String> memberships = new ArrayList<>();
    memberships.add("manager");
    memberships.add("participator");
    Set<String> managers = new HashSet<>(Arrays.asList(memberships.get(0)));
    Set<String> participators = new HashSet<>(Arrays.asList(memberships.get(1)));
    ProjectDto projectDto = new ProjectDto();
    Space space = mock(Space.class);
    WorkFlowEntity workFlowEntity = new WorkFlowEntity();
    when(EntityMapper.toEntity(workFlow)).thenReturn(workFlowEntity);
    when(workFlow.getId()).thenReturn(0L);
    when(workFlow.getProjectId()).thenReturn(0L);
    when(workFlow.getSpaceId()).thenReturn("1");

    when(identityManager.getIdentity("1")).thenReturn(identity);
    when(space.getGroupId()).thenReturn("/spaces/processes_space");
    when(spaceService.getSpaceByGroupId("/spaces/processes_space")).thenReturn(space);
    when(spaceService.getSpaceById("1")).thenReturn(space);
    when(UserUtil.getSpaceMemberships(space.getGroupId())).thenReturn(memberships);
    workFlow.setDescription("anything");
    workFlow.setTitle("title");
    workFlow.setProjectId(1L);
    workFlow.setSpaceId("1");
    when(ProjectUtil.newProjectInstanceDto(workFlow.getTitle(),
                                           workFlow.getDescription(),
                                           managers,
                                           participators)).thenReturn(projectDto);
    projectDto.setId(1L);
    when(projectService.createProject(projectDto)).thenReturn(projectDto);
    PowerMockito.doNothing().when(statusService).createInitialStatuses(projectDto);
    when(workFlow.getAttachments()).thenReturn(attachments.toArray(new Attachment[attachments.size()]));
    when(EntityMapper.toEntity(workFlow)).thenReturn(workFlowEntity);
    WorkFlowEntity newWorkFlowEntity = new WorkFlowEntity();
    newWorkFlowEntity.setId(1L);
    newWorkFlowEntity.setProjectId(1L);
    newWorkFlowEntity.hashCode();
    newWorkFlowEntity.toString();
    newWorkFlowEntity.equals(workFlow);
    when(workFlowDAO.create(workFlowEntity)).thenReturn(newWorkFlowEntity);
    ProcessesFilter filter = new ProcessesFilter("", null);
    when(workFlowDAO.countWorkFlows(filter)).thenReturn(1);
    when(workFlow.getIllustrativeAttachment()).thenReturn(illustrativeAttachment);
    List<CreatorIdentityEntity> identityEntities = new ArrayList<>();
    ProfileEntity profile =
                          new ProfileEntity(StringUtils.isNotEmpty(space.getAvatarUrl()) ? space.getAvatarUrl()
                                                                                         : "/portal/rest/v1/social/spaces/"
                                                                                             + space.getPrettyName() + "/avatar",
                                            space.getDisplayName());
    IdentityEntity identityEntity = new IdentityEntity("", space.getPrettyName(), "space", profile);
    identityEntities.add(new CreatorIdentityEntity(identityEntity));
    profile = new ProfileEntity(null, "users");
    identityEntity = new IdentityEntity("group:users", "platform/users", "group", profile);
    identityEntities.add(new CreatorIdentityEntity(identityEntity));
    workFlow.setRequestsCreators(identityEntities);
    this.processesStorage.saveWorkFlow(workFlow, 1L);
    assertEquals(1, this.processesStorage.countWorkFlows(filter));
  }
}
