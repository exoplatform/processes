package org.exoplatform.processes.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.Workspace;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.processes.Utils.ProcessesUtils;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.services.attachments.model.Attachment;
import org.exoplatform.services.attachments.service.AttachmentService;
import org.exoplatform.services.attachments.utils.Utils;
import org.exoplatform.services.cms.drives.DriveData;
import org.exoplatform.services.cms.drives.ManageDriveService;
import org.exoplatform.services.cms.link.NodeFinder;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.wcm.core.NodetypeConstant;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.task.dto.ProjectDto;
import org.exoplatform.task.service.ProjectService;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ProcessesAttachmentServiceImplTest {

  private static final MockedStatic<CommonsUtils>      COMMONS_UTILS      =
                                                                     mockStatic(CommonsUtils.class);

  private static final MockedStatic<Utils>             UTILS              =
                                                             mockStatic(Utils.class);

  private static final MockedStatic<ConversationState> CONVERSATION_STATE =
                                                                          mockStatic(ConversationState.class);

  private static final MockedStatic<ProcessesUtils>    PROCESSES_UTILS    = mockStatic(ProcessesUtils.class);

  @Mock
  private AttachmentService          attachmentService;

  @Mock
  private RepositoryService          repositoryService;

  @Mock
  private SessionProviderService     sessionProviderService;

  @Mock
  private ManageDriveService         manageDriveService;

  @Mock
  private NodeHierarchyCreator       nodeHierarchyCreator;

  @Mock
  private NodeFinder                 nodeFinder;

  @Mock
  private ProjectService             projectService;

  @Mock
  private IdentityManager            identityManager;

  private ProcessAttachmentService processAttachmentService;

  private final String  userName = "testuser";

  @AfterClass
  public static void afterRunBare() { // NOSONAR
    COMMONS_UTILS.close();
    UTILS.close();
    CONVERSATION_STATE.close();
    PROCESSES_UTILS.close();
  }

  @Before
  public void setUp() {
    this.processAttachmentService = new ProcessAttachmentServiceImpl(attachmentService,
                                                                         repositoryService,
                                                                         sessionProviderService,
                                                                         manageDriveService,
                                                                         nodeHierarchyCreator,
                                                                         nodeFinder,
                                                                         projectService,
                                                                         identityManager);
    ConversationState conversationState = mock(ConversationState.class);
    CONVERSATION_STATE.when(ConversationState::getCurrent).thenReturn(conversationState);
    Identity identity = mock(Identity.class);
    org.exoplatform.social.core.identity.model.Identity userIdentity = mock(org.exoplatform.social.core.identity.model.Identity.class);
    when(conversationState.getIdentity()).thenReturn(identity);
    when(identityManager.getIdentity(anyLong())).thenReturn(userIdentity);
    when(identity.getUserId()).thenReturn("test");
    when(userIdentity.getRemoteId()).thenReturn("test");
  }

  @Test
  public void linkAttachmentsToEntity() throws Exception {
    org.exoplatform.social.core.identity.model.Identity socIdentity = mock(org.exoplatform.social.core.identity.model.Identity.class);
    List<Attachment> attachmentList = new ArrayList<>();
    Attachment attachment = new Attachment();
    attachment.setId("1");
    ProjectDto projectDto = new ProjectDto();
    projectDto.setId(1L);
    Set<String> managers = new HashSet<>();
    Set<String> participators = new HashSet<>();
    managers.add("manager:/spaces/processes_space");
    participators.add("members:/spaces/processes_space");
    projectDto.setManager(managers);
    projectDto.setParticipator(participators);
    Space space = new Space();
    space.setGroupId("/spaces/processes_space");
    PROCESSES_UTILS.when(() -> ProcessesUtils.getProjectParentSpace(1L)).thenReturn(space);
    processAttachmentService.linkAttachmentsToEntity(attachmentList.toArray(new Attachment[0]), userName, 1L, "workflow", 1L);
    verify(attachmentService, times(0)).linkAttachmentToEntity(1L, 1L, "workflow", "1");
    attachmentList.add(attachment);
    when(projectService.getProject(1L)).thenReturn(projectDto);
    when(identityManager.getOrCreateUserIdentity(userName)).thenReturn(null);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> processAttachmentService.linkAttachmentsToEntity(attachmentList.toArray(new Attachment[0]), userName, 1L, "workflow", 1L));
    assertEquals("identity does not exist", exception.getMessage());
    verify(attachmentService, times(0)).linkAttachmentToEntity(1L, 1L, "workflow", "1");
    when(identityManager.getOrCreateUserIdentity(userName)).thenReturn(socIdentity);
    when(socIdentity.getId()).thenReturn("1");
    processAttachmentService.linkAttachmentsToEntity(attachmentList.toArray(new Attachment[0]), userName, 1L, "workflow", 1L);
    verify(attachmentService, times(1)).linkAttachmentToEntity(1L, 1L, "workflow", "1");
  }

  @Test
  public void moveAttachmentsToEntity() throws Exception {
    org.exoplatform.social.core.identity.model.Identity socIdentity = mock(org.exoplatform.social.core.identity.model.Identity.class);
    when(identityManager.getIdentity(1)).thenReturn(null);
    List<Attachment> attachmentList = new ArrayList<>();
    Attachment attachment = new Attachment();
    attachment.setId("1");
    Session session = mock(Session.class);
    Node node = mock(Node.class);
    ExtendedNode extendedNode = mock(ExtendedNode.class);
    ProjectDto projectDto = new ProjectDto();
    projectDto.setId(1L);
    Set<String> managers = new HashSet<>();
    Set<String> participators = new HashSet<>();
    managers.add("manager:/spaces/processes_space");
    participators.add("members:/spaces/processes_space");
    projectDto.setManager(managers);
    projectDto.setParticipator(participators);
    Space space = new Space();
    space.setGroupId("/spaces/processes_space");
    when(projectService.getProject(1L)).thenReturn(projectDto);
    PROCESSES_UTILS.when(() -> ProcessesUtils.getProjectParentSpace(1L)).thenReturn(space);
    ProcessAttachmentService processAttachmentService1 = mock(ProcessAttachmentService.class);
    when(attachmentService.getAttachmentsByEntity(1L, 1L, "workflow")).thenReturn(attachmentList);
    when(identityManager.getOrCreateUserIdentity(userName)).thenReturn(null);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> processAttachmentService.moveAttachmentsToEntity(userName, 1L, "workflow", 1L, "workdraft", 1L));
    assertEquals("identity does not exist", exception.getMessage());
    when(identityManager.getOrCreateUserIdentity(userName)).thenReturn(socIdentity);
    when(identityManager.getIdentity(1L)).thenReturn(socIdentity);
    when(socIdentity.getId()).thenReturn("1");
    processAttachmentService.moveAttachmentsToEntity(userName, 1L, "workflow", 1L, "workdraft", 1L);
    verify(processAttachmentService1, times(0)).linkAttachmentsToEntity(attachmentList.toArray(new Attachment[0]),
            userName,
                                                                         1L,
                                                                         "workdraft", 1L);
    attachmentList.add(attachment);
    UTILS.when(() -> Utils.getSystemSession(sessionProviderService, repositoryService)).thenReturn(session);
    DriveData driveData = new DriveData();
    driveData.setHomePath("path");
    driveData.setName("processes.drive");
    when(manageDriveService.getGroupDrive("/spaces/processes_space")).thenReturn(driveData);
    when(session.getItem(driveData.getHomePath())).thenReturn(node);
    when(node.hasNode("workdraft")).thenReturn(false);
    when(node.hasNode("1")).thenReturn(false);
    when(node.addNode("workdraft", NodetypeConstant.NT_FOLDER)).thenReturn(node);
    when(node.addNode("1", NodetypeConstant.NT_FOLDER)).thenReturn(node);
    when(session.getNodeByUUID("1")).thenReturn(node);
    UTILS.when(() -> Utils.getParentFolderNode(session,
                                   this.manageDriveService,
                                   this.nodeHierarchyCreator,
                                   this.nodeFinder,
                                   driveData.getName(),
                                   "workdraft/1")).thenReturn(extendedNode);
    when(extendedNode.canAddMixin(NodetypeConstant.EXO_PRIVILEGEABLE)).thenReturn(true);
    when(extendedNode.getName()).thenReturn("test");
    when(node.getName()).thenReturn("test");
    when(node.getPath()).thenReturn("srcPath");
    when(extendedNode.getPath()).thenReturn("destPath");
    when(node.getParent()).thenReturn(node);
    processAttachmentService.moveAttachmentsToEntity(userName, 1L, "workflow", 1L, "workdraft", 1L);
    verify(session, times(1)).move("srcPath", "destPath/test");

  }

  @Test
  public void copyAttachmentsToEntity() throws Exception {
    org.exoplatform.social.core.identity.model.Identity socIdentity = mock(org.exoplatform.social.core.identity.model.Identity.class);
    List<Attachment> attachmentList = new ArrayList<>();
    Attachment attachment = new Attachment();
    attachment.setId("1");
    Session session = mock(Session.class);
    UTILS.when(() -> Utils.getSystemSession(sessionProviderService, repositoryService)).thenReturn(session);
    Node node = mock(Node.class);
    ExtendedNode extendedNode = mock(ExtendedNode.class);
    Workspace workspace = mock(Workspace.class);
    ProjectDto projectDto = new ProjectDto();
    projectDto.setId(1L);
    Set<String> managers = new HashSet<>();
    Set<String> participators = new HashSet<>();
    managers.add("manager:/spaces/processes_space");
    participators.add("members:/spaces/processes_space");
    projectDto.setManager(managers);
    projectDto.setParticipator(participators);
    Space space = new Space();
    space.setGroupId("/spaces/processes_space");
    PROCESSES_UTILS.when(() -> ProcessesUtils.getProjectParentSpace(1L)).thenReturn(space);
    when(projectService.getProject(1L)).thenReturn(projectDto);
    ProcessAttachmentService processAttachmentService1 = mock(ProcessAttachmentService.class);
    when(attachmentService.getAttachmentsByEntity(1L, 1L, "workdraft")).thenReturn(attachmentList);
    when(identityManager.getOrCreateUserIdentity(userName)).thenReturn(null);
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> processAttachmentService.copyAttachmentsToEntity(userName, 1L, "workdraft", 1L, "work", 1L));
    assertEquals("identity does not exist", exception.getMessage());
    when(identityManager.getOrCreateUserIdentity(userName)).thenReturn(socIdentity);
    processAttachmentService.copyAttachmentsToEntity(userName, 1L, "workdraft", 1L, "work", 1L);
    verify(processAttachmentService1,
           times(0)).linkAttachmentsToEntity(attachmentList.toArray(new Attachment[0]), userName, 1L, "work", 1L);
    attachmentList.add(attachment);

    DriveData driveData = new DriveData();
    driveData.setHomePath("path");
    driveData.setName("processes.drive");
    when(manageDriveService.getGroupDrive("/spaces/processes_space")).thenReturn(driveData);
    when(session.getItem(driveData.getHomePath())).thenReturn(node);
    when(node.hasNode("work")).thenReturn(false);
    when(node.hasNode("1")).thenReturn(false);
    when(node.addNode("work", NodetypeConstant.NT_FOLDER)).thenReturn(node);
    when(node.addNode("1", NodetypeConstant.NT_FOLDER)).thenReturn(node);
    when(session.getNodeByUUID("1")).thenReturn(node);
    UTILS.when(() -> Utils.getParentFolderNode(session,
            this.manageDriveService,
            this.nodeHierarchyCreator,
            this.nodeFinder,
            driveData.getName(),
            "work/1")).thenReturn(extendedNode);
    when(extendedNode.canAddMixin(NodetypeConstant.EXO_PRIVILEGEABLE)).thenReturn(true);
    when(extendedNode.getName()).thenReturn("test");
    when(node.getName()).thenReturn("test");
    when(node.getPath()).thenReturn("srcPath");
    when(extendedNode.getPath()).thenReturn("destPath");
    when(node.getParent()).thenReturn(node);
    when(session.getWorkspace()).thenReturn(workspace);
    when(session.getItem("destPath/test")).thenReturn(node);
    when(node.getUUID()).thenReturn("2");
    Attachment attachment1 = new Attachment();
    attachment1.setId("2");
    when(attachmentService.getAttachmentById("2")).thenReturn(attachment1);
    when(identityManager.getIdentity(1L)).thenReturn(socIdentity);
    when(socIdentity.getId()).thenReturn("1");
    processAttachmentService.copyAttachmentsToEntity(userName, 1L, "workdraft", 1L, "work", 1L);
    verify(workspace, times(1)).copy("srcPath", "destPath/test");

    DriveData userDriveData = new DriveData();
    userDriveData.setName("user");
    userDriveData.setHomePath("path/user/Private/Documents");
    when(session.getItem("path/user/Public/Documents")).thenReturn(node);
    PROCESSES_UTILS.when(() -> ProcessesUtils.getProjectParentSpace(1L)).thenReturn(null);
    when(manageDriveService.getUserDrive(managers.iterator().next())).thenReturn(userDriveData);
    UTILS.when(() -> Utils.getParentFolderNode(session,
            this.manageDriveService,
            this.nodeHierarchyCreator,
            this.nodeFinder,
            userDriveData.getName(),
            "work/1")).thenReturn(extendedNode);
    when(session.getNodeByUUID("2")).thenReturn(node);
    when(node.getName()).thenReturn("test.docxf");
    when(session.getItem("destPath/test.docxf")).thenReturn(node);
    Node content = mock(Node.class);
    when(node.getNode(NodetypeConstant.JCR_CONTENT)).thenReturn(content);
    when(node.hasProperty(NodetypeConstant.EXO_TITLE)).thenReturn(true);
    when(node.hasProperty(NodetypeConstant.EXO_NAME)).thenReturn(true);
    processAttachmentService.copyAttachmentsToEntity(userName, 1L, "workdraft", 1L, "work", 1L);
    verify(workspace, times(1)).copy("srcPath", "destPath/test.docxf");
  }
  
  @Test
  public void createNewFormDocument() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setProjectId(1L);
    ProcessService processService = mock(ProcessService.class);
    COMMONS_UTILS.when(() -> CommonsUtils.getService(ProcessService.class)).thenReturn(processService);
    when(processService.getWorkFlow(1L)).thenReturn(workFlow);
    Attachment attachment = mock(Attachment.class);
    when(attachmentService.createNewDocument(any(), any(), any(), any(), any())).thenReturn(attachment);
    copyAttachmentsToEntity();
    processAttachmentService.createNewFormDocument(userName, "doc", "path", "spaces.processes_space", "template", "workflow", 1L);
  }
}
