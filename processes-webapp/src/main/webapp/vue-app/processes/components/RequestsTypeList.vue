<template>
  <v-container>
    <v-row no-gutters>
      <v-col cols="12">
        <v-btn
          class="ml-1 mt-2 mb-4 btn-primary btn"
          dark
          large
          color="primary">
          {{ $t('processes.requests.label.addRequestType') }}
        </v-btn>
      </v-col>
    </v-row>
    <v-row no-gutters>
      <v-col
        xl="3"
        lg="3"
        md="3"
        sm="4"
        v-for="requestType in requestTypes"
        :key="requestType.id">
        <request-type-card-item
          :request-type="requestType" />
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
export default {
  props: {
    requestTypes: {
      type: Object,
      default: function() {
        return [];
      },
    },
    hasMore: {
      type: Boolean,
      default: false
    },
    loading: {
      type: Boolean,
      default: false
    }
  },
};
</script>