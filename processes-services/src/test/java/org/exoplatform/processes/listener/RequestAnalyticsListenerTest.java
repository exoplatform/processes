/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.processes.listener;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.mockito.MockedStatic;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.utils.AnalyticsUtils;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.task.dto.ProjectDto;

/**
 * A request's analytics name the process it was created for, whichever
 * processes share its task project.
 */
public class RequestAnalyticsListenerTest {

  /**
   * Request 22014 was created on process 2 in project 159, whose first
   * process is 1: its analytics name process 2. A request that names no
   * process falls back to its project's first process. Mutation: read the
   * process from the project only and this test fails.
   *
   * @throws Exception never, the mocks throw nothing
   */
  @Test
  @SuppressWarnings("removal")
  public void aRequestsAnalyticsNameTheProcessItWasCreatedFor() throws Exception {
    ProcessesService processesService = mock(ProcessesService.class);
    when(processesService.getWorkFlow(2L, null)).thenReturn(process(2L, "editor B"));
    when(processesService.getWorkFlowByProjectId(159L)).thenReturn(process(1L, "editor A2"));
    InitParams params = new InitParams();
    ValueParam operation = new ValueParam();
    operation.setName("operation");
    operation.setValue("requestCreated");
    params.addParameter(operation);
    RequestAnalyticsListener listener = new RequestAnalyticsListener(params, processesService);
    ProjectDto project = new ProjectDto();
    project.setId(159L);
    List<StatisticData> recorded = new ArrayList<>();

    try (MockedStatic<AnalyticsUtils> analytics = mockStatic(AnalyticsUtils.class)) {
      analytics.when(() -> AnalyticsUtils.addStatisticData(any())).thenAnswer(invocation -> recorded.add(invocation.getArgument(0)));

      listener.onEvent(new Event<>("exo.process.request.created", request(22014L, 2L), project));
      listener.onEvent(new Event<>("exo.process.request.created", request(500L, 0L), project));
    }

    assertEquals("2", String.valueOf(recorded.get(0).getParameters().get("processID")));
    assertEquals("editor B", recorded.get(0).getParameters().get("processName"));
    assertEquals("1", String.valueOf(recorded.get(1).getParameters().get("processID")));
  }

  /**
   * @param id the process id
   * @param title its title
   * @return the process
   */
  private static WorkFlow process(long id, String title) {
    WorkFlow workFlow = new WorkFlow();
    workFlow.setId(id);
    workFlow.setTitle(title);
    return workFlow;
  }

  /**
   * @param id the request id
   * @param workFlowId the process it names, 0 for none
   * @return the request
   */
  private static Work request(long id, long workFlowId) {
    Work work = new Work();
    work.setId(id);
    work.setWorkFlowId(workFlowId);
    work.setCreatorId(1L);
    work.setTitle("request " + id);
    return work;
  }
}
