<template>
  <v-card
    class="ma-1"
    max-width="400"
    outlined>
    <v-btn
      outlined
      color="primary"
      class="mb-0"
      icon>
      <v-icon>mdi-information-outline</v-icon>
    </v-btn>
    <v-card-title
      class="text-center d-block mt-0 text-truncate">
      <v-avatar
        color="blue"
        size="30px">
        <v-icon dark>
          mdi-clock
        </v-icon>
      </v-avatar>
      {{ workflow.title }}
    </v-card-title>
    <v-card-text
      class="text-center">
      <p class="text-truncate-4">
        {{ workflow.description }}
      </p>
      <v-chip
        class="ma-2 text-truncate"
        color="orange"
        text-color="white">
        1 Request in progress
      </v-chip>
    </v-card-text>
    <v-divider />
    <v-card-actions
      class="d-inline-block work-card-actions">
      <v-btn
        class="float-right me-8"
        right
        plain
        depressed
        color="deep-grey lighten-3">
        {{ $t('processes.works.label.makeRequest') }}
      </v-btn>
    </v-card-actions>
  </v-card>
</template>

<script>
export default {
  props: {
    workflow: {
      type: Object,
      default: function() {
        return {};
      },
    }
  },
};
</script>