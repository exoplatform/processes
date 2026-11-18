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
  <v-card
    id="requestDocumentsCard"
    :class="expanded && 'requestDocumentsCard-wide'"
    class="requestDocumentsCard mb-4"
    outlined>
    <div class="requestDocumentsCard-head d-flex align-center px-3 py-2">
      <span class="font-weight-bold">{{ $t('processes.request.documentToFill') }}</span>
      <template v-if="!readonly && filledCount < documents.length">
        <v-icon
          class="error--text ms-1"
          size="6"
          aria-hidden="true">
          fa-circle
        </v-icon>
        <span class="d-sr-only">{{ $t('processes.request.documentToFill.required') }}</span>
      </template>
      <span class="ms-auto text-caption text-light-color text-no-wrap">
        {{ $t('processes.request.documentToFill.count', {0: filledCount, 1: documents.length}) }}
      </span>
    </div>
    <request-document-line
      v-for="attachment in documents"
      :key="attachment.id"
      :attachment="attachment"
      :name="nameOf(attachment)"
      :state="state(attachment)"
      :filled="(hasDraft || readonly) && isFilled(attachment)"
      :readonly="readonly"
      @open="$emit('open', attachment)"
      @fill="$emit('fill', attachment)"
      @open-original="$emit('open-original', attachment)" />
  </v-card>
</template>

<script>
/**
 * The request's documents to fill in, in one outlined card: its header says
 * « Document to fill in » and how many are filled, then one row per document,
 * which the request needs filled, so an unfilled one is flagged, and the
 * header carries the required dot Forms' labels show until all are filled,
 * before the draft exists the process's documents, then the request's copies,
 * each with its fill state. The rows are laid out in columns in full screen.
 */
export default {
  props: {
    documents: {
      type: Array,
      default: () => [],
    },
    hasDraft: {
      type: Boolean,
      default: false,
    },
    // Tells whether the requester wrote in a copy
    isFilled: {
      type: Function,
      default: () => false,
    },
    expanded: {
      type: Boolean,
      default: false,
    },
    // Gives the name a row shows: a copy shows its original's
    nameOf: {
      type: Function,
      default: () => null,
    },
    /** A sent request's documents: each row opens its copy, read-only, and says whether it was filled */
    readonly: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    /** @returns {Number} how many copies the requester filled */
    filledCount() {
      return this.hasDraft || this.readonly ? this.documents.filter(this.isFilled).length : 0;
    },
  },
  methods: {
    /**
     * @param {Object} copy the request's copy of a process document
     * @returns {String} « Not started », or « Filled » with how long ago it was written
     */
    state(copy) {
      if (this.readonly) {
        return this.isFilled(copy)
          ? this.$t('processes.request.documentToFill.filledAgo', {0: this.shortDate(copy.updated)})
          : this.$t('processes.request.documentToFill.notProvided');
      }
      if (!this.hasDraft || !this.isFilled(copy)) {
        return this.$t('processes.request.documentToFill.notStarted');
      }
      return this.$t('processes.request.documentToFill.filledAgo', {0: this.relativeTime(copy.updated)});
    },
    /**
     * Writes the day a sent request's copy was last written, as « 12 Nov ».
     *
     * @param {String|Number} date the copy's last update
     * @returns {String} the day and the month, in the viewer's language
     */
    shortDate(date) {
      return new Date(date).toLocaleDateString(eXo.env.portal.language || 'en', {day: 'numeric', month: 'short'});
    },
    /**
     * @param {String} date an ISO date
     * @returns {String} how long ago it was, in the user's language
     */
    relativeTime(date) {
      const seconds = Math.round((new Date(date).getTime() - Date.now()) / 1000);
      const format = new Intl.RelativeTimeFormat(eXo.env.portal.language || 'en', {numeric: 'auto'});
      const units = [['day', 86400], ['hour', 3600], ['minute', 60]];
      const unit = units.find(([, length]) => Math.abs(seconds) >= length);
      return unit ? format.format(Math.round(seconds / unit[1]), unit[0]) : format.format(seconds, 'second');
    },
  },
};
</script>
