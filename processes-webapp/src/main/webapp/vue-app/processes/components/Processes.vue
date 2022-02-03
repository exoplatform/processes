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
          <requests-type-list />
        </v-tab-item>
        <v-tab-item>
          not yet implemented
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
    };
  },
};
</script>
