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

  public RequestAnalyticsListener(InitParams initParams, ProcessesService processesService) {
    this.operation = initParams.getValueParam("operation").getValue();
    this.processesService = processesService;
  }

  @Override
  public void onEvent(Event<Work, ProjectDto> event) throws Exception {
    StatisticData statisticData = new StatisticData();
    Work work = event.getSource();
    ProjectDto project = event.getData();
    WorkFlow workFlow = processesService.getWorkFlowByProjectId(project.getId());
    long userId = work.getCreatorId();
    statisticData.setModule("processes");
    statisticData.setSubModule("request");
    statisticData.setOperation(operation);
    statisticData.setUserId(userId);
    statisticData.addParameter("processID", workFlow.getId());
    statisticData.addParameter("processName", workFlow.getTitle());
    statisticData.addParameter("requestID", work.getId());
    statisticData.addParameter("requestName", work.getTitle());
    AnalyticsUtils.addStatisticData(statisticData);
  }
}
