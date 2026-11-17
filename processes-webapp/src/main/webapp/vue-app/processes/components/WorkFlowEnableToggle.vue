<!--
Copyright (C) 2026 eXo Platform SAS.
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.
You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <exo-confirm-dialog
    ref="confirmDialog"
    :title="$t('processes.workflow.disable.confirm.title')"
    :message="confirmMessage"
    :ok-label="$t('processes.workflow.disable.label')"
    :cancel-label="$t('processes.workflow.cancel.label')"
    @ok="save(false)" />
</template>

<script>
export default {
  props: {
    workflow: {
      type: Object,
      default: () => ({}),
    },
  },
  data: () => ({
    saving: false,
  }),
  computed: {
    /**
     * Builds the confirmation, which the dialog renders as HTML: the process title is escaped.
     *
     * @returns {String} the message
     */
    confirmMessage() {
      const title = (this.workflow.title || '').replace(/[&<>"']/g, c => `&#${c.charCodeAt(0)};`);
      return this.$t('processes.workflow.disable.confirm.message', {0: `<strong>${title}</strong>`});
    },
  },
  methods: {
    /**
     * Enables a disabled process at once, or asks to confirm before disabling an enabled one.
     *
     * @returns {void}
     */
    toggle() {
      if (this.workflow.enabled) {
        this.$refs.confirmDialog.open();
      } else {
        this.save(true);
      }
    },
    /**
     * Saves the process with its new state, as the editor saves it: read again by its id, with
     * the list's illustration, which the read by id names by its id only, and no labels, so the
     * process keeps its stored ones. The list then drops or keeps the card for its filter.
     *
     * @param {Boolean} enabled the new state
     * @returns {Promise} resolved once saved and reported
     */
    save(enabled) {
      if (this.saving) {
        return Promise.resolve();
      }
      this.saving = true;
      return this.$processesService.getWorkflowById(this.workflow.id, '')
        .then(storedWorkflow => {
          const payload = Object.assign({}, storedWorkflow, {
            enabled,
            illustrativeAttachment: this.workflow.illustrativeAttachment,
          });
          delete payload.labels;
          delete payload.projectMode;
          return this.$processesService.updateWorkflow(payload);
        })
        .then(savedWorkflow => {
          if (savedWorkflow) {
            this.$root.$emit('workflow-updated', savedWorkflow);
          }
          this.$root.$emit('show-alert', {
            type: 'success',
            message: this.$t(enabled ? 'processes.workflow.enable.success' : 'processes.workflow.disable.success'),
          });
        })
        .catch(() => this.$root.$emit('show-alert', {
          type: 'error',
          message: this.$t('processes.workflow.update.error.message'),
        }))
        .finally(() => this.saving = false);
    },
  },
};
</script>
