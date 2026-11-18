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
  <v-progress-linear
    v-if="fill.total && (fill.filled || showEmpty)"
    id="requestFillBar"
    :value="filledPercent"
    :aria-label="$t('processes.request.fill.count', {0: fill.filled, 1: fill.total})"
    class="mb-2"
    color="primary"
    background-color="grey lighten-3"
    height="4"
    rounded />
</template>

<script>
/**
 * Shows how far the request is filled, from the fill state the drawer
 * reports ({total, filled, missing}): a track of the values holding a value,
 * with no text since the count beside it already says « x of y required ».
 * Renders nothing while the total is 0, nor while nothing is filled, an
 * empty grey bar reading as a divider, unless showEmpty asks for the empty
 * track (the full-screen checklist, where its title frames it).
 */
export default {
  props: {
    fill: {
      type: Object,
      default: () => ({total: 0, filled: 0, missing: []}),
    },
    showEmpty: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    /**
     * @returns {Number} the share of the values holding a value, in percent
     */
    filledPercent() {
      return this.fill.total ? Math.round(this.fill.filled * 100 / this.fill.total) : 0;
    },
  },
};
</script>
