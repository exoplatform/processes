<!--
Copyright (C) 2026 eXo Platform SAS.
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.
You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <exo-drawer
    id="workDetailDrawer"
    ref="drawer"
    class="ProcessesApplication"
    allow-expand
    right
    @closed="onClosed">
    <template #title>
      <work-detail-title :work-flow="work.workFlow" />
    </template>
    <template #titleIcons>
      <work-actions-menu
        :work="work"
        :allow-cancel="allowCancel"
        :task-link="taskLink"
        @archive="archiveWork"
        @cancel="cancelWork" />
    </template>
    <template v-if="work.id" #content>
      <div class="pa-4">
        <div class="d-flex align-center mb-4">
          <my-request-status-chip
            :is-completed="work.completed"
            :status="work.status"
            class="me-2" />
          <custom-date-format
            :timestamp="work.createdTime && work.createdTime.time"
            :message="$t('processes.myRequests.sentOn')"
            class="caption text-light-color" />
        </div>
        <!-- Extension point ProcessRequestDetail / request-detail-section: the request's form values -->
        <div id="requestDetailFormValues">
          <extension-registry-components
            :key="work.id"
            :params="extensionParams"
            name="ProcessRequestDetail"
            type="request-detail-section"
            strict-type />
        </div>
        <div class="work-detail-message mb-5">
          <div class="subtitle-1 font-weight-bold mb-2">{{ $t('processes.work.message.to.manager') }}</div>
          <div
            v-if="work.description"
            v-sanitized-html="work.description"
            class="work-description text-break"></div>
          <div v-else class="text-light-color font-italic">{{ $t('processes.work.description.empty') }}</div>
        </div>
        <v-divider />
        <div class="work-detail-documents my-5">
          <div class="subtitle-1 font-weight-bold mb-2">{{ $t('processes.works.form.label.documents') }}</div>
          <processes-attachments
            ref="attachments"
            :entity-id="work.id"
            edit-mode
            entity-type="task" />
        </div>
        <v-divider />
        <task-view-all-comments
          :task="work"
          :comments="comments"
          class="work-detail-comments mt-4" />
      </div>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  data: () => ({
    work: {},
    comments: [],
    taskLink: null,
  }),
  computed: {
    /**
     * Names the request for the sections other add-ons contribute to the detail.
     *
     * @returns {Object} the process id, the request id and its task id
     */
    extensionParams() {
      return {
        workflowId: this.work.workFlow && this.work.workFlow.id || null,
        workId: this.work.id,
        taskId: this.work.taskId || this.work.id,
      };
    },
    /**
     * Tells whether the request is not decided yet, so it may still be canceled.
     *
     * @returns {Boolean} true when undecided
     */
    allowCancel() {
      return !['Validated', 'Refused'].includes(this.work.status);
    },
  },
  created() {
    // Loads the modules whose name contains ProcessRequestDetailExtension, which register their
    // request-detail-section and the rows' request-row-summary components
    this.$utils.includeExtensions('ProcessRequestDetailExtension');
    this.$root.$on('open-work-detail-drawer', this.open);
    this.$root.$on('close-work-drawer', this.close);
  },
  beforeDestroy() {
    this.$root.$off('open-work-detail-drawer', this.open);
    this.$root.$off('close-work-drawer', this.close);
  },
  methods: {
    /**
     * Opens the drawer on a sent request, names it in the address bar, and lists its documents:
     * the documents list stays mounted once the drawer opened, so a later opening tells it which
     * request to read.
     *
     * @param {Object} event the request and its comments, {work, comments}
     * @returns {void}
     */
    open(event) {
      this.work = event.work || {};
      this.comments = event.comments || [];
      this.taskLink = null;
      this.checkTaskAccess(this.work);
      this.$root.$emit('update-url-path', 'requestDetails', `/myRequests/requestDetails/${this.work.id}`);
      // A first opening mounts the list, which reads its request when created
      if (this.$refs.attachments) {
        this.$root.$emit('init-list-attachments', {entityId: this.work.id, entityType: 'task'});
      }
      this.$refs.drawer.open();
    },
    /**
     * Offers « Open the task » only when GET /rest/tasks/{id} answers 200 for the viewer: Tasks
     * refuses a requester outside the task's project, and nothing on the request says it.
     * An answer for a request the drawer no longer shows is ignored.
     *
     * @param {Object} work the request, whose id is its task's id
     * @returns {Promise} resolved once the answer is read
     */
    checkTaskAccess(work) {
      const taskId = work.taskId || work.id;
      if (!taskId) {
        return Promise.resolve();
      }
      return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/tasks/${taskId}`, {credentials: 'include'})
        .then(resp => {
          if (resp && resp.ok && this.work === work) {
            this.taskLink = `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/tasks/taskDetail/${taskId}`;
          }
        })
        .catch(() => null);
    },
    /**
     * Closes the drawer, as the address bar went back to the list.
     *
     * @returns {void}
     */
    close() {
      this.$refs.drawer.close();
    },
    /**
     * Archives or unarchives the request, then closes the drawer, which would show its former state.
     *
     * @returns {void}
     */
    archiveWork() {
      this.$root.$emit('update-work-completed', this.work);
      this.$refs.drawer.close();
    },
    /**
     * Asks the confirmation of the cancellation, then closes the drawer.
     *
     * @returns {void}
     */
    cancelWork() {
      this.$root.$emit('show-confirm-action', {model: this.work, reason: 'cancel_work'});
      this.$refs.drawer.close();
    },
    /**
     * Puts « My requests » back in the address bar.
     *
     * @returns {void}
     */
    onClosed() {
      this.$root.$emit('add-work-drawer-closed');
      this.$root.$emit('update-url-path', 'myRequest', '/myRequests');
    },
  },
};
</script>
