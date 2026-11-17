package org.exoplatform.processes.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.ext.RuntimeDelegate;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.task.dto.LabelDto;
import org.mockito.InOrder;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.task.service.LabelService;
import org.exoplatform.services.security.MembershipEntry;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.model.FileInfo;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.processes.Utils.EntityMapper;
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.dao.RequestDAO;
import org.exoplatform.processes.dao.WorkDraftDAO;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.WorkEntity;
import org.exoplatform.processes.entity.RequestEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.CreatorIdentityEntity;
import org.exoplatform.processes.model.IdentityEntity;
import org.exoplatform.processes.model.IllustrativeAttachment;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.ProfileEntity;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFilter;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.model.WorkStatus;
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

@RunWith(MockitoJUnitRunner.Silent.class)
public class ProcessesStorageImplTest {

  private static final MockedStatic<CommonsUtils>   COMMONS_UTILS   = mockStatic(CommonsUtils.class);

  private static final MockedStatic<EntityMapper>   ENTITY_MAPPER   = mockStatic(EntityMapper.class);

  private static final MockedStatic<UserUtil>       USER_UTIL       = mockStatic(UserUtil.class);

  private static final MockedStatic<ProcessesUtils> PROCESSES_UTILS = mockStatic(ProcessesUtils.class);

  private static final MockedStatic<ProjectUtil>    PROJECT_UTIL    = mockStatic(ProjectUtil.class);

  @Mock
  private LabelService labelService;

  @Mock
  private WorkFlowDAO                workFlowDAO;

  @Mock
  private WorkDraftDAO               workDraftDAO;

  @Mock
  private RequestDAO                 requestDAO;

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
  private UserACL                    userACL;

  private ProcessesStorage           processesStorage;

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    COMMONS_UTILS.close();
    ENTITY_MAPPER.close();
    USER_UTIL.close();
    PROCESSES_UTILS.close();
    PROJECT_UTIL.close();
  }

  @Before
  public void setUp() throws Exception {
    RuntimeDelegate.setInstance(new RuntimeDelegateImpl());
    listenerService = new ListenerService(new ExoContainerContext(null));
    listenerService = spy(listenerService);
    this.processesStorage = new ProcessesStorageImpl(workFlowDAO,
                                                     workDraftDAO,
                                                     requestDAO,
                                                     taskService,
                                                     projectService,
                                                     statusService,
                                                     identityManager,
                                                     spaceService,
                                                     listenerService,
                                                     processesAttachmentService,
                                                     fileService,
                                                     userACL,
                                                     labelService);

  }

  /**
   * Deleting a process deletes its drafts, its request records, then the
   * process, and never its task project, which other processes may feed and
   * whose tasks stay as plain tasks; the request records go before the process
   * they reference. An unknown process is not found. Mutation: remove the
   * project again, or drop the request records' deletion, and this test fails.
   *
   * @throws EntityNotFoundException never, the mocks throw nothing
   */
  @Test
  public void deletingAProcessKeepsItsProjectAndDeletesItsRequestRecords() throws EntityNotFoundException {
    ProcessesStorageImpl storage = (ProcessesStorageImpl) processesStorage;
    when(workFlowDAO.find(1L)).thenReturn(null);
    assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> storage.deleteWorkflowRows(1L));
    verify(workFlowDAO, never()).delete(any());

    WorkFlowEntity workFlowEntity = new WorkFlowEntity();
    workFlowEntity.setId(1L);
    workFlowEntity.setProjectId(9L);
    List<WorkEntity> drafts = new ArrayList<>(List.of(new WorkEntity()));
    when(workFlowDAO.find(1L)).thenReturn(workFlowEntity);
    when(workDraftDAO.getDraftsByWorkflowId(1L)).thenReturn(drafts);

    storage.deleteWorkflowRows(1L);

    org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(workDraftDAO, requestDAO, workFlowDAO);
    inOrder.verify(workDraftDAO).deleteAll(drafts);
    inOrder.verify(requestDAO).deleteByWorkFlowId(1L);
    inOrder.verify(workFlowDAO).delete(workFlowEntity);
    verify(projectService, never()).removeProject(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
  }

  /**
   * The deletion runs in one database transaction: the framework applies it
   * through the annotation, so a test calling the method's body cannot see it
   * removed. Mutation: drop the annotation or its atomic flag and this test
   * fails.
   *
   * @throws NoSuchMethodException never
   */
  @Test
  public void deletingAProcessIsAtomic() throws NoSuchMethodException {
    io.meeds.common.ContainerTransactional transactional =
                                                       ProcessesStorageImpl.class.getMethod("deleteWorkflowById", Long.class)
                                                                                 .getAnnotation(io.meeds.common.ContainerTransactional.class);
    assertNotNull(transactional);
    assertTrue(transactional.atomic());
  }

  @Test
  public void saveWorkflow() throws EntityNotFoundException {
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

    when(identityManager.getIdentity(1)).thenReturn(null);
    Throwable exception2 = assertThrows(IllegalArgumentException.class, () -> this.processesStorage.saveWorkFlow(workFlow, 1l));
    assertEquals("identity is not exist", exception2.getMessage());

    ENTITY_MAPPER.when(() -> EntityMapper.toEntity(workFlow)).thenReturn(workFlowEntity);
    when(workFlow.getId()).thenReturn(0L);
    when(workFlow.getProjectId()).thenReturn(0L);
    when(workFlow.getSpaceId()).thenReturn("1");
    when(projectService.getProject(workFlow.getProjectId())).thenReturn(projectDto);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(space.getGroupId()).thenReturn("/spaces/processes_space");
    when(space.getId()).thenReturn("2");
    when(spaceService.getSpaceByGroupId("/spaces/processes_space")).thenReturn(space);
    when(spaceService.getSpaceById("1")).thenReturn(space);
    USER_UTIL.when(() -> UserUtil.getSpaceMemberships(space.getGroupId())).thenReturn(memberships);
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
    PROJECT_UTIL.when(() -> ProjectUtil.newProjectInstanceDto(workFlow.getTitle(),
                                           workFlow.getDescription(),
                                           managers,
                                           participators)).thenReturn(projectDto);
    PROCESSES_UTILS.when(() -> ProcessesUtils.getProjectParentSpace(workFlow.getProjectId())).thenReturn(space);
    projectDto.setId(1L);
    when(projectService.createProject(projectDto)).thenReturn(projectDto);
    doNothing().when(statusService).createInitialStatuses(projectDto);
    when(workFlow.getAttachments()).thenReturn(attachments.toArray(new Attachment[attachments.size()]));
    ENTITY_MAPPER.when(() -> EntityMapper.toEntity(workFlow)).thenReturn(workFlowEntity);
    WorkFlowEntity newWorkFlowEntity = new WorkFlowEntity();
    newWorkFlowEntity.setId(1L);
    newWorkFlowEntity.setProjectId(1L);
    newWorkFlowEntity.hashCode();
    newWorkFlowEntity.toString();
    newWorkFlowEntity.equals(workFlow);
    when(workFlowDAO.create(workFlowEntity)).thenReturn(newWorkFlowEntity);
    when(workFlowDAO.update(workFlowEntity)).thenReturn(newWorkFlowEntity);
    when(workFlow.getIllustrativeAttachment()).thenReturn(illustrativeAttachment);
    // The Service creates a new process's project first: the storage's save
    // writes nothing through Task, so it may run in one transaction
    Throwable exception3 = assertThrows(IllegalArgumentException.class, () -> this.processesStorage.saveWorkFlow(workFlow, 1L));
    assertEquals("processes.project.required", exception3.getMessage());
    verify(projectService, never()).createProject(any());
    when(workFlow.getProjectId()).thenReturn(1L);
    this.processesStorage.saveWorkFlow(workFlow, 1L);
    verify(workFlowDAO, times(1)).create(workFlowEntity);
    // The Service broadcasts the creation and links the attachments, after
    // the transaction of the save
    PROCESSES_UTILS.verify(() -> ProcessesUtils.broadcast(any(ListenerService.class), eq("exo.process.created"), any(), any()),
                           never());
    verify(processesAttachmentService, never()).linkAttachmentsToEntity(any(), anyLong(), anyLong(), any(), anyLong());
    WorkFlow saved = new WorkFlow();
    saved.setId(1L);
    saved.setProjectId(1L);
    this.processesStorage.linkWorkFlowAttachments(saved, attachments.toArray(new Attachment[0]), 1L);
    verify(processesAttachmentService,
           times(1)).linkAttachmentsToEntity(attachments.toArray(new Attachment[0]), 1L, 1L, "workflow", 1L);
    when(workFlow.getId()).thenReturn(1L);
    this.processesStorage.saveWorkFlow(workFlow, 1L);
    verify(workFlowDAO, times(1)).update(workFlowEntity);
  }

  /**
   * An update that does not state the process's space keeps the stored one,
   * and one that states it saves it; a space the process takes gives it that
   * space's participants, and the stored space keeps the stored ones; none
   * rewrites the members of the task project, which other processes may feed.
   * Mutation: drop the fallback to the stored space, keep the former
   * participants on a new space, or rewrite the project's members again, and
   * this test fails.
   */
  @Test
  public void anUpdateWithoutASpaceKeepsTheStoredOne() {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(5L);
    workFlow.setProjectId(9L);
    workFlow.setRequestsCreators(new ArrayList<>());
    when(identityManager.getIdentity(1L)).thenReturn(mock(Identity.class));
    WorkFlowEntity stored = new WorkFlowEntity();
    stored.setId(5L);
    stored.setSpaceId(7L);
    stored.setParticipator(new HashSet<>(Arrays.asList("manager:/spaces/hr", "member:/spaces/hr")));
    when(workFlowDAO.find(5L)).thenReturn(stored);
    Space otherSpace = mock(Space.class);
    when(otherSpace.getId()).thenReturn("8");
    PROCESSES_UTILS.when(() -> ProcessesUtils.getProjectParentSpace(9L)).thenReturn(otherSpace);
    WorkFlowEntity sent = new WorkFlowEntity();
    sent.setId(5L);
    sent.setProjectId(9L);
    ENTITY_MAPPER.when(() -> EntityMapper.toEntity(workFlow)).thenReturn(sent);
    when(workFlowDAO.update(any(WorkFlowEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    processesStorage.saveWorkFlow(workFlow, 1L);
    assertEquals(Long.valueOf(7L), sent.getSpaceId());
    assertEquals(stored.getParticipator(), sent.getParticipator());

    WorkFlowEntity sentWithASpace = new WorkFlowEntity();
    sentWithASpace.setId(5L);
    sentWithASpace.setProjectId(9L);
    sentWithASpace.setSpaceId(8L);
    ENTITY_MAPPER.when(() -> EntityMapper.toEntity(workFlow)).thenReturn(sentWithASpace);

    Space newSpace = mock(Space.class);
    when(newSpace.getGroupId()).thenReturn("/spaces/sales");
    when(spaceService.getSpaceById("8")).thenReturn(newSpace);
    USER_UTIL.when(() -> UserUtil.getSpaceMemberships("/spaces/sales")).thenReturn(Arrays.asList("manager:/spaces/sales", "member:/spaces/sales"));

    processesStorage.saveWorkFlow(workFlow, 1L);
    assertEquals(Long.valueOf(8L), sentWithASpace.getSpaceId());
    assertEquals("a space the process takes gives it that space's participants",
                 new HashSet<>(Arrays.asList("manager:/spaces/sales", "member:/spaces/sales")),
                 sentWithASpace.getParticipator());

    WorkFlowEntity sentWithTheStoredSpace = new WorkFlowEntity();
    sentWithTheStoredSpace.setId(5L);
    sentWithTheStoredSpace.setProjectId(9L);
    sentWithTheStoredSpace.setSpaceId(7L);
    ENTITY_MAPPER.when(() -> EntityMapper.toEntity(workFlow)).thenReturn(sentWithTheStoredSpace);

    processesStorage.saveWorkFlow(workFlow, 1L);
    assertEquals("the stored space keeps the stored participants", stored.getParticipator(), sentWithTheStoredSpace.getParticipator());

    // A process stored before it had a space takes one with its participants
    stored.setSpaceId(null);
    WorkFlowEntity sentToALegacyProcess = new WorkFlowEntity();
    sentToALegacyProcess.setId(5L);
    sentToALegacyProcess.setProjectId(9L);
    sentToALegacyProcess.setSpaceId(8L);
    ENTITY_MAPPER.when(() -> EntityMapper.toEntity(workFlow)).thenReturn(sentToALegacyProcess);

    processesStorage.saveWorkFlow(workFlow, 1L);
    assertEquals(new HashSet<>(Arrays.asList("manager:/spaces/sales", "member:/spaces/sales")), sentToALegacyProcess.getParticipator());
    verify(projectService, never()).updateProjectNoReturn(any());
  }

  /**
   * A process created in an existing project keeps it, creates no project,
   * and gets the managing space's two memberships as participants, which an
   * existing-project creation would otherwise leave empty. Mutation: write the
   * participants only when a project is created and this test fails.
   *
   * @throws EntityNotFoundException never, the mocks throw nothing
   */
  @Test
  public void aProcessCreatedInAnExistingProjectGetsTheSpaceAsParticipants() throws EntityNotFoundException {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setProjectId(9L);
    workFlow.setSpaceId("3");
    workFlow.setRequestsCreators(new ArrayList<>());
    when(identityManager.getIdentity(1L)).thenReturn(mock(Identity.class));
    Space space = mock(Space.class);
    when(space.getGroupId()).thenReturn("/spaces/hr");
    when(spaceService.getSpaceById("3")).thenReturn(space);
    USER_UTIL.when(() -> UserUtil.getSpaceMemberships("/spaces/hr")).thenReturn(Arrays.asList("manager:/spaces/hr", "member:/spaces/hr"));
    WorkFlowEntity sent = new WorkFlowEntity();
    sent.setProjectId(9L);
    ENTITY_MAPPER.when(() -> EntityMapper.toEntity(workFlow)).thenReturn(sent);
    when(workFlowDAO.create(any(WorkFlowEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    processesStorage.saveWorkFlow(workFlow, 1L);

    assertEquals(Long.valueOf(9L), sent.getProjectId());
    assertEquals(new HashSet<>(Arrays.asList("manager:/spaces/hr", "member:/spaces/hr")), sent.getParticipator());
    verify(projectService, never()).createProject(any());
  }

  /**
   * A new request is recorded as a request of its process, with its
   * requester, once its task exists, and starts in the project's status named
   * Request exactly, the one of lowest id when a column was duplicated, never
   * the project's first column; the created request names its process.
   * Mutation: drop the record, go back to the default status, or drop the
   * process id from the created request, and this test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  public void aNewRequestIsRecordedAndStartsInTheRequestStatus() throws Exception {
    Work work = newRequest();
    when(statusService.getStatuses(9L)).thenReturn(Arrays.asList(status(1L, "To Do"), status(7L, "Request"), status(5L, "Request")));
    ArgumentCaptor<TaskDto> createdTask = ArgumentCaptor.forClass(TaskDto.class);

    Work created = processesStorage.saveWork(work, 1L);

    verify(taskService).createTask(createdTask.capture());
    assertEquals(Long.valueOf(5L), createdTask.getValue().getStatus().getId());
    verify(requestDAO).create(new RequestEntity(40L, 3L, 1L));
    assertEquals("the created request names its process, which its answer shows", 3L, created.getWorkFlowId());
    verify(statusService, never()).getDefaultStatus(anyLong());
  }

  /**
   * A new request gets the labels its process applies, once its task and its
   * record exist: each one that still exists and belongs to the task's
   * project, on its own; a label that can't be applied is logged and the
   * request is still created. Mutation: skip the labels, apply one of another
   * project, or let a failing label fail the request, and this test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  public void aNewRequestGetsTheLabelsOfItsProcess() throws Exception {
    Work work = newRequest();
    when(statusService.getStatuses(9L)).thenReturn(Arrays.asList(status(7L, "Request")));
    WorkFlowEntity process = new WorkFlowEntity();
    process.setId(3L);
    process.setProjectId(9L);
    process.setLabelIds(new HashSet<>(Arrays.asList(11L, 12L, 13L, 14L)));
    when(workFlowDAO.find(3L)).thenReturn(process);
    when(labelService.getLabel(11L)).thenReturn(projectLabel(11L, 9L));
    when(labelService.getLabel(12L)).thenReturn(projectLabel(12L, 8L));
    when(labelService.getLabel(14L)).thenReturn(projectLabel(14L, 9L));
    doThrow(new RuntimeException("Task refuses the label")).when(labelService).addTaskToLabel(any(TaskDto.class), eq(11L));

    Work created = processesStorage.saveWork(work, 1L);

    assertNotNull(created);
    InOrder order = inOrder(requestDAO, labelService);
    order.verify(requestDAO).create(new RequestEntity(40L, 3L, 1L));
    order.verify(labelService).addTaskToLabel(any(TaskDto.class), eq(11L));
    order.verify(labelService).addTaskToLabel(any(TaskDto.class), eq(14L));
    verify(labelService, never()).addTaskToLabel(any(TaskDto.class), eq(12L));
    verify(labelService, never()).addTaskToLabel(any(TaskDto.class), eq(13L));
  }

  /**
   * Builds a Task label of a project.
   *
   * @param id its id
   * @param projectId its project id
   * @return the label
   */
  private static LabelDto projectLabel(long id, long projectId) {
    ProjectDto project = new ProjectDto();
    project.setId(projectId);
    LabelDto label = new LabelDto();
    label.setId(id);
    label.setName("label " + id);
    label.setProject(project);
    return label;
  }

  /**
   * A project whose Request column a manager deleted takes the project's
   * default status, as before.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  public void aProjectWithoutARequestColumnTakesItsDefaultStatus() throws Exception {
    Work work = newRequest();
    StatusDto toDo = status(1L, "To Do");
    when(statusService.getStatuses(9L)).thenReturn(Arrays.asList(toDo));
    when(statusService.getDefaultStatus(9L)).thenReturn(toDo);
    ArgumentCaptor<TaskDto> createdTask = ArgumentCaptor.forClass(TaskDto.class);

    processesStorage.saveWork(work, 1L);

    verify(taskService).createTask(createdTask.capture());
    assertEquals(Long.valueOf(1L), createdTask.getValue().getStatus().getId());
  }

  /**
   * When the request record cannot be saved, the task Task already committed
   * is removed and the request fails, before any draft is consumed, so no task
   * stays in a shared project without its record. Mutation: drop the removal
   * and this test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  public void aRequestWhoseRecordFailsRemovesItsTask() throws Exception {
    Work work = newRequest();
    work.setDraftId(8L);
    when(statusService.getStatuses(9L)).thenReturn(Arrays.asList(status(7L, "Request")));
    doThrow(new jakarta.persistence.PersistenceException("the process is gone")).when(requestDAO).create(any(RequestEntity.class));

    assertThrows(IllegalStateException.class, () -> processesStorage.saveWork(work, 1L));

    verify(taskService).removeTask(40L);
    verify(workDraftDAO, never()).delete(any());
    verify(processesAttachmentService, never()).moveAttachmentsToEntity(any(), anyLong(), anyLong(), any(), anyLong(), any(), anyLong());
  }

  /**
   * Builds a new request of process 3 in project 9, whose task Task creates
   * with id 40.
   *
   * @return the request
   * @throws Exception never, the mocks throw nothing
   */
  private Work newRequest() throws Exception {
    Work work = new Work();
    work.setProjectId(9L);
    work.setWorkFlowId(3L);
    Identity identity = mock(Identity.class);
    Profile profile = new Profile();
    profile.setProperty("fullName", "Jean Martin");
    when(identity.getProfile()).thenReturn(profile);
    when(identity.getRemoteId()).thenReturn("jean");
    when(identityManager.getIdentity(1L)).thenReturn(identity);
    when(projectService.getProject(9L)).thenReturn(new ProjectDto());
    TaskDto taskDto = new TaskDto();
    taskDto.setTitle("title");
    ENTITY_MAPPER.when(() -> EntityMapper.workToTask(work)).thenReturn(taskDto);
    ENTITY_MAPPER.when(() -> EntityMapper.taskToWork(taskDto)).thenReturn(new Work());
    when(taskService.createTask(any(TaskDto.class))).thenAnswer(invocation -> {
      TaskDto created = invocation.getArgument(0);
      created.setId(40L);
      return created;
    });
    return work;
  }

  /**
   * Builds a column of project 9.
   *
   * @param id its id
   * @param name its name
   * @return the column
   */
  private static StatusDto status(long id, String name) {
    StatusDto status = new StatusDto();
    status.setId(id);
    status.setName(name);
    ProjectDto project = new ProjectDto();
    project.setId(9L);
    status.setProject(project);
    return status;
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
  public void saveWork() throws EntityNotFoundException, IllegalAccessException, ObjectNotFoundException {
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
    when(identityManager.getIdentity(1)).thenReturn(null);
    Throwable exception2 = assertThrows(IllegalArgumentException.class, () -> this.processesStorage.saveWork(work, 1l));
    assertEquals("identity is not exist", exception2.getMessage());
    work.setId(0L);
    work.setProjectId(1L);
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(projectService.getProject(work.getProjectId())).thenReturn(projectDto);
    ENTITY_MAPPER.when(() -> EntityMapper.workToTask(work)).thenReturn(taskDto);
    ENTITY_MAPPER.when(() -> EntityMapper.taskToWork(taskDto)).thenReturn(work);
    when(taskDto.getTitle()).thenReturn("");
    when(taskService.createTask(taskDto)).thenReturn(taskDto);
    processesStorage.saveWork(work, 1L);
    PROCESSES_UTILS.verify(() -> ProcessesUtils.broadcast(listenerService, "exo.process.request.created", work, projectDto), times(1));

    work.setIsDraft(true);
    work.setId(0);
    work.setDraftId(1L);
    work.setAttachments(new ArrayList<Attachment>());
    WorkFlow workFlow = new WorkFlow();
    workFlow.setProjectId(1L);
    when(taskDto.getId()).thenReturn(1L);
    when(workDraftDAO.find(1L)).thenReturn(WorkEntity);
    processesStorage.saveWork(work, 1L);
    verify(processesAttachmentService, times(1)).moveAttachmentsToEntity(new ArrayList<Attachment>(), 1L, 1L, "workdraft", 1L, "task", 1L);
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
    PROCESSES_UTILS.verify(() -> ProcessesUtils.broadcast(listenerService, "exo.process.request.canceled", updatedTask, updatedTask.getStatus().getProject()), times(1));
    verify(taskService, times(1)).updateTask(taskDto);

    when(taskService.getTask(work.getId())).thenThrow(EntityNotFoundException.class);
    Throwable exception3 = assertThrows(IllegalArgumentException.class, () -> this.processesStorage.saveWork(work, 1l));
    assertEquals("Task not found", exception3.getMessage());

    work.setId(0L);
    Throwable exception4 = assertThrows(IllegalArgumentException.class, () -> this.processesStorage.saveWork(work, 1l));
    assertEquals("Task's project not found", exception4.getMessage());
  }

  @Test
  public void saveWorkDraft() {
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
    when(identityManager.getIdentity(1)).thenReturn(null);
    Throwable exception1 = assertThrows(IllegalArgumentException.class, () -> this.processesStorage.saveWorkDraft(work, 1l));
    assertEquals("identity is not exist", exception1.getMessage());
    ENTITY_MAPPER.when(() -> EntityMapper.toEntity(work)).thenReturn(workEntity);
    when(identityManager.getIdentity(1)).thenReturn(identity);
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
    Throwable exception1 = assertThrows(jakarta.persistence.EntityNotFoundException.class,
                                        () -> this.processesStorage.deleteWorkDraftById(1l));
    assertEquals("Work Draft not found", exception1.getMessage());
    when(workDraftDAO.find(1L)).thenReturn(WorkEntity);
    processesStorage.deleteWorkDraftById(1L);
    verify(workDraftDAO, times(1)).delete(WorkEntity);
  }

  @Test
  public void getWorkById() throws Exception {
    TaskDto taskDto = mock(TaskDto.class);
    Identity identity = mock(Identity.class);
    List<TaskDto> list = new ArrayList<>();
    list.add(taskDto);
    when(identity.getRemoteId()).thenReturn("root");
    when(identityManager.getIdentity(1)).thenReturn(null);
    Throwable exception1 = assertThrows(IllegalArgumentException.class, () -> this.processesStorage.getWorkById(1L, 1L));
    assertEquals("identity is not exist", exception1.getMessage());
    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(taskService.findTasks(any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(list);
    processesStorage.getWorkById(1L, 1L);
    EntityMapper.taskToWork(taskDto);
    when(taskService.findTasks(any(), anyInt(), anyInt())).thenThrow(new EntityNotFoundException(1L, Object.class));
    Throwable exception2 = assertThrows(jakarta.persistence.EntityNotFoundException.class,
                                        () -> this.processesStorage.getWorkById(1L, 1L));
    assertEquals("work not found", exception2.getMessage());
  }

  @Test
  public void updateWorkCompleted() throws EntityNotFoundException {
    COMMONS_UTILS.when(() -> CommonsUtils.getService(SpaceService.class)).thenReturn(spaceService);
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
    Throwable exception2 = assertThrows(jakarta.persistence.EntityNotFoundException.class,
                                        () -> this.processesStorage.updateWorkCompleted(1L, true));
    assertEquals("work not found", exception2.getMessage());
  }

  @Test
  public void getWorks() throws Exception {
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

    when(identityManager.getIdentity(currentOwnerId)).thenReturn(currentIdentity);

    PROCESSES_UTILS.when(() -> ProcessesUtils.getUserNameByIdentityId(any(), anyLong())).thenCallRealMethod();
    String user = ProcessesUtils.getUserNameByIdentityId(identityManager, 1l);
    assertNotNull(user);
    COMMONS_UTILS.when(() -> CommonsUtils.getService(ProjectService.class)).thenReturn(projectService);
    when(projectService.getProject(1l)).thenReturn(projectDto);
    // The class-wide static mock may hold a stub of another test for project 1
    PROCESSES_UTILS.when(() -> ProcessesUtils.getProjectParentSpace(1l)).thenCallRealMethod();
    Space space = ProcessesUtils.getProjectParentSpace(1l);
    assertNull(space);
    when(processesStorage.findWorkFlows(any(), anyLong(), anyInt(), anyInt())).thenReturn(workFlows);
    when(taskService.findTasks(taskQuery, 0, 0)).thenReturn(tasks);
    ENTITY_MAPPER.when(() -> EntityMapper.tasksToWorkList(tasks)).thenReturn(works);
    processesStorage.getWorks(1L, workFilter, 0, 0);
    verify(taskService, times(1)).findTasks(any(), anyInt(), anyInt());
  }

  @Test
  public void getAvailableWorkStatuses() {
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
    ENTITY_MAPPER.when(() -> EntityMapper.toWorkStatuses(statuses)).thenReturn(workStatuses);

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
  public void testFindWorkflow() throws Exception {
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
    ENTITY_MAPPER.when(() -> EntityMapper.toEntity(workFlow)).thenReturn(workFlowEntity);
    when(workFlow.getId()).thenReturn(0L);
    when(workFlow.getProjectId()).thenReturn(1L);
    when(workFlow.getSpaceId()).thenReturn("1");

    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(identity.getRemoteId()).thenReturn("user");
    when(identity.getId()).thenReturn("1");
    org.exoplatform.services.security.Identity aclIdentity = mock(org.exoplatform.services.security.Identity.class);
    when(userACL.getUserIdentity("user")).thenReturn(aclIdentity);
    when(space.getGroupId()).thenReturn("/spaces/processes_space");
    when(spaceService.getSpaceByGroupId("/spaces/processes_space")).thenReturn(space);
    when(spaceService.getSpaceById("1")).thenReturn(space);
    USER_UTIL.when(() -> UserUtil.getSpaceMemberships(space.getGroupId())).thenReturn(memberships);
    workFlow.setDescription("anything");
    workFlow.setTitle("title");
    workFlow.setProjectId(1L);
    workFlow.setSpaceId("1");
    PROJECT_UTIL.when(() -> ProjectUtil.newProjectInstanceDto(workFlow.getTitle(),
                                           workFlow.getDescription(),
                                           managers,
                                           participators)).thenReturn(projectDto);
    projectDto.setId(1L);
    when(projectService.createProject(projectDto)).thenReturn(projectDto);
    doNothing().when(statusService).createInitialStatuses(projectDto);
    when(workFlow.getAttachments()).thenReturn(attachments.toArray(new Attachment[attachments.size()]));
    ENTITY_MAPPER.when(() -> EntityMapper.toEntity(workFlow)).thenReturn(workFlowEntity);
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
    ProcessesFilter filter = new ProcessesFilter("", null, null, true);
    List<WorkFlowEntity> workFlowEntities = new ArrayList<>();
    workFlowEntities.add(newWorkFlowEntity);
    memberships = new ArrayList<>();
    memberships.add(identity.getRemoteId());
    memberships.add("member:/platform/administrators");
    memberships.add("*:/platform/administrators");
    memberships.add("manager:/platform/administrators");
    memberships.add("*:/platform/users");
    memberships.add("manager:/platform/users");
    memberships.add("member:/platform/users");
    when(workFlowDAO.findWorkFlows(filter, memberships, 0, 0)).thenReturn(workFlowEntities);
    when(workFlow.getIllustrativeAttachment()).thenReturn(illustrativeAttachment);
    this.processesStorage.saveWorkFlow(workFlow, 1L);
    assertEquals(null, this.processesStorage.getWorkFlowById(1, null));

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

    PROCESSES_UTILS.when(() -> ProcessesUtils.getProjectParentSpace(workFlow.getProjectId())).thenReturn(space);
    ENTITY_MAPPER.when(() -> EntityMapper.fromEntity(newWorkFlowEntity1, null)).thenReturn(workFlow);
    when(aclIdentity.getMemberships()).thenReturn(memberships_.stream().map(m -> new MembershipEntry(m.getGroupId(), m.getMembershipType())).toList());
    assertEquals(1, this.processesStorage.findWorkFlows(filter, Long.parseLong(identity.getId()), 0, 0).size());

    MembershipImpl adminProcesses = new MembershipImpl();
    adminProcesses.setMembershipType("*");
    adminProcesses.setUserName("user");
    adminProcesses.setGroupId("/platform/processes");
    memberships_.add(adminProcesses);

    PROCESSES_UTILS.when(() -> ProcessesUtils.getProjectParentSpace(workFlow.getProjectId())).thenReturn(space);
    ENTITY_MAPPER.when(() -> EntityMapper.fromEntity(newWorkFlowEntity1, null)).thenReturn(workFlow);

    when(aclIdentity.getMemberships()).thenReturn(memberships_.stream().map(m -> new MembershipEntry(m.getGroupId(), m.getMembershipType())).toList());
    assertEquals(0, this.processesStorage.findWorkFlows(filter, Long.parseLong(identity.getId()), 0, 0).size());
  }

  @Test
  public void countWorkflow() {
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
    ENTITY_MAPPER.when(() -> EntityMapper.toEntity(workFlow)).thenReturn(workFlowEntity);
    when(workFlow.getId()).thenReturn(0L);
    when(workFlow.getProjectId()).thenReturn(1L);
    when(workFlow.getSpaceId()).thenReturn("1");

    when(identityManager.getIdentity(1)).thenReturn(identity);
    when(space.getGroupId()).thenReturn("/spaces/processes_space");
    when(spaceService.getSpaceByGroupId("/spaces/processes_space")).thenReturn(space);
    when(spaceService.getSpaceById("1")).thenReturn(space);
    USER_UTIL.when(() -> UserUtil.getSpaceMemberships(space.getGroupId())).thenReturn(memberships);
    workFlow.setDescription("anything");
    workFlow.setTitle("title");
    workFlow.setProjectId(1L);
    workFlow.setSpaceId("1");
    PROJECT_UTIL.when(() -> ProjectUtil.newProjectInstanceDto(workFlow.getTitle(),
                                           workFlow.getDescription(),
                                           managers,
                                           participators)).thenReturn(projectDto);
    projectDto.setId(1L);
    when(projectService.createProject(projectDto)).thenReturn(projectDto);
    doNothing().when(statusService).createInitialStatuses(projectDto);
    when(workFlow.getAttachments()).thenReturn(attachments.toArray(new Attachment[attachments.size()]));
    ENTITY_MAPPER.when(() -> EntityMapper.toEntity(workFlow)).thenReturn(workFlowEntity);
    WorkFlowEntity newWorkFlowEntity = new WorkFlowEntity();
    newWorkFlowEntity.setId(1L);
    newWorkFlowEntity.setProjectId(1L);
    newWorkFlowEntity.hashCode();
    newWorkFlowEntity.toString();
    newWorkFlowEntity.equals(workFlow);
    when(workFlowDAO.create(workFlowEntity)).thenReturn(newWorkFlowEntity);
    ProcessesFilter filter = new ProcessesFilter("", null, true, true);
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

  /**
   * The processes a user manages are those whose managing space the user
   * administers: the manager filter queries the manager memberships only,
   * while the other lists query all of them, and a '*' membership, which the
   * permissions count as both, is queried as a manager and a member one too.
   * Mutation: query every membership with the manager filter, or stop
   * expanding the '*' membership, and this test fails.
   */
  @Test
  public void findWorkFlowsManagedByTheUserQueriesTheManagerMembershipsOnly() {
    mockAclIdentity(5L,
                    "john",
                    new MembershipEntry("/spaces/hr", "member"),
                    new MembershipEntry("/spaces/hr", "manager"),
                    new MembershipEntry("/spaces/legal", "*"));
    ProcessesFilter managedFilter = new ProcessesFilter("managed", null, true, false);
    ProcessesFilter allFilter = new ProcessesFilter("all", null, null, false);

    this.processesStorage.findWorkFlows(managedFilter, 5L, 0, 10);
    this.processesStorage.findWorkFlows(allFilter, 5L, 0, 10);

    verify(workFlowDAO).findWorkFlows(eq(managedFilter),
                                      argThat(memberships -> memberships.size() == 2
                                          && memberships.containsAll(List.of("manager:/spaces/hr", "manager:/spaces/legal"))),
                                      eq(0),
                                      eq(10));
    verify(workFlowDAO).findWorkFlows(eq(allFilter),
                                      argThat(memberships -> memberships.size() == 6
                                          && memberships.containsAll(List.of("john",
                                                                             "member:/spaces/hr",
                                                                             "manager:/spaces/hr",
                                                                             "*:/spaces/legal",
                                                                             "manager:/spaces/legal",
                                                                             "member:/spaces/legal"))),
                                      eq(0),
                                      eq(10));
  }

  /**
   * A user who administers no space manages no process: the manager filter
   * lists nothing, without a query. Mutation: query with every membership and
   * this test fails.
   */
  @Test
  public void findWorkFlowsManagedByASpaceMemberListsNothing() {
    mockAclIdentity(5L, "jane", new MembershipEntry("/spaces/hr", "member"));
    ProcessesFilter managedFilter = new ProcessesFilter("managed", null, true, false);

    assertTrue(this.processesStorage.findWorkFlows(managedFilter, 5L, 0, 10).isEmpty());
    verify(workFlowDAO, never()).findWorkFlows(eq(managedFilter), any(), anyInt(), anyInt());
  }

  /**
   * A processes administrator, platform administrators included, holds every
   * permission on every process: the storage maps the process without
   * membership restriction. Mutation: test the /platform/processes membership
   * alone and this test fails.
   */
  @Test
  public void getWorkFlowByIdGivesTheProcessesAdministratorsEveryPermission() {
    org.exoplatform.services.security.Identity aclIdentity = mockAclIdentity(6L, "admin");
    PROCESSES_UTILS.when(() -> ProcessesUtils.isProcessesAdministrator(userACL, aclIdentity)).thenReturn(true);
    WorkFlowEntity workFlowEntity = new WorkFlowEntity();
    when(workFlowDAO.find(1L)).thenReturn(workFlowEntity);

    this.processesStorage.getWorkFlowById(1L, 6L);

    ENTITY_MAPPER.verify(() -> EntityMapper.fromEntity(workFlowEntity, null));
  }

  private org.exoplatform.services.security.Identity mockAclIdentity(long identityId,
                                                                    String username,
                                                                    MembershipEntry... memberships) {
    Identity identity = mock(Identity.class);
    when(identity.getRemoteId()).thenReturn(username);
    when(identityManager.getIdentity(identityId)).thenReturn(identity);
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity(username,
                                                                                                             List.of(memberships));
    when(userACL.getUserIdentity(username)).thenReturn(aclIdentity);
    return aclIdentity;
  }
}
