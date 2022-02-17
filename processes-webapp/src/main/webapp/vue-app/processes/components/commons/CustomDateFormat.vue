<template>
  <span>{{ formatDate(timestamp) }}</span>
</template>

<script>
export default {
  props: {
    timestamp: {
      type: Number,
      default: null
    }
  },
  methods: {
    formatDate(value) {
      return new Date(value).toLocaleDateString();
    }
  }
};
</script>