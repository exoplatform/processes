<template>
  <v-container align="center">
    <v-row no-gutters>
      <v-col
        class="mt-15 mb-12"
        cols="12"
        align="center"
        justify="center">
        <v-progress-circular
          v-if="loading"
          :size="50"
          color="primary"
          indeterminate />
        <slot v-if="!loading" name="empty"></slot>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
export default {
  props: {
    loading: {
      type: Boolean,
      default: false
    },
  }
};
</script>
