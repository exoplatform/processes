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
