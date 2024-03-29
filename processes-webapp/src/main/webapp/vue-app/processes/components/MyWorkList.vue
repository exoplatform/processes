<template>
  <div
    id="myWorks">
    <v-container class="pa-0 work-controls" v-show="showWorkFilter">
      <v-row no-gutters>
        <v-col
          height="150">
          <v-select
            v-if="!isMobile"
            ref="filter"
            class="work-filter pt-5 me-9 float-e"
            v-model="filter"
            :items="filterItems"
            item-text="label"
            item-value="value"
            return-object
            attach
            @blur="$refs.filter.blur();"
            @change="updateFilter"
            dense
            outlined />
          <v-text-field
            v-if="!isMobile"
            class="work-filter-query me-4"
            @keyup="updateFilter"
            v-model="query"
            :placeholder="$t('processes.work.filter.query.placeholder')"
            prepend-inner-icon="mdi-filter">
            <template #append>
              <btn
                v-if="query"
                @click="resetQueryInput"
                icon>
                <v-icon class="mt-1">
                  fa-times
                </v-icon>
              </btn>
            </template>
          </v-text-field>
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
        </v-col>
        <v-col
          v-if="showMobileFilter && isMobile"
          v-show="showWorkFilter"
          class="d-flex me-4 mb-n4"
          cols="12">
          <v-text-field
            class="pt-5 me-2 mb-1 workflow-filter-query float-e d-flex"
            @keyup="updateFilter"
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
    <empty-or-loading
      :loading="isLoading"
      v-if="canShowEmptyOrLoad">
      <template #empty>
        <div>
          <v-img
            width="280px"
            height="273px"
            src="/processes/images/noRequest.png" />
          <p
            class="mt-2">
            {{ $t('processes.work.noRequest.label') }}
          </p>
        </div>
      </template>
    </empty-or-loading>
    <v-expansion-panels
      class="mt-5"
      v-if="canShowPanels"
      v-model="panel"
      multiple>
      <v-expansion-panel
        v-if="draftList.length>0"
        class="elevation-0 ml-n5">
        <v-expansion-panel-header
          class="text-md-body-1 font-weight-regular grey--text text--darken-1">
          <div class="panel-custom-header">
            {{ this.$t('processes.myWorks.status.draft') }} ({{ draftList.length }})
          </div>
          <hr class="line-panel-work">
        </v-expansion-panel-header>
        <v-expansion-panel-content
          class="elevation-0 work-panel-content">
          <work
            v-for="draft in draftList"
            :is-draft="true"
            :key="draft.id"
            :work="draft" />
        </v-expansion-panel-content>
      </v-expansion-panel>
      <v-expansion-panel
        v-for="item in items"
        :key="item.status"
        class="elevation-0 ml-n5">
        <v-expansion-panel-header
          class="text-md-body-1 font-weight-regular grey--text text--darken-1">
          <div class="panel-custom-header">
            {{ statusI18n(item.status) }} ({{ item.works.length }})
          </div>
          <hr class="line-panel-work">
        </v-expansion-panel-header>
        <v-expansion-panel-content
          class="elevation-0 work-panel-content">
          <work
            v-for="work in item.works"
            :key="work.id"
            :work="work" />
        </v-expansion-panel-content>
      </v-expansion-panel>
      <v-expansion-panel
        v-if="completedWorkList.length>0"
        class="elevation-0 ml-n5">
        <v-expansion-panel-header
          class="text-md-body-1 font-weight-regular grey--text text--darken-1">
          <div class="panel-custom-header">
            {{ this.$t('label.task.completed') }} ({{ completedWorkList.length }})
          </div>
          <hr class="line-panel-work">
        </v-expansion-panel-header>
        <v-expansion-panel-content
          class="elevation-0 work-panel-content">
          <work
            v-for="work in completedWorkList"
            :is-draft="false"
            :key="work.id"
            :work="work" />
        </v-expansion-panel-content>
      </v-expansion-panel>
    </v-expansion-panels>
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
      panel: [],
      filter: {label: this.$t('processes.workflow.all.label'), value: null},
      query: null,
      MOBILE_WIDTH: 600,
      showMobileFilter: false,
      activatedFilters: [],
    };
  },
  props: {
    works: {
      type: Array,
      default: null,
    },
    workDrafts: {
      type: Array,
      default: null,
    },
    completedWorks: {
      type: Array,
      default: null,
    },
    loading: {
      type: Boolean,
      default: false
    },
    availableWorkStatuses: {
      type: Object,
      default: () => {
        return {};
      }
    },
    showWorkFilter: {
      type: Boolean,
    }
  },
  created() {
    this.$root.$on('work-draft-updated', (draft) => {
      const object = JSON.parse(draft);
      const index = this.draftList.map(object => object.id).indexOf(object.id);
      this.draftList.splice(index, 1, object);
    });
    this.$root.$on('work-draft-removed', (draft) => {
      const index = this.draftList.map(draft => draft.id).indexOf(draft.id);
      this.draftList.splice(index, 1);
    });
    this.$root.$on('work-removed', (work) => {
      this.workList.splice(this.workList.indexOf(work), 1);
    });
    this.$root.$on('work-canceled', (work) => {
      this.handleCompleted(work);
    });
    this.$root.$on('work-completed', (work) => {
      this.handleCompleted(work);
    });
    this.$root.$on('work-uncanceled', (work) => {
      const index = this.completedWorkList.map(work => work.id).indexOf(work.id);
      this.completedWorkList.splice(index, 1);
      work.completed = false;
    });
  },
  watch: {
    works() {
      this.initPanels();
    },
    workDrafts() {
      this.initPanels();
    }
  },
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.width < this.MOBILE_WIDTH;
    },
    draftList() {
      return this.workDrafts || [];
    },
    workList() {
      return this.works || [];
    },
    completedWorkList() {
      return this.completedWorks || [];
    },
    isLoading() {
      return this.loading || false;
    },
    items() {
      return this.groupByKey(this.workList, 'status');
    },
    filterItems() {
      const items = [];
      items.push({label: this.$t('processes.workflow.all.label'), value: null});
      if (this.availableWorkStatuses) {
        this.availableWorkStatuses.forEach((value) => {
          if (!items.find(object => object.value === value.name)) {
            items.push({label: this.statusI18n(value.name), value: value.name});
          }
        });
      }
      items.push({label: this.$t('processes.myWorks.status.draft'), value: 'drafts'});
      items.push({label: this.$t('label.task.completed'), value: 'completed'});
      return items;
    },
    canShowEmptyOrLoad() {
      return this.workList.length === 0 && this.draftList.length === 0 && this.completedWorkList.length === 0;
    },
    canShowPanels() {
      return this.workList.length>0 || this.draftList.length>0 || this.completedWorkList.length>0;
    }
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
    handleCompleted(work) {
      const index = this.workList.map(work => work.id).indexOf(work.id);
      this.workList.splice(index, 1);
      work.completed = true;
    },
    statusI18n(value){
      if (value === 'completed') {
        return this.$t('label.task.completed') || value;
      }
      const key = `tasks.status.${value}`;
      const translation = this.$t(key);
      return translation === key && value || translation;
    },
    groupByKey(arr, prop) {
      arr = this.sortWorks(arr);
      const statuses = [];
      const map = new Map(Array.from(arr, obj => [obj[prop], []]));
      arr.forEach(obj => map.get(obj[prop]).push(obj));
      for (const x of map.keys()) {
        statuses.push({status: x,works: map.get(x)});
      }
      return statuses;
    },
    statusList() {
      const statusList = new Set();
      this.workList.map(work => work.workFlow).map(workflow => workflow.statuses)
        .filter(statuses => statuses && statuses.length > 0).forEach(statuses => {
          statuses.forEach(status => {
            if (status) {
              statusList.add(status.name);
            }
          });
        });
      statusList.add('completed');
      return Array.from(statusList);
    },
    sortWorks(works) {
      const statusList = this.statusList();
      return works.sort((a, b) => statusList.indexOf(a['status']) - statusList.indexOf(b['status']));
    },
    updateFilter() {
      this.isLoading = true;
      this.$root.$emit('work-filter-changed', {status: this.filter.value, query: this.query});
    },
    initPanels() {
      window.setTimeout(() => {
        this.panel =  [...Array(this.statusList().length + 1).keys()].map(i => i);
      }, 200);
    }
  }
};
</script>