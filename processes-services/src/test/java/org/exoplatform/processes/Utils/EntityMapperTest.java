package org.exoplatform.processes.Utils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.GroupHandler;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

@RunWith(MockitoJUnitRunner.Silent.class)
public class EntityMapperTest {

  private static final MockedStatic<CommonsUtils>                                             COMMONS_UTILS             =
                                                                                                            mockStatic(CommonsUtils.class);

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

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    COMMONS_UTILS.close();
  }

  @Test
  public void fromEntity() throws Exception {
    COMMONS_UTILS.when(() -> CommonsUtils.getService(OrganizationService.class)).thenReturn(organizationService);
    COMMONS_UTILS.when(() -> CommonsUtils.getService(SpaceService.class)).thenReturn(spaceService);
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
