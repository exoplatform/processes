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
      :class="tinted && 'my-request-status-chip-tinted'"
      :x-small="xSmall"
      :small="!xSmall"
      class="my-request-status-chip font-weight-bold"
      outlined>
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
    /**
     * Whether the requester canceled the request.
     */
    isCanceled: {
      type: Boolean,
      default: false,
    },
    /**
     * Whether the requester archived the request.
     */
    archived: {
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
     * Tells whether the archive icon shows: the requester archived the request.
     *
     * @returns {Boolean} true when the archive icon shows
     */
    isArchived() {
      return !this.isDraft && this.archived;
    },
    /**
     * Reads the chip's label: « Draft », « Canceled » for a request its requester canceled, else the
     * translated name of the column the request sits in, whose wording the board's owners choose.
     *
     * @returns {String} the label
     */
    label() {
      if (this.isDraft) {
        return this.$t('processes.myWorks.status.draftLabel');
      }
      if (this.isCanceledRequest) {
        // a cancel leaves the request in its column: the chip says the cancel
        return this.$t('processes.request.state.canceled');
      }
      const key = `tasks.status.${this.status}`;
      const translation = this.$t(key);
      return translation === key && (this.status || '') || translation;
    },
    /**
     * Picks the chip's colour for the request's state, never for its column, whose names the board's
     * owners choose: from the Material palette Vuetify ships, in a shade of each hue dark enough for
     * its 12px text to reach 4.5:1 on the chip's light tint over a white row. A draft neutral grey, a
     * pending request deep orange, a closed (decided) one blue, a canceled one grey.
     *
     * @returns {String} a Vuetify colour
     */
    color() {
      if (this.isDraft || this.isCanceledRequest) {
        return 'grey darken-2';
      }
      return this.isCompleted ? 'blue darken-4' : 'deep-orange darken-4';
    },
    /**
     * Tells whether the requester canceled the request, still closed.
     *
     * @returns {Boolean} true when canceled
     */
    isCanceledRequest() {
      return !this.isDraft && this.isCompleted && this.isCanceled;
    },
    /**
     * Tells whether the chip takes a light tint of its colour: every state but a canceled request,
     * which keeps an outline only.
     *
     * @returns {Boolean} true when the chip is tinted
     */
    tinted() {
      return !this.isCanceledRequest;
    },
  },
};
</script>
