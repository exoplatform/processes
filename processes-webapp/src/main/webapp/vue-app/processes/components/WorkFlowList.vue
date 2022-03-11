<template>
  <v-main
    id="workflows">
    <v-container>
      <v-row
        class="pe-10"
        v-if="isProcessesManager"
        no-gutters>
        <v-col
          cols="2"
          md="9"
          lg="9">
          <v-btn
            class="ml-1 mt-2 mb-4 btn-primary btn add-process-btn"
            dark
            @click="open"
            color="primary">
            <v-icon
              left
              dark>
              mdi-plus-thick
            </v-icon>
            <span v-if="!isMobile">
              {{ $t('processes.works.label.addRequestType') }}
            </span>
          </v-btn>
        </v-col>
        <v-col
          cols="5"
          md="2"
          lg="2">
          <v-text-field
            class="me-3 float-right workflow-filter-query"
            @keyup="updateFilter"
            v-model="query"
            :placeholder="$t('processes.workflow.filter.query.placeholder')"
            prepend-inner-icon="mdi-filter" />
        </v-col>
        <v-col
          cols="4"
          md="1"
          lg="1">
          <v-select
            ref="filter"
            class="pt-5 workflow-filter mt-n3"
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
    </v-container>
    <v-container
      v-if="!loading"
      no-gutters>
      <v-row
        v-if="workflows.length>0"
        no-gutters>
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
    </v-container>
    <empty-or-loading
      :loading="loading"
      v-if="workflows.length === 0">
      <template v-slot:empty>
        <div>
          <v-img
            width="280px"
            height="273px"
            src="/processes/images/noWorkflow.png" />
          <p
            class="mt-2">
            {{ $t('processes.workflow.noProcess.label') }}
          </p>
        </div>
      </template>
    </empty-or-loading>
  </v-main>
</template>

<script>
export default {
  data () {
    return {
      filter: {label: this.$t('processes.works.form.label.enabled'), value: true},
      items: [
        {label: this.$t('processes.workflow.activated.label'), value: true },
        {label: this.$t('processes.workflow.deactivated.label'), value: false },
        {label: this.$t('processes.workflow.all.label'), value: null},
      ],
      query: null,
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
  created() {
    this.$root.$on('workflow-added', (event) => {
      if (event.workflow.enabled === event.filter) {
        this.workflows.unshift(event.workflow);
      }
    });
    this.$root.$on('workflow-updated', (workflow) => {
      workflow = JSON.parse(workflow);
      const index = this.workflows.map(workflow => workflow.id).indexOf(workflow.id);
      if (this.filter.value === workflow.enabled) {
        this.workflows.splice(index, 1, workflow);
      } else {
        this.workflows.splice(this.workflows.indexOf(workflow), 1);
      }
    });
    this.$root.$on('workflow-removed', (workflow) => {
      this.workflows.splice(this.workflows.indexOf(workflow), 1);
    });
  },
  computed: {
    lg () {
      return this.workflows.length >= 4 ? 3 : this.workflows.length === 3 ? 4 : 6 ;
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    }
  },
  methods: {
    open() {
      this.$root.$emit('open-workflow-drawer', {workflow: null, mode: 'create_workflow'});
    },
    updateFilter() {
      this.$root.$emit('workflow-filter-changed', {filter: this.filter.value, query: this.query});
    }
  }
};
</script>