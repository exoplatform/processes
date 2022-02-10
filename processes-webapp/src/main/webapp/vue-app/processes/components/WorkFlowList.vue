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
      <v-row
        v-if="isProcessesManager"
        no-gutters>
        <v-col cols="6">
          <v-btn
            class="ml-1 mt-2 mb-4 btn-primary btn"
            dark
            @click="open"
            color="primary">
            <v-icon
              left
              dark>
              mdi-plus-thick
            </v-icon>
            {{ $t('processes.works.label.addRequestType') }}
          </v-btn>
        </v-col>
        <v-col
          cols="6">
          <v-select
            width="100px"
            ref="filter"
            class="workflow-filter pt-5"
            v-model="filter"
            :items="items"
            item-text="label"
            item-value="value"
            return-object
            @blur="$refs.filter.blur();"
            @change="updateFilter"
            dense
            outlined />
        </v-col>
      </v-row>
      <v-row no-gutters>
        <v-col
          xl="3"
          :lg="lg"
          md="6"
          cols="12"
          v-for="workflow in workflows"
          :key="workflow.id">
          <workflow-card-item
            :is-processes-manager="isProcessesManager"
            :workflow="workflow" />
        </v-col>
      </v-row>
      <add-workflow-drawer ref="addWorkFlow" />
      <add-work-drawer ref="addWork" />
    </v-container>
  </v-main>
</template>

<script>
export default {
  data () {
    return {
      filter: {label: this.$t('processes.works.form.label.enabled'), value: true},
      items: [
        {label: this.$t('processes.works.form.label.enabled'), value: true },
        {label: this.$t('processes.works.form.label.disabled'), value: false },
        {label: this.$t('processes.workflow.all.label'), value: null},
      ],
    };
  },
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
    },
    isProcessesManager: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    lg () {
      return this.workflows.length >= 4 ? 3 : this.workflows.length === 3 ? 4 : 6 ;
    }
  },
  created() {
    this.$root.$on('open-add-work-drawer', event => {
      this.$refs.addWork.open(event.usedWorkflow, event.mode);
    });
  },
  methods: {
    open() {
      this.$refs.addWorkFlow.open();
    },
    updateFilter() {
      this.$root.$emit('workflow-filter-changed', this.filter.value);
    }
  }
};
</script>