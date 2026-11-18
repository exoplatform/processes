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
package org.exoplatform.processes.plugin;

import java.util.List;
import java.util.Map;

import org.exoplatform.services.security.Identity;

/**
 * The values a request carries beyond its title and description, and the form
 * a process asks its requesters to fill. The Process add-on knows nothing of
 * the add-on that implements it: it hands over the values a requester sent,
 * and the form an editor chose, at the steps of a request's creation and of a
 * process's save where they must be checked or written. At most one plugin is
 * registered, through
 * {@link org.exoplatform.processes.service.ProcessesService#addRequestValuesPlugin(RequestValuesPlugin)}.
 * <p>
 * Every method throws {@link IllegalArgumentException}, whose message is the
 * code of the refusal, for a refused value, and {@link IllegalAccessException}
 * for a refused right. A plugin translates its own add-on's exceptions into
 * those two before they leave it.
 * <p>
 * The values are given as form id → field key → values: an absent form or
 * field is not sent, an empty list clears the field.
 */
public interface RequestValuesPlugin {

  /**
   * Checks the values a requester sends with a new request of a process,
   * before its task exists: the forms the process asks for, the required
   * fields, the shape of every value.
   *
   * When the request is made from a draft, the draft's stored values are
   * checked with the sent ones, which override them field by field; the
   * draft must be of the request's process.
   *
   * @param workFlowId the id of the process the request is made for
   * @param draftId the id of the requester's draft the request is made from,
   *          or null
   * @param values the sent values, form id → field key → values, empty when
   *          none was sent
   * @param requester the requester's identity
   * @throws IllegalAccessException when the requester may not write the values
   */
  void checkRequestValues(long workFlowId,
                          Long draftId,
                          Map<Long, Map<String, List<Object>>> values,
                          Identity requester) throws IllegalAccessException;

  /**
   * Writes the values of a new request on its task, once the task and its
   * request record exist: the defaults of every field not sent, then the sent
   * values. It checks them again, so a refusal here, after a change of the
   * process's form, makes the caller remove the request.
   *
   * When the request is made from a draft, the draft's stored values are
   * written with the sent ones, which override them field by field.
   *
   * @param workFlowId the id of the process the request was made for
   * @param draftId the id of the requester's draft the request is made from,
   *          or null
   * @param taskId the id of the request's task
   * @param values the sent values, form id → field key → values, empty when
   *          none was sent
   * @param requester the requester's identity, the task's creator
   * @throws IllegalAccessException when the requester may not write the values
   */
  void writeRequestValues(long workFlowId,
                          Long draftId,
                          long taskId,
                          Map<Long, Map<String, List<Object>>> values,
                          Identity requester) throws IllegalAccessException;

  /**
   * Writes the values its owner sent with a request draft's save, with the
   * value rules of the forms the draft's process asks for and no
   * required-field check: required fields block a request's submit only.
   *
   * @param workFlowId the id of the stored draft's process, never the one the
   *          client sent
   * @param draftId the draft id
   * @param values the sent values, form id → field key → values
   * @param owner the draft owner's identity
   * @throws IllegalAccessException when the owner may not write the values
   */
  void writeDraftValues(long workFlowId, long draftId, Map<Long, Map<String, List<Object>>> values, Identity owner) throws IllegalAccessException;

  /**
   * Reads the forms bound to a process itself, the ones its editor shows. It
   * is the platform's own read: the caller checks the user's right on the
   * process first.
   *
   * @param workFlowId the process id
   * @return the ids of the forms bound to the process, empty when it has none
   */
  List<Long> getProcessFormIds(long workFlowId);

  /**
   * Checks the form an editor chose for a process, before any write of the
   * process's save: the refusals that need no process row.
   *
   * @param workFlowId the process id, or null for a new process
   * @param spaceId the id of the space that manages the process, or null when
   *          it is not known
   * @param formId the chosen form id, or null for no form
   * @param editor the editor's identity
   * @throws IllegalAccessException when the editor may not choose the form of
   *           the process
   */
  void checkProcessForm(Long workFlowId, String spaceId, Long formId, Identity editor) throws IllegalAccessException;

  /**
   * Makes a form the process's only own form, or removes every own form of the
   * process, inside the process's save, once its row is written. It checks the
   * choice again.
   *
   * @param workFlowId the process id
   * @param formId the chosen form id, or null for no form
   * @param editor the editor's identity
   * @throws IllegalAccessException when the editor may not choose the form of
   *           the process
   */
  void setProcessForm(long workFlowId, Long formId, Identity editor) throws IllegalAccessException;

  /**
   * Checks the forms of an existing task project an editor newly chose for a
   * process's requesters to fill, before any write of the process's save:
   * each one must be a form the project gives its tasks, which the editor may
   * see. The forms the process already stores are not sent again. A plugin
   * that does not know the choice refuses any form, so that no choice is
   * stored unchecked.
   *
   * @param workFlowId the process id, or null for a new process
   * @param projectId the id of the task project the process stores its
   *          requests on
   * @param formIds the newly chosen form ids, distinct, never empty
   * @param editor the editor's identity
   * @throws IllegalAccessException when the editor may not choose the forms
   *           of that project
   * @throws IllegalArgumentException when a form is not one the project gives
   */
  default void checkRequesterForms(Long workFlowId, long projectId, List<Long> formIds, Identity editor) throws IllegalAccessException {
    throw new IllegalArgumentException("processes.workflow.formUnsupported");
  }
}
