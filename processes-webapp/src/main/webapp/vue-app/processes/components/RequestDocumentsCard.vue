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
    class="mb-4"
    outlined>
    <div class="d-flex align-center px-3 pt-3">
      <v-icon size="18" class="icon-default-color me-2">fas fa-file-alt</v-icon>
      <span class="font-weight-bold">{{ $t('processes.request.documentToFill') }}</span>
      <v-spacer />
      <span v-if="hasDraft && documents.length > 1" class="text-caption text-light-color">
        {{ $t('processes.request.documentToFill.count', {0: filledCount, 1: documents.length}) }}
      </span>
    </div>
    <div :class="expanded && 'requestDocumentsCard-columns'" class="px-3 pb-1">
      <request-document-line
        v-for="attachment in documents"
        :key="attachment.id"
        :attachment="attachment"
        :name="nameOf(attachment)"
        :state="hasDraft ? state(attachment) : null"
        :filled="hasDraft && isFilled(attachment)"
        :original="hasDraft"
        @open="$emit('open', attachment)"
        @fill="$emit('fill', attachment)"
        @open-original="$emit('open-original', attachment)" />
    </div>
  </v-card>
</template>

<script>
/**
 * The request's documents to fill in, in one outlined card: before the draft
 * exists, the process's documents; then the request's copies, each with its
 * fill state, and how many are filled when there are several. In full screen
 * its rows are laid out in columns.
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
  },
  computed: {
    /** @returns {Number} how many copies the requester filled */
    filledCount() {
      return this.documents.filter(this.isFilled).length;
    },
  },
  methods: {
    /**
     * @param {Object} copy the request's copy of a process document
     * @returns {String} « Not started », or « Filled » with how long ago it was written
     */
    state(copy) {
      if (!this.isFilled(copy)) {
        return this.$t('processes.request.documentToFill.notStarted');
      }
      return this.$t('processes.request.documentToFill.filledAgo', {0: this.relativeTime(copy.updated)});
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
