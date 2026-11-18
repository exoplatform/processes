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
    <v-spacer />
    <v-btn
      id="saveRequestDraftButton"
      :loading="savingDraft"
      :disabled="!canSaveDraft"
      class="btn me-2"
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
 * The request drawer's footer: « Save as draft » and Submit, on the right.
 * Submit waits for every required value and every document to fill in, and
 * says what is left when it is held back.
 */
export default {
  props: {
    fill: {
      type: Object,
      default: () => ({total: 0, filled: 0, missing: []}),
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
     * @returns {Number} how many required items are still missing: the required values and the documents to fill in
     */
    requiredMissing() {
      return this.fill.requiredMissing ?? (this.fill.missing || []).length;
    },
    /**
     * @returns {Number} how many documents to fill in are still unfilled
     */
    documentsMissing() {
      return (this.fill.documentsTotal || 0) - (this.fill.documentsFilled || 0);
    },
    /**
     * @returns {String} the tooltip of a Submit held back: the document alone, the value alone, or how many items
     */
    submitTip() {
      if (this.requiredMissing === 1) {
        return this.$t(this.documentsMissing ? 'processes.request.fill.submitTipDocument' : 'processes.request.fill.submitTipOne');
      }
      return this.$t('processes.request.fill.submitTip', {0: this.requiredMissing});
    },
  },
};
</script>
