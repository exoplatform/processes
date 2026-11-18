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
  <div class="requestDocumentLine">
    <v-icon
      :color="icon.color"
      class="requestDocumentLine-icon"
      size="20"
      aria-hidden="true">
      {{ icon.class }}
    </v-icon>
    <a
      :title="title"
      class="requestDocumentLine-name text-body-2 text-color"
      @click="$emit('open')">
      {{ title }}
    </a>
    <span class="requestDocumentLine-meta text-caption text-light-color">{{ meta }}</span>
    <v-chip
      :class="stateClass"
      class="requestDocumentLine-state"
      small>
      {{ state }}
    </v-chip>
    <v-btn
      class="requestFillDocumentButton requestDocumentLine-button font-weight-bold"
      color="primary"
      outlined
      @click="$emit(readonly ? 'open' : 'fill')">
      {{ $t(buttonLabelKey) }}
    </v-btn>
    <v-menu
      ref="menu"
      v-model="showMenu"
      bottom
      left
      offset-y>
      <template #activator="{ on, attrs }">
        <v-btn
          :aria-label="$t('processes.request.documentToFill.actions')"
          class="requestDocumentLine-menu"
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
import MenuCloseMixin from '../js/MenuCloseMixin.js';

const BYTES_IN_KB = 1024;
const BYTES_IN_MB = 1024 * 1024;

/**
 * One document to fill in, a row of the documents card: its type icon, the
 * one the platform's attachments give its type, its name, which opens it, its
 * size and type, its fill state as a chip, « Fill in » (« Continue » once
 * filled) and a menu opening the process's original, which is the row's own
 * document until the request has its copy.
 */
export default {
  mixins: [MenuCloseMixin],
  props: {
    attachment: {
      type: Object,
      required: true,
    },
    // « Not started » or « Filled · 2 min ago »
    state: {
      type: String,
      default: '',
    },
    filled: {
      type: Boolean,
      default: false,
    },
    // The name shown, the original's for a request's copy, whose own title carries the requester and the date
    name: {
      type: String,
      default: null,
    },
    /** A sent request's copy: the button opens it, read-only, rather than filling it */
    readonly: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    /**
     * Picks the button's label: « Open » on a sent request, else « Continue » once filled, or « Fill in ».
     *
     * @returns {String} the label's key
     */
    buttonLabelKey() {
      if (this.readonly) {
        return 'processes.request.documentToFill.open';
      }
      return this.filled ? 'processes.request.documentToFill.continue' : 'processes.request.documentToFill.fill';
    },
    /** @returns {String} the document's name */
    title() {
      return this.name || this.attachment.title || this.attachment.name || '';
    },
    /** @returns {Object} the icon class and colour the platform gives the document's type */
    icon() {
      const extensions = this.$documentsIconsExtension && this.$documentsIconsExtension[0];
      const icon = extensions && (extensions.get(this.attachment.mimetype) || extensions.get('file'));
      return icon || {class: 'fas fa-file', color: ''};
    },
    /** @returns {String} the chip's colour: green once filled, the error colour while the required document is not */
    stateClass() {
      return this.filled ? 'requestDocumentLine-tinted success--text' : 'requestDocumentLine-tinted error--text';
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
