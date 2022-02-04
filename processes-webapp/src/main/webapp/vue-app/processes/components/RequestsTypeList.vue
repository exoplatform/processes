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
        sm="4">
        <request-type-card-item
          title="test title"
          description="test description"
          status="1 Request in progress" />
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
export default {
};
</script>