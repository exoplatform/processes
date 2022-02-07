<template>
  <v-main>
    <v-container v-if="loading">
      <v-row no-gutters>
        <v-col cols="12">
          <span>loading...</span>
        </v-col>
      </v-row>
    </v-container>
    <v-container v-if="!loading">
      <v-row no-gutters>
        <v-col cols="12">
          <v-btn
            class="ml-1 mt-2 mb-4 btn-primary btn"
            dark
            @click="open"
            color="primary">
            {{ $t('processes.works.label.addRequestType') }}
          </v-btn>
        </v-col>
      </v-row>
      <v-row no-gutters>
        <v-col
          xl="3"
          lg="3"
          md="3"
          sm="4"
          v-for="workflow in workflows"
          :key="workflow.id">
          <workflow-card-item
            :workflow="workflow" />
        </v-col>
      </v-row>
      <add-workflow-drawer ref="addWorkFlow" />
    </v-container>
  </v-main>
</template>

<script>
export default {
  props: {
    workflows: {
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
  methods: {
    open() {
      this.$refs.addWorkFlow.open();
    }
  }
};
</script>