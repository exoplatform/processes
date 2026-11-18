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
  <section
    :id="`myRequestsGroup-${hookKey}`"
    :class="`my-request-group-${hookKey}`"
    class="my-request-group">
    <h4 class="my-request-group-header d-flex align-center text-light-color font-weight-regular subtitle-2 mt-4 mb-2">
      <span class="flex-shrink-0 me-3">{{ label }} ({{ works.length }})</span>
      <v-divider />
    </h4>
    <work
      v-for="work in shownWorks"
      :key="work.id"
      :work="work"
      :is-draft="isDraft" />
    <v-btn
      v-if="hiddenCount"
      :id="`myRequestsShowMore-${hookKey}`"
      class="my-requests-show-more text-none mb-2"
      color="primary"
      small
      text
      @click="expanded = true">
      {{ $t('processes.myRequests.showMore', {0: hiddenCount}) }}
    </v-btn>
    <p
      v-if="hint"
      id="myRequestsDraftsOnlyHint"
      class="my-requests-group-hint caption text-light-color mb-2 ms-1">
      {{ hint }}
    </p>
  </section>
</template>

<script>
export default {
  props: {
    groupKey: {
      type: String,
      default: null,
    },
    label: {
      type: String,
      default: null,
    },
    works: {
      type: Array,
      default: () => [],
    },
    isDraft: {
      type: Boolean,
      default: false,
    },
    cap: {
      type: Number,
      default: 0,
    },
    hint: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    expanded: false,
  }),
  computed: {
    /**
     * Turns the group key, a status name a project may write with spaces, into a hook for ids and classes.
     *
     * @returns {String} the key in lower case, each run of other characters than letters and digits as a dash
     */
    hookKey() {
      return String(this.groupKey || '').toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-+|-+$/g, '');
    },
    /**
     * Lists the requests the group shows: all of them, or the first ones of a capped group
     * until « Show N more » is clicked.
     *
     * @returns {Array} the shown requests
     */
    shownWorks() {
      return this.cap && !this.expanded ? this.works.slice(0, this.cap) : this.works;
    },
    /**
     * Counts the requests a capped group hides.
     *
     * @returns {Number} the hidden count, 0 when all are shown
     */
    hiddenCount() {
      return this.works.length - this.shownWorks.length;
    },
  },
};
</script>
