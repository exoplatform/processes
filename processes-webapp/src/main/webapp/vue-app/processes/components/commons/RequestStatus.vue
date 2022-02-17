<template>
  <span>
    <v-chip
      v-if="type === 'chip'"
      class="ma-2"
      :color="statusColor"
      text-color="white">
      {{ status }}
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
    type: {
      type: String,
      default: 'chip'
    }
  },
  computed: {
    statusColor() {
      return this.status === 'accepted' || this.status === 'Done' ? 'green'
        : this.status === 'pending' || this.status === 'WaitingOn' ? 'orange'
          : this.status === 'refused' ? 'red' : 'grey';
    }
  },
};
</script>