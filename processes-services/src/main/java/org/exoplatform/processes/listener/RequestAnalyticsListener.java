package org.exoplatform.processes.listener;

import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.analytics.utils.AnalyticsUtils;
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
