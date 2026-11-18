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
  <v-card
    :id="rowId"
    :aria-label="title"
    class="my-request-row hover-elevation position-relative d-flex align-center mb-2 ps-4 pe-2 py-2"
    role="link"
    tabindex="0"
    outlined
    @click="$emit('open')"
    @keydown.enter.self="$emit('open')">
    <request-status
      :is-completed="work.completed"
      :is-draft="isDraft"
      :status="work.status"
      type="indicator"
      class="my-request-row-indicator" />
    <div class="my-request-row-process d-flex align-center flex-grow-1 text-truncate">
      <v-avatar
        :class="!avatarUrl && 'workflow-card-avatar-placeholder primary--text'"
        class="my-request-row-avatar flex-shrink-0 me-3"
        size="32">
        <v-img v-if="avatarUrl" :src="avatarUrl" />
        <span v-else class="font-weight-bold">{{ initial }}</span>
      </v-avatar>
      <div class="text-truncate">
        <div class="d-flex align-center">
          <span
            :class="work.completed && 'text-decoration-line-through'"
            class="my-request-row-title font-weight-bold text-truncate">
            {{ title }}
          </span>
          <span
            v-if="isDraft"
            class="my-request-draft-badge text-uppercase ms-2 px-1">
            {{ $t('processes.myWorks.status.draftLabel') }}
          </span>
        </div>
        <custom-date-format
          :timestamp="createdTime"
          class="caption text-light-color" />
      </div>
    </div>
    <!-- Extension point ProcessRequestRow / request-row-summary: a few of the request's form values.
         Its modules load with the detail drawer's: their name contains ProcessRequestDetailExtension -->
    <div
      :id="`requestRowSummary-${work.id}`"
      class="my-request-row-summary d-none d-md-flex flex-shrink-1 text-truncate mx-2">
      <extension-registry-components
        :params="{work, isDraft}"
        name="ProcessRequestRow"
        type="request-row-summary"
        strict-type />
    </div>
    <div class="my-request-row-status flex-shrink-0 mx-2">
      <request-status
        :is-completed="work.completed"
        :is-draft="isDraft"
        :status="work.status" />
    </div>
    <div class="my-request-row-comments d-none d-sm-flex flex-shrink-0 justify-end mx-2">
      <v-btn
        v-if="!isDraft"
        :title="$t('processes.myRequests.comments')"
        class="my-request-comments-button text-light-color"
        small
        text
        @click.stop="$emit('open-comments')">
        <v-icon size="16" class="me-1">mdi-chat-outline</v-icon>
        {{ commentsCount }}
      </v-btn>
    </div>
    <div class="my-request-row-actions d-flex align-center justify-end flex-shrink-0">
      <v-btn
        v-if="isDraft"
        :title="$t('processes.myRequests.resume.tooltip')"
        class="my-request-resume btn text-none me-1"
        small
        @click.stop="$emit('resume')">
        {{ $t('processes.myRequests.resume') }}
      </v-btn>
      <work-actions-menu
        :work="work"
        :is-draft="isDraft"
        :allow-cancel="allowCancel"
        small
        @archive="$emit('archive')"
        @cancel="$emit('cancel')"
        @delete="$emit('delete')" />
    </div>
  </v-card>
</template>

<script>
export default {
  props: {
    work: {
      type: Object,
      default: () => ({}),
    },
    isDraft: {
      type: Boolean,
      default: false,
    },
    commentsCount: {
      type: Number,
      default: 0,
    },
    avatarUrl: {
      type: String,
      default: null,
    },
    allowCancel: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    /**
     * Builds the row's hook, the request id or the draft id: the two are counted apart.
     *
     * @returns {String} the row id
     */
    rowId() {
      return `${this.isDraft ? 'myRequestDraft' : 'myRequest'}${this.work.id}`;
    },
    /**
     * Reads the title of the request's process.
     *
     * @returns {String} the process title
     */
    title() {
      return this.work.workFlow && this.work.workFlow.title || '';
    },
    /**
     * Reads the first letter of the process title, shown when the process has no illustration.
     *
     * @returns {String} the initial
     */
    initial() {
      return this.title.charAt(0).toUpperCase();
    },
    /**
     * Reads the request's creation date.
     *
     * @returns {Number} the timestamp, or null
     */
    createdTime() {
      return this.work.createdTime && this.work.createdTime.time || null;
    },
  },
};
</script>
