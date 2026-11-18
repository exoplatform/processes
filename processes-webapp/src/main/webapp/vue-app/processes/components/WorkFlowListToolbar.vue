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
    id="processesToolbar"
    :hide-right="!showFilter"
    :right-text-filter="{placeholder: $t('processes.workflow.filter.query.placeholder')}"
    :right-select-box="{
      hide: !canFilterByStatus,
      selected: filter,
      items: filterItems,
    }"
    @filter-text-input-end-typing="updateQuery"
    @filter-select-change="updateFilter">
    <template #left>
      <v-btn
        v-if="isProcessesManager"
        id="addProcessButton"
        :aria-label="$t('processes.works.label.addRequestType')"
        :title="isMobile && $t('processes.works.label.addRequestType') || null"
        :class="isMobile && 'px-0'"
        class="btn btn-primary text-none"
        @click="$emit('add')">
        <v-icon
          :class="!isMobile && 'me-2'"
          size="16"
          dark>
          fa-plus
        </v-icon>
        <span v-if="!isMobile">{{ $t('processes.works.label.addRequestType') }}</span>
      </v-btn>
    </template>
  </application-toolbar>
</template>

<script>
export default {
  props: {
    isProcessesManager: {type: Boolean, default: false},
    showFilter: {type: Boolean, default: false},
    loading: {type: Boolean, default: false},
  },
  data: () => ({
    ALL_FILTER: 'all',
    RETRY_DELAY: 200,
    retryTimer: null,
    filter: null,
    defaultFilter: null,
    filterItems: [],
    query: null,
    isMemberSpaceManager: false,
  }),
  computed: {
    canFilterByStatus() {
      return this.isMemberSpaceManager || this.isProcessesManager;
    },
    isMobile() {
      return this.$vuetify.breakpoint.mobile;
    },
  },
  created() {
    this.init();
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
     * Sends the status and the text to the application, which reloads the processes,
     * and tells the list which status is selected and whether it differs from the default view.
     * While a load runs, it waits for its end: the application appends each answer to the list
     * it holds when the answer arrives, so a second load started meanwhile would mix both results.
     *
     * @returns {void}
     */
    emitFilter() {
      window.clearTimeout(this.retryTimer);
      if (this.loading) {
        this.retryTimer = window.setTimeout(this.emitFilter, this.RETRY_DELAY);
        return;
      }
      const filter = this.filter === this.ALL_FILTER ? null : this.filter;
      this.$emit('change', {filter, filtered: !!this.query || this.filter !== this.defaultFilter});
      this.$root.$emit('workflow-filter-changed', {filter, query: this.query});
    },
    /**
     * Builds the status choices: a processes administrator gets them all, any other user
     * gets « I manage » and « Disabled » only when they manage a process's space.
     *
     * @returns {void}
     */
    init() {
      const managerFilterItem = {text: this.$t('processes.workflow.manager.label'), value: 'manager'};
      this.defaultFilter = this.isProcessesManager ? 'activated' : this.ALL_FILTER;
      this.filter = this.defaultFilter;
      if (this.isProcessesManager) {
        this.filterItems.push({text: this.$t('processes.workflow.activated.label'), value: 'activated'});
        this.filterItems.push({text: this.$t('processes.workflow.deactivated.label'), value: 'deactivated'});
        this.filterItems.push(managerFilterItem);
      } else {
        this.$processesService.getWorkFlows({manager: true}, 0, 1).then(workflows => {
          this.isMemberSpaceManager = workflows.length > 0;
          if (this.isMemberSpaceManager) {
            this.filterItems.unshift(managerFilterItem);
            this.filterItems.splice(1, 0, {text: this.$t('processes.workflow.deactivated.label'), value: 'deactivated'});
          }
        });
      }
      this.filterItems.push({text: this.$t('processes.workflow.all.label'), value: this.ALL_FILTER});
    },
  },
};
</script>
