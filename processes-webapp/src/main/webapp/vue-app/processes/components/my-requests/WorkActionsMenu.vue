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
  <!-- opens beside its button, so it never covers the button of the row below -->
  <v-menu
    v-model="showMenu"
    ref="menu"
    content-class="my-request-menu"
    offset-x
    left>
    <template #activator="{ on, attrs }">
      <v-btn
        :aria-label="$t('processes.myRequests.actions')"
        :title="$t('processes.myRequests.actions')"
        :small="small"
        class="my-request-menu-button"
        icon
        v-bind="attrs"
        v-on="on"
        @click.stop>
        <v-icon size="16" class="icon-default-color">fa-ellipsis-v</v-icon>
      </v-btn>
    </template>
    <v-list dense>
      <v-list-item
        v-if="!isDraft"
        class="my-request-copy-link"
        @click="copyLink">
        <v-list-item-title class="subtitle-2">
          <v-icon size="13" class="processes-work-menu-icon me-1">mdi-link-variant</v-icon>
          {{ $t('processes.workflow.copy.link.label') }}
        </v-list-item-title>
      </v-list-item>
      <v-list-item
        v-if="taskLink"
        :href="taskLink"
        class="my-request-open-task"
        target="_blank"
        rel="noopener">
        <v-list-item-title class="subtitle-2">
          <v-icon size="13" class="processes-work-menu-icon me-1">mdi-open-in-new</v-icon>
          {{ $t('processes.myRequests.openTask') }}
        </v-list-item-title>
      </v-list-item>
      <v-list-item
        v-if="canArchive"
        class="my-request-archive"
        @click="$emit('archive')">
        <v-list-item-title class="subtitle-2">
          <v-icon size="13" class="processes-work-menu-icon me-1">{{ work.completed && 'mdi-archive-arrow-up-outline' || 'mdi-archive-outline' }}</v-icon>
          {{ work.completed && $t('processes.work.unComplete.request.message') || $t('processes.work.complete.request.message') }}
        </v-list-item-title>
      </v-list-item>
      <v-list-item
        v-if="canCancel"
        class="my-request-cancel"
        @click="$emit('cancel')">
        <v-list-item-title class="subtitle-2">
          <v-icon size="13" class="processes-work-menu-icon me-1">mdi-close-circle-outline</v-icon>
          {{ $t('processes.myRequests.cancel') }}
        </v-list-item-title>
      </v-list-item>
      <v-list-item
        v-if="isDraft"
        class="my-request-delete-draft"
        @click="$emit('delete')">
        <v-list-item-title class="subtitle-2">
          <v-icon size="13" class="processes-work-menu-icon me-1">mdi-trash-can-outline</v-icon>
          {{ $t('processes.myRequests.deleteDraft') }}
        </v-list-item-title>
      </v-list-item>
    </v-list>
  </v-menu>
</template>

<script>
import MenuCloseMixin from '../../js/MenuCloseMixin.js';

export default {
  mixins: [MenuCloseMixin],
  props: {
    work: {
      type: Object,
      default: () => ({}),
    },
    isDraft: {
      type: Boolean,
      default: false,
    },
    allowCancel: {
      type: Boolean,
      default: false,
    },
    taskLink: {
      type: String,
      default: null,
    },
    // Vuetify's small icon button (28px), the nearest to the mockup's 30px row button; the drawer header keeps the default 36px
    small: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    /**
     * Tells whether the requester may archive the request, or unarchive it: a sent request
     * that is not decided (Validated or Refused).
     *
     * @returns {Boolean} true when Archive or Unarchive is offered
     */
    canArchive() {
      return !this.isDraft && !['Validated', 'Refused'].includes(this.work.status);
    },
    /**
     * Tells whether the request may still be canceled: sent, not done, not decided.
     *
     * @returns {Boolean} true when Cancel request is offered
     */
    canCancel() {
      return !this.isDraft && !this.work.completed && this.allowCancel;
    },
  },
  methods: {
    /**
     * Copies the link that opens this request's detail in « My requests ».
     *
     * @returns {void}
     */
    copyLink() {
      const spacePath = eXo.env.portal.spaceName
        ? `${eXo.env.portal.context}/g/:spaces:${eXo.env.portal.spaceGroup}/${eXo.env.portal.selectedNodeUri}`
        : `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}`;
      const inputTemp = $('<input>');
      $('body').append(inputTemp);
      inputTemp.val(`${window.location.origin}${spacePath}/processes/myRequests/requestDetails/${this.work.id}`).select();
      document.execCommand('copy');
      inputTemp.remove();
      this.$root.$emit('show-alert', {
        type: 'success',
        message: this.$t('processes.myRequests.copyLink.success'),
      });
    },
  },
};
</script>
