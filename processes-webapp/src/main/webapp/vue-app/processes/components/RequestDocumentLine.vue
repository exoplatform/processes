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
  <div class="requestDocumentLine d-flex align-start py-1">
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
      <div class="text-caption text-light-color">{{ meta }}</div>
    </div>
    <button
      class="requestFillDocumentButton primary--text border-radius flex-shrink-0 px-2"
      type="button"
      @click="$emit('fill')">
      {{ $t('processes.request.documentToFill.fill') }}
    </button>
  </div>
</template>

<script>
const BYTES_IN_KB = 1024;
const BYTES_IN_MB = 1024 * 1024;

/**
 * One document to fill in, on a compact line: its type icon at a row icon's
 * size, its name, which opens it, its size, type and fill state in a caption,
 * and « Fill in » at the line's end, drawn as the form's « + Add ». The type
 * icon is the one the platform's attachments show.
 */
export default {
  props: {
    attachment: {
      type: Object,
      required: true,
    },
    // « Filled » or « Not filled yet », none before the request has its copy
    state: {
      type: String,
      default: null,
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
    /** @returns {String} the size, the type and the fill state, joined by a dot */
    meta() {
      const extension = this.title.includes('.') ? this.title.split('.').pop().toUpperCase() : '';
      return [this.humanizedSize(), extension, this.state].filter(Boolean).join(' · ');
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
