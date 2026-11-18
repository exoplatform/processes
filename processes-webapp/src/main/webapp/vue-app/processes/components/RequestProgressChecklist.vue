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
  <div
    v-if="items.length"
    id="requestProgressChecklist"
    class="text-start">
    <div class="text-subtitle-2 font-weight-bold mb-2">{{ $t('processes.request.checklist.title') }}</div>
    <div
      v-for="item in items"
      :key="item.id"
      :id="`requestProgress-${item.id}`"
      class="d-flex align-center text-body-2 mb-2">
      <v-icon
        :class="item.done ? 'success--text' : 'icon-default-color'"
        :aria-label="$t(item.done ? 'processes.request.checklist.done' : 'processes.request.checklist.todo')"
        class="me-2"
        size="16">
        {{ item.done ? 'fas fa-check-circle' : 'far fa-circle' }}
      </v-icon>
      <span class="text-truncate">{{ $t(item.label) }}</span>
      <v-spacer />
      <span class="text-caption text-light-color ms-2 text-no-wrap">
        {{ $t('processes.request.checklist.count', {0: item.filled, 1: item.total}) }}
      </span>
    </div>
    <request-fill-status :fill="fill" class="mt-1" />
  </div>
</template>

<script>
/**
 * The full-screen left pane's « Before you submit » checklist: the required
 * fields and the documents to fill in, each with its count and a check once
 * all are done, then the track of the required items. A part with nothing to
 * do is left out, and the checklist with it when both are.
 */
export default {
  props: {
    // The request's required items: {total, filled, fieldsTotal, fieldsFilled, documentsTotal, documentsFilled}
    fill: {
      type: Object,
      default: () => ({total: 0, filled: 0}),
    },
  },
  computed: {
    /** @returns {Array} the checklist's lines, each {id, label, filled, total, done} */
    items() {
      return [
        {id: 'fields', label: 'processes.request.checklist.fields', filled: this.fill.fieldsFilled || 0, total: this.fill.fieldsTotal || 0},
        {id: 'documents', label: 'processes.request.checklist.documents', filled: this.fill.documentsFilled || 0, total: this.fill.documentsTotal || 0},
      ].filter(item => item.total).map(item => Object.assign(item, {done: item.filled >= item.total}));
    },
  },
};
</script>
