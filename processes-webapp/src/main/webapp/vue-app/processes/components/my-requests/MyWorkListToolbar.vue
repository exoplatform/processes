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
  <application-toolbar
    id="myRequestsToolbar"
    :hide-right="hideFilters"
    :hide-cone-button="!$vuetify.breakpoint.mobile"
    :filters-count="filtersCount"
    :right-text-filter="{placeholder: $t('processes.myRequests.filter.query')}"
    :right-select-box="{
      selected: filter,
      items: filterItems,
      ariaLabel: $t('processes.myRequests.filter.status'),
    }"
    @filter-text-input-end-typing="updateQuery"
    @filter-select-change="updateFilter">
    <template #left>
      <span
        v-if="!hideFilters"
        id="myRequestsCount"
        class="my-requests-count text-light-color subtitle-2"
        aria-live="polite">
        {{ countLabel }}
      </span>
    </template>
    <template #right>
      <select
        id="myRequestsProcessFilter"
        v-model="workflowId"
        :aria-label="$t('processes.myRequests.filter.process')"
        class="my-requests-process-filter flex-grow-0 ignore-vuetify-classes py-2 height-auto width-auto text-truncate my-auto ms-4"
        @change="emitFilter(false)">
        <option :value="ALL_FILTER">{{ $t('processes.myRequests.filter.allProcesses') }}</option>
        <option
          v-for="process in processes"
          :id="`myRequestsProcessFilterItem-${process.id}`"
          :key="process.id"
          :value="process.id">
          {{ $t('processes.myRequests.filter.processItem', {0: process.title, 1: process.count}) }}
        </option>
      </select>
    </template>
  </application-toolbar>
</template>

<script>
export default {
  props: {
    availableWorkStatuses: {
      type: Array,
      default: () => [],
    },
    processes: {
      type: Array,
      default: () => [],
    },
    totalCount: {
      type: Number,
      default: 0,
    },
    draftsCount: {
      type: Number,
      default: 0,
    },
    shownCount: {
      type: Number,
      default: 0,
    },
    hideFilters: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    ALL_FILTER: 'all',
    filter: 'all',
    workflowId: 'all',
    query: null,
  }),
  computed: {
    /**
     * Builds the status choices: all, each status the requests may take, drafts, and completed.
     *
     * @returns {Array} the choices, as {text, value}
     */
    filterItems() {
      const items = [{text: this.$t('processes.myRequests.filter.all'), value: this.ALL_FILTER}];
      (this.availableWorkStatuses || []).forEach(status => {
        if (status && status.name && !items.find(item => item.value === status.name)) {
          items.push({text: this.statusI18n(status.name), value: status.name});
        }
      });
      items.push({text: this.$t('processes.myWorks.status.draft'), value: 'drafts'});
      items.push({text: this.$t('label.task.completed'), value: 'completed'});
      return items;
    },
    /**
     * Tells whether a filter is applied.
     *
     * @returns {Boolean} true when a text, a process or a status is chosen
     */
    filtered() {
      return !!this.query || this.workflowId !== this.ALL_FILTER || this.filter !== this.ALL_FILTER;
    },
    /**
     * Counts the chosen process and status, for the toolbar's filter button on a phone.
     *
     * @returns {Number} how many of the two are chosen
     */
    filtersCount() {
      return (this.workflowId !== this.ALL_FILTER ? 1 : 0) + (this.filter !== this.ALL_FILTER ? 1 : 0);
    },
    /**
     * Says how many requests the list holds, « 13 requests · 2 drafts », or how many of them
     * the filters keep, « 2 of 13 requests ».
     *
     * @returns {String} the count
     */
    countLabel() {
      if (this.filtered) {
        return this.$t('processes.myRequests.count.filtered', {0: this.shownCount, 1: this.totalCount});
      }
      const requests = this.totalCount === 1
        ? this.$t('processes.myRequests.count.request')
        : this.$t('processes.myRequests.count.requests', {0: this.totalCount});
      if (!this.draftsCount) {
        return requests;
      }
      const drafts = this.draftsCount === 1
        ? this.$t('processes.myRequests.count.draft')
        : this.$t('processes.myRequests.count.drafts', {0: this.draftsCount});
      return `${requests} · ${drafts}`;
    },
  },
  methods: {
    /**
     * Applies the text typed in the filter.
     *
     * @param {String} query the typed text
     * @returns {void}
     */
    updateQuery(query) {
      this.query = query || null;
      this.emitFilter(false);
    },
    /**
     * Applies the status chosen in the select box.
     *
     * @param {String} filter the chosen status
     * @returns {void}
     */
    updateFilter(filter) {
      this.filter = filter;
      this.emitFilter(true);
    },
    /**
     * Sends the text, the process and the status to the list, which filters the requests it
     * holds, and, when the status changed, tells the application, which loads the completed
     * requests only when « completed » is chosen.
     *
     * @param {Boolean} statusChanged whether the status is what changed
     * @returns {void}
     */
    emitFilter(statusChanged) {
      const status = this.filter === this.ALL_FILTER ? null : this.filter;
      const workflowId = this.workflowId === this.ALL_FILTER ? null : this.workflowId;
      this.$emit('change', {query: this.query, workflowId, status});
      if (statusChanged) {
        this.$root.$emit('work-filter-changed', {status});
      }
    },
    /**
     * Translates a task status name, or keeps the name when no translation exists.
     *
     * @param {String} value the status name
     * @returns {String} the label
     */
    statusI18n(value) {
      const key = `tasks.status.${value}`;
      const translation = this.$t(key);
      return translation === key && value || translation;
    },
  },
};
</script>
