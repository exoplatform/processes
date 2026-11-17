<template>
  <div
    id="myWorks">
    <my-work-list-toolbar
      v-show="showWorkFilter"
      :available-work-statuses="availableWorkStatuses || []"
      :requests-count="workList.length + completedWorkList.length"
      :drafts-count="draftList.length"
      @change="filtered = $event.filtered" />
    <div
      v-if="isEmpty && isLoading"
      class="d-flex justify-center py-10">
      <v-progress-circular color="primary" indeterminate />
    </div>
    <div
      v-else-if="isEmpty"
      id="myRequestsEmptyState"
      class="d-flex flex-column align-center text-center text-light-color py-10 px-4">
      <v-avatar
        class="my-requests-empty-icon"
        size="120">
        <v-icon color="primary" size="56">mdi-inbox-outline</v-icon>
      </v-avatar>
      <h3 class="text-color text-h6 mt-4 mb-1">
        {{ filtered && $t('processes.myRequests.empty.noResult') || $t('processes.myRequests.empty.title') }}
      </h3>
      <p
        v-if="!filtered"
        class="my-requests-empty-description mb-0">
        {{ $t('processes.myRequests.empty.description') }}
      </p>
    </div>
    <div
      v-else
      id="myRequestsList"
      class="px-1">
      <my-work-group
        v-if="draftList.length"
        :works="draftList"
        :label="$t('processes.myWorks.status.draft')"
        group-key="drafts"
        is-draft />
      <my-work-group
        v-for="item in items"
        :key="item.status"
        :works="item.works"
        :label="statusI18n(item.status)"
        :group-key="item.status" />
      <my-work-group
        v-if="completedWorkList.length"
        :works="completedWorkList"
        :label="$t('label.task.completed')"
        group-key="completed" />
    </div>
    <work-detail-drawer />
  </div>
</template>

<script>
export default {
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
    filtered: false,
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
    isLoading() {
      return this.loading || false;
    },
    /**
     * Groups the sent requests by status, in the order the processes list their statuses.
     *
     * @returns {Array} the groups, as {status, works}
     */
    items() {
      return this.groupByKey(this.workList, 'status');
    },
    /**
     * Tells whether the list holds no request, draft or completed request.
     *
     * @returns {Boolean} true when nothing is listed
     */
    isEmpty() {
      return !this.workList.length && !this.draftList.length && !this.completedWorkList.length;
    },
  },
  created() {
    this.$root.$on('work-draft-updated', this.replaceDraft);
    this.$root.$on('work-draft-removed', this.removeDraft);
    this.$root.$on('work-removed', this.removeWork);
    this.$root.$on('work-canceled', this.handleCompleted);
    this.$root.$on('work-completed', this.handleCompleted);
    this.$root.$on('work-uncanceled', this.handleUncompleted);
  },
  beforeDestroy() {
    this.$root.$off('work-draft-updated', this.replaceDraft);
    this.$root.$off('work-draft-removed', this.removeDraft);
    this.$root.$off('work-removed', this.removeWork);
    this.$root.$off('work-canceled', this.handleCompleted);
    this.$root.$off('work-completed', this.handleCompleted);
    this.$root.$off('work-uncanceled', this.handleUncompleted);
  },
  methods: {
    /**
     * Replaces a saved draft in the list.
     *
     * @param {String} draft the saved draft, as JSON
     * @returns {void}
     */
    replaceDraft(draft) {
      const object = JSON.parse(draft);
      const index = this.draftList.findIndex(item => item.id === object.id);
      if (index >= 0) {
        this.draftList.splice(index, 1, object);
      }
    },
    /**
     * Removes a deleted draft from the list.
     *
     * @param {Object} draft the deleted draft
     * @returns {void}
     */
    removeDraft(draft) {
      const index = this.draftList.findIndex(item => item.id === draft.id);
      if (index >= 0) {
        this.draftList.splice(index, 1);
      }
    },
    /**
     * Removes a deleted request from the list.
     *
     * @param {Object} work the deleted request
     * @returns {void}
     */
    removeWork(work) {
      const index = this.workList.indexOf(work);
      if (index >= 0) {
        this.workList.splice(index, 1);
      }
    },
    /**
     * Takes a request that was canceled or archived out of its status group.
     *
     * @param {Object} work the request
     * @returns {void}
     */
    handleCompleted(work) {
      const index = this.workList.findIndex(item => item.id === work.id);
      if (index >= 0) {
        this.workList.splice(index, 1);
      }
      work.completed = true;
    },
    /**
     * Takes an unarchived request out of the completed group.
     *
     * @param {Object} work the request
     * @returns {void}
     */
    handleUncompleted(work) {
      const index = this.completedWorkList.findIndex(item => item.id === work.id);
      if (index >= 0) {
        this.completedWorkList.splice(index, 1);
      }
      work.completed = false;
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
    /**
     * Groups requests by a property, in the order of the statuses list.
     *
     * @param {Array} arr the requests
     * @param {String} prop the property to group by
     * @returns {Array} the groups, as {status, works}
     */
    groupByKey(arr, prop) {
      const statusList = this.statusList();
      const sorted = arr.slice().sort((a, b) => statusList.indexOf(a[prop]) - statusList.indexOf(b[prop]));
      const map = new Map(sorted.map(obj => [obj[prop], []]));
      sorted.forEach(obj => map.get(obj[prop]).push(obj));
      return Array.from(map.keys()).map(status => ({status, works: map.get(status)}));
    },
    /**
     * Lists the statuses of the requests' processes, in their order, then « completed ».
     *
     * @returns {Array} the status names
     */
    statusList() {
      const statusList = new Set();
      this.workList.map(work => work.workFlow).filter(workflow => workflow && workflow.statuses)
        .forEach(workflow => workflow.statuses.forEach(status => status && statusList.add(status.name)));
      statusList.add('completed');
      return Array.from(statusList);
    },
  }
};
</script>
