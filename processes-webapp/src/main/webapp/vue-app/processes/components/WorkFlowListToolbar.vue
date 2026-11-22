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
  <div>
    <!-- the search, then the filter icon holding the status, the category and the grouping, at the
         right of the toolbar as « My requests » places them -->
    <application-toolbar
      id="processesToolbar"
      :hide-right="!showFilter"
      :right-text-filter="{placeholder: $t('processes.workflow.filter.query.placeholder')}"
      compact
      @filter-expand="filterExpand = $event"
      @filter-text-input-end-typing="updateQuery">
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
      <template v-if="!filterExpand" #right>
        <div class="ms-2 d-flex align-center">
          <v-btn
            id="processesFilterButton"
            :aria-label="filterButtonLabel"
            :title="filterButtonLabel"
            icon
            @click="openFilterDrawer">
            <v-badge
              :value="filtersCount > 0"
              :content="filtersCount"
              class="processes-filter-count"
              color="primary"
              offset-x="8"
              offset-y="8"
              bordered
              overlap>
              <v-icon
                :class="filtersCount && 'primary--text' || 'icon-default-color'"
                size="20">
                fa-sliders-h
              </v-icon>
            </v-badge>
          </v-btn>
        </div>
      </template>
    </application-toolbar>
    <categories-filter
      v-if="showFilter && categories.size"
      id="processesCategoryChips"
      :key="categoriesKey"
      :value="categoryId"
      :category-depth="CATEGORY_DEPTH"
      object-type="process"
      filter-type="category"
      class="mt-2"
      hide-on-empty
      @input="chooseCategory" />
    <workflow-filter-drawer
      ref="filterDrawer"
      :status-items="filterItems"
      :default-status="defaultFilter"
      :categories="categories"
      @apply="applyFilters" />
  </div>
</template>

<script>
import {CATEGORY_DEPTH} from '../js/ProcessCategories.js';

export default {
  props: {
    isProcessesManager: {type: Boolean, default: false},
    showFilter: {type: Boolean, default: false},
    loading: {type: Boolean, default: false},
    categories: {type: Map, default: () => new Map()},
  },
  data: () => ({
    ALL_FILTER: 'all',
    CATEGORY_DEPTH,
    RETRY_DELAY: 200,
    retryTimer: null,
    filter: null,
    defaultFilter: null,
    filterItems: [],
    query: null,
    categoryId: null,
    grouped: false,
    filterExpand: false,
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.mobile;
    },
    /**
     * @returns {String} the ids of the page's categories: the chips, which read their own tree once,
     *          read it again whenever these change
     */
    categoriesKey() {
      return [...this.categories.keys()].sort((a, b) => a - b).join(',');
    },
    /**
     * @returns {Number} how many filters narrow the processes: the status when it isn't the one the
     *          page opens with, and the category; the grouping narrows nothing
     */
    filtersCount() {
      return (this.filter !== this.defaultFilter ? 1 : 0) + (this.categoryId ? 1 : 0);
    },
    filterButtonLabel() {
      if (!this.filtersCount) {
        return this.$t('processes.workflow.filter.button');
      }
      if (this.filtersCount === 1) {
        return this.$t('processes.workflow.filter.buttonOne');
      }
      return this.$t('processes.workflow.filter.buttonCount', {0: this.filtersCount});
    },
  },
  watch: {
    categories(categories) {
      // a category no longer offered, its last process moved out of it, filters nothing any more
      if (this.categoryId && categories.size && !categories.has(this.categoryId)) {
        this.categoryId = null;
        this.emitFilter();
      }
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
     * Opens the drawer on the filters applied now.
     *
     * @returns {void}
     */
    openFilterDrawer() {
      this.$refs.filterDrawer.open({status: this.filter, categoryId: this.categoryId, grouped: this.grouped});
    },
    /**
     * Applies the status, the category and the grouping chosen in the drawer.
     *
     * @param {Object} filters the chosen status, categoryId and grouped
     * @returns {void}
     */
    applyFilters(filters) {
      this.filter = filters.status || this.defaultFilter;
      this.categoryId = filters.categoryId || null;
      this.grouped = !!filters.grouped;
      this.emitFilter();
    },
    /**
     * Applies the category chosen among the chips, or any category when the chip is unselected.
     *
     * @param {String} categoryId the chosen category id, or null
     * @returns {void}
     */
    chooseCategory(categoryId) {
      const chosen = categoryId && Number(categoryId) || null;
      if (chosen !== this.categoryId) {
        this.categoryId = chosen;
        this.emitFilter();
      }
    },
    /**
     * Sends the status, the category and the text to the application, which reloads the processes,
     * and tells the list which status is selected, whether it groups by category and whether the view
     * differs from the default one.
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
      this.$emit('change', {
        filter,
        categoryId: this.categoryId,
        grouped: this.grouped,
        filtered: !!this.query || this.filter !== this.defaultFilter || !!this.categoryId,
      });
      this.$root.$emit('workflow-filter-changed', {filter, query: this.query, categoryId: this.categoryId});
    },
    /**
     * Builds the status choices: a processes administrator gets them all, any other user
     * gets « I manage » and « Disabled » only when they manage a process's space. The status the
     * page opens with is « Enabled » for an administrator, « All » for anyone else.
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
          if (workflows.length > 0) {
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
