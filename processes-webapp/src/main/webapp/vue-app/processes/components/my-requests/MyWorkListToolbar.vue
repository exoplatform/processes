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
    :right-text-filter="{placeholder: $t('processes.work.filter.query.placeholder')}"
    :right-select-box="{
      selected: filter,
      items: filterItems,
    }"
    @filter-text-input-end-typing="updateQuery"
    @filter-select-change="updateFilter">
    <template #left>
      <span class="my-requests-count text-light-color subtitle-2">{{ countLabel }}</span>
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
    requestsCount: {
      type: Number,
      default: 0,
    },
    draftsCount: {
      type: Number,
      default: 0,
    },
  },
  data: () => ({
    ALL_FILTER: 'all',
    filter: 'all',
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
     * Says how many requests and drafts the list shows, « 5 requests · 1 draft ».
     *
     * @returns {String} the count
     */
    countLabel() {
      const requests = this.requestsCount === 1
        ? this.$t('processes.myRequests.count.request')
        : this.$t('processes.myRequests.count.requests', {0: this.requestsCount});
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
      this.emitFilter();
    },
    /**
     * Applies the status chosen in the select box.
     *
     * @param {String} filter the chosen status
     * @returns {void}
     */
    updateFilter(filter) {
      this.filter = filter;
      this.emitFilter();
    },
    /**
     * Sends the status and the text to the application, which reloads the requests, and tells
     * the list whether a filter is applied, so its empty state says « no match ».
     *
     * @returns {void}
     */
    emitFilter() {
      const status = this.filter === this.ALL_FILTER ? null : this.filter;
      this.$emit('change', {filtered: !!this.query || !!status});
      this.$root.$emit('work-filter-changed', {status, query: this.query});
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
