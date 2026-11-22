<template>
  <div id="workflows">
    <workflow-list-toolbar
      :is-processes-manager="isProcessesManager"
      :show-filter="showWorkflowFilter || allDisabled"
      :loading="loading || loadingMore"
      :categories="categories"
      @add="open"
      @change="applyFilter" />
    <v-container
      v-if="!loading && workflowList.length"
      id="processesCardList"
      class="pa-0 pt-2"
      fluid>
      <template v-if="grouped">
        <section
          v-for="group in categoryGroups"
          :id="`processesCategoryGroup-${group.category && group.category.id || 'none'}`"
          :key="group.key"
          class="processes-category-group mb-4">
          <h2 class="processes-category-group-title text-header text-truncate mb-1">
            {{ group.category && group.category.path.join(' › ') || $t('processes.workflow.filter.noCategory') }}
          </h2>
          <v-row class="mx-n2 my-0">
            <v-col
              v-for="workflow in group.workflows"
              :key="workflow.id"
              class="px-2 py-2"
              cols="12"
              sm="6"
              md="4"
              lg="4"
              xl="3">
              <workflow-card-item
                :is-processes-manager="isProcessesManager"
                :categories="categoriesOf(workflow)"
                :workflow="workflow" />
            </v-col>
          </v-row>
        </section>
      </template>
      <v-row v-else class="mx-n2 my-0">
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
            :categories="categoriesOf(workflow)"
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
      :loading="loading || checkingDisabled && !isFiltered">
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
import {categoriesOf, groupByCategory, isFiledUnder, loadCategories} from '../js/ProcessCategories.js';

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
    checkingDisabled: false,
    checkId: 0,
    grouped: false,
    categoryId: null,
    categories: new Map(),
    categoriesReadId: 0,
  }),
  computed: {
    workflowList() {
      return this.workflows || [];
    },
    categoryGroups() {
      return groupByCategory(this.workflowList, this.categories);
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
    this.$root.$on('workflow-categories-updated', this.refreshCategories);
    this.refreshCategories();
  },
  beforeDestroy() {
    this.$root.$off('workflow-updated', this.replaceWorkflow);
    this.$root.$off('workflow-removed', this.removeWorkflow);
    this.$root.$off('workflow-categories-updated', this.refreshCategories);
  },
  methods: {
    /**
     * Reads the categories the user may filter the processes by again: filing a process under a
     * category, or out of one, changes them. A category view then drops the processes the new tree
     * places outside the chosen category. Only the latest read applies, and only when it succeeds: two
     * saves in a row start two reads, which may answer in either order, and a failed read keeps the
     * tree the page holds.
     *
     * @returns {Promise} resolved once they are read
     */
    refreshCategories() {
      const readId = ++this.categoriesReadId;
      return loadCategories(this.$categoryService).then(categories => {
        if (readId !== this.categoriesReadId || !categories) {
          return;
        }
        this.categories = categories;
        this.dropOutsideChosenCategory();
      });
    },
    /**
     * Removes from a category view the processes no longer filed under the chosen category or one of
     * its subcategories, as the page's tree now places them.
     *
     * @returns {void}
     */
    dropOutsideChosenCategory() {
      if (!this.categoryId) {
        return;
      }
      for (let index = this.workflowList.length - 1; index >= 0; index--) {
        if (!this.isUnderChosenCategory(this.workflowList[index])) {
          this.workflowList.splice(index, 1);
        }
      }
    },
    /**
     * @param {Object} workflow a process
     * @returns {Array<Object>} the categories it is filed under, by name
     */
    categoriesOf(workflow) {
      return categoriesOf(workflow, this.categories);
    },
    /**
     * @param {Object} workflow a process
     * @returns {Boolean} whether the chosen category, or no category, lists it: filed under that
     *          category or one of its subcategories, as the server's filter reads it. A process filed
     *          under a category the page's tree doesn't hold, as one just used for the first time
     *          while the tree reloads, stays until the reloaded tree places it.
     */
    isUnderChosenCategory(workflow) {
      return !this.categoryId
        || isFiledUnder(workflow, this.categoryId, this.categories)
        || (workflow.categoryIds || []).some(id => !this.categories.has(id));
    },
    /**
     * Replaces an updated process in the list, or removes it when it no longer matches the selected
     * status or category. An answer that carries no categories, as the enable toggle's, keeps the
     * ones the list holds.
     *
     * @param {String} workflowJson the updated process, as JSON
     * @returns {void}
     */
    replaceWorkflow(workflowJson) {
      const workflow = JSON.parse(workflowJson);
      const index = this.workflowList.findIndex(item => item.id === workflow.id);
      if (index >= 0) {
        if (!Array.isArray(workflow.categoryIds)) {
          workflow.categoryIds = this.workflowList[index].categoryIds;
        }
        // a non-administrator's « I manage » and « All » views list enabled processes only
        const enabledOrAdministrator = this.isProcessesManager || workflow.enabled;
        const matches = {
          activated: workflow.enabled,
          deactivated: !workflow.enabled,
          manager: !!(workflow.acl && workflow.acl.canEdit) && enabledOrAdministrator,
        }[this.filter] ?? enabledOrAdministrator;
        const listed = matches !== false && this.isUnderChosenCategory(workflow);
        this.workflowList.splice(index, 1, ...(listed ? [workflow] : []));
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
     * the enabled ones, all exist as disabled processes. The empty state waits for the answer, so it
     * never says « No process yet » before knowing. An answer that a newer check replaced changes
     * nothing; one that fails ends the wait and keeps the previous answer, « No process yet » on a
     * first check.
     *
     * @returns {Promise} resolved once the answer is read
     */
    checkAllDisabled() {
      const checkId = ++this.checkId;
      this.checkingDisabled = true;
      return this.$processesService.getWorkFlows({enabled: false, manager: false}, 0, 1)
        .then(workflows => {
          if (checkId === this.checkId) {
            this.allDisabled = !!(workflows && workflows.length);
          }
        })
        .catch(() => null)
        .finally(() => {
          if (checkId === this.checkId) {
            this.checkingDisabled = false;
          }
        });
    },
    /**
     * Keeps the status and the category the toolbar selected, which decide whether an updated process
     * stays listed, and whether the processes are grouped by category.
     *
     * @param {Object} event the selected status, the category, the grouping, and whether the view
     *          differs from the default one
     * @returns {void}
     */
    applyFilter(event) {
      this.filter = event.filter;
      this.categoryId = event.categoryId || null;
      this.isFiltered = event.filtered;
      this.grouped = !!event.grouped;
    },
  },
};
</script>
