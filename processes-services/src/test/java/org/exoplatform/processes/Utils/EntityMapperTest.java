package org.exoplatform.processes.Utils;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.GroupHandler;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import java.util.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
public class EntityMapperTest {
  @Mock
  private OrganizationService organizationService;

  @Mock
  private SpaceService        spaceService;
  
  @Mock
  private Space               testSpace;

  @Mock
  private Group               contributorsGroup;
  
  @Mock
  private Group               administratorsGroup;

  @Mock
  private GroupHandler        groupHandler;

  @Test
  @PrepareForTest({ CommonsUtils.class })
  public void fromEntity() throws Exception {
    PowerMockito.mockStatic(CommonsUtils.class);
    when(CommonsUtils.getService(OrganizationService.class)).thenReturn(organizationService);
    when(CommonsUtils.getService(SpaceService.class)).thenReturn(spaceService);
    when(spaceService.getSpaceByGroupId("web-contributors")).thenReturn(null);
    when(spaceService.getSpaceByGroupId("/platform/administrators")).thenReturn(null);
    Space testSpace = Mockito.mock(Space.class);
    when(spaceService.getSpaceByGroupId("/spaces/testSpace")).thenReturn(testSpace);
    when(organizationService.getGroupHandler()).thenReturn(groupHandler);
    when(groupHandler.findGroupById("web-contributors")).thenReturn(null);
    Group contributorsGroup = Mockito.mock(Group.class);
    when(groupHandler.findGroupById("/platform/web-contributors")).thenReturn(contributorsGroup);
    Group administratorsGroup = Mockito.mock(Group.class);
    when(groupHandler.findGroupById("/platform/administrators")).thenReturn(administratorsGroup);
    WorkFlowEntity workFlowEntity = new WorkFlowEntity();
    workFlowEntity.setId(1L);
    workFlowEntity.setTitle("workFlow");
    workFlowEntity.setCreatorId(1L);
    workFlowEntity.setSummary("workFlow summary");
    workFlowEntity.setModifierId(1L);
    workFlowEntity.setTitle("workFlow");
    workFlowEntity.setEnabled(true);
    workFlowEntity.setDescription("test");
    workFlowEntity.setProjectId(1L);
    Set<String> managers = new HashSet<String>();
    managers.add("web-contributors");
    managers.add("/platform/administrators");
    managers.add("/spaces/testSpace");
    workFlowEntity.setManager(managers);
    WorkFlow workFlow = EntityMapper.fromEntity(workFlowEntity, null);
    assertEquals(workFlow.getManager().size(), managers.size());
  }
}
