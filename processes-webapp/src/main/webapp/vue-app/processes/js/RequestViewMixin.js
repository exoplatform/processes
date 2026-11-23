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

import {toProcessDocument} from './ProcessesService.js';

/**
 * The request drawer's view of a sent request: its task's link, its process's documents, which tell
 * the request's copies among its files, its menu's actions, and the comment its footer posts.
 * The drawer gives `work`, `viewMode`, `viewDraft`, `openKey`, `processDocuments` and `$refs.work`.
 */
export default {
  data: () => ({
    taskLink: null,
    canComment: false,
  }),
  computed: {
    /**
     * Tells whether the request may still be canceled: pending, its task not completed, whatever its
     * column.
     *
     * @returns {Boolean} true while the request is pending
     */
    allowCancel() {
      return !!this.work && !this.work.completed;
    },
  },
  methods: {
    /**
     * Prepares the view of a sent request: its task's number and link, once the viewer may edit the task, and its
     * process's documents, whose names tell the request's copies.
     *
     * @returns {void}
     */
    initViewState() {
      this.taskLink = null;
      this.canComment = false;
      if (!this.viewMode || this.viewDraft || !this.work) {
        return;
      }
      this.checkTaskAccess(this.work);
      this.loadViewDocuments();
    },
    /**
     * Offers the task's number and link only when GET /rest/tasks/{id} says the viewer may edit the task
     * (`editable`: a member of the process's space, the task's assignee or a coworker, or a platform
     * administrator). A requester outside the process's space, who sees and comments on the request here,
     * does not open its task from the drawer. An answer for a request the drawer no longer shows is ignored.
     *
     * @param {Object} work the request
     * @returns {Promise} resolved once the answer is read
     */
    checkTaskAccess(work) {
      const taskId = work.taskId || work.id;
      if (!taskId) {
        return Promise.resolve();
      }
      return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/tasks/${taskId}`, {credentials: 'include'})
        .then(resp => (resp && resp.ok ? resp.json() : null))
        .then(task => {
          if (task && task.editable === true && this.work === work) {
            this.taskLink = `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/tasks/taskDetail/${taskId}`;
          }
        })
        .catch(() => null);
    },
    /**
     * Loads the process's documents to fill in, which the drawer's layout reads only while composing: their
     * names tell which of the sent request's files are its copies.
     *
     * @returns {Promise} settled once they are loaded
     */
    loadViewDocuments() {
      const openKey = this.openKey;
      const workflow = this.work.workFlow || this.work.workflow;
      if (!workflow || !workflow.id) {
        return Promise.resolve();
      }
      return this.$processesService.getDocumentsToFill(workflow.id)
        .then(documents => {
          if (openKey === this.openKey) {
            this.processDocuments = (documents || []).map(toProcessDocument);
          }
        })
        .catch(() => null);
    },
    /**
     * Archives or unarchives the request, then closes the drawer, which would show its former state.
     *
     * @returns {void}
     */
    archiveViewedWork() {
      this.$root.$emit('update-work-completed', this.work);
      this.$refs.work.close();
    },
    /**
     * Asks the confirmation of the cancellation, then closes the drawer.
     *
     * @returns {void}
     */
    cancelViewedWork() {
      this.$root.$emit('show-confirm-action', {model: this.work, reason: 'cancel_work'});
      this.$refs.work.close();
    },
    /**
     * Posts the comment written in the view's composer.
     *
     * @returns {Promise} settled once it is posted
     */
    postViewComment() {
      return this.$refs.viewBody ? this.$refs.viewBody.postComment() : Promise.resolve();
    },
  },
};
