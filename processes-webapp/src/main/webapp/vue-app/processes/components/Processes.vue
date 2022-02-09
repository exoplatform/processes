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
          {{ $t('processes.toolbar.label.workflow') }}
        </v-tab>
        <v-tab>
          {{ $t('processes.toolbar.label.myWorks') }}
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
          <workflow-list
            :workflows="workflows"
            :has-more="hasMoreTypes"
            :loading="loading" />
        </v-tab-item>
        <v-tab-item>
          <my-work-list :works="works" :loading="loading" />
        </v-tab-item>
      </v-tabs-items>
    </v-card>
    <v-alert
      v-model="alert"
      :type="type"
      class="processesAlert"
      dismissible>
      {{ message }}
    </v-alert>
  </v-app>
</template>

<script>
export default {
  data () {
    return {
      tab: 0,
      alert: false,
      type: '',
      message: '',
      workflows: [],
      works: [],
      query: null,
      pageSize: 10,
      offset: 0,
      limit: 0,
      loading: false,
      hasMoreTypes: false,
    };
  },

  created() {
    this.getWorkFlows();
    this.getWorks();
    this.$root.$on('show-alert', alert => {
      this.displayMessage(alert);
    });
    this.$root.$on('workflow-added', workflow => {
      this.workflows.push(workflow);
    });
    this.$root.$on('add-work', work => {
      this.addWork(work);
    });
    this.$root.$on('add-workflow', workflow => {
      this.addNewWorkFlow(workflow);
    });
    this.$root.$on('refresh-works', () => {
      this.getWorks();
    });
  },
  methods: {
    getWorkFlows() {
      const filter = {};
      if (this.query) {
        filter.query = this.query;
      }
      const expand = '';
      this.limit = this.limit || this.pageSize;
      this.loading = true;
      return this.$processesService
        .getWorkFlows(filter, this.offset, this.limit + 1, expand)
        .then(workflows => {
          this.workflows = workflows || [];
          this.hasMoreTypes = workflows && workflows.length > this.limit;
        })
        .finally(() => this.loading = false);
    },
    getWorks() {
      const filter = {};
      if (this.query) {
        filter.query = this.query;
      }
      const expand = 'workFlow';
      this.limit = this.limit || this.pageSize;
      this.loading = true;
      return this.$processesService
        .getWorks(filter, 0, 0, expand)
        .then(works => {
          this.works = works || [];
        })
        .finally(() => this.loading = false);
    },
    displayMessage(alert) {
      this.message = alert.message;
      this.type = alert.type;
      this.alert = true;
      window.setTimeout(() => this.alert = false, 5000);
    },

    
    addNewWorkFlow(workflow) {
      this.saving = true;
      this.$processesService.addNewWorkFlow(workflow).then(workflow => {
        if (workflow){
          this.$root.$emit('workflow-added');
          this.displayMessage({type: 'success', message: this.$t('processes.workflow.add.success.message')});
        }
      }).catch(() => {
        this.displayMessage( {type: 'error', message: this.$t('processes.workflow.add.error.message')});
      });
    },

    addWork(work) {
      this.$processesService.addWork(work).then(work => {
        if (work){
          this.$root.$emit('work-added');
          this.displayMessage({type: 'success', message: this.$t('processes.work.add.success.message')});
        }
      }).catch(() => {
        this.displayMessage({type: 'error', message: this.$t('processes.work.add.error.message')});
      });
    }
  }
};
</script>
