<template>
  <div
    id="workflows">
    <v-container class="workflow-controls pb-0">
      <v-row
        :class="isMobile? 'pe-5': 'pe-0'"
        no-gutters>
        <v-col
          v-if="!showMobileFilter || !isMobile"
          :cols="2"
          md="4"
          sm="1"
          xs="1"
          lg="6">
          <v-btn
            v-if="isProcessesManager"
            class="mt-2 mb-4 btn-primary btn add-process-btn"
            dark
            @click="open"
            color="primary">
            <v-icon
              :class="isCMobile? 'me-n1':''"
              left
              dark>
              mdi-plus-thick
            </v-icon>
            <span
              v-if="!isCMobile">
              {{ $t('processes.works.label.addRequestType') }}
            </span>
          </v-btn>
        </v-col>
        <v-col
          v-show="showWorkflowFilter"
          :cols="10"
          md="8"
          xs="11"
          sm="11"
          lg="6">
          <v-select
            v-if="!isMobile"
            ref="filter"
            class="pt-5 workflow-filter mt-n3 float-e"
            v-model="filter"
            :items="filterItems"
            item-text="label"
            item-value="value"
            return-object
            @blur="$refs.filter.blur();"
            @change="updateFilter"
            dense
            outlined />
          <v-btn
            v-if="!showMobileFilter && isMobile"
            class="float-e mt-2"
            @click="switchToMobileFilter"
            icon>
            <v-icon v-if="activatedFilters.length || query">
              mdi-filter
            </v-icon>
            <v-icon
              v-else>
              mdi-filter-outline
            </v-icon>
          </v-btn>
          <v-text-field
            v-if="!isMobile"
            class="me-4 workflow-filter-query filter-query-width float-e"
            @keyup="filterByQuery"
            v-model="query"
            :placeholder="$t('processes.workflow.filter.query.placeholder')"
            prepend-inner-icon="mdi-filter" />
        </v-col>
        <v-col
          v-if="showMobileFilter && isMobile"
          v-show="showWorkflowFilter"
          class="d-flex me-4"
          cols="12">
          <v-text-field
            class="pt-5 me-2 mb-1 workflow-filter-query float-e d-flex"
            @keyup="filterByQuery"
            v-model="query"
            :placeholder="$t('processes.workflow.filter.query.placeholder')">
            <template #prepend>
              <btn
                @click="switchToMobileFilter"
                icon>
                <v-icon class="mt-1">
                  fa-arrow-left
                </v-icon>
              </btn>
            </template>
            <template #append>
              <btn
                v-if="query"
                @click="resetQueryInput"
                icon>
                <v-icon>
                  fa-times
                </v-icon>
              </btn>
            </template>
          </v-text-field>
          <v-btn
            outlined
            class="btn btn-primary pa-0 mt-2 mobile-filter-btn"
            @click="openMobileFilter">
            <v-icon
              class="pa-0"
              size="16">
              fa-sliders-h
            </v-icon>
            <span v-if="activatedFilters.length">({{ activatedFilters.length }})</span>
          </v-btn>
        </v-col>
      </v-row>
    </v-container>
    <v-container
      v-if="!loading"
      no-gutters>
      <v-row
        v-if="workflowList.length>0"
        class="d-flex flex-wrap"
        no-gutters>
        <v-col
          xl="4"
          :lg="lg"
          md="6"
          sm="6"
          cols="12"
          v-for="workflow in workflowList"
          :key="workflow.id">
          <workflow-card-item
            :is-processes-manager="isProcessesManager"
            :workflow="workflow" />
        </v-col>
      </v-row>
      <v-row
        align="center"
        v-if="hasMore">
        <v-col>
          <v-btn
            :loading="loadingMore"
            @click="loadMore"
            class="btn">
            {{ $t('processes.load.more.label') }}
          </v-btn>
        </v-col>
      </v-row>
    </v-container>
    <empty-or-loading
      :loading="loading"
      v-if="workflowList.length === 0">
      <template #empty>
        <div>
          <v-img
            width="280px"
            height="273px"
            src="/processes/images/noWorkflow.png" />
          <p
            class="mt-2">
            {{ $t('processes.workflow.noProcess.label') }}
          </p>
        </div>
      </template>
    </empty-or-loading>
    <mobile-filter
      ref="workMobileFilter"
      :items="filterItems"
      default-value-index="0"
      @filter-changed="handleFilterChange"
      @activated-filters-update="handleActiveFilters" />
  </div>
</template>

<script>
export default {
  data () {
    return {
      MOBILE_WIDTH: 600,
      CUSTOM_MOBILE_WIDTH: 768,
      filter: {},
      showMobileFilter: false,
      displayFilterMenu: true,
      filterItems: [],
      query: null,
      searchTimer: null,
      endTypingKeywordTimeout: 200,
      activatedFilters: [],
    };
  },
  props: {
    workflows: {
      type: Array,
      default: function() {
        return [];
      },
    },
    hasMore: {
      type: Boolean,
      default: false
    },
    loading: {
      type: Boolean,
      default: false
    },
    loadingMore: {
      type: Boolean,
      default: false
    },
    isProcessesManager: {
      type: Boolean,
      default: false
    },
    showWorkflowFilter: {
      type: Boolean,
      default: false
    }
  },
  created() {
    this.init();
    this.$root.$on('workflow-added', (event) => {
      if (event.filter == null || event.workflow.enabled === event.filter) {
        this.workflowList.unshift(event.workflow);
      }
    });
    this.$root.$on('workflow-updated', (workflow) => {
      workflow = JSON.parse(workflow);
      const index = this.workflowList.map(workflow => workflow.id).indexOf(workflow.id);
      if (this.filter.value === 'activated' && workflow.enabled) {
        this.workflowList.splice(index, 1, workflow);
      } else if (this.filter.value === 'deactivated' && !workflow.enabled) {
        this.workflowList.splice(index, 1, workflow);
      } else {
        this.workflowList.splice(index, 1);
      }
    });
    this.$root.$on('workflow-removed', (workflow) => {
      this.workflowList.splice(this.workflowList.indexOf(workflow), 1);
    });
  },
  computed: {
    workflowList(){
      return this.workflows || [];
    },
    lg() {
      if (this.workflowList.length >= 4) {
        return 4;
      }
      return this.workflowList.length === 3 ? 4 : 6;
    },
    isMobile() {
      return this.$vuetify.breakpoint.width < this.MOBILE_WIDTH;
    },
    isCMobile() {
      return this.$vuetify.breakpoint.width < this.CUSTOM_MOBILE_WIDTH;
    },
  },
  methods: {
    openMobileFilter() {
      this.$refs.workMobileFilter.open();
    },
    handleFilterChange(event) {
      this.filter.value = event.filter;
      this.updateFilter();
    },
    handleActiveFilters(filter) {
      const exists = this.activatedFilters.includes(filter.filterType);
      if (!exists && filter.enabled) {
        this.activatedFilters.push(filter.filterType);
      } else if (exists && !filter.enabled) {
        const index = this.activatedFilters.indexOf(filter.filterType);
        this.activatedFilters.splice(index, 1);
      }
    },
    resetQueryInput() {
      if (!this.query) {
        return;
      }
      this.query = null;
      this.updateFilter();
    },
    switchToMobileFilter() {
      this.showMobileFilter = !this.showMobileFilter;
    },
    open() {
      this.$root.$emit('open-workflow-drawer', {workflow: null, mode: 'create_workflow'});
    },
    loadMore() {
      this.$root.$emit('load-more-workflows');
    },
    filterByQuery() {
      clearTimeout(this.searchTimer);
      this.searchTimer = setTimeout(() => {
        if (this.loading) {
          this.filterByQuery();
          return;
        }
        this.updateFilter();
      }, this.endTypingKeywordTimeout);
    },
    updateFilter() {
      this.$root.$emit('workflow-filter-changed', {filter: this.filter.value, query: this.query});
    },
    init(){
      this.filter = this.isProcessesManager ? {label: this.$t('processes.workflow.activated.label'), value: 'activated'} : {label: this.$t('processes.workflow.all.label'), value: null};
      if (this.isProcessesManager) {
        this.filterItems.push({label: this.$t('processes.workflow.activated.label'), value: 'activated'});
        this.filterItems.push({label: this.$t('processes.workflow.deactivated.label'), value: 'deactivated'});
      }
      this.filterItems.push({label: this.$t('processes.workflow.manager.label'), value: 'manager' });
      this.filterItems.push({label: this.$t('processes.workflow.all.label'), value: null});
    }
  }
};
</script>
