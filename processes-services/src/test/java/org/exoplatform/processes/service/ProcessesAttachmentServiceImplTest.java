package org.exoplatform.processes.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.jcr.AccessDeniedException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.Workspace;
import javax.jcr.nodetype.NodeType;

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
import org.exoplatform.processes.model.ProcessPermission;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.services.attachments.model.Attachment;
import org.exoplatform.services.attachments.service.AttachmentService;
import org.exoplatform.services.attachments.utils.Utils;
import org.exoplatform.services.cms.drives.DriveData;
import org.exoplatform.services.cms.drives.ManageDriveService;
import org.exoplatform.services.cms.link.NodeFinder;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.wcm.core.NodetypeConstant;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
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

  @Mock
  private UserACL                    userACL;

  @Mock
  private SpaceService               spaceService;

  private ProcessesAttachmentService processesAttachmentService;

  private static final long          REQUESTER_ID       = 5L;

  private static final long          DRAFT_ID           = 50L;

  private static final long          WORKFLOW_ID        = 7L;

  private static final long          PROJECT_ID         = 10L;

  private static final String        SPACE_GROUP        = "/spaces/hr";

  private static final String        DRIVE_HOME         = "/Groups/spaces/hr/Documents";

  private static final String        DRAFT_FOLDER       = DRIVE_HOME + "/workdraft/" + DRAFT_ID;

  private static final String        WORKSPACE          = "collaboration";

  private ExtendedSession            systemSession;

  private ExtendedSession            requesterSession;

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    COMMONS_UTILS.close();
    UTILS.close();
    CONVERSATION_STATE.close();
    PROCESSES_UTILS.close();
  }

  @Before
  public void setUp() throws Exception {
    this.processesAttachmentService = new ProcessesAttachmentServiceImpl(attachmentService,
                                                                         repositoryService,
                                                                         sessionProviderService,
                                                                         manageDriveService,
                                                                         nodeHierarchyCreator,
                                                                         nodeFinder,
                                                                         projectService,
                                                                         identityManager,
                                                                         userACL,
                                                                         spaceService);
    ConversationState conversationState = mock(ConversationState.class);
    CONVERSATION_STATE.when(() -> ConversationState.getCurrent()).thenReturn(conversationState);
    Identity identity = mock(Identity.class);
    org.exoplatform.social.core.identity.model.Identity userIdentity = mock(org.exoplatform.social.core.identity.model.Identity.class);
    when(conversationState.getIdentity()).thenReturn(identity);
    when(identityManager.getIdentity(any())).thenReturn(userIdentity);
    when(identity.getUserId()).thenReturn("test");
    when(userIdentity.getRemoteId()).thenReturn("test");
  }

  @Test
  public void linkAttachmentsToEntity() throws Exception {
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
    processesAttachmentService.linkAttachmentsToEntity(attachmentList.toArray(new Attachment[0]), 1L, 1L, "workflow", 1L);
    verify(attachmentService, times(0)).linkAttachmentToEntity(1L, 1L, "workflow", "1");
    attachmentList.add(attachment);
    when(projectService.getProject(1L)).thenReturn(projectDto);
    processesAttachmentService.linkAttachmentsToEntity(attachmentList.toArray(new Attachment[0]), 1L, 1L, "workflow", 1L);
    verify(attachmentService, times(1)).linkAttachmentToEntity(1L, 1L, "workflow", "1");
  }

  @Test
  public void moveAttachmentsToEntity() throws Exception {
    List<Attachment> attachmentList = new ArrayList<>();
    Attachment attachment = new Attachment();
    attachment.setId("1");
    Workspace workspace = mock(Workspace.class);
    Session session = mock(Session.class);
    Node node = mock(Node.class);
    when(session.getWorkspace()).thenReturn(workspace);
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
    ProcessesAttachmentService processesAttachmentService1 = mock(ProcessesAttachmentService.class);
    when(attachmentService.getAttachmentsByEntity(1L, 1L, "workflow")).thenReturn(attachmentList);
    processesAttachmentService.moveAttachmentsToEntity(1L, 1L, "workflow", 1L, "workdraft", 1L);
    verify(processesAttachmentService1, times(0)).linkAttachmentsToEntity(attachmentList.toArray(new Attachment[0]),
                                                                         1L,
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
    processesAttachmentService.moveAttachmentsToEntity(1L, 1L, "workflow", 1L, "workdraft", 1L);
    verify(workspace, times(1)).move("srcPath", "destPath/test");

  }

  @Test
  public void copyAttachmentsToEntity() throws Exception {
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
    ProcessesAttachmentService processesAttachmentService1 = mock(ProcessesAttachmentService.class);
    when(attachmentService.getAttachmentsByEntity(1L, 1L, "workdraft")).thenReturn(attachmentList);
    processesAttachmentService.copyAttachmentsToEntity(1L, 1L, "workdraft", 1L, "work", 1L);
    verify(processesAttachmentService1,
           times(0)).linkAttachmentsToEntity(attachmentList.toArray(new Attachment[0]), 1L, 1L, "work", 1L);
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
    processesAttachmentService.copyAttachmentsToEntity(1L, 1L, "workdraft", 1L, "work", 1L);
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
    processesAttachmentService.copyAttachmentsToEntity(1L, 1L, "workdraft", 1L, "work", 1L);
    verify(workspace, times(1)).copy("srcPath", "destPath/test.docxf");
  }
  
  @Test
  public void createNewFormDocument() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setProjectId(1L);
    workFlow.setAcl(new ProcessPermission(true, true, false, false));
    ProcessesService processesService = mock(ProcessesService.class);
    COMMONS_UTILS.when(() -> CommonsUtils.getService(ProcessesService.class)).thenReturn(processesService);
    when(processesService.getWorkFlow(1L, 1L)).thenReturn(workFlow);
    Attachment attachment = mock(Attachment.class);
    when(attachmentService.createNewDocument(any(), any(), any(), any(), any())).thenReturn(attachment);
    copyAttachmentsToEntity();
    processesAttachmentService.createNewFormDocument(1L, "doc", "path", "spaces.processes_space", "template", "workflow", 1L);
    verify(attachmentService).createNewDocument(any(), any(), any(), any(), any());
  }

  /**
   * A document is created in a process only by a user who can edit it, and the
   * refusal comes before the document exists. Mutation: drop the canEdit guard,
   * or resolve the workflow with a null user, and this test fails.
   */
  @Test
  public void createNewFormDocumentIsRefusedWhenUserCantEditTheWorkflow() throws Exception {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setProjectId(1L);
    workFlow.setAcl(new ProcessPermission(false, false, false, true));
    ProcessesService processesService = mock(ProcessesService.class);
    COMMONS_UTILS.when(() -> CommonsUtils.getService(ProcessesService.class)).thenReturn(processesService);
    when(processesService.getWorkFlow(1L, 2L)).thenReturn(workFlow);

    // The pin is that no document is created; the exception types are asserted
    // after it, so that a guard removed fails here and not on what follows it
    Exception refusal = assertThrows(Exception.class,
                                     () -> processesAttachmentService.createNewFormDocument(2L,
                                                                                           "doc",
                                                                                           "path",
                                                                                           "drive",
                                                                                           "template",
                                                                                           "workflow",
                                                                                           1L));
    Exception notFound = assertThrows(Exception.class,
                                      () -> processesAttachmentService.createNewFormDocument(2L,
                                                                                            "doc",
                                                                                            "path",
                                                                                            "drive",
                                                                                            "template",
                                                                                            "workflow",
                                                                                            3L));
    verify(attachmentService, never()).createNewDocument(any(), any(), any(), any(), any());
    assertEquals(IllegalAccessException.class, refusal.getClass());
    assertEquals(ObjectNotFoundException.class, notFound.getClass());
  }
  /**
   * A request takes its draft's stored attachments, and the list the client
   * sends only tells which of them were picked from a drive. Mutation: select
   * among the client's list and the foreign attachment is kept, or ignore the
   * client's drive pick and the picked document is moved: this test fails.
   */
  @Test
  public void selectRequestAttachmentsTakesTheDraftsStoredAttachmentsOnly() throws Exception {
    mockRequesterSessions();
    readableNode("picked", "/Users/r___/requester/Private/Documents", true);
    when(attachmentService.getAttachmentsByEntity(REQUESTER_ID, DRAFT_ID, "workdraft")).thenReturn(attachments("picked"));
    readableNode("foreign", "/Groups/spaces/secret/Documents", true);
    Attachment pickedFromDrive = attachment("picked");
    pickedFromDrive.setEXoDrive(true);

    List<Attachment> selected = processesAttachmentService.selectRequestAttachments(REQUESTER_ID,
                                                                                    DRAFT_ID,
                                                                                    PROJECT_ID,
                                                                                    Arrays.asList(attachment("foreign"),
                                                                                                  pickedFromDrive));

    assertEquals(Collections.singletonList("picked"), ids(selected));
    assertTrue("a document picked from a drive is copied", selected.get(0).isEXoDrive());
  }

  /**
   * A draft attachment the requester can't read through their own session
   * refuses the request, even when the platform's session reads it.
   * Mutation: skip it silently, or read it with the platform's session, and
   * this test fails.
   */
  @Test
  public void selectRequestAttachmentsRefusesAnAttachmentTheRequesterCantRead() throws Exception {
    mockRequesterSessions();
    unreadableNode("secret", "/Groups/spaces/secret/Documents");
    when(attachmentService.getAttachmentsByEntity(REQUESTER_ID, DRAFT_ID, "workdraft")).thenReturn(attachments("secret"));

    assertThrows(IllegalAccessException.class,
                 () -> processesAttachmentService.selectRequestAttachments(REQUESTER_ID,
                                                                          DRAFT_ID,
                                                                          PROJECT_ID,
                                                                          Collections.emptyList()));
  }

  /**
   * The user's session reads as the current thread's user, whatever identity
   * it is built with: a user who isn't the current one is refused before any
   * attachment is read, and so is a thread with no user. Mutation: drop
   * either check and this test fails.
   */
  @Test
  public void selectRequestAttachmentsRefusesAUserWhoIsntTheCurrentOne() throws Exception {
    mockRequesterSessions();
    readableNode("picked", "/Users/r___/requester/Private/Documents", true);
    when(attachmentService.getAttachmentsByEntity(REQUESTER_ID, DRAFT_ID, "workdraft")).thenReturn(attachments("picked"));

    ConversationState otherUserState = mock(ConversationState.class);
    when(otherUserState.getIdentity()).thenReturn(new Identity("test"));
    CONVERSATION_STATE.when(ConversationState::getCurrent).thenReturn(otherUserState);
    assertThrows(IllegalAccessException.class,
                 () -> processesAttachmentService.selectRequestAttachments(REQUESTER_ID,
                                                                          DRAFT_ID,
                                                                          PROJECT_ID,
                                                                          Collections.emptyList()));
    CONVERSATION_STATE.when(ConversationState::getCurrent).thenReturn(null);
    assertThrows(IllegalAccessException.class,
                 () -> processesAttachmentService.selectRequestAttachments(REQUESTER_ID,
                                                                          DRAFT_ID,
                                                                          PROJECT_ID,
                                                                          Collections.emptyList()));
    verify(requesterSession, never()).getNodeByIdentifier(any());
  }

  /**
   * A user with no active identity is refused before any attachment is read.
   * Mutation: drop the guard and the selection fails with a
   * NullPointerException in the current-user check instead: this test fails.
   */
  @Test
  public void selectRequestAttachmentsRefusesAnInactiveUser() throws Exception {
    mockRequesterSessions();
    readableNode("picked", "/Users/r___/requester/Private/Documents", true);
    when(attachmentService.getAttachmentsByEntity(REQUESTER_ID, DRAFT_ID, "workdraft")).thenReturn(attachments("picked"));

    when(userACL.getUserIdentity("requester")).thenReturn(null);
    assertThrows(IllegalAccessException.class,
                 () -> processesAttachmentService.selectRequestAttachments(REQUESTER_ID,
                                                                          DRAFT_ID,
                                                                          PROJECT_ID,
                                                                          Collections.emptyList()));
    when(identityManager.getIdentity(String.valueOf(REQUESTER_ID))).thenReturn(null);
    assertThrows(IllegalAccessException.class,
                 () -> processesAttachmentService.selectRequestAttachments(REQUESTER_ID,
                                                                          DRAFT_ID,
                                                                          PROJECT_ID,
                                                                          Collections.emptyList()));
    verify(requesterSession, never()).getNodeByIdentifier(any());
  }

  /**
   * A readable attachment is moved only when the user may remove it and
   * didn't pick it from a drive; otherwise it is copied and stays where it
   * is. Mutation: ignore the remove permission, or the drive pick, and this
   * test fails.
   */
  @Test
  public void selectRequestAttachmentsCopiesWhatTheUserMayNotMove() throws Exception {
    mockRequesterSessions();
    readableNode("uploaded", "/Users/r___/requester/Private/Documents", true);
    readableNode("picked", "/Users/r___/requester/Private/Documents", true);
    readableNode("readOnly", "/Groups/spaces/sales/Documents", false);
    when(attachmentService.getAttachmentsByEntity(REQUESTER_ID,
                                                  DRAFT_ID,
                                                  "workdraft")).thenReturn(attachments("uploaded", "picked", "readOnly"));
    Attachment pickedFromDrive = attachment("picked");
    pickedFromDrive.setEXoDrive(true);

    List<Attachment> selected = processesAttachmentService.selectRequestAttachments(REQUESTER_ID,
                                                                                    DRAFT_ID,
                                                                                    PROJECT_ID,
                                                                                    Collections.singletonList(pickedFromDrive));

    assertEquals(Arrays.asList("uploaded", "picked", "readOnly"), ids(selected));
    assertFalse("an upload the user may remove is moved", selected.get(0).isEXoDrive());
    assertTrue("a document picked from a drive is copied", selected.get(1).isEXoDrive());
    assertTrue("a document the user may not remove is copied", selected.get(2).isEXoDrive());
  }

  /**
   * An attachment the processes placed in the draft's own folder, the copy of
   * a process form, is kept and moved even when the user can't read it; one
   * in another draft's folder is not trusted. Mutation: drop the folder
   * exemption, or compare the folder loosely, and this test fails.
   */
  @Test
  public void selectRequestAttachmentsKeepsWhatTheProcessesPlacedInTheDraftFolder() throws Exception {
    mockRequesterSessions();
    unreadableNode("form", DRAFT_FOLDER);
    when(attachmentService.getAttachmentsByEntity(REQUESTER_ID, DRAFT_ID, "workdraft")).thenReturn(attachments("form"));

    List<Attachment> selected = processesAttachmentService.selectRequestAttachments(REQUESTER_ID,
                                                                                    DRAFT_ID,
                                                                                    PROJECT_ID,
                                                                                    Collections.emptyList());

    assertEquals(Collections.singletonList("form"), ids(selected));
    assertFalse(selected.get(0).isEXoDrive());

    unreadableNode("otherForm", DRIVE_HOME + "/workdraft/51");
    when(attachmentService.getAttachmentsByEntity(REQUESTER_ID, DRAFT_ID, "workdraft")).thenReturn(attachments("otherForm"));
    assertThrows(IllegalAccessException.class,
                 () -> processesAttachmentService.selectRequestAttachments(REQUESTER_ID,
                                                                          DRAFT_ID,
                                                                          PROJECT_ID,
                                                                          Collections.emptyList()));
  }

  /**
   * On a process update, an attachment already linked to the process that the
   * editor can't read, another editor's drive pick, is left as it is and not
   * moved; one that isn't linked refuses the update, as on a creation, where
   * nothing is linked yet. Mutation: drop the linked exemption, or apply it to
   * every attachment, and this test fails.
   */
  @Test
  public void selectProcessAttachmentsLeavesAnUnreadableLinkedAttachmentAsItIs() throws Exception {
    mockRequesterSessions();
    unreadableNode("othersPick", "/Users/o___/other/Private/Documents");
    readableNode("uploaded", "/Users/r___/requester/Private/Documents", true);
    unreadableNode("foreign", "/Groups/spaces/secret/Documents");
    when(attachmentService.getAttachmentsByEntity(REQUESTER_ID, WORKFLOW_ID, "workflow")).thenReturn(attachments("othersPick"));

    Attachment[] selected = processesAttachmentService.selectProcessAttachments(REQUESTER_ID,
                                                                                WORKFLOW_ID,
                                                                                PROJECT_ID,
                                                                                new Attachment[] { attachment("othersPick"),
                                                                                    attachment("uploaded") });

    assertEquals(Collections.singletonList("uploaded"), ids(Arrays.asList(selected)));
    assertThrows(IllegalAccessException.class,
                 () -> processesAttachmentService.selectProcessAttachments(REQUESTER_ID,
                                                                          WORKFLOW_ID,
                                                                          PROJECT_ID,
                                                                          new Attachment[] { attachment("foreign") }));
    assertThrows(IllegalAccessException.class,
                 () -> processesAttachmentService.selectProcessAttachments(REQUESTER_ID,
                                                                          0,
                                                                          0,
                                                                          new Attachment[] { attachment("othersPick") }));
  }

  /**
   * On a process update, what the client sends back that is already linked to
   * the process, a drive pick's original in the editor's drive that the editor
   * may remove, sent without the drive-pick flag, or a document in the process
   * folder, is neither linked again, nor moved, nor copied, while an attachment
   * that isn't linked yet is still linked and moved. The selection goes through
   * the link as the process storage passes it. Mutation: select the linked
   * attachments again and this test fails on their link; leave every sent
   * attachment out on an update and it fails on the new one's link.
   */
  @Test
  public void selectProcessAttachmentsLeavesAnAlreadyLinkedAttachmentAsItIs() throws Exception {
    mockRequesterSessions();
    Workspace workspace = mockProcessFolder();
    String editorDocuments = "/Users/r___/requester/Private/Documents";
    String processFolder = DRIVE_HOME + "/workflow/" + WORKFLOW_ID;
    readableNode("original", editorDocuments, true);
    sourceNode(systemSession, "original", folder(editorDocuments));
    readableNode("processForm", processFolder, true);
    sourceNode(systemSession, "processForm", folder(processFolder));
    readableNode("uploaded", editorDocuments, true);
    sourceNode(systemSession, "uploaded", folder(editorDocuments));
    when(attachmentService.getAttachmentsByEntity(REQUESTER_ID, WORKFLOW_ID, "workflow")).thenReturn(attachments("original",
                                                                                                                 "processForm"));

    Attachment[] selected = processesAttachmentService.selectProcessAttachments(REQUESTER_ID,
                                                                                WORKFLOW_ID,
                                                                                PROJECT_ID,
                                                                                new Attachment[] { attachment("original"),
                                                                                    attachment("processForm"),
                                                                                    attachment("uploaded") });
    processesAttachmentService.linkAttachmentsToEntity(selected, REQUESTER_ID, WORKFLOW_ID, "workflow", PROJECT_ID);

    for (String linkedId : Arrays.asList("original", "processForm")) {
      verify(attachmentService, never()).linkAttachmentToEntity(REQUESTER_ID, WORKFLOW_ID, "workflow", linkedId);
      verify(workspace, never()).move(eq("/source/" + linkedId), anyString());
      verify(workspace, never()).copy(eq("/source/" + linkedId), anyString());
    }
    verify(attachmentService).linkAttachmentToEntity(REQUESTER_ID, WORKFLOW_ID, "workflow", "uploaded");
    verify(workspace).move("/source/uploaded", processFolder + "/uploaded");
    assertEquals(Collections.singletonList("uploaded"), ids(Arrays.asList(selected)));
  }

  /**
   * A process to create has no attachment list yet, and the attachment service
   * refuses an entity id of 0: the selection doesn't ask for one, and a
   * readable attachment sent with a new process is selected. Mutation: read
   * the process's attachment list whatever its id and this test fails.
   */
  @Test
  public void selectProcessAttachmentsReadsNoAttachmentListOnACreation() throws Exception {
    mockRequesterSessions();
    readableNode("uploaded", "/Users/r___/requester/Private/Documents", true);
    when(attachmentService.getAttachmentsByEntity(anyLong(),
                                                  eq(0L),
                                                  anyString())).thenThrow(new IllegalArgumentException("Entity Id should be positive"));

    Attachment[] selected = processesAttachmentService.selectProcessAttachments(REQUESTER_ID,
                                                                                0,
                                                                                0,
                                                                                new Attachment[] { attachment("uploaded") });

    assertEquals(Collections.singletonList("uploaded"), ids(Arrays.asList(selected)));
    assertFalse(selected[0].isEXoDrive());
  }

  /**
   * What the processes placed in the process folder of the process space's
   * drive is kept without a read check. Mutation: look for the entity's
   * folder in the process space's drive no more and this test fails.
   */
  @Test
  public void selectProcessAttachmentsKeepsWhatTheProcessesPlacedInTheProcessFolder() throws Exception {
    mockRequesterSessions();
    unreadableNode("processForm", DRIVE_HOME + "/workflow/" + WORKFLOW_ID);

    Attachment[] selected = processesAttachmentService.selectProcessAttachments(REQUESTER_ID,
                                                                                WORKFLOW_ID,
                                                                                PROJECT_ID,
                                                                                new Attachment[] { attachment("processForm") });

    assertEquals(Collections.singletonList("processForm"), ids(Arrays.asList(selected)));
    assertFalse(selected[0].isEXoDrive());
  }

  /**
   * A draft's folder stays in the drive of the space its process was in when
   * the draft was saved: after a space move, what the processes placed there
   * is kept, while a folder of another draft or entity there, or one that only
   * looks like a draft's folder outside the root of a space's drive, isn't.
   * Only a draft's folder is looked for in another space's drive. Mutation:
   * look in the current drive only, accept any group, drop the draft or the
   * folder name check, or look for a process folder too, and this test fails.
   */
  @Test
  public void selectRequestAttachmentsKeepsWhatTheProcessesPlacedInTheDraftFolderBeforeASpaceMove() throws Exception {
    mockRequesterSessions();
    when(spaceService.getSpaceByGroupId("/spaces/old")).thenReturn(new Space());
    unreadableNode("oldForm", "/Groups/spaces/old/Documents/workdraft/" + DRAFT_ID);
    when(attachmentService.getAttachmentsByEntity(REQUESTER_ID, DRAFT_ID, "workdraft")).thenReturn(attachments("oldForm"));

    List<Attachment> selected = processesAttachmentService.selectRequestAttachments(REQUESTER_ID,
                                                                                    DRAFT_ID,
                                                                                    PROJECT_ID,
                                                                                    Collections.emptyList());

    assertEquals(Collections.singletonList("oldForm"), ids(selected));
    assertFalse(selected.get(0).isEXoDrive());
    List<String> otherFolders = Arrays.asList("/Groups/spaces/old/Documents/workdraft/51",
                                              "/Groups/spaces/old/Documents/task/" + DRAFT_ID,
                                              "/Groups/spaces/hr/Documents/sub/Documents/workdraft/" + DRAFT_ID,
                                              "/Groups/platform/users/Documents/workdraft/" + DRAFT_ID);
    for (int i = 0; i < otherFolders.size(); i++) {
      String id = "other" + i;
      unreadableNode(id, otherFolders.get(i));
      when(attachmentService.getAttachmentsByEntity(REQUESTER_ID, DRAFT_ID, "workdraft")).thenReturn(attachments(id));
      assertThrows(otherFolders.get(i),
                   IllegalAccessException.class,
                   () -> processesAttachmentService.selectRequestAttachments(REQUESTER_ID,
                                                                            DRAFT_ID,
                                                                            PROJECT_ID,
                                                                            Collections.emptyList()));
    }
    unreadableNode("oldProcessForm", "/Groups/spaces/old/Documents/workflow/" + WORKFLOW_ID);
    assertThrows(IllegalAccessException.class,
                 () -> processesAttachmentService.selectProcessAttachments(REQUESTER_ID,
                                                                          WORKFLOW_ID,
                                                                          PROJECT_ID,
                                                                          new Attachment[] { attachment("oldProcessForm") }));
  }

  /**
   * A shortcut isn't referenceable, so only a lookup by identifier finds it:
   * a readable shortcut is selected on a request and on a process update.
   * Mutation: look either session's node up by UUID and this test fails.
   */
  @Test
  public void selectAttachmentsTakesAReadableShortcut() throws Exception {
    mockRequesterSessions();
    readableNode("shortcut", "/Users/r___/requester/Private/Documents/Shared", false);
    UnsupportedRepositoryOperationException notReferenceable = new UnsupportedRepositoryOperationException("not referenceable");
    when(systemSession.getNodeByUUID("shortcut")).thenThrow(notReferenceable);
    when(requesterSession.getNodeByUUID("shortcut")).thenThrow(notReferenceable);
    when(attachmentService.getAttachmentsByEntity(REQUESTER_ID, DRAFT_ID, "workdraft")).thenReturn(attachments("shortcut"));

    List<Attachment> selected = processesAttachmentService.selectRequestAttachments(REQUESTER_ID,
                                                                                    DRAFT_ID,
                                                                                    PROJECT_ID,
                                                                                    Collections.emptyList());
    Attachment[] updated = processesAttachmentService.selectProcessAttachments(REQUESTER_ID,
                                                                               WORKFLOW_ID,
                                                                               PROJECT_ID,
                                                                               new Attachment[] { attachment("shortcut") });

    assertEquals(Collections.singletonList("shortcut"), ids(selected));
    assertEquals(Collections.singletonList("shortcut"), ids(Arrays.asList(updated)));
  }

  /**
   * Only a denied or missing node counts as unreadable: another repository
   * error fails the selection instead of passing for a refusal. Mutation:
   * count every repository error as unreadable and this test fails.
   */
  @Test
  public void selectProcessAttachmentsFailsOnARepositoryError() throws Exception {
    mockRequesterSessions();
    systemNode("broken", "/Groups/spaces/hr/Documents");
    when(requesterSession.getNodeByIdentifier("broken")).thenThrow(new RepositoryException("broken"));

    assertThrows(IllegalStateException.class,
                 () -> processesAttachmentService.selectProcessAttachments(REQUESTER_ID,
                                                                          WORKFLOW_ID,
                                                                          PROJECT_ID,
                                                                          new Attachment[] { attachment("broken") }));
  }

  /**
   * A move removes the source folder only when it is the folder the processes
   * keep for the source entity in the process drive, never the folder a
   * user's document came from, nor a folder of the same names in another
   * drive or of another draft. Mutation: remove any source folder, or none, or
   * compare the folder's names only, and this test fails.
   */
  @Test
  public void moveRemovesOnlyTheSourceEntityFolder() throws Exception {
    Session session = mock(Session.class);
    Workspace workspace = mock(Workspace.class);
    when(session.getWorkspace()).thenReturn(workspace);
    UTILS.when(() -> Utils.getSystemSession(sessionProviderService, repositoryService)).thenReturn(session);
    ProjectDto projectDto = new ProjectDto();
    projectDto.setId(PROJECT_ID);
    projectDto.setManager(new HashSet<>(Collections.singletonList("manager:" + SPACE_GROUP)));
    projectDto.setParticipator(new HashSet<>(Collections.singletonList("member:" + SPACE_GROUP)));
    when(projectService.getProject(PROJECT_ID)).thenReturn(projectDto);
    Space space = new Space();
    space.setGroupId(SPACE_GROUP);
    PROCESSES_UTILS.when(() -> ProcessesUtils.getProjectParentSpace(PROJECT_ID)).thenReturn(space);
    DriveData driveData = new DriveData();
    driveData.setHomePath(DRIVE_HOME);
    driveData.setName("spaces.hr");
    when(manageDriveService.getGroupDrive(SPACE_GROUP)).thenReturn(driveData);
    Node rootNode = mock(Node.class);
    when(rootNode.getPath()).thenReturn(DRIVE_HOME);
    when(rootNode.hasNode("task")).thenReturn(true);
    when(rootNode.getNode("task")).thenReturn(rootNode);
    when(rootNode.hasNode("9")).thenReturn(true);
    ExtendedNode destNode = mock(ExtendedNode.class);
    when(destNode.getPath()).thenReturn(DRIVE_HOME + "/task/9");
    UTILS.when(() -> Utils.getParentFolderNode(session,
                                               manageDriveService,
                                               nodeHierarchyCreator,
                                               nodeFinder,
                                               "spaces.hr",
                                               "task/9")).thenReturn(destNode);
    when(session.getItem(any())).thenAnswer(invocation -> DRIVE_HOME.equals(invocation.getArgument(0)) ? rootNode
                                                                                                         : mock(Node.class));

    Node documentsFolder = folder("/Users/r___/requester/Private/Documents");
    sourceNode(session, "upload", documentsFolder);
    processesAttachmentService.moveAttachmentsToEntity(attachments("upload"),
                                                       REQUESTER_ID,
                                                       DRAFT_ID,
                                                       "workdraft",
                                                       9L,
                                                       "task",
                                                       PROJECT_ID);
    verify(workspace).move("/source/upload", DRIVE_HOME + "/task/9/upload");
    verify(documentsFolder, never()).remove();

    Node sameNameFolder = folder("/Groups/spaces/sales/Documents/workdraft/" + DRAFT_ID);
    sourceNode(session, "named", sameNameFolder);
    processesAttachmentService.moveAttachmentsToEntity(attachments("named"),
                                                       REQUESTER_ID,
                                                       DRAFT_ID,
                                                       "workdraft",
                                                       9L,
                                                       "task",
                                                       PROJECT_ID);
    verify(workspace).move("/source/named", DRIVE_HOME + "/task/9/named");
    verify(sameNameFolder, never()).remove();

    Node otherDraftFolder = folder(DRIVE_HOME + "/workdraft/51");
    sourceNode(session, "otherDraft", otherDraftFolder);
    processesAttachmentService.moveAttachmentsToEntity(attachments("otherDraft"),
                                                       REQUESTER_ID,
                                                       DRAFT_ID,
                                                       "workdraft",
                                                       9L,
                                                       "task",
                                                       PROJECT_ID);
    verify(workspace).move("/source/otherDraft", DRIVE_HOME + "/task/9/otherDraft");
    verify(otherDraftFolder, never()).remove();

    Node draftFolder = folder(DRAFT_FOLDER);
    sourceNode(session, "form", draftFolder);
    processesAttachmentService.moveAttachmentsToEntity(attachments("form"),
                                                       REQUESTER_ID,
                                                       DRAFT_ID,
                                                       "workdraft",
                                                       9L,
                                                       "task",
                                                       PROJECT_ID);
    verify(workspace).move("/source/form", DRIVE_HOME + "/task/9/form");
    verify(draftFolder).remove();
  }

  private void mockRequesterSessions() throws Exception {
    org.exoplatform.social.core.identity.model.Identity requester =
                                                                  mock(org.exoplatform.social.core.identity.model.Identity.class);
    when(requester.getRemoteId()).thenReturn("requester");
    when(identityManager.getIdentity(String.valueOf(REQUESTER_ID))).thenReturn(requester);
    Identity requesterAclIdentity = new Identity("requester");
    when(userACL.getUserIdentity("requester")).thenReturn(requesterAclIdentity);
    ConversationState requesterState = mock(ConversationState.class);
    when(requesterState.getIdentity()).thenReturn(requesterAclIdentity);
    CONVERSATION_STATE.when(ConversationState::getCurrent).thenReturn(requesterState);
    requesterSession = mock(ExtendedSession.class);
    SessionProvider requesterSessionProvider = mock(SessionProvider.class);
    ManageableRepository repository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    UTILS.when(() -> Utils.getCurrentWorkspace(repositoryService)).thenReturn(WORKSPACE);
    when(requesterSessionProvider.getSession(WORKSPACE, repository)).thenReturn(requesterSession);
    UTILS.when(() -> Utils.getUserSessionProvider(repositoryService, requesterAclIdentity)).thenReturn(requesterSessionProvider);
    systemSession = mock(ExtendedSession.class);
    UTILS.when(() -> Utils.getSystemSession(sessionProviderService, repositoryService)).thenReturn(systemSession);
    Space space = new Space();
    space.setGroupId(SPACE_GROUP);
    PROCESSES_UTILS.when(() -> ProcessesUtils.getProjectParentSpace(PROJECT_ID)).thenReturn(space);
    when(spaceService.getSpaceByGroupId(SPACE_GROUP)).thenReturn(space);
    DriveData driveData = new DriveData();
    driveData.setHomePath(DRIVE_HOME);
    when(manageDriveService.getGroupDrive(SPACE_GROUP)).thenReturn(driveData);
    DriveData groupDriveTemplate = new DriveData();
    groupDriveTemplate.setHomePath("/Groups${groupId}/Documents");
    when(manageDriveService.getGroupDriveTemplate()).thenReturn(groupDriveTemplate);
  }

  /**
   * Lets the platform's session move or copy a node into the process folder,
   * after {@link #mockRequesterSessions()}.
   *
   * @return the workspace that moves and copies
   */
  private Workspace mockProcessFolder() throws Exception {
    ProjectDto projectDto = new ProjectDto();
    projectDto.setId(PROJECT_ID);
    projectDto.setManager(new HashSet<>(Collections.singletonList("manager:" + SPACE_GROUP)));
    projectDto.setParticipator(new HashSet<>(Collections.singletonList("member:" + SPACE_GROUP)));
    when(projectService.getProject(PROJECT_ID)).thenReturn(projectDto);
    Node rootNode = mock(Node.class);
    when(rootNode.getPath()).thenReturn(DRIVE_HOME);
    when(rootNode.hasNode("workflow")).thenReturn(true);
    when(rootNode.getNode("workflow")).thenReturn(rootNode);
    when(rootNode.hasNode(String.valueOf(WORKFLOW_ID))).thenReturn(true);
    when(systemSession.getItem(any())).thenAnswer(invocation -> DRIVE_HOME.equals(invocation.getArgument(0)) ? rootNode
                                                                                                               : mock(Node.class));
    ExtendedNode processFolder = mock(ExtendedNode.class);
    when(processFolder.getPath()).thenReturn(DRIVE_HOME + "/workflow/" + WORKFLOW_ID);
    UTILS.when(() -> Utils.getParentFolderNode(systemSession,
                                               manageDriveService,
                                               nodeHierarchyCreator,
                                               nodeFinder,
                                               null,
                                               "workflow/" + WORKFLOW_ID))
         .thenReturn(processFolder);
    Workspace workspace = mock(Workspace.class);
    when(systemSession.getWorkspace()).thenReturn(workspace);
    return workspace;
  }

  private void readableNode(String id, String parentPath, boolean removable) throws Exception {
    systemNode(id, parentPath);
    ExtendedNode node = mock(ExtendedNode.class);
    if (!removable) {
      doThrow(new AccessControlException("no remove")).when(node).checkPermission(PermissionType.REMOVE);
    }
    when(requesterSession.getNodeByIdentifier(id)).thenReturn(node);
  }

  private void unreadableNode(String id, String parentPath) throws Exception {
    systemNode(id, parentPath);
    when(requesterSession.getNodeByIdentifier(id)).thenThrow(new AccessDeniedException("no read"));
  }

  private void systemNode(String id, String parentPath) throws Exception {
    Node parent = pathNode(parentPath);
    ExtendedNode node = mock(ExtendedNode.class);
    when(node.getParent()).thenReturn(parent);
    when(systemSession.getNodeByIdentifier(id)).thenReturn(node);
  }

  private Node pathNode(String path) throws Exception {
    Node node = mock(Node.class);
    when(node.getPath()).thenReturn(path);
    if ("/".equals(path)) {
      when(node.getName()).thenReturn("");
      when(node.getParent()).thenThrow(new ItemNotFoundException("root"));
    } else {
      String parentPath = path.substring(0, path.lastIndexOf('/'));
      Node parent = pathNode(parentPath.isEmpty() ? "/" : parentPath);
      when(node.getName()).thenReturn(path.substring(path.lastIndexOf('/') + 1));
      when(node.getParent()).thenReturn(parent);
    }
    return node;
  }

  private Node folder(String path) throws Exception {
    Node folder = mock(Node.class);
    when(folder.getPath()).thenReturn(path);
    when(folder.getName()).thenReturn(path.substring(path.lastIndexOf('/') + 1));
    NodeType folderType = mock(NodeType.class);
    when(folderType.isNodeType(NodetypeConstant.NT_FOLDER)).thenReturn(true);
    when(folder.getPrimaryNodeType()).thenReturn(folderType);
    return folder;
  }

  private void sourceNode(Session session, String id, Node parent) throws Exception {
    Node node = mock(Node.class);
    when(node.getName()).thenReturn(id);
    when(node.getPath()).thenReturn("/source/" + id);
    when(node.getParent()).thenReturn(parent);
    when(session.getNodeByUUID(id)).thenReturn(node);
  }

  private List<Attachment> attachments(String... ids) {
    return Arrays.stream(ids).map(this::attachment).collect(Collectors.toCollection(ArrayList::new));
  }

  private Attachment attachment(String id) {
    Attachment attachment = new Attachment();
    attachment.setId(id);
    return attachment;
  }

  private List<String> ids(List<Attachment> attachments) {
    return attachments.stream().map(Attachment::getId).collect(Collectors.toList());
  }
}
