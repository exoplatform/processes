<template>
  <span>
    <v-chip
      v-if="type === 'chip'"
      class="ma-2"
      :color="statusColor"
      text-color="white">
      <v-tooltip bottom>
        <template v-slot:activator="{ on, attrs }">
          <span
            v-bind="attrs"
            v-on="on"
            class="text-truncate work-status">
            {{ statusLabel }}
          </span>
        </template>
        <span>{{ statusLabel }}</span>
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
    type: {
      type: String,
      default: 'chip'
    }
  },
  computed: {
    statusColor() {
      switch (this.status.toLowerCase())
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
      return !this.isDraft ? this.statusI18n(this.status) : this.$t('processes.myWorks.status.draftLabel');
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