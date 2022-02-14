<template>
  <v-container>
    <v-row no-gutters>
      <v-col
        class="mt-12 mb-12"
        cols="12"
        align="center"
        justify="center">
        <slot v-if="loading" name="loading"></slot>
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
