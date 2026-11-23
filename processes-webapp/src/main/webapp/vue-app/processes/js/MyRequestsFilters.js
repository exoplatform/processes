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

/**
 * Tells whether a request changes no more unless reopened: decided or canceled, its task completed,
 * whatever column it sits in. « Archive all » archives the open requests that are.
 *
 * @param {Object} work the request
 * @returns {Boolean} true when finished
 */
export function isFinished(work) {
  return !!work && !!work.completed;
}

/**
 * How « My requests » groups its requests: not at all, by status or by process.
 */
export const GROUP_BY_NONE = 'none';
export const GROUP_BY_STATUS = 'status';
export const GROUP_BY_PROCESS = 'process';

/**
 * Where this browser remembers the grouping chosen in « My requests ».
 */
const GROUP_BY_STORAGE_KEY = 'processes.myRequests.groupBy';

/**
 * How many completed requests « My requests » loads at a time.
 */
export const COMPLETED_PAGE_SIZE = 50;

/**
 * How many requests a capped group shows before « Show N more ».
 */
export const CAPPED_GROUP_SIZE = 5;

/**
 * The viewport widths from which « My requests » shows more columns: the comments and the status
 * from 600px, the process from 960px, the platform's sm and md thresholds. They are compared with
 * `$vuetify.breakpoint.width`, never read from the breakpoint names, which the platform's Vuetify
 * preset shifts (its `xs` threshold is 0, so `xsOnly` is never true).
 */
export const WIDE_WIDTH = 600;
export const LARGE_WIDTH = 960;

/**
 * Reads the id of a request's process.
 *
 * @param {Object} work the request or the draft
 * @returns {String} the process id, or null when the request carries no process
 */
export function workflowIdOf(work) {
  return work && work.workFlow && work.workFlow.id != null ? String(work.workFlow.id) : null;
}

/**
 * Reads when a request or a draft was created.
 *
 * @param {Object} work the request or the draft
 * @returns {Number} the time in milliseconds, 0 when unknown
 */
export function createdTimeOf(work) {
  return work && work.createdTime && work.createdTime.time || 0;
}

/**
 * Sorts requests most recent first, without changing the given list.
 *
 * @param {Array} works the requests
 * @returns {Array} a sorted copy
 */
export function sortRecentFirst(works) {
  return (works || []).slice().sort((a, b) => createdTimeOf(b) - createdTimeOf(a));
}

/**
 * Tells whether a request matches the text typed in the filter: its process title, its title,
 * its status label or its description contain the text, whatever the case.
 *
 * @param {Object} work the request or the draft
 * @param {String} query the typed text
 * @param {String} statusLabel the request's status, as shown
 * @returns {Boolean} true when the request matches, or when no text is typed
 */
export function matchesQuery(work, query, statusLabel) {
  const text = query && query.trim().toLowerCase();
  if (!text) {
    return true;
  }
  const description = plainTextOf(work.description);
  return [work.workFlow && work.workFlow.title, work.title, statusLabel, description]
    .some(value => value && String(value).toLowerCase().includes(text));
}

/**
 * Lists the processes of the given requests and drafts, each with how many of them it holds,
 * sorted by title.
 *
 * @param {Array} works the requests and the drafts
 * @returns {Array} the processes, as {id, title, count}
 */
export function processesOf(works) {
  const processes = new Map();
  (works || []).forEach(work => {
    const id = workflowIdOf(work);
    if (id) {
      const process = processes.get(id) || {id, title: work.workFlow.title || '', count: 0};
      process.count++;
      processes.set(id, process);
    }
  });
  return Array.from(processes.values()).sort((a, b) => a.title.localeCompare(b.title));
}

/**
 * Lists the statuses of the requests' processes, in the order each process lists them, then
 * any other status a request holds.
 *
 * @param {Array} works the sent requests, each with its process
 * @returns {Array} the status names
 */
export function statusesOf(works) {
  const statuses = new Set();
  (works || []).map(work => work.workFlow).filter(workflow => workflow && workflow.statuses)
    .forEach(workflow => workflow.statuses.forEach(status => status && status.name && statuses.add(status.name)));
  (works || []).forEach(work => work.status && statuses.add(work.status));
  return Array.from(statuses);
}

/**
 * Reads the text of a request's message, without its markup.
 *
 * @param {String} html the message, as HTML
 * @returns {String} its text, with runs of white space as one space
 */
export function plainTextOf(html) {
  if (!html) {
    return '';
  }
  const text = new DOMParser().parseFromString(html, 'text/html').body.textContent || '';
  return text.replace(/\s+/g, ' ').trim();
}

/**
 * Turns a group key, which may hold a status name a project wrote with spaces, into a hook for
 * ids and classes.
 *
 * @param {String} key the group key
 * @returns {String} the key in lower case, each run of other characters than letters and digits as a dash
 */
export function hookKeyOf(key) {
  return String(key || '').toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-+|-+$/g, '');
}

/**
 * Reads the grouping this browser remembers for « My requests ».
 *
 * @returns {String} the remembered grouping, none when nothing is remembered or the storage is unavailable
 */
export function readGroupBy() {
  try {
    const value = window.localStorage.getItem(GROUP_BY_STORAGE_KEY);
    return [GROUP_BY_STATUS, GROUP_BY_PROCESS].includes(value) ? value : GROUP_BY_NONE;
  } catch (e) {
    return GROUP_BY_NONE;
  }
}

/**
 * Remembers in this browser the grouping chosen in « My requests »; an unavailable storage remembers nothing.
 *
 * @param {String} groupBy the chosen grouping
 * @returns {void}
 */
export function storeGroupBy(groupBy) {
  try {
    window.localStorage.setItem(GROUP_BY_STORAGE_KEY, groupBy || GROUP_BY_NONE);
  } catch (e) {
    // a private window or blocked site data: the grouping lasts until the page is left
  }
}

/**
 * Reads the title of a request's process.
 *
 * @param {Object} work the request or the draft
 * @returns {String} the process title, empty when the request carries no process
 */
export function processTitleOf(work) {
  return work && work.workFlow && work.workFlow.title || '';
}

/**
 * Sorts requests by date, by status label or by process title, without changing the given list.
 *
 * @param {Array} works the requests
 * @param {String} sortBy date, status or process
 * @param {Boolean} sortDesc whether the order is descending
 * @param {Function} statusLabelOf gives a request's status label
 * @returns {Array} a sorted copy
 */
export function sortWorks(works, sortBy, sortDesc, statusLabelOf) {
  const direction = sortDesc ? -1 : 1;
  // by status or by process, the requests of one status or process stay most recent first, whatever the direction
  let compare = (a, b) => direction * (createdTimeOf(a) - createdTimeOf(b));
  if (sortBy === 'status') {
    compare = (a, b) => direction * statusLabelOf(a).localeCompare(statusLabelOf(b)) || createdTimeOf(b) - createdTimeOf(a);
  } else if (sortBy === 'process') {
    compare = (a, b) => direction * processTitleOf(a).localeCompare(processTitleOf(b)) || createdTimeOf(b) - createdTimeOf(a);
  }
  return (works || []).slice().sort(compare);
}
