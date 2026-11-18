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
  <span class="my-request-status d-inline-flex align-center text-no-wrap">
    <v-chip
      :color="color"
      :text-color="textColor"
      :x-small="xSmall"
      :small="!xSmall"
      class="my-request-status-chip">
      {{ label }}
    </v-chip>
    <v-tooltip v-if="isArchived" bottom>
      <template #activator="{ on, attrs }">
        <v-icon
          :aria-label="$t('processes.myRequests.archived')"
          class="my-request-archived-icon icon-default-color ms-1"
          size="16"
          v-bind="attrs"
          v-on="on">
          mdi-archive-outline
        </v-icon>
      </template>
      <span>{{ $t('processes.myRequests.archived') }}</span>
    </v-tooltip>
  </span>
</template>

<script>
export default {
  props: {
    status: {
      type: String,
      default: null,
    },
    isDraft: {
      type: Boolean,
      default: false,
    },
    isCompleted: {
      type: Boolean,
      default: false,
    },
    xSmall: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    /**
     * Tells whether the request is archived: completed without being canceled, whose chip says so.
     *
     * @returns {Boolean} true when the archive icon shows
     */
    isArchived() {
      return this.isCompleted && this.status !== 'Canceled';
    },
    /**
     * Reads the chip's label: « Draft », or the translated task status.
     *
     * @returns {String} the label
     */
    label() {
      if (this.isDraft) {
        return this.$t('processes.myWorks.status.draftLabel');
      }
      const key = `tasks.status.${this.status}`;
      const translation = this.$t(key);
      return translation === key && (this.status || '') || translation;
    },
    /**
     * Picks the chip's theme colour for the state: a draft is neutral, a decided request
     * success or error, a pending request or one in progress warning, any other status grey.
     *
     * @returns {String} a Vuetify theme colour
     */
    color() {
      if (this.isDraft) {
        return 'grey lighten-2';
      }
      switch (String(this.status || '').toLowerCase()) {
      case 'accepted':
      case 'done':
      case 'validated':
        return 'success';
      case 'request':
      case 'pending':
      case 'requestinprogress':
      case 'waitingon':
      case 'inprogress':
        return 'warning';
      case 'refused':
        return 'error';
      default:
        return 'grey';
      }
    },
    /**
     * Picks the label's colour: dark on the light draft chip, white on the others.
     *
     * @returns {String} a colour
     */
    textColor() {
      return this.isDraft ? 'grey darken-3' : 'white';
    },
  },
};
</script>
