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
  <div class="d-flex flex-wrap align-center">
    <request-fill-status
      :fill="fill"
      :class="expanded ? 'me-4' : 'full-width mb-2'"
      variant="line" />
    <v-spacer />
    <v-btn
      id="saveRequestDraftButton"
      :loading="savingDraft"
      :disabled="!canSaveDraft"
      class="btn me-2"
      text
      @click="$emit('save-draft')">
      <v-icon size="16" class="me-1">far fa-save</v-icon>
      {{ $t('processes.work.saveDraft.label') }}
    </v-btn>
    <v-tooltip :disabled="!requiredMissing" top>
      <template #activator="{ on, attrs }">
        <div v-bind="attrs" v-on="on">
          <v-btn
            id="sendRequestButton"
            :loading="saving"
            :disabled="!!requiredMissing || !canSubmit"
            class="btn btn-primary"
            @click="$emit('submit')">
            {{ $t('processes.work.sendWork.label') }}
          </v-btn>
        </div>
      </template>
      <span>{{ submitTip }}</span>
    </v-tooltip>
  </div>
</template>

<script>
/**
 * The request drawer's footer: the line naming the required values still
 * missing, « Save as draft », and Submit, which waits for every required
 * value. The line takes the whole first row in the drawer and sits beside
 * the buttons in full screen.
 */
export default {
  props: {
    fill: {
      type: Object,
      default: () => ({total: 0, filled: 0, missing: []}),
    },
    expanded: {
      type: Boolean,
      default: false,
    },
    saving: {
      type: Boolean,
      default: false,
    },
    savingDraft: {
      type: Boolean,
      default: false,
    },
    canSaveDraft: {
      type: Boolean,
      default: false,
    },
    canSubmit: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    /**
     * @returns {Number} how many required values are still missing
     */
    requiredMissing() {
      return (this.fill.missing || []).length;
    },
    /**
     * @returns {String} the tooltip of a Submit held back by missing values
     */
    submitTip() {
      const key = this.requiredMissing > 1 ? 'processes.request.fill.submitTip' : 'processes.request.fill.submitTipOne';
      return this.$t(key, {0: this.requiredMissing});
    },
  },
};
</script>
