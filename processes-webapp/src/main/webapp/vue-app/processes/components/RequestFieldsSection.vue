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
  <div id="requestFieldsSection">
    <div v-if="!hideHeader" class="d-flex flex-wrap align-center mb-1">
      <span class="text-subtitle-1 font-weight-bold me-2">{{ $t('processes.request.section.title') }}</span>
      <span
        v-if="missingCount"
        class="d-flex align-center text-caption error--text me-2">
        <v-icon
          class="error--text me-1"
          size="6">
          fa-circle
        </v-icon>
        {{ $t('processes.request.section.missing', {0: missingCount}) }}
      </span>
      <span
        v-if="fill.total && fill.filled >= fill.total"
        class="d-flex align-center text-caption success--text">
        <v-icon class="success--text me-1" size="12">fa-check</v-icon>
        {{ $t('processes.request.fill.complete') }}
      </span>
      <span v-else-if="fill.total" class="text-caption text-light-color">
        {{ $t('processes.request.fill.count', {0: fill.filled, 1: fill.total}) }}
      </span>
    </div>
    <request-fill-status
      v-if="!hideHeader"
      :fill="fill"
      class="mb-3" />
    <slot></slot>
  </div>
</template>

<script>
/**
 * The request drawer's « Your request » section: its heading, which says
 * how many required values are missing and how many required items (the
 * required fields and the documents to fill in) are done, the fill track
 * (once something is done), then the fields: the form's, and the drawer's own
 * rows. In full screen (hideHeader), the left pane's checklist carries the
 * heading's count and the track.
 */
export default {
  props: {
    fill: {
      type: Object,
      default: () => ({total: 0, filled: 0, missing: []}),
    },
    // In full screen, the drawer's header carries the section's title, and the left pane its progress
    hideHeader: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    /** @returns {Number} how many required values are still missing */
    missingCount() {
      return (this.fill.missing || []).length;
    },
  },
};
</script>
