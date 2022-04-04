<template>
  <div
    id="myWorks">
    <v-container class="pa-0 work-controls">
      <v-row no-gutters>
        <v-col
          height="150">
          <v-select
            ref="filter"
            class="work-filter pt-5 me-9 float-e"
            v-model="filter"
            :items="filterItems"
            item-text="label"
            item-value="value"
            return-object
            @blur="$refs.filter.blur();"
            @change="updateFilter"
            dense
            outlined />
          <v-text-field
            class="work-filter-query me-4"
            @keyup="updateFilter"
            v-model="query"
            :placeholder="$t('processes.work.filter.query.placeholder')"
            prepend-inner-icon="mdi-filter" />
        </v-col>
      </v-row>
    </v-container>
    <empty-or-loading
      :loading="loading"
      v-if="canShowEmptyOrLoad">
      <template v-slot:empty>
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
        v-if="workDrafts.length>0"
        class="elevation-0 ml-n5">
        <v-expansion-panel-header
          class="text-md-body-1 font-weight-regular grey--text text--darken-1">
          <v-icon class="text-md-body-5" v-if="!panel.includes(0)">mdi-chevron-down</v-icon>
          <v-icon class="text-md-body-5" v-if="panel.includes(0)">mdi-chevron-up</v-icon>
          {{ this.$t('processes.myWorks.status.draft') }} ({{ workDrafts.length }})
          <hr class="line-panel-work">
          <template v-slot:actions>
            <v-icon />
          </template>
        </v-expansion-panel-header>
        <v-expansion-panel-content
          class="elevation-0 work-panel-content">
          <work
            v-for="draft in workDrafts"
            :is-draft="true"
            :key="draft.id"
            :work="draft" />
        </v-expansion-panel-content>
      </v-expansion-panel>
      <v-expansion-panel
        v-for="(item, index) in items"
        :key="item.status"
        class="elevation-0 ml-n5">
        <v-expansion-panel-header
          class="text-md-body-1 font-weight-regular grey--text text--darken-1">
          <v-icon class="text-md-body-5" v-if="!panel.includes(index + 1)">mdi-chevron-down</v-icon>
          <v-icon class="text-md-body-5" v-if="panel.includes(index + 1)">mdi-chevron-up</v-icon>
          {{ statusI18n(item.status) }} ({{ item.works.length }})
          <hr class="line-panel-work">
          <template v-slot:actions>
            <v-icon />
          </template>
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
        v-if="completedWorks.length>0"
        class="elevation-0 ml-n5">
        <v-expansion-panel-header
          class="text-md-body-1 font-weight-regular grey--text text--darken-1">
          <v-icon class="text-md-body-5" v-if="!panel.includes(items.length + 1)">mdi-chevron-down</v-icon>
          <v-icon class="text-md-body-5" v-if="panel.includes(items.length + 1)">mdi-chevron-up</v-icon>
          {{ this.$t('label.task.completed') }} ({{ completedWorks.length }})
          <hr class="line-panel-work">
          <template v-slot:actions>
            <v-icon />
          </template>
        </v-expansion-panel-header>
        <v-expansion-panel-content
          class="elevation-0 work-panel-content">
          <work
            v-for="work in completedWorks"
            :is-draft="false"
            :key="work.id"
            :work="work" />
        </v-expansion-panel-content>
      </v-expansion-panel>
    </v-expansion-panels>
  </div>
</template>

<script>
export default {
  data () {
    return {
      panel: [],
      filter: {label: this.$t('processes.workflow.all.label'), value: null},
      query: null
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
    }
  },
  created() {
    this.$root.$on('work-draft-updated', (draft) => {
      const object = JSON.parse(draft);
      const index = this.workDrafts.map(object => object.id).indexOf(object.id);
      this.workDrafts.splice(index, 1, object);
    });
    this.$root.$on('work-draft-removed', (draft) => {
      this.workDrafts.splice(this.workDrafts.indexOf(draft), 1);
    });
    this.$root.$on('work-removed', (work) => {
      this.works.splice(this.works.indexOf(work), 1);
    });
    this.$root.$on('work-canceled', (work) => {
      this.handleCompleted(work);
    });
    this.$root.$on('work-completed', (work) => {
      this.handleCompleted(work);
    });
    this.$root.$on('work-uncanceled', (work) => {
      const index = this.completedWorks.map(work => work.id).indexOf(work.id);
      this.completedWorks.splice(index, 1);
      work.completed = false;
      this.works.unshift(work);
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
    items() {
      return this.groupByKey(this.works, 'status');
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
      return this.works.length === 0 && this.workDrafts.length === 0 && this.completedWorks.length === 0;
    },
    canShowPanels() {
      return this.works.length>0 || this.workDrafts.length>0 || this.completedWorks.length>0;
    }
  },
  methods: {
    handleCompleted(work) {
      const index = this.works.map(work => work.id).indexOf(work.id);
      this.works.splice(index, 1);
      work.completed = true;
      this.completedWorks.unshift(work);
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
      this.works.map(work => work.workFlow).map(workflow => workflow.statuses)
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
      works.forEach(work => {
        if (work.completed) {
          work.status = 'completed';
        }
      });
      return works.sort((a, b) => statusList.indexOf(a['status']) - statusList.indexOf(b['status']));
    },
    updateFilter() {
      this.loading = true;
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