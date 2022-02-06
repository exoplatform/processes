<template>
  <v-app>
    <v-toolbar
      color="transparent"
      flat
      class="mt-4 pr-4 pl-4 mb-1">
      <v-tabs
        background-color="white"
        v-model="tab">
        <v-tabs-slider color="blue" />
        <v-tab>
          {{ $t('processes.toolbar.label.typeOfRequest') }}
        </v-tab>
        <v-tab>
          {{ $t('processes.toolbar.label.myRequests') }}
        </v-tab>
      </v-tabs>
    </v-toolbar>
    <v-card
      color="transparent"
      elevation="0"
      class="pr-4 pl-4">
      <v-tabs-items
        class="mt-2 pa-4"
        v-model="tab">
        <v-tab-item>
          <requests-type-list
            :request-types="requestTypes"
            :has-more="hasMoreTypes"
            :loading="loading" />
        </v-tab-item>
        <v-tab-item>
          <my-requests-list :requests="requests" :loading="loading" />
        </v-tab-item>
      </v-tabs-items>
    </v-card>
  </v-app>
</template>

<script>
export default {
  data () {
    return {
      tab: 0,
      requestTypes: [],
      requests: [],
      query: null,
      pageSize: 10,
      offset: 0,
      limit: 0,
      loading: false,
      hasMoreTypes: false,
    };
  },

  created() {
    this.getRequestTypes();
    this.refreshRequests(); 
  },
  methods: {
    getRequestTypes() {
      const filter = {};
      if (this.query) {
        filter.query = this.query;
      }
      const expand = '';
      this.limit = this.limit || this.pageSize;
      this.loading = true;
      return this.$processesService
        .getRequestTypes(filter, this.offset, this.limit + 1, expand)
        .then(requestTypes => {
          this.requestTypes = requestTypes || [];
          this.hasMoreTypes = requestTypes && requestTypes.length > this.limit;
        })
        .finally(() => this.loading = false);
    },
    refreshRequests() {
      const filter = {};
      if (this.query) {
        filter.query = this.query;
      }
      const expand = 'demandeType';
      this.limit = this.limit || this.pageSize;
      this.loading = true;
      return this.$processesService
        .getRequets(filter, 0, 0, expand)
        .then(requests => {
          this.requests = requests || [];
        })
        .finally(() => this.loading = false);
    },
  }
};
</script>
