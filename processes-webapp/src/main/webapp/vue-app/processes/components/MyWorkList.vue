<template>
  <div
    id="myWorks">
    <my-work-list-toolbar
      v-show="showWorkFilter"
      :key="toolbarKey"
      :available-work-statuses="availableWorkStatuses || []"
      :processes="processes"
      :total-count="allWorks.length"
      :drafts-count="draftList.length"
      :shown-count="shownCount"
      :hide-filters="isEmpty"
      @change="filter = $event" />
    <div
      v-if="loading && !allWorks.length"
      class="d-flex justify-center py-10">
      <v-progress-circular color="primary" indeterminate />
    </div>
    <my-work-list-empty
      v-else-if="!shownCount"
      :never-requested="isEmpty"
      :filtered="!!(filter.query || filter.workflowId || filter.status)"
      @clear="clearFilters" />
    <div
      v-else
      id="myRequestsList"
      class="px-1">
      <my-work-group
        v-for="group in groups"
        :key="group.key"
        :works="group.works"
        :label="group.label"
        :group-key="group.key"
        :is-draft="group.isDraft"
        :cap="group.cap"
        :hint="group.hint" />
    </div>
    <work-detail-drawer />
  </div>
</template>

<script>
import {CAPPED_GROUP_SIZE, CAPPED_STATUSES, matchesQuery, processesOf, sortRecentFirst, statusesOf, workflowIdOf} from '../js/MyRequestsFilters.js';
import MyWorkListUpdatesMixin from '../js/MyWorkListUpdatesMixin.js';

export default {
  mixins: [MyWorkListUpdatesMixin],
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
      type: Array,
      default: null,
    },
    showWorkFilter: {
      type: Boolean,
    }
  },
  data: () => ({
    filter: {query: null, workflowId: null, status: null},
    toolbarKey: 0,
  }),
  computed: {
    draftList() {
      return this.workDrafts || [];
    },
    workList() {
      return this.works || [];
    },
    completedWorkList() {
      return this.completedWorks || [];
    },
    /**
     * Lists every request and draft the application loaded, whatever the filters.
     *
     * @returns {Array} the requests and the drafts
     */
    allWorks() {
      return [...this.draftList, ...this.workList, ...this.completedWorkList];
    },
    /**
     * Lists the processes of the loaded requests, with their counts, for the process filter.
     *
     * @returns {Array} the processes, as {id, title, count}
     */
    processes() {
      return processesOf(this.allWorks);
    },
    /**
     * Tells whether the user has no request at all: none loaded, and none sent, completed ones included.
     *
     * @returns {Boolean} true when the user never made a request
     */
    isEmpty() {
      return !this.allWorks.length && !this.showWorkFilter;
    },
    /**
     * Builds the shown groups: the drafts, each status in the order the processes list them,
     * then the completed requests, each filtered and sorted most recent first, without the empty ones.
     *
     * @returns {Array} the groups, as {key, label, works, isDraft, cap, hint}
     */
    groups() {
      const status = this.filter.status;
      const groups = [];
      if (!status || status === 'drafts') {
        groups.push({key: 'drafts', label: this.$t('processes.myWorks.status.draft'), works: this.draftList, isDraft: true});
      }
      if (status !== 'drafts' && status !== 'completed') {
        statusesOf(this.workList).filter(name => !status || name === status).forEach(name => groups.push({
          key: name,
          label: this.statusI18n(name),
          works: this.workList.filter(work => work.status === name),
          cap: CAPPED_STATUSES.includes(name) ? CAPPED_GROUP_SIZE : 0,
        }));
      }
      if (!status || status === 'completed') {
        groups.push({key: 'completed', label: this.$t('label.task.completed'), works: this.completedWorkList});
      }
      groups.forEach(group => group.works = sortRecentFirst(group.works.filter(this.matches)));
      const shown = groups.filter(group => group.works.length);
      const drafts = shown.find(group => group.isDraft);
      if (drafts && this.isDraftsOnlyProcess) {
        drafts.hint = this.$t('processes.myRequests.draftsOnly');
      }
      return shown;
    },
    /**
     * Tells whether the chosen process holds drafts only among the loaded requests: no open request.
     *
     * @returns {Boolean} true when a process is chosen and none of its loaded requests was sent
     */
    isDraftsOnlyProcess() {
      const workflowId = this.filter.workflowId;
      return !!workflowId
        && this.draftList.some(work => workflowIdOf(work) === workflowId)
        && ![...this.workList, ...this.completedWorkList].some(work => workflowIdOf(work) === workflowId);
    },
    /**
     * Counts the requests and drafts the filters keep.
     *
     * @returns {Number} the shown count
     */
    shownCount() {
      return this.groups.reduce((count, group) => count + group.works.length, 0);
    },
  },
  methods: {
    /**
     * Tells whether a request matches the chosen process and the typed text.
     *
     * @param {Object} work the request or the draft
     * @returns {Boolean} true when the filters keep it
     */
    matches(work) {
      return (!this.filter.workflowId || workflowIdOf(work) === this.filter.workflowId)
        && matchesQuery(work, this.filter.query, this.statusI18n(work.status));
    },
    /**
     * Resets the text, the process and the status filters, and the toolbar that holds them.
     *
     * @returns {void}
     */
    clearFilters() {
      if (this.filter.status) {
        this.$root.$emit('work-filter-changed', {status: null});
      }
      this.filter = {query: null, workflowId: null, status: null};
      this.toolbarKey++;
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
  }
};
</script>
