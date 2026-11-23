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
 * Keeps « My requests » lists in step with what happens to a request elsewhere in the
 * application (a draft saved or deleted, a request deleted, canceled, archived or restored),
 * without reloading them. The component gives the lists as `draftList`, `workList` and
 * `completedWorkList`.
 */
export default {
  created() {
    this.$root.$on('work-draft-updated', this.replaceDraft);
    this.$root.$on('work-draft-removed', this.removeDraft);
    this.$root.$on('work-removed', this.removeWork);
    this.$root.$on('work-canceled', this.handleCanceled);
    this.$root.$on('work-completed', this.handleCompleted);
    this.$root.$on('work-uncanceled', this.handleUncompleted);
  },
  beforeDestroy() {
    this.$root.$off('work-draft-updated', this.replaceDraft);
    this.$root.$off('work-draft-removed', this.removeDraft);
    this.$root.$off('work-removed', this.removeWork);
    this.$root.$off('work-canceled', this.handleCanceled);
    this.$root.$off('work-completed', this.handleCompleted);
    this.$root.$off('work-uncanceled', this.handleUncompleted);
  },
  methods: {
    /**
     * Replaces a saved draft in the list.
     *
     * @param {String} draft the saved draft, as JSON
     * @returns {void}
     */
    replaceDraft(draft) {
      const object = JSON.parse(draft);
      const index = this.draftList.findIndex(item => item.id === object.id);
      if (index >= 0) {
        this.draftList.splice(index, 1, object);
      }
    },
    /**
     * Removes a deleted draft from the list.
     *
     * @param {Object} draft the deleted draft
     * @returns {void}
     */
    removeDraft(draft) {
      const index = this.draftList.findIndex(item => item.id === draft.id);
      if (index >= 0) {
        this.draftList.splice(index, 1);
      }
    },
    /**
     * Removes a deleted request from the list.
     *
     * @param {Object} work the deleted request
     * @returns {void}
     */
    removeWork(work) {
      const index = this.workList.indexOf(work);
      if (index >= 0) {
        this.workList.splice(index, 1);
      }
    },
    /**
     * Shows a canceled request as canceled where it is listed: it stays among the open requests,
     * whose « Archive » then archives it. Its process and comments count stay those listed.
     *
     * @param {Object} work the canceled request, as the server answered
     * @returns {void}
     */
    handleCanceled(work) {
      const listed = this.workList.find(item => item.id === work.id);
      if (listed) {
        Object.assign(listed, {status: work.status, completed: work.completed, canceled: work.canceled, archived: work.archived});
      }
    },
    /**
     * Takes an archived request out of its status group.
     *
     * @param {Object} work the request
     * @returns {void}
     */
    handleCompleted(work) {
      const index = this.workList.findIndex(item => item.id === work.id);
      if (index >= 0) {
        this.workList.splice(index, 1);
      }
      work.archived = true;
    },
    /**
     * Takes an unarchived request out of the completed group, and reloads the open requests,
     * so it shows again in its status group with its process.
     *
     * @param {Object} work the request
     * @returns {void}
     */
    handleUncompleted(work) {
      const index = this.completedWorkList.findIndex(item => item.id === work.id);
      if (index >= 0) {
        this.completedWorkList.splice(index, 1);
      }
      work.archived = false;
      this.$root.$emit('refresh-works');
    },
  },
};
