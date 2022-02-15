package org.exoplatform.processes.listener;

import org.exoplatform.services.organization.*;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import java.util.Collection;
import java.util.Iterator;

public class ProcessesMemberShipListener extends MembershipEventListener {

  private static final String PROCESSES_GROUP_ID       = "/platform/processes";

  private static final String PROCESSES_SPACE_GROUP_ID = "/spaces/processes_space";

  private static final String MEMBER                   = "member";

  private static final String MANAGER                  = "manager";

  private SpaceService        spaceService;

  private OrganizationService organizationService;

  public ProcessesMemberShipListener(SpaceService spaceService, OrganizationService organizationService) {
    this.spaceService = spaceService;
    this.organizationService = organizationService;
  }

  @Override
  public void postSave(Membership m, boolean isNew) throws Exception {
    Space space = spaceService.getSpaceByGroupId(PROCESSES_SPACE_GROUP_ID);
    if (space == null) {
      return;
    }
    if (m.getGroupId().equals(PROCESSES_GROUP_ID) && !spaceService.isManager(space, m.getUserName())) {
      spaceService.setManager(space, m.getUserName(), true);
    }
    if (m.getGroupId().equals(PROCESSES_SPACE_GROUP_ID) && m.getMembershipType().equals(MANAGER)) {
      Group group = organizationService.getGroupHandler().findGroupById(PROCESSES_GROUP_ID);
      User user = organizationService.getUserHandler().findUserByName(m.getUserName());
      MembershipType membershipType = organizationService.getMembershipTypeHandler().findMembershipType(MEMBER);
      organizationService.getMembershipHandler().linkMembership(user, group, membershipType, true);
    }
  }

  @Override
  public void postDelete(Membership m) throws Exception {
    Space space = spaceService.getSpaceByGroupId(PROCESSES_SPACE_GROUP_ID);
    if (space == null) {
      return;
    }
    if (m.getGroupId().equals(PROCESSES_GROUP_ID) && spaceService.isManager(space, m.getUserName())) {
      spaceService.setManager(space, m.getUserName(), false);
    }
    if (m.getGroupId().equals(PROCESSES_SPACE_GROUP_ID) && m.getMembershipType().equals(MANAGER)) {
      Group group = organizationService.getGroupHandler().findGroupById(PROCESSES_GROUP_ID);
      Collection<Membership> memberships = organizationService.getMembershipHandler()
                                                              .findMembershipsByUserAndGroup(m.getUserName(), group.getId());
      Iterator<Membership> iterator = memberships.iterator();
      while (iterator.hasNext()) {
        Membership membership = iterator.next();
        organizationService.getMembershipHandler().removeMembership(membership.getId(), true);
      }
    }
  }
}
