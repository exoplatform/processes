/*
 * Copyright (C) 2022 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.processes.listener;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.utils.AnalyticsUtils;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.processes.model.Work;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.service.ProcessesService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.task.dto.ProjectDto;

public class RequestAnalyticsListener extends Listener<Work, ProjectDto> {

  private ProcessesService processesService;
  private final String operation;

  /**
   * @param initParams its parameters: the analytics operation it records
   * @param processesService reads the request's process
   */
  public RequestAnalyticsListener(InitParams initParams, ProcessesService processesService) {
    this.operation = initParams.getValueParam("operation").getValue();
    this.processesService = processesService;
  }

  /**
   * Records a request's event for analytics, with the process it was created
   * for.
   *
   * @param event the event: the request, and its task project
   * @throws Exception when the process cannot be read
   */
  @Override
  public void onEvent(Event<Work, ProjectDto> event) throws Exception {
    StatisticData statisticData = new StatisticData();
    Work work = event.getSource();
    ProjectDto project = event.getData();
    WorkFlow workFlow = workFlowOf(work, project);
    long userId = work.getCreatorId();
    statisticData.setModule("processes");
    statisticData.setSubModule("request");
    statisticData.setOperation(operation);
    statisticData.setUserId(userId);
    statisticData.addKeyword("processID", workFlow.getId());
    statisticData.addKeyword("processName", workFlow.getTitle());
    statisticData.addKeyword("requestID", work.getId());
    statisticData.addKeyword("requestName", work.getTitle());
    AnalyticsUtils.addStatisticData(statisticData);
  }

  /**
   * Reads the process a request was created for: the one the request names,
   * whichever processes share its project, else the first process of its
   * project when the request names none or the one it names is not found.
   * The read is the platform's own.
   *
   * @param work the request
   * @param project its task project
   * @return the process
   * @throws IllegalAccessException never, the platform's read applies no right
   */
  @SuppressWarnings("removal")
  private WorkFlow workFlowOf(Work work, ProjectDto project) throws IllegalAccessException {
    WorkFlow workFlow = work.getWorkFlowId() > 0 ? processesService.getWorkFlow(work.getWorkFlowId(), null) : null;
    return workFlow != null ? workFlow : processesService.getWorkFlowByProjectId(project.getId());
  }
}
