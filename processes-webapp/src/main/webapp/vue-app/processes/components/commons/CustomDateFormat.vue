<template>
  <span>{{ message }} {{ formatDate(timestamp) }}</span>
</template>

<script>
export default {
  props: {
    timestamp: {
      type: Number,
      default: null
    },
    message: {
      type: String,
      default: ''
    }
  },
  methods: {
    formatDate(value) {
      return new Date(value).toLocaleDateString();
    }
  }
};
</script>