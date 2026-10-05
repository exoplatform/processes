package org.exoplatform.processes.Utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
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
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.GroupHandler;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dto.TaskDto;

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

  /**
   * A workflow permission entry grants exactly its group, membership or user,
   * never a group or a user whose name merely contains it. Mutation: go back
   * to a String contains match and the sales_team, johnny and processes-x
   * assertions fail.
   */
  @Test
  public void getACLMatchesMembershipsExactly() {
    WorkFlowEntity workFlowEntity = new WorkFlowEntity();
    workFlowEntity.setManager(new HashSet<>(java.util.Arrays.asList("/spaces/sales", "john", "*:/spaces/legal")));
    workFlowEntity.setParticipator(new HashSet<>(java.util.Arrays.asList("manager:/spaces/sales", "member:/spaces/sales")));

    assertTrue(EntityMapper.getACL(workFlowEntity, java.util.List.of("member:/spaces/sales")).isCanAddRequest());
    assertTrue(EntityMapper.getACL(workFlowEntity, java.util.List.of("john")).isCanAddRequest());
    assertTrue(EntityMapper.getACL(workFlowEntity, java.util.List.of("manager:/spaces/legal")).isCanAddRequest());
    assertFalse(EntityMapper.getACL(workFlowEntity, java.util.List.of("member:/spaces/sales_team")).isCanAddRequest());
    assertFalse(EntityMapper.getACL(workFlowEntity, java.util.List.of("johnny")).isCanAddRequest());

    assertTrue(EntityMapper.getACL(workFlowEntity, java.util.List.of("member:/spaces/sales")).isCanEdit());
    assertTrue(EntityMapper.getACL(workFlowEntity, java.util.List.of("*:/spaces/sales")).isCanEdit());
    assertFalse(EntityMapper.getACL(workFlowEntity, java.util.List.of("member:/spaces/sales_team")).isCanEdit());

    assertTrue(EntityMapper.getACL(workFlowEntity, java.util.List.of("member:/platform/processes")).isCanDelete());
    assertFalse(EntityMapper.getACL(workFlowEntity, java.util.List.of("member:/platform/processes-x")).isCanDelete());
  }

  /**
   * A personal task has no status: mapping it must not fail, so that a
   * processes write on it is refused (403) rather than crashing (500).
   * Mutation: dereference the status unconditionally and this test fails.
   */
  @Test
  public void taskToWorkWithoutStatus() {
    TaskDto task = new TaskDto();
    task.setId(3L);
    task.setCreatedBy("user");

    Work work = EntityMapper.taskToWork(task);

    assertEquals(3L, work.getId());
    assertNull(work.getStatus());
    assertEquals(0L, work.getProjectId());
  }
}
