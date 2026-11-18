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
  <tr
    :id="rowId"
    :aria-label="requestTitle"
    class="my-request-row"
    tabindex="0"
    @click="$emit('open')"
    @keydown.enter.self="$emit('open')">
    <td class="my-request-cell-request">
      <div class="my-request-title font-weight-bold text-truncate">{{ requestTitle }}</div>
      <div
        v-if="preview"
        :title="preview"
        class="my-request-preview caption text-light-color text-truncate">
        {{ preview }}
      </div>
      <!-- Extension point ProcessRequestRow / request-row-summary: a few of the request's form values.
           Its modules load with the detail drawer's: their name contains ProcessRequestDetailExtension -->
      <div :id="`requestRowSummary-${work.id}`" class="my-request-row-summary text-truncate">
        <extension-registry-components
          :params="{work, isDraft}"
          name="ProcessRequestRow"
          type="request-row-summary"
          strict-type />
      </div>
      <my-request-status-chip
        :status="work.status"
        :is-draft="isDraft"
        v-if="!wide"
        :is-completed="work.completed"
        class="mt-1"
        x-small />
    </td>
    <td v-if="wide" class="my-request-cell-comments">
      <v-btn
        v-if="!isDraft && commentsCount > 0"
        :title="$t('processes.myRequests.comments')"
        class="my-request-comments-button text-light-color px-1"
        small
        text
        @click.stop="$emit('open-comments')">
        <v-icon size="16" class="me-1">mdi-chat-outline</v-icon>
        {{ commentsCount }}
      </v-btn>
    </td>
    <td v-if="large" class="my-request-cell-process">
      <div class="d-flex align-center text-truncate">
        <v-avatar
          :class="!avatarUrl && 'workflow-card-avatar-placeholder primary--text'"
          class="my-request-row-avatar flex-shrink-0 me-2"
          size="24">
          <v-img v-if="avatarUrl" :src="avatarUrl" />
          <span v-else class="caption font-weight-bold">{{ initial }}</span>
        </v-avatar>
        <span class="text-truncate">{{ processTitle }}</span>
      </div>
    </td>
    <td class="my-request-cell-date text-no-wrap">
      <custom-date-format :timestamp="createdTime" class="text-light-color" />
    </td>
    <td v-if="wide" class="my-request-cell-status">
      <my-request-status-chip
        :status="work.status"
        :is-draft="isDraft"
        :is-completed="work.completed" />
    </td>
    <td class="my-request-cell-actions text-end">
      <work-actions-menu
        :work="work"
        :is-draft="isDraft"
        :allow-cancel="allowCancel"
        small
        @archive="$emit('archive')"
        @cancel="$emit('cancel')"
        @delete="$emit('delete')" />
    </td>
  </tr>
</template>

<script>
import {LARGE_WIDTH, WIDE_WIDTH, plainTextOf} from '../../js/MyRequestsFilters.js';

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
     * Tells whether the screen is wide enough for the comments and the status cells, the same
     * test as the table's columns.
     *
     * @returns {Boolean} true from 600px
     */
    wide() {
      return this.$vuetify.breakpoint.width >= WIDE_WIDTH;
    },
    /**
     * Tells whether the screen is wide enough for the process cell, the same test as the table's
     * columns.
     *
     * @returns {Boolean} true from 960px
     */
    large() {
      return this.$vuetify.breakpoint.width >= LARGE_WIDTH;
    },
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
    processTitle() {
      return this.work.workFlow && this.work.workFlow.title || '';
    },
    /**
     * Reads the request's own title, or its process's when it has none.
     *
     * @returns {String} the title
     */
    requestTitle() {
      return this.work.title || this.processTitle;
    },
    /**
     * Reads a one-line preview of the request's message, without its markup.
     *
     * @returns {String} the preview, empty when the request has no message
     */
    preview() {
      return plainTextOf(this.work.description);
    },
    /**
     * Reads the first letter of the process title, shown when the process has no illustration.
     *
     * @returns {String} the initial
     */
    initial() {
      return this.processTitle.charAt(0).toUpperCase();
    },
    /**
     * Reads when the request was created.
     *
     * @returns {Number} the time in milliseconds
     */
    createdTime() {
      return this.work.createdTime && this.work.createdTime.time || null;
    },
  },
};
</script>
