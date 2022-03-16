<template>
  <div
    id="myWorks">
    <v-container class="pa-0">
      <v-row no-gutters>
        <v-col
          height="150">
          <v-select
            ref="filter"
            class="work-filter pt-5 me-9 float-right"
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
            class="work-filter-query me-3"
            @keyup="updateFilter"
            v-model="query"
            :placeholder="$t('processes.work.filter.query.placeholder')"
            prepend-inner-icon="mdi-filter" />
        </v-col>
      </v-row>
    </v-container>
    <empty-or-loading
      :loading="loading"
      v-if="works.length === 0 && workDrafts.length === 0">
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
      class="mt-0"
      v-if="works.length>0 || workDrafts.length>0"
      v-model="panel"
      multiple>
      <v-expansion-panel
        v-if="workDrafts.length>0"
        class="elevation-0 ml-n5">
        <v-expansion-panel-header
          class="text-md-body-1 font-weight-regular grey--text text--darken-1">
          <v-icon class="text-md-body-5" v-if="!panel.includes(0)">mdi-chevron-up</v-icon>
          <v-icon class="text-md-body-5" v-if="panel.includes(0)">mdi-chevron-down</v-icon>
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
          <v-icon class="text-md-body-5" v-if="!panel.includes(index + 1)">mdi-chevron-up</v-icon>
          <v-icon class="text-md-body-5" v-if="panel.includes(index + 1)">mdi-chevron-down</v-icon>
          {{ item.status }} ({{ item.works.length }})
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
    </v-expansion-panels>
  </div>
</template>

<script>
export default {
  data () {
    return {
      panel: [0, 1, 2],
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
      draft = JSON.parse(draft);
      const index = this.workDrafts.map(draft => draft.id).indexOf(draft.id);
      this.workDrafts.splice(index, 1, draft);
    });
    this.$root.$on('work-draft-removed', (draft) => {
      this.workDrafts.splice(this.workDrafts.indexOf(draft), 1);
    });
    this.$root.$on('work-removed', (work) => {
      this.works.splice(this.works.indexOf(work), 1);
    });
  },
  updated() {
    window.setTimeout(() => {
      this.panel = [0, 1, 2];
    }, 500);
  },
  computed: {
    items() {
      return this.groupByKey(this.works, 'status');
    },
    filterItems() {
      const items = [];
      items.push({label: this.$t('processes.workflow.all.label'), value: null});
      this.availableWorkStatuses.forEach((value) => {
        if (!items.find(object => object.value === value.name)) {
          items.push({label: this.statusI18n(value.name), value: value.name});
        }
      });
      items.push({label: this.$t('processes.myWorks.status.draft'), value: 'drafts'});
      return items;
    }
  },
  methods: {
    statusI18n(value){
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
      return Array.from(statusList);
    },
    sortWorks(works) {
      const statusList = this.statusList();
      return works.sort((a, b) => statusList.indexOf(a['status']) - statusList.indexOf(b['status']));
    },
    updateFilter() {
      this.loading = true;
      this.$root.$emit('work-filter-changed', {status: this.filter.value, query: this.query});
    }
  }
};
</script>