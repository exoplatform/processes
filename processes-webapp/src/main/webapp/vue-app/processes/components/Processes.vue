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
            :is-processes-manager="isManager"
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
    <exo-confirm-dialog
      ref="confirmDialog"
      :title="confirmTitle"
      :message="confirmMessage"
      :ok-label="$t('processes.workflow.delete.label')"
      :cancel-label="$t('processes.workflow.cancel.label')"
      @ok="confirmAction"
      @dialog-closed="onDialogClosed" />
    <add-workflow-drawer ref="addWorkFlow" />
    <add-work-drawer
      :processes-space-id="processesSpaceId"
      ref="addWork" />
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
      enabled: true,
      pageSize: 10,
      offset: 0,
      limit: 0,
      loading: false,
      hasMoreTypes: false,
      isManager: false,
      confirmTitle: this.$t('processes.workflow.action.confirmation.label'),
      confirmMessage: '',
      dialogAction: null,
      targetModel: null,
      processesSpaceInfo: null
    };
  },
  beforeCreate() {
    this.$processesService.isProcessesManager().then(value => {
      this.isManager = value === 'true';
    });
    this.$processesService.getProcessesSpaceInfo().then(spaceInfo => {
      this.processesSpaceInfo = spaceInfo;
    });
  },
  created() {
    this.getWorkFlows();
    this.getWorks();
    this.$root.$on('show-alert', alert => {
      this.displayMessage(alert);
    });
    this.$root.$on('add-work', work => {
      this.addWork(work);
    });
    this.$root.$on('add-workflow', workflow => {
      this.addNewWorkFlow(workflow);
    });
    this.$root.$on('update-workflow', workflow => {
      this.updateWorkFlow(workflow);
    });
    this.$root.$on('refresh-works', () => {
      this.getWorks();
    });
    this.$root.$on('workflow-filter-changed', value => {
      this.enabled = value;
      this.getWorkFlows();
    });
    this.$root.$on('show-confirm-action', event => {
      this.showConfirmDialog(event.model, event.reason);
    });
    this.$root.$on('open-add-work-drawer', event => {
      this.$refs.addWork.open(event.object, event.mode);
    });
    this.$root.$on('open-workflow-drawer', event => {
      this.$refs.addWorkFlow.open(event.workflow, event.mode);
    });
  },
  computed: {
    processesSpaceId() {
      return this.processesSpaceInfo && this.processesSpaceInfo.id;
    }
  },
  methods: {
    getWorkFlows() {
      const filter = {};
      if (this.query) {
        filter.query = this.query;
      }
      filter.enabled = this.enabled;
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
          if (workflow.enabled === this.enabled) {
            this.workflows.unshift(workflow);
          }
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
    },
    showConfirmDialog(model, reason){
      if (reason === 'delete_workflow') {
        this.dialogAction = reason;
        this.targetModel = model;
        this.confirmMessage = this.$t('processes.workflow.delete.confirmDialog.message', {0: `<strong>${model.title}</strong>`});
      }
      this.$refs.confirmDialog.open();
    },
    confirmAction() {
      if (this.dialogAction && this.dialogAction === 'delete_workflow') {
        this.deleteWorkflowById(this.targetModel);
      }
    },
    deleteWorkflowById(workflow) {
      this.$processesService.deleteWorkflowById(workflow.id).then(value => {
        if (value === 'ok') {
          this.workflows.splice(this.workflows.indexOf(workflow), 1);
          this.displayMessage({type: 'success', message: this.$t('processes.workflow.delete.success.message')});
        }
      }).catch(() => {
        this.displayMessage({type: 'error', message: this.$t('processes.workflow.delete.error.message')});
      });
    },
    updateWorkFlow(workflow) {
      this.saving = true;
      this.$processesService.updateWorkflow(workflow).then(newWorkflow => {
        if (newWorkflow) {
          this.$root.$emit('workflow-updated');
          this.displayMessage({type: 'success', message: this.$t('processes.workflow.update.success.message')});
        }
      }).catch(() => {
        this.displayMessage({type: 'error', message: this.$t('processes.workflow.update.error.message')});
      }).finally(() => {
        this.getWorkFlows();
      });
    },
    onDialogClosed() {
      this.dialogAction = null;
      this.targetModel = null;
    }
  }
};
</script>
