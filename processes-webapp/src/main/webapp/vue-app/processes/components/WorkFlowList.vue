<template>
  <v-main
    id="workflows">
    <v-container class="workflow-controls pb-0">
      <v-row
        class="pe-0"
        no-gutters>
        <v-col
          :cols="3"
          md="4"
          lg="6">
          <v-btn
            v-if="isProcessesManager"
            class="ml-1 mt-2 mb-4 btn-primary btn add-process-btn"
            dark
            @click="open"
            color="primary">
            <v-icon
              left
              dark>
              mdi-plus-thick
            </v-icon>
            <span v-if="!isMobile">
              {{ $t('processes.works.label.addRequestType') }}
            </span>
          </v-btn>
        </v-col>
        <v-col
          v-show="showWorkflowFilter"
          :cols="9"
          md="8"
          lg="6">
          <v-select
            v-if="isProcessesManager"
            ref="filter"
            class="pt-5 workflow-filter mt-n3 float-e"
            v-model="filter"
            :items="items"
            item-text="label"
            item-value="value"
            return-object
            @blur="$refs.filter.blur();"
            @change="updateFilter"
            dense
            outlined />
          <v-text-field
            v-if="!isXSmall"
            class="me-4 workflow-filter-query filter-query-width float-e"
            @keyup="filterByQuery"
            v-model="query"
            :placeholder="$t('processes.workflow.filter.query.placeholder')"
            prepend-inner-icon="mdi-filter" />
        </v-col>
      </v-row>
      <v-row v-if="isXSmall && showWorkflowFilter">
        <v-col
          class="d-flex"
          cols="12">
          <v-text-field
            class="me-10 workflow-filter-query float-e"
            @keyup="filterByQuery"
            v-model="query"
            :placeholder="$t('processes.workflow.filter.query.placeholder')"
            prepend-inner-icon="mdi-filter" />
        </v-col>
      </v-row>
    </v-container>
    <v-container
      v-if="!loading"
      no-gutters>
      <v-row
        v-if="workflowList.length>0"
        no-gutters>
        <v-col
          xl="3"
          :lg="lg"
          md="6"
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
  </v-main>
</template>

<script>
export default {
  data () {
    return {
      filter: {label: this.$t('processes.works.form.label.enabled'), value: true},
      items: [
        {label: this.$t('processes.workflow.activated.label'), value: true },
        {label: this.$t('processes.workflow.deactivated.label'), value: false },
        {label: this.$t('processes.workflow.all.label'), value: null},
      ],
      query: null,
      searchTimer: null,
      endTypingKeywordTimeout: 200,
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
    this.$root.$on('workflow-added', (event) => {
      if (event.workflow.enabled === event.filter) {
        this.workflowList.unshift(event.workflow);
      }
    });
    this.$root.$on('workflow-updated', (workflow) => {
      workflow = JSON.parse(workflow);
      const index = this.workflowList.map(workflow => workflow.id).indexOf(workflow.id);
      if (this.filter.value === workflow.enabled) {
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
    emptyWorkflow(){
      return !this.workflowList.length > 0 ; 
    },
    workflowList(){
      return this.workflows || [];
    },
    lg() {
      if (this.workflowList.length >= 4) {
        return 3;
      }
      return this.workflowList.length === 3 ? 4 : 6;
    },
    isXSmall() {
      return this.$vuetify.breakpoint.name === 'xs';
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    }
  },
  methods: {
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
    }
  }
};
</script>
