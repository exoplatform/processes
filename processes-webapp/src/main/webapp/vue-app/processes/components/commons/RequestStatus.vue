<template>
  <span>
    <v-chip
      v-if="type === 'chip' && !isCompleted"
      class="ma-2"
      :color="statusColor"
      text-color="white">
      <v-tooltip
        bottom>
        <template #activator="{ on, attrs }">
          <span
            v-bind="attrs"
            v-on="on"
            class="text-truncate work-status">
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
  </span>
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
    statusColor() {
      if (this.isCompleted) {
        return '#6596cd';
      }
      switch (this.statusLabel.toLowerCase())
      {
      case 'accepted':
      case 'done':
      case 'validated':
        return 'green';
      case 'pending':
      case 'requestinprogress':
      case 'waitingon':
      case 'inprogress':
        return 'orange';
      case 'refused':
        return 'red';
      default:
        return 'grey';
      }
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