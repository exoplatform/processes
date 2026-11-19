/*
 * Copyright (C) 2026 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.processes.upgrade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.processes.dao.RequestDAO;
import org.exoplatform.processes.dao.WorkFlowDAO;
import org.exoplatform.processes.entity.RequestEntity;
import org.exoplatform.processes.entity.WorkFlowEntity;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.task.dao.TaskQuery;
import org.exoplatform.task.dto.TaskDto;
import org.exoplatform.task.service.ProjectService;
import org.exoplatform.task.service.TaskService;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ProcessesRequestUpgradePluginTest {

  @Mock
  private WorkFlowDAO                   workFlowDAO;

  @Mock
  private RequestDAO                    requestDAO;

  @Mock
  private TaskService                   taskService;

  @Mock
  private ProjectService                projectService;

  @Mock
  private IdentityManager               identityManager;

  @Mock
  private SpaceService                  spaceService;

  private ProcessesRequestUpgradePlugin plugin;

  @Before
  public void setUp() {
    InitParams params = new InitParams();
    ValueParam groupId = new ValueParam();
    groupId.setName("product.group.id");
    groupId.setValue("org.exoplatform.processes");
    params.addParameter(groupId);
    plugin = new ProcessesRequestUpgradePlugin(params,
                                               workFlowDAO,
                                               requestDAO,
                                               taskService,
                                               projectService,
                                               identityManager,
                                               spaceService);
    when(identityManager.getOrCreateUserIdentity("mary")).thenReturn(identity("31"));
  }

  /**
   * The tasks of a project fed by one process are recorded as its requests,
   * with their creator's identity, every page read; a task already recorded is
   * left as it is, and a task whose creator has no identity is skipped. The
   * tasks of a project two processes feed are not recorded. Mutation: drop the
   * shared-project check, the already-recorded check or the next page, and
   * this test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  public void theTasksOfAProjectFedByOneProcessAreRecordedAsItsRequests() throws Exception {
    when(workFlowDAO.findAll()).thenReturn(List.of(process(1L, 10L, 5L), process(2L, 20L, 5L), process(3L, 20L, 5L)));
    // Project 10: a full page, then a page of three tasks
    List<TaskDto> firstPage = LongStream.rangeClosed(1, ProcessesRequestUpgradePlugin.PAGE_SIZE)
                                        .mapToObj(id -> task(id, "mary"))
                                        .collect(Collectors.toCollection(ArrayList::new));
    firstPage.set(0, task(1L, "ghost"));
    List<TaskDto> secondPage = List.of(task(501L, "mary"), task(502L, "mary"), task(503L, "mary"));
    when(taskService.findTasks(any(TaskQuery.class), eq(0), eq(ProcessesRequestUpgradePlugin.PAGE_SIZE))).thenReturn(firstPage,
                                                                                                                    secondPage);
    when(requestDAO.findWorkFlowIdsByTaskIds(any())).thenAnswer(invocation -> {
      List<Long> ids = invocation.getArgument(0);
      return ids.contains(502L) ? Map.of(502L, 1L) : Map.of();
    });

    plugin.processUpgrade("7.2.0", "7.3.0");

    ArgumentCaptor<RequestEntity> created = ArgumentCaptor.forClass(RequestEntity.class);
    verify(requestDAO, times(ProcessesRequestUpgradePlugin.PAGE_SIZE - 1 + 2)).create(created.capture());
    Set<Long> recordedTasks = created.getAllValues().stream().map(RequestEntity::getTaskId).collect(Collectors.toSet());
    assertEquals("the second page is read", true, recordedTasks.contains(503L));
    assertEquals("an existing record is not rewritten", false, recordedTasks.contains(502L));
    assertEquals("a creator with no identity is skipped", false, recordedTasks.contains(1L));
    created.getAllValues().forEach(request -> {
      assertEquals(Long.valueOf(1L), request.getWorkFlowId());
      assertEquals(Long.valueOf(31L), request.getCreatorId());
    });
    verify(taskService, times(2)).findTasks(any(TaskQuery.class), anyInt(), anyInt());
  }

  /**
   * A failure to record a request is thrown, so the upgrade is not recorded as
   * done. Mutation: catch it per task and this test fails.
   *
   * @throws Exception never, the mocks throw only where stubbed
   */
  @Test
  public void aFailureToRecordARequestFailsTheUpgrade() throws Exception {
    when(workFlowDAO.findAll()).thenReturn(List.of(process(1L, 10L, 5L)));
    when(taskService.findTasks(any(TaskQuery.class), anyInt(), anyInt())).thenReturn(List.of(task(7L, "mary")));
    when(requestDAO.findWorkFlowIdsByTaskIds(any())).thenReturn(Map.of());
    when(requestDAO.create(any(RequestEntity.class))).thenThrow(new IllegalStateException("database down"));

    assertThrows(IllegalStateException.class, () -> plugin.processUpgrade("7.2.0", "7.3.0"));
  }

  /**
   * A process with no space takes the space its participants name, else the
   * one of its project; one that has a space keeps it; one with neither keeps
   * none. Mutation: overwrite a stored space, or drop the participants' space,
   * and this test fails.
   */
  @Test
  public void aProcessWithNoSpaceTakesTheSpaceItsParticipantsName() {
    Space hr = new Space();
    hr.setId("8");
    when(spaceService.getSpaceByGroupId("/spaces/hr")).thenReturn(hr);
    WorkFlowEntity legacy = process(1L, 0L, null);
    legacy.setParticipator(new HashSet<>(List.of("manager:/spaces/hr", "member:/spaces/hr")));
    WorkFlowEntity withSpace = process(2L, 0L, 5L);
    withSpace.setParticipator(new HashSet<>(List.of("manager:/spaces/hr")));
    WorkFlowEntity orphan = process(3L, 0L, null);
    orphan.setParticipator(new HashSet<>(List.of("manager:/spaces/gone")));
    when(workFlowDAO.findAll()).thenReturn(List.of(legacy, withSpace, orphan));

    plugin.processUpgrade("7.2.0", "7.3.0");

    assertEquals(Long.valueOf(8L), legacy.getSpaceId());
    assertEquals(Long.valueOf(5L), withSpace.getSpaceId());
    assertNull(orphan.getSpaceId());
    verify(workFlowDAO).update(legacy);
    verify(workFlowDAO, never()).update(withSpace);
    verify(workFlowDAO, never()).update(orphan);
    verify(requestDAO, never()).create(any(RequestEntity.class));
    verify(identityManager, never()).getIdentity(anyLong());
  }

  private static WorkFlowEntity process(long id, long projectId, Long spaceId) {
    WorkFlowEntity workFlow = new WorkFlowEntity();
    workFlow.setId(id);
    workFlow.setProjectId(projectId);
    workFlow.setSpaceId(spaceId);
    return workFlow;
  }

  private static TaskDto task(long id, String createdBy) {
    TaskDto task = new TaskDto();
    task.setId(id);
    task.setCreatedBy(createdBy);
    return task;
  }

  private static Identity identity(String id) {
    Identity identity = new Identity("organization", "mary");
    identity.setId(id);
    return identity;
  }
}
