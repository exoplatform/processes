package org.exoplatform.processes.Utils;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.GroupHandler;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import java.util.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
public class EntityMapperTest {
    @Mock
    private OrganizationService        organizationService;
    @Mock
    private SpaceService               spaceService;
    @Mock
    private Group group;
    @Mock
    private GroupHandler groupHandler;

    @Test
    @PrepareForTest({ CommonsUtils.class })
    public void fromEntity() throws Exception {
        PowerMockito.mockStatic(CommonsUtils.class);
        when(CommonsUtils.getService(OrganizationService.class)).thenReturn(organizationService);
        when(CommonsUtils.getService(SpaceService.class)).thenReturn(spaceService);
        when(spaceService.getSpaceByGroupId("manager")).thenReturn(null);
        when(spaceService.getSpaceByGroupId("/platform/manager")).thenReturn(null);
        when(organizationService.getGroupHandler()).thenReturn(groupHandler);
        when(groupHandler.findGroupById("manager")).thenReturn(group);
        when(groupHandler.findGroupById("/platform/manager")).thenReturn(group);
        when(groupHandler.findGroupById("exception")).thenReturn(null);
        when(group.getGroupName()).thenReturn("manager");
        when(group.getId()).thenReturn("manager");
        when(group.getLabel()).thenReturn("managers");
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
        workFlowEntity.hashCode();
        workFlowEntity.equals(workFlowEntity);
        workFlowEntity.toString();
        List<String> memberships = new ArrayList<>();
        memberships.add("manager");
        memberships.add("/platform/manager");
        Set<String> managers = new HashSet<>(Arrays.asList(memberships.get(0),memberships.get(1)));
        workFlowEntity.setManager(managers);
        WorkFlow newWorkFlow = EntityMapper.fromEntity(workFlowEntity,memberships);
        assertEquals(newWorkFlow.getManager().size(),managers.size());
    }
}
