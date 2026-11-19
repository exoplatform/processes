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
  <div class="requestDrawerActions d-flex flex-nowrap justify-end align-center">
    <!-- « Save as draft » gives way to Submit: its label is cut with an ellipsis when the two do not fit, and the
         tooltip then shows it whole -->
    <v-tooltip :disabled="!draftLabelCut" top>
      <template #activator="{ on, attrs }">
        <v-btn
          id="saveRequestDraftButton"
          :loading="savingDraft"
          :disabled="!canSaveDraft"
          :aria-label="draftLabel"
          class="requestDrawerActionsDraft btn me-2"
          v-bind="attrs"
          v-on="on"
          @click="$emit('save-draft')">
          <v-icon size="16" class="flex-shrink-0 me-1">far fa-save</v-icon>
          <span ref="draftLabel" class="text-truncate">{{ draftLabel }}</span>
        </v-btn>
      </template>
      <span>{{ draftLabel }}</span>
    </v-tooltip>
    <v-tooltip :disabled="!requiredMissing" top>
      <template #activator="{ on, attrs }">
        <div
          class="flex-shrink-0"
          v-bind="attrs"
          v-on="on">
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
 * The request drawer's footer: « Save as draft » and Submit, on one line
 * on the right, Submit last. Submit keeps its size; the draft button's label
 * is cut when a language makes the two wider than the drawer. Submit waits
 * for every required value and every document to fill in, and says what is
 * left when it is held back.
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
  data: () => ({
    draftLabelCut: false,
    labelObserver: null,
  }),
  computed: {
    /**
     * @returns {String} the draft button's label, whole
     */
    draftLabel() {
      return this.$t('processes.work.saveDraft.label');
    },
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
  /**
   * Follows the draft label's width, so its tooltip shows only while the label is cut.
   *
   * @returns {void}
   */
  mounted() {
    this.measureDraftLabel();
    if (window.ResizeObserver && this.$refs.draftLabel) {
      this.labelObserver = new ResizeObserver(() => this.measureDraftLabel());
      this.labelObserver.observe(this.$refs.draftLabel);
    }
  },
  /**
   * Stops following the draft label's width.
   *
   * @returns {void}
   */
  beforeDestroy() {
    this.labelObserver?.disconnect();
  },
  methods: {
    /**
     * Tells whether the draft label is cut: its text is wider than the room it is given.
     *
     * @returns {void}
     */
    measureDraftLabel() {
      const label = this.$refs.draftLabel;
      this.draftLabelCut = !!label && label.scrollWidth > label.clientWidth;
    },
  },
};
</script>
