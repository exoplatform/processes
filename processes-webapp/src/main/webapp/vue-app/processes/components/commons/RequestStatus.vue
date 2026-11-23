<template>
  <div class="width-fit-content ma-auto">
    <v-chip
      v-if="type === 'chip' && !isCompleted"
      class="ma-2 d-flex"
      :color="statusColor"
      text-color="white">
      <v-tooltip
        bottom>
        <template #activator="{ on, attrs }">
          <span
            v-bind="attrs"
            v-on="on"
            class="text-truncate cursor-default">
            {{ translatedLabel }}
          </span>
        </template>
        <span>{{ translatedLabel }}</span>
      </v-tooltip>
    </v-chip>
    <div
      v-if="type === 'indicator'"
      :style="'background-color:' + statusColor"
      class="indicator position-absolute">
    </div>
  </div>
</template>

<script>
export default {
  props: {
    status: {
      type: String,
      default: null
    },
    isDraft: {
      type: Boolean,
      default: false
    },
    isCompleted: {
      type: Boolean,
      default: false
    },
    type: {
      type: String,
      default: 'chip'
    }
  },
  computed: {
    /**
     * Picks the colour for the request's state, never for its column, whose names the board's
     * owners choose: closed blue, pending orange.
     *
     * @returns {String} a colour
     */
    statusColor() {
      return this.isCompleted ? '#6596cd' : 'orange';
    },
    statusLabel() {
      return !this.isDraft ? this.status: this.$t('processes.myWorks.status.draftLabel');
    },
    translatedLabel() {
      return this.statusLabel && !this.isDraft && this.statusI18n(this.statusLabel) || this.statusLabel;
    }
  },
  methods: {
    statusI18n(value){
      const key = `tasks.status.${value}`;
      const translation = this.$t(key);
      return translation === key && value || translation;
    },
  }
};
</script>