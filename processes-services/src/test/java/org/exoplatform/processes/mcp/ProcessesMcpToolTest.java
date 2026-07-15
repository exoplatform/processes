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
package org.exoplatform.processes.mcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.task.service.TaskService;

/**
 * Guards the Processes MCP tools against the live NPE where a ProcessesFilter is
 * passed to {@code getWorkFlows} with a null {@code isProcessManager}, which the
 * service / WorkFlowDAO unboxes into a primitive {@code boolean}. These tests
 * exercise the real {@code ProcessesMcpTool} (only its collaborators are mocked)
 * and assert the filter it builds is fully populated, so the null-unbox trap
 * cannot regress.
 */
public class ProcessesMcpToolTest {

  private ProcessesService processesService;

  private TaskService      taskService;

  private IdentityManager  identityManager;

  private ProcessesMcpTool tool;

  @Before
  public void setUp() {
    processesService = mock(ProcessesService.class);
    taskService = mock(TaskService.class);
    identityManager = mock(IdentityManager.class);
    tool = new ProcessesMcpTool(processesService, taskService, identityManager);

    Identity socialIdentity = mock(Identity.class);
    when(socialIdentity.getId()).thenReturn("1");
    when(identityManager.getOrCreateUserIdentity("testuser")).thenReturn(socialIdentity);

    ConversationState.setCurrent(new ConversationState(new org.exoplatform.services.security.Identity("testuser")));
  }

  @After
  public void tearDown() {
    ConversationState.setCurrent(null);
  }

  @Test
  public void listProcessesSetsIsProcessManagerToAvoidNpe() throws Exception {
    when(processesService.getWorkFlows(any(ProcessesFilter.class),
                                       anyInt(),
                                       anyInt(),
                                       anyLong())).thenReturn(Collections.emptyList());

    tool.listProcesses();

    ArgumentCaptor<ProcessesFilter> captor = ArgumentCaptor.forClass(ProcessesFilter.class);
    verify(processesService).getWorkFlows(captor.capture(), anyInt(), anyInt(), anyLong());
    ProcessesFilter filter = captor.getValue();
    assertNotNull("isProcessManager must be non-null (the service unboxes it into a boolean)",
                  filter.getIsProcessManager());
    assertFalse(filter.getIsProcessManager());
    assertEquals(Boolean.TRUE, filter.getEnabled());
  }

  @Test
  public void getPendingApprovalsSetsIsProcessManagerToAvoidNpe() throws Exception {
    when(processesService.getWorkFlows(any(ProcessesFilter.class),
                                       anyInt(),
                                       anyInt(),
                                       anyLong())).thenReturn(Collections.emptyList());

    tool.getPendingApprovals();

    ArgumentCaptor<ProcessesFilter> captor = ArgumentCaptor.forClass(ProcessesFilter.class);
    verify(processesService).getWorkFlows(captor.capture(), anyInt(), anyInt(), anyLong());
    assertNotNull("isProcessManager must be non-null (the service unboxes it into a boolean)",
                  captor.getValue().getIsProcessManager());
  }

}
