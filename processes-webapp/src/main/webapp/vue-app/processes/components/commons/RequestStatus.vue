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
      switch (this.status.toLowerCase())
      {
      case 'accepted':
      case 'done':
      case 'validated':
        return 'green';
      case 'pending':
      case 'waitingon':
      case 'inprogress':
        return 'orange';
      case 'refused':
        return 'red';
      default:
        return 'grey';
      }
    }
  },
};
</script>