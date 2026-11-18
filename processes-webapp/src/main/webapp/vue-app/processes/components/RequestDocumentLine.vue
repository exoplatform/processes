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
  <div class="requestDocumentLine d-flex align-center py-2">
    <v-icon
      :color="icon.color"
      class="flex-shrink-0 me-2"
      size="20"
      aria-hidden="true">
      {{ icon.class }}
    </v-icon>
    <div class="flex-grow-1 text-start" style="min-width: 0">
      <a
        class="requestDocumentLine-name text-color text-break"
        @click="$emit('open')">
        {{ title }}
      </a>
      <div class="d-flex flex-wrap align-center">
        <span class="text-caption text-light-color me-2">{{ meta }}</span>
        <v-chip
          v-if="state"
          :color="filled ? 'success' : ''"
          :outlined="!filled"
          class="requestDocumentLine-state"
          x-small>
          {{ state }}
        </v-chip>
      </div>
    </div>
    <v-btn
      class="requestFillDocumentButton flex-shrink-0 ms-2"
      color="primary"
      outlined
      small
      @click="$emit('fill')">
      {{ $t(filled ? 'processes.request.documentToFill.continue' : 'processes.request.documentToFill.fill') }}
    </v-btn>
    <v-menu
      v-if="original"
      bottom
      left
      offset-y>
      <template #activator="{ on, attrs }">
        <v-btn
          :aria-label="$t('processes.request.documentToFill.actions')"
          class="flex-shrink-0 ms-1"
          icon
          small
          v-bind="attrs"
          v-on="on">
          <v-icon size="16" class="icon-default-color">fas fa-ellipsis-v</v-icon>
        </v-btn>
      </template>
      <v-list dense>
        <v-list-item @click="$emit('open-original')">
          <v-list-item-title>{{ $t('processes.request.documentToFill.openOriginal') }}</v-list-item-title>
        </v-list-item>
      </v-list>
    </v-menu>
  </div>
</template>

<script>
const BYTES_IN_KB = 1024;
const BYTES_IN_MB = 1024 * 1024;

/**
 * One document to fill in, a row of the documents card: its type icon, the
 * one the platform's attachments give its type, its name, which opens it, its
 * size and type, its fill state as a chip once the request has its copy,
 * « Fill in » (« Continue » once filled) and, when the copy exists, a menu
 * opening the process's original.
 */
export default {
  props: {
    attachment: {
      type: Object,
      required: true,
    },
    // « Not started » or « Filled · 2 min ago », none before the request has its copy
    state: {
      type: String,
      default: null,
    },
    filled: {
      type: Boolean,
      default: false,
    },
    // True when the row is the request's copy, whose original the menu opens
    original: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    /** @returns {String} the document's name */
    title() {
      return this.attachment.title || this.attachment.name || '';
    },
    /** @returns {Object} the icon class and colour the platform gives the document's type */
    icon() {
      const extensions = this.$documentsIconsExtension && this.$documentsIconsExtension[0];
      const icon = extensions && (extensions.get(this.attachment.mimetype) || extensions.get('file'));
      return icon || {class: 'fas fa-file', color: ''};
    },
    /** @returns {String} the size and the type, joined by a dot */
    meta() {
      const extension = this.title.includes('.') ? this.title.split('.').pop().toUpperCase() : '';
      return [this.humanizedSize(), extension].filter(Boolean).join(' · ');
    },
  },
  methods: {
    /** @returns {String} the document's size in bytes, KB or MB */
    humanizedSize() {
      const size = Number(this.attachment.size);
      if (!size || size <= 0) {
        return '';
      }
      if (size < BYTES_IN_KB) {
        return `${size} B`;
      }
      return size < BYTES_IN_MB ? `${(size / BYTES_IN_KB).toFixed(1)} KB` : `${(size / BYTES_IN_MB).toFixed(1)} MB`;
    },
  },
};
</script>
