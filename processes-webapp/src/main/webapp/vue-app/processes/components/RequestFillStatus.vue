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
    v-if="variant === 'bar' && fill.total"
    id="requestFillBar"
    class="d-flex align-center text-caption text-light-color mb-2">
    <span class="text-no-wrap me-2">{{ $t('processes.request.fill.count', {0: fill.filled, 1: fill.total}) }}</span>
    <v-progress-linear
      :value="filledPercent"
      color="primary"
      background-color="grey lighten-3"
      height="4"
      rounded />
  </div>
  <div
    v-else-if="variant === 'line' && fill.total"
    id="requestMissingLine"
    :class="missingCount ? 'error--text' : 'success--text'"
    :title="missingText"
    class="d-flex align-center text-caption text-truncate">
    <v-icon
      :class="missingCount ? 'error--text' : 'success--text'"
      class="me-1"
      size="14">
      {{ missingCount ? 'fas fa-exclamation-circle' : 'fas fa-check-circle' }}
    </v-icon>
    <span class="text-truncate">{{ missingText }}</span>
  </div>
</template>

<script>
/**
 * Shows how far the request's form is filled, from the fill state the
 * drawer's sections report ({total, filled, missing}): the bar counts the
 * fields holding a value, the line names the required values still missing,
 * or says the request is ready. Renders nothing when the request has no form.
 */
export default {
  props: {
    fill: {
      type: Object,
      default: () => ({total: 0, filled: 0, missing: []}),
    },
    variant: {
      type: String,
      default: 'bar',
    },
  },
  computed: {
    missingCount() {
      return (this.fill.missing || []).length;
    },
    filledPercent() {
      return this.fill.total ? Math.round(this.fill.filled * 100 / this.fill.total) : 0;
    },
    missingText() {
      if (!this.missingCount) {
        return this.$t('processes.request.fill.ready');
      }
      const key = this.missingCount > 1 ? 'processes.request.fill.missing' : 'processes.request.fill.missingOne';
      return this.$t(key, {0: this.missingCount, 1: this.fill.missing.map(field => field.label).join(', ')});
    },
  },
};
</script>
