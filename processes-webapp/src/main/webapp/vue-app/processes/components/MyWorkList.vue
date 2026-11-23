<template>
  <div
    id="myWorks">
    <my-work-list-toolbar
      v-show="showWorkFilter"
      :key="toolbarKey"
      :available-work-statuses="availableWorkStatuses || []"
      :processes="processes"
      :total-count="totalCount"
      :drafts-count="draftList.length"
      :shown-count="shownCount"
      :hide-filters="isEmpty"
      :archivable-count="archivableCount"
      @change="filter = $event"
      @archive-all="$root.$emit('archive-finished-works', archivableCount)" />
    <div
      v-if="loading && !shownCount"
      class="d-flex justify-center py-10">
      <v-progress-circular color="primary" indeterminate />
    </div>
    <my-work-list-empty
      v-else-if="!shownCount"
      :never-requested="isEmpty"
      :filtered="!!(filter.query || filter.workflowId || filter.status)"
      @clear="clearFilters" />
    <my-requests-table
      v-else
      id="myRequestsList"
      :groups="groups"
      :options.sync="tableOptions" />
  </div>
</template>

<script>
import {CAPPED_GROUP_SIZE, GROUP_BY_NONE, GROUP_BY_PROCESS, GROUP_BY_STATUS, matchesQuery, processesOf, sortRecentFirst, statusesOf, workflowIdOf, isFinished} from '../js/MyRequestsFilters.js';
import MyWorkListUpdatesMixin from '../js/MyWorkListUpdatesMixin.js';

export default {
  mixins: [MyWorkListUpdatesMixin],
  created() {
    // Loads the modules whose name contains ProcessRequestDetailExtension, which register the rows'
    // summaries and the sent request's forms
    this.$utils.includeExtensions('ProcessRequestDetailExtension');
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
    /**
     * How many completed requests the user has, as the server counts them, while the completed
     * requests load page by page; null when they are not shown.
     */
    completedTotal: {
      type: Number,
      default: null,
    },
    /**
     * Whether older completed requests remain to load.
     */
    completedHasMore: {
      type: Boolean,
      default: false,
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
    filter: {query: null, workflowId: null, status: null, groupBy: GROUP_BY_NONE},
    toolbarKey: 0,
    // the table's sort, held here so it outlives the table between a loading and its rows
    tableOptions: {sortBy: ['date'], sortDesc: [true]},
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
     * Counts the user's requests and drafts as the toolbar shows them: the completed ones as the
     * server counts them while they load page by page.
     *
     * @returns {Number} the count
     */
    totalCount() {
      const completedCount = this.completedTotal == null ? this.completedWorkList.length : Math.max(this.completedTotal, this.completedWorkList.length);
      return this.draftList.length + this.workList.length + completedCount;
    },
    /**
     * Counts the open requests « Archive all » archives: the finished ones, decided or canceled.
     * Every open request is loaded, so the count is the server's.
     *
     * @returns {Number} the count
     */
    archivableCount() {
      return this.workList.filter(isFinished).length;
    },
    /**
     * Tells whether a text or a process filter is chosen, or the rows are sorted otherwise than
     * newest first: the completed requests then all load, so the filter or the order reads every
     * one of them.
     *
     * @returns {Boolean} true when a text or a process is chosen, or another order
     */
    narrowed() {
      return !!(this.filter.query || this.filter.workflowId) || this.sortedOtherwise;
    },
    /**
     * Tells whether the rows are sorted otherwise than newest first: the completed requests load
     * page by page, newest first, so any other order needs them all.
     *
     * @returns {Boolean} true unless the rows are sorted by date, newest first
     */
    sortedOtherwise() {
      const options = this.tableOptions;
      return !((options.sortBy[0] || 'date') === 'date' && !!options.sortDesc[0]);
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
     * Builds what the table shows: by default one flat list of every kept request, drafts included,
     * or, when a grouping is chosen, the status groups or the process groups.
     *
     * @returns {Array} the groups, as {key, label, works, isDraft, cap, hint}, or the flat list's
     *   single group, as {key, flat, works, drafts, hint}
     */
    groups() {
      if (this.filter.groupBy === GROUP_BY_STATUS) {
        return this.statusGroups;
      }
      if (this.filter.groupBy === GROUP_BY_PROCESS) {
        return this.processGroups;
      }
      const works = this.statusGroups.flatMap(group => group.works);
      if (!works.length) {
        return [];
      }
      return [{
        key: 'all',
        flat: true,
        works,
        drafts: new Set(this.draftList),
        hint: this.isDraftsOnlyProcess && this.$t('processes.myRequests.draftsOnly') || null,
        hasMore: this.statusGroups.some(group => group.hasMore),
      }];
    },
    /**
     * Builds the status groups: the drafts, each status label in the order the processes list
     * their statuses, then the completed requests, each filtered and sorted most recent first,
     * without the empty ones. Statuses shown with the same label are one group, and the status
     * filter keeps the group of the chosen status's label.
     *
     * @returns {Array} the groups, as {key, label, works, isDraft, cap, hint}
     */
    statusGroups() {
      const status = this.filter.status;
      const groups = [];
      if (!status || status === 'drafts') {
        groups.push({key: 'drafts', label: this.$t('processes.myWorks.status.draft'), works: this.draftList, isDraft: true});
      }
      if (status !== 'drafts' && status !== 'completed') {
        const chosenLabel = status && this.statusI18n(status);
        const namesByLabel = new Map();
        statusesOf(this.workList).forEach(name => {
          const label = this.statusI18n(name);
          namesByLabel.set(label, [...(namesByLabel.get(label) || []), name]);
        });
        namesByLabel.forEach((names, label) => {
          if (!chosenLabel || label === chosenLabel) {
            const works = this.workList.filter(work => names.includes(work.status));
            groups.push({
              key: `status-${names[0]}`,
              label,
              works,
              // a column whose requests are all closed shows its most recent ones first
              cap: works.length && works.every(isFinished) ? CAPPED_GROUP_SIZE : 0,
            });
          }
        });
      }
      if (!status || status === 'completed') {
        groups.push({
          key: 'completed',
          label: this.$t('label.task.completed'),
          works: this.completedWorkList,
          // the server's count while pages remain, unless a filter narrows the list
          total: this.narrowed || this.completedTotal == null ? null : Math.max(this.completedTotal, this.completedWorkList.length),
          hasMore: this.completedHasMore,
        });
      }
      groups.forEach(group => group.works = sortRecentFirst(group.works.filter(this.matches)));
      const shown = groups.filter(group => group.works.length || group.hasMore);
      const drafts = shown.find(group => group.isDraft);
      if (drafts && this.isDraftsOnlyProcess) {
        drafts.hint = this.$t('processes.myRequests.draftsOnly');
      }
      return shown;
    },
    /**
     * Builds the process groups: the requests and drafts the filters keep, one group per process,
     * sorted by title, each most recent first; the loading of older completed requests follows the
     * last group.
     *
     * @returns {Array} the groups, as {key, label, works, drafts, hasMore}
     */
    processGroups() {
      const works = this.statusGroups.flatMap(group => group.works);
      const drafts = new Set(this.draftList);
      const groups = processesOf(works).map(process => ({
        key: `process-${process.id}`,
        label: process.title,
        works: sortRecentFirst(works.filter(work => workflowIdOf(work) === process.id)),
        drafts,
      }));
      const withoutProcess = works.filter(work => !workflowIdOf(work));
      if (withoutProcess.length) {
        groups.push({key: 'process-none', label: this.$t('processes.myRequests.group.noProcess'), works: withoutProcess, drafts});
      }
      if (groups.length && this.statusGroups.some(group => group.hasMore)) {
        groups[groups.length - 1].hasMore = true;
      }
      return groups;
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
  watch: {
    /**
     * Loads every remaining completed request once a text or a process filter, or another order, is
     * chosen, so the filter or the order reads all of them, as it did when they loaded at once.
     *
     * @returns {void}
     */
    narrowed() {
      this.loadAllCompletedWhenNarrowed();
    },
    /**
     * Loads the remaining completed requests when a page arrives while a filter is chosen.
     *
     * @returns {void}
     */
    completedHasMore() {
      this.loadAllCompletedWhenNarrowed();
    },
  },
  methods: {
    /**
     * Asks the application for every remaining completed request while a text or a process
     * filter is chosen.
     *
     * @returns {void}
     */
    loadAllCompletedWhenNarrowed() {
      if (this.narrowed && this.completedHasMore) {
        this.$root.$emit('load-more-completed-works', true);
      }
    },
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
     * Resets the text, the process and the status filters, the grouping, and the toolbar that holds them.
     *
     * @returns {void}
     */
    clearFilters() {
      if (this.filter.status) {
        this.$root.$emit('work-filter-changed', {status: null});
      }
      // the grouping is no filter: the toolbar, mounted again, keeps the one this browser remembers
      this.filter = {query: null, workflowId: null, status: null, groupBy: this.filter.groupBy};
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
