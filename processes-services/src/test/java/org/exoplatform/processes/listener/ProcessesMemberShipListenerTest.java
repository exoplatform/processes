package org.exoplatform.processes.listener;

import org.exoplatform.services.organization.*;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import static org.mockito.Mockito.verify;

import static org.mockito.internal.verification.VerificationModeFactory.times;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
public class ProcessesMemberShipListenerTest {

  @Mock
  private SpaceService                spaceService;

  @Mock
  private OrganizationService         organizationService;

  private ProcessesMemberShipListener processesMemberShipListener;

  private Space                       processesSpace;

  private static final String         PROCESSES_GROUP_ID       = "/platform/processes";

  private static final String         PROCESSES_SPACE_GROUP_ID = "/spaces/processes_space";

  private static final String         MEMBER                   = "member";

  private static final String         MANAGER                  = "manager";

  @Before
  public void setUp() throws Exception {
    this.processesMemberShipListener = new ProcessesMemberShipListener(spaceService, organizationService);
    processesSpace = new Space();
    processesSpace.setDisplayName("Processes Space");
    processesSpace.setVisibility(Space.HIDDEN);
    processesSpace.setRegistration(Space.CLOSED);
    processesSpace.setPriority(Space.INTERMEDIATE_PRIORITY);
    processesSpace.setTemplate("community");
    processesSpace.setGroupId(PROCESSES_SPACE_GROUP_ID);
    processesSpace.setPrettyName("processes_space");

  }

  @Test
  public void postSave() throws Exception {
    Membership memberShip = mock(Membership.class);
    MembershipType membershipType = mock(MembershipType.class);
    MembershipTypeHandler membershipTypeHandler = mock(MembershipTypeHandler.class);
    MembershipHandler membershipHandler = mock(MembershipHandler.class);
    when(memberShip.getUserName()).thenReturn("user");
    when(spaceService.getSpaceByGroupId(PROCESSES_SPACE_GROUP_ID)).thenReturn(processesSpace);
    when(memberShip.getGroupId()).thenReturn(PROCESSES_GROUP_ID);
    when(spaceService.isManager(processesSpace, "user")).thenReturn(false);
    processesMemberShipListener.postSave(memberShip, true);
    verify(spaceService, times(1)).setManager(processesSpace, "user", true);
    when(memberShip.getGroupId()).thenReturn(PROCESSES_SPACE_GROUP_ID);
    when(memberShip.getMembershipType()).thenReturn(MANAGER);
    Group group = mock(Group.class);
    User user = mock(User.class);
    GroupHandler groupHandler = mock(GroupHandler.class);
    UserHandler userHandler = mock(UserHandler.class);
    when(organizationService.getGroupHandler()).thenReturn(groupHandler);
    when(groupHandler.findGroupById(PROCESSES_GROUP_ID)).thenReturn(group);
    when(organizationService.getUserHandler()).thenReturn(userHandler);
    when(userHandler.findUserByName("user")).thenReturn(user);
    when(organizationService.getMembershipTypeHandler()).thenReturn(membershipTypeHandler);
    when(membershipTypeHandler.findMembershipType(MEMBER)).thenReturn(membershipType);
    when(organizationService.getMembershipHandler()).thenReturn(membershipHandler);
    processesMemberShipListener.postSave(memberShip, true);
    verify(membershipHandler, times(1)).linkMembership(user, group, membershipType, true);
  }

  @Test
  public void postDelete() throws Exception {
    Membership memberShip = mock(Membership.class);
    MembershipHandler membershipHandler = mock(MembershipHandler.class);
    Collection<Membership> memberships = new ArrayList<>();
    memberships.add(memberShip);
    when(spaceService.getSpaceByGroupId(PROCESSES_SPACE_GROUP_ID)).thenReturn(processesSpace);
    when(memberShip.getGroupId()).thenReturn(PROCESSES_GROUP_ID);
    when(memberShip.getUserName()).thenReturn("user");
    when(spaceService.isManager(processesSpace, "user")).thenReturn(true);
    processesMemberShipListener.postDelete(memberShip);
    verify(spaceService, times(1)).setManager(processesSpace, "user", false);
    when(memberShip.getGroupId()).thenReturn(PROCESSES_SPACE_GROUP_ID);
    when(memberShip.getMembershipType()).thenReturn(MANAGER);
    Group group = mock(Group.class);
    GroupHandler groupHandler = mock(GroupHandler.class);
    when(organizationService.getGroupHandler()).thenReturn(groupHandler);
    when(groupHandler.findGroupById(PROCESSES_GROUP_ID)).thenReturn(group);
    when(organizationService.getMembershipHandler()).thenReturn(membershipHandler);
    when(membershipHandler.findMembershipsByUserAndGroup("user", group.getId())).thenReturn(memberships);
    processesMemberShipListener.postDelete(memberShip);
    verify(membershipHandler, times(1)).removeMembership(memberShip.getId(), true);
  }
}
