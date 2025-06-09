/*
 * Copyright (C) 2025 eXo Platform SAS
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
package org.exoplatform.processes.service;

import java.util.List;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.processes.model.IllustrativeAttachment;
import org.exoplatform.processes.model.ProcessesFilter;
import org.exoplatform.processes.model.WorkFlow;
import org.exoplatform.processes.model.WorkStatus;

public interface ProcessService {

  /**
   * Retrieves a list of accessible Processes, for a selected user, by applying
   * the designated filter. The returned results will be of type {@link WorkFlow}
   * only. The ownerId of filter object will be used to select the list of
   * accessible processes to retrieve.
   * 
   * @param filter {@link ProcessesFilter} that contains filtering criteria
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @param userName name of the user accessing files
   * @return {@link List} of {@link WorkFlow}
   */
  List<WorkFlow> getWorkFlows(ProcessesFilter filter, int offset, int limit, String userName);

  /**
   * Return the number of proceses after applying a given filter
   *
   * @param filter: process filter to apply
   * @return Filtered processes count
   */
  int countWorkFlows(ProcessesFilter filter, String userName);

  /**
   * get a process by its given id
   *
   * @param id process id
   * @param userName user name
   * @return {@link WorkFlow} object
   * @throws IllegalAccessException if the user does not have access to the
   *           process
   * @throws ObjectNotFoundException if the process with the given id does not
   *           exist
   */
  WorkFlow getWorkFlow(long id, String userName) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * get a process by its given id
   *
   * @param id process id
   * @return {@link WorkFlow} object
   * @throws ObjectNotFoundException if the process with the given id does not
   *           exist
   */
  WorkFlow getWorkFlow(long id) throws ObjectNotFoundException;

  /**
   * Create a process
   *
   * @param workFlow process object
   * @param userName user name
   * @return {@link WorkFlow} object
   * @throws IllegalAccessException if the user does not have the rights to create
   *           a process
   */
  WorkFlow createWorkFlow(WorkFlow workFlow, String userName) throws IllegalAccessException;

  /**
   * Update a process
   *
   * @param workFlow process object
   * @param userName user name
   * @return {@link WorkFlow} object
   * @throws IllegalAccessException if the user does not have the rights to update
   *           the process
   * @throws IllegalArgumentException if the process is null or its id is 0
   * @throws ObjectNotFoundException if the process does not exist
   */
  WorkFlow updateWorkFlow(WorkFlow workFlow,
                          String userName) throws IllegalArgumentException, ObjectNotFoundException, IllegalAccessException;

  /**
   * get process by its project id
   *
   * @param projectId process(s project id
   * @param userName user name
   * @return {@link WorkFlow} object
   * @throws IllegalAccessException if the user does not have access to the
   *           process
   * @throws ObjectNotFoundException if the project with the given id does not
   *           exist
   */
  WorkFlow getWorkFlowByProjectId(long projectId, String userName) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * get process by its project id
   *
   * @param projectId process(s project id
   * @return {@link WorkFlow} object
   * @throws ObjectNotFoundException if the project with the given id does not
   *           exist
   */
  WorkFlow getWorkFlowByProjectId(long projectId) throws ObjectNotFoundException;

  /**
   * Delete a workflow by its given id.
   *
   * @param workflowId : workflow id
   * @param userName user name
   * @throws ObjectNotFoundException if the project with the given id does not
   *           exist
   * @throws IllegalAccessException if the user does not have access to the
   *           process
   */
  void deleteWorkflowById(Long workflowId, String userName) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * @param projectId: Tasks project id
   * @param isCompleted: filter by completed and uncompleted tasks
   * @param userName user name
   * @return Filtered tasks count
   * @throws ObjectNotFoundException if the project with the given id does not
   *           exist
   * @throws IllegalAccessException if the user does not have access to the
   *           process
   */
  int countWorksByWorkflow(Long projectId, String userName, Boolean isCompleted) throws ObjectNotFoundException,
                                                                                 IllegalAccessException;

  /**
   * Retrieves the list of available statuses in all workflows
   *
   * @return {@link List} of {@link WorkStatus}
   */
  List<WorkStatus> getAvailableWorkStatuses();

  /**
   * Retrieves an illustration image by its given id
   *
   * @param illustrationId illustration file id
   * @param userName user name
   * @return {@link IllustrativeAttachment}
   */
  IllustrativeAttachment getIllustrationImageById(Long illustrationId, String userName);

  boolean canAccessProcess(WorkFlow workFlow, org.exoplatform.services.security.Identity identity);

  boolean canAddProcess(org.exoplatform.services.security.Identity identity);

  boolean canEditProcess(WorkFlow workFlow, org.exoplatform.services.security.Identity identity);

  boolean canDeleteProcess(WorkFlow workFlow, org.exoplatform.services.security.Identity identity);
}
