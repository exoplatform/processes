<template>
  <v-tooltip
    :disabled="!requestDisabledReason"
    bottom>
    <template #activator="{ on, attrs }">
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
        v-bind="attrs"
        v-on="on"
        @click.native="openRequest"
        @keydown.native.enter.self="openRequest"
        @keydown.native.space.self.prevent="openRequest">
        <div class="d-flex align-center ps-3 pe-2 pt-3">
          <v-avatar
            :class="!showIllustration && 'workflow-card-avatar-placeholder primary--text'"
            class="workflow-card-avatar flex-shrink-0 me-3"
            size="40">
            <v-img
              v-if="showIllustration"
              :src="avatarUrl"
              @error="illustrationFailed = true" />
            <span
              v-else
              class="font-weight-bold">
              {{ initial }}
            </span>
          </v-avatar>
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
          v-if="showPending && openRequestsCount !== null"
          class="workflow-card-footer d-flex align-center mt-auto ps-3 pe-2 py-2">
          <v-tooltip bottom>
            <template #activator="{ on: linkOn, attrs: linkAttrs }">
              <a
                :href="projectLink"
                class="workflow-card-open-requests caption text-light-color"
                target="_blank"
                rel="noopener"
                v-bind="linkAttrs"
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
export default {
  props: {
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
    illustrationFailed: false,
  }),
  computed: {
    avatarUrl() {
      return this.workflow && this.workflow.illustrativeAttachment
                           && this.workflow.illustrativeAttachment.id
                           && `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/illustration/${this.workflow.id}?v=${this.workflow.illustrativeAttachment.lastUpdated}`;
    },
    showIllustration() {
      return this.avatarUrl && !this.illustrationFailed;
    },
    initial() {
      return (this.workflow.title || '').trim().charAt(0).toUpperCase();
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
  watch: {
    avatarUrl() {
      this.illustrationFailed = false;
    },
  },
  created() {
    if (this.showPending) {
      this.countOpenRequests();
    }
  },
  methods: {
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
     * Reads the number of uncompleted requests of the process's task project.
     *
     * @returns {Promise} resolved once the count is read
     */
    countOpenRequests() {
      return this.$processesService.countWorksByWorkflow(this.workflow.projectId, false).then(value => {
        this.openRequestsCount = value;
      });
    },
  },
};
</script>
