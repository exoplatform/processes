package org.exoplatform.processes.listener;

import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.analytics.utils.AnalyticsUtils;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.container.xml.InitParams;

public class ProcessAnalyticsListener extends Listener<Long, WorkFlow> {

  private final String operation;

  public ProcessAnalyticsListener(InitParams initParams) {
    this.operation = initParams.getValueParam("operation").getValue();
  }

  @Override
  public void onEvent(Event<Long, WorkFlow> event) throws Exception {
    StatisticData statisticData = new StatisticData();
    long userId = event.getSource();
    WorkFlow workFlow = event.getData();
    statisticData.setModule("processes");
    statisticData.setSubModule("process");
    statisticData.setOperation(operation);
    statisticData.setUserId(userId);
    statisticData.addParameter("processID", workFlow.getId());
    statisticData.addParameter("processName", workFlow.getTitle());
    AnalyticsUtils.addStatisticData(statisticData);
  }
}
