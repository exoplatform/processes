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
  <div class="d-inline-flex">
    <v-menu
      v-model="showMenu"
      :disabled="isMobile"
      ref="menu"
      content-class="workflow-card-menu"
      transition="slide-x-reverse-transition"
      bottom
      offset-y
      left>
      <template #activator="{ on, attrs }">
        <v-btn
          :aria-label="$t('processes.workflow.card.actions')"
          :title="$t('processes.workflow.card.actions')"
          class="workflow-card-menu-button"
          small
          icon
          v-bind="attrs"
          v-on="on"
          @click.stop="openMobileDrawer">
          <v-icon size="16" class="icon-default-color">fa-ellipsis-v</v-icon>
        </v-btn>
      </template>
      <v-list dense>
        <v-list-item
          class="workflow-card-copy-link"
          @click="copyLink">
          <v-list-item-title class="subtitle-2">
            <v-icon size="13" class="processes-work-menu-icon me-1">
              mdi-link-variant
            </v-icon>
            {{ $t('processes.workflow.copy.link.label') }}
          </v-list-item-title>
        </v-list-item>
        <v-list-item
          class="workflow-card-edit"
          @click="editWorkflow">
          <v-list-item-title class="subtitle-2">
            <v-icon size="13" class="processes-work-menu-icon me-1">
              mdi-square-edit-outline
            </v-icon>
            {{ $t('processes.workflow.edit.label') }}
          </v-list-item-title>
        </v-list-item>
        <v-list-item
          class="workflow-card-toggle-enabled"
          @click="toggleEnabled">
          <v-list-item-title class="subtitle-2">
            <v-icon size="13" class="processes-work-menu-icon me-1">
              {{ workflow.enabled && 'mdi-close-circle-outline' || 'mdi-check-circle-outline' }}
            </v-icon>
            {{ workflow.enabled && $t('processes.workflow.disable.label') || $t('processes.workflow.enable.label') }}
          </v-list-item-title>
        </v-list-item>
        <v-list-item
          v-if="workflow.acl && workflow.acl.canDelete"
          class="workflow-card-delete"
          @click="deleteWorkflow">
          <v-list-item-title class="subtitle-2">
            <v-icon size="13" class="processes-work-menu-icon me-1">
              mdi-trash-can-outline
            </v-icon>
            {{ $t('processes.workflow.delete.label') }}
          </v-list-item-title>
        </v-list-item>
      </v-list>
    </v-menu>
    <workflow-enable-toggle
      ref="enableToggle"
      :workflow="workflow" />
    <process-mobile-action-drawer
      ref="mobileActionDrawer"
      :enabled="workflow.enabled"
      @toggleEnabled="toggleEnabled"
      @editWorkflow="editWorkflow"
      @deleteWorkflow="deleteWorkflow"
      @copyLink="copyLink" />
  </div>
</template>

<script>
import MenuCloseMixin from '../js/MenuCloseMixin.js';

export default {
  mixins: [MenuCloseMixin],
  props: {
    workflow: {
      type: Object,
      default: () => ({}),
    },
  },
  data: () => ({
    MOBILE_WIDTH: 600,
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.width < this.MOBILE_WIDTH;
    },
  },
  methods: {
    /**
     * Opens the bottom action drawer instead of the menu on a phone.
     *
     * @returns {void}
     */
    openMobileDrawer() {
      if (this.isMobile) {
        this.$refs.mobileActionDrawer.open();
      }
    },
    /**
     * Opens the process editor drawer on this process.
     *
     * @returns {void}
     */
    editWorkflow() {
      this.$root.$emit('open-workflow-drawer', {workflow: this.workflow, mode: 'edit_workflow'});
    },
    /**
     * Enables this process, or disables it once confirmed.
     *
     * @returns {void}
     */
    toggleEnabled() {
      this.$refs.enableToggle.toggle();
    },
    /**
     * Asks the confirmation of the deletion of this process.
     *
     * @returns {void}
     */
    deleteWorkflow() {
      this.$root.$emit('show-confirm-action', {model: this.workflow, reason: 'delete_workflow'});
    },
    /**
     * Copies the link that opens a new request on this process.
     *
     * @returns {void}
     */
    copyLink() {
      const inputTemp = $('<input>');
      $('body').append(inputTemp);
      inputTemp.val(`${window.location.href}/${this.workflow.id}/createRequest`).select();
      document.execCommand('copy');
      inputTemp.remove();
      this.$root.$emit('show-alert', {
        type: 'success',
        message: this.$t('processes.workflow.copy.link.success.message')
      });
    },
  },
};
</script>
