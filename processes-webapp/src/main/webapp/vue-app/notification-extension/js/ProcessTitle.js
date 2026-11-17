/*
 * Copyright (C) 2026 eXo Platform SAS.
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

/** The title of each process read, by its id, shared by every notification of the page */
const titles = {};

/**
 * Returns the title of a notification's process in the reader's language: the
 * process the notification names by its id, read once per page, or the title
 * stored in the notification when it names none or the process can't be read.
 *
 * @param {Object} notification the notification
 * @return {Promise<String>} the process title
 */
export function getProcessTitle(notification) {
  const storedTitle = notification?.parameters?.REQUEST_PROCESS;
  const workflowId = notification?.parameters?.WORKFLOW_ID;
  if (!workflowId) {
    return Promise.resolve(storedTitle);
  }
  if (!titles[workflowId]) {
    titles[workflowId] = fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workflows/${workflowId}`, {
      method: 'GET',
      credentials: 'include',
    }).then(resp => resp?.ok && resp.json() || null)
      .then(workflow => workflow?.title || null)
      .catch(() => null);
  }
  return titles[workflowId].then(title => title || storedTitle);
}
