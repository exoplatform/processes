<template>
  <v-tooltip
    :disabled="!requestDisabledReason"
    bottom>
    <template #activator="{ on }">
      <v-card
        :id="`workflowCard${workflow.id}`"
        :class="cardClass"
        :aria-label="cardAriaLabel"
        :aria-disabled="requestDisabledReason ? 'true' : null"
        :ripple="!requestDisabledReason"
        class="workflow-card d-flex flex-column"
        role="button"
        tabindex="0"
        outlined
        v-on="on"
        @click.native="openRequest"
        @keydown.native.enter.self="openRequest"
        @keydown.native.space.self.prevent="openRequest">
        <div class="workflow-card-head d-flex align-center text-start ps-3 pe-2 pt-3">
          <process-avatar
            :avatar-url="avatarUrl"
            :icon="workflow.image"
            class="workflow-card-avatar flex-shrink-0 me-3"
            size="40" />
          <div
            :title="workflow.title"
            class="workflow-card-title flex-grow-1 font-weight-bold">
            {{ workflow.title }}
          </div>
          <div class="d-flex align-center flex-shrink-0 ms-1">
            <v-chip
              v-if="!workflow.enabled"
              class="workflow-card-disabled-chip"
              small
              outlined>
              {{ $t('processes.workflow.deactivated.label') }}
            </v-chip>
            <workflow-card-menu
              v-if="canManage"
              :workflow="workflow" />
          </div>
        </div>
        <div
          v-if="workflow.description"
          :title="workflow.description"
          class="workflow-card-description text-light-color px-3 pt-2">
          {{ workflow.description }}
        </div>
        <div
          v-else
          class="workflow-card-description text-light-color font-italic px-3 pt-2">
          {{ $t('processes.workflow.card.noDescription') }}
        </div>
        <div
          v-if="categories.length"
          :aria-label="$t('processes.workflow.card.categories')"
          class="workflow-card-categories d-flex flex-wrap px-3 pt-2"
          role="list">
          <v-chip
            v-for="category in categories"
            :key="category.id"
            :title="category.path.join(' › ')"
            class="workflow-card-category me-1 mb-1"
            role="listitem"
            x-small
            outlined>
            {{ category.name }}
          </v-chip>
        </div>
        <div
          v-if="showPending && openRequestsCount !== null"
          class="workflow-card-footer d-flex align-center mt-auto ps-3 pe-2 py-2">
          <v-tooltip bottom>
            <template #activator="{ on: linkOn }">
              <a
                :href="projectLink"
                class="workflow-card-open-requests caption text-light-color"
                target="_blank"
                rel="noopener"
                v-on="linkOn"
                @click.stop>
                <v-icon size="14" class="icon-default-color me-1">mdi-clock-time-four-outline</v-icon>
                {{ openRequestsLabel }}
              </a>
            </template>
            <span>{{ $t('processes.workflow.card.openInTasks') }}</span>
          </v-tooltip>
        </div>
      </v-card>
    </template>
    <span>{{ requestDisabledReason }}</span>
  </v-tooltip>
</template>

<script>
// The page's events that change how many open requests a process has
const REQUEST_EVENTS = ['work-added', 'work-canceled', 'work-uncanceled', 'work-completed', 'work-removed'];
// How long the count waits before it is read again, so the events of one action read it once
const RECOUNT_DELAY_MS = 300;

export default {
  props: {
    /** The categories the process is filed under, by name. */
    categories: {
      type: Array,
      default: () => [],
    },
    workflow: {
      type: Object,
      default: () => ({}),
    },
    isProcessesManager: {
      type: Boolean,
      default: false
    },
  },
  data: () => ({
    openRequestsCount: null,
  }),
  computed: {
    avatarUrl() {
      return this.workflow && this.workflow.illustrativeAttachment
                           && this.workflow.illustrativeAttachment.id
                           && `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/illustration/${this.workflow.id}?v=${this.workflow.illustrativeAttachment.lastUpdated}`;
    },
    canManage() {
      return this.isProcessesManager || (this.workflow.acl && this.workflow.acl.canEdit);
    },
    showPending() {
      return this.workflow && this.workflow.canShowPending;
    },
    requestDisabledReason() {
      if (!this.workflow.enabled) {
        return this.$t('processes.workflow.request.disabled.processDisabled');
      }
      if (!this.workflow.acl || !this.workflow.acl.canAddRequest) {
        return this.$t('processes.workflow.request.disabled.notAllowed');
      }
      return null;
    },
    openRequestsLabel() {
      return this.openRequestsCount === 1
        ? this.$t('processes.workflow.card.openRequest')
        : this.$t('processes.workflow.card.openRequests', {0: this.openRequestsCount});
    },
    cardClass() {
      return {
        'workflow-card-disabled': !this.workflow.enabled,
        'workflow-card-requestable': !this.requestDisabledReason,
        'hover-elevation': !this.requestDisabledReason,
      };
    },
    cardAriaLabel() {
      return this.requestDisabledReason
        ? `${this.workflow.title}: ${this.requestDisabledReason}`
        : this.$t('processes.workflow.card.makeRequest', {0: this.workflow.title});
    },
    projectLink() {
      return `${eXo.env.portal.context}/${eXo.env.portal.portalName}/tasks/projectDetail/${this.workflow.projectId}`;
    },
  },
  created() {
    if (this.showPending) {
      this.countOpenRequests();
      REQUEST_EVENTS.forEach(event => this.$root.$on(event, this.recountOnRequestChange));
    }
  },
  beforeDestroy() {
    REQUEST_EVENTS.forEach(event => this.$root.$off(event, this.recountOnRequestChange));
    window.clearTimeout(this.recountTimer);
  },
  methods: {
    /**
     * Reads the count again once a request of this process is sent, canceled, reopened, completed or deleted on the
     * page, the card staying shown meanwhile. A request that names no process may be one of this process's: the count
     * is read again then too.
     *
     * @param {Object} event the request, or for a sent one the event that carries it as work
     * @returns {void}
     */
    recountOnRequestChange(event) {
      const work = event && event.work || event;
      const workflowId = work && work.workFlow && work.workFlow.id;
      if (workflowId && String(workflowId) !== String(this.workflow.id)) {
        return;
      }
      window.clearTimeout(this.recountTimer);
      // Not reactive: the pending read of the count
      this.recountTimer = window.setTimeout(() => {
        this.recountTimer = null;
        this.countOpenRequests();
      }, RECOUNT_DELAY_MS);
    },
    /**
     * Opens the request drawer on this process, when the user may make a request on it.
     *
     * @returns {void}
     */
    openRequest() {
      if (!this.requestDisabledReason) {
        this.$root.$emit('open-add-work-drawer', {object: this.workflow, mode: 'create_work'});
      }
    },
    /**
     * Reads the number of open requests of the process, whatever task project they are in; the endpoint answers it as text.
     *
     * @returns {Promise} resolved once the count is read
     */
    countOpenRequests() {
      return this.$processesService.countRequests(this.workflow.id, false).then(value => {
        const count = Number(value);
        this.openRequestsCount = Number.isFinite(count) ? count : null;
      }).catch(() => this.openRequestsCount = null);
    },
  },
};
</script>
