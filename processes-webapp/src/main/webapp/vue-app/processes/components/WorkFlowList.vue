<template>
  <div id="workflows">
    <workflow-list-toolbar
      :is-processes-manager="isProcessesManager"
      :show-filter="showWorkflowFilter || allDisabled"
      :loading="loading || loadingMore"
      @add="open"
      @change="applyFilter" />
    <v-container
      v-if="!loading && workflowList.length"
      id="processesCardList"
      class="pa-0 pt-2"
      fluid>
      <v-row class="mx-n2 my-0">
        <v-col
          v-for="workflow in workflowList"
          :key="workflow.id"
          class="px-2 py-2"
          cols="12"
          sm="6"
          md="4"
          lg="4"
          xl="3">
          <workflow-card-item
            :is-processes-manager="isProcessesManager"
            :workflow="workflow" />
        </v-col>
      </v-row>
      <v-btn
        v-if="hasMore"
        id="processesLoadMoreButton"
        :loading="loadingMore"
        class="btn text-none mt-2"
        block
        @click="loadMore">
        {{ $t('processes.load.more.label') }}
      </v-btn>
    </v-container>
    <empty-or-loading
      v-else
      :loading="loading">
      <template #empty>
        <workflow-empty-state
          :can-add="isProcessesManager"
          :filtered="isFiltered"
          :all-disabled="allDisabled"
          @add="open" />
      </template>
    </empty-or-loading>
  </div>
</template>

<script>
export default {
  props: {
    workflows: {
      type: Array,
      default: () => [],
    },
    hasMore: {type: Boolean, default: false},
    loading: {type: Boolean, default: false},
    loadingMore: {type: Boolean, default: false},
    isProcessesManager: {type: Boolean, default: false},
    showWorkflowFilter: {type: Boolean, default: false},
  },
  data: () => ({
    filter: null,
    isFiltered: false,
    allDisabled: false,
  }),
  computed: {
    workflowList() {
      return this.workflows || [];
    },
    showsEmptyDefaultView() {
      return this.isProcessesManager && !this.loading && !this.workflowList.length && !this.isFiltered;
    },
  },
  watch: {
    showsEmptyDefaultView: {
      immediate: true,
      handler(value) {
        if (value) {
          this.checkAllDisabled();
        }
      },
    },
  },
  created() {
    this.filter = this.isProcessesManager ? 'activated' : null;
    this.$root.$on('workflow-updated', this.replaceWorkflow);
    this.$root.$on('workflow-removed', this.removeWorkflow);
  },
  beforeDestroy() {
    this.$root.$off('workflow-updated', this.replaceWorkflow);
    this.$root.$off('workflow-removed', this.removeWorkflow);
  },
  methods: {
    /**
     * Replaces an updated process in the list, or removes it when it no longer matches the selected filter.
     *
     * @param {String} workflowJson the updated process, as JSON
     * @returns {void}
     */
    replaceWorkflow(workflowJson) {
      const workflow = JSON.parse(workflowJson);
      const index = this.workflowList.findIndex(item => item.id === workflow.id);
      if (index >= 0) {
        const matches = {
          activated: workflow.enabled,
          deactivated: !workflow.enabled,
          manager: !!(workflow.acl && workflow.acl.canEdit),
        }[this.filter];
        this.workflowList.splice(index, 1, ...(matches === false ? [] : [workflow]));
      }
    },
    /**
     * Removes a deleted process from the list.
     *
     * @param {Object} workflow the removed process
     * @returns {void}
     */
    removeWorkflow(workflow) {
      const index = this.workflowList.indexOf(workflow);
      if (index >= 0) {
        this.workflowList.splice(index, 1);
      }
    },
    /**
     * Opens the process editor drawer to create a process.
     *
     * @returns {void}
     */
    open() {
      this.$root.$emit('open-workflow-drawer', {workflow: null, mode: 'create_workflow'});
    },
    /**
     * Asks the application for the next page of processes.
     *
     * @returns {void}
     */
    loadMore() {
      this.$root.$emit('load-more-workflows');
    },
    /**
     * Tells whether the processes an administrator does not see on the default view, which lists
     * the enabled ones, all exist as disabled processes.
     *
     * @returns {Promise} resolved once the answer is read
     */
    checkAllDisabled() {
      return this.$processesService.getWorkFlows({enabled: false, manager: false}, 0, 1).then(workflows => {
        this.allDisabled = !!(workflows && workflows.length);
      });
    },
    /**
     * Keeps the status the toolbar selected, which decides whether an updated process stays listed.
     *
     * @param {Object} event the selected status and whether it differs from the default view
     * @returns {void}
     */
    applyFilter(event) {
      this.filter = event.filter;
      this.isFiltered = event.filtered;
    },
  },
};
</script>
