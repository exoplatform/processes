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
          <my-work-list
            :available-work-statuses="availableWorkStatuses"
            :works="works"
            :work-drafts="workDrafts"
            :loading="loading" />
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
    <add-workflow-drawer
      ref="addWorkFlow"
      :processes-space-id="processesSpaceId" />
    <add-work-drawer
      :processes-space-id="processesSpaceId"
      ref="addWork" />
    <task-comments-drawer
      ref="taskCommentDrawer"
      :task="work"
      :comments="workComments" />
  </v-app>
</template>

<script>

export default {
  data () {
    return {
      work: null,
      workComments: [],
      availableWorkStatuses: null,
      tab: 0,
      alert: false,
      type: '',
      message: '',
      workflows: [],
      works: [],
      workDrafts: [],
      query: null,
      enabled: true,
      status: null,
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
    this.$processesService.getAvailableWorkStatuses().then(statuses => {
      this.availableWorkStatuses = statuses;
    });
  },
  created() {
    this.getWorkFlows();
    this.getWorks();
    this.getWorkDrafts();
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
    this.$root.$on('workflow-filter-changed', event => {
      this.enabled = event.filter;
      this.query = event.query;
      this.getWorkFlows();
    });
    this.$root.$on('show-confirm-action', event => {
      this.showConfirmDialog(event.model, event.reason);
    });
    this.$root.$on('open-add-work-drawer', event => {
      this.work = event.object;
      this.workComments = event.object.comments;
      this.$refs.addWork.open(event.object, event.mode, event.isDraft);
    });
    this.$root.$on('open-workflow-drawer', event => {
      this.$refs.addWorkFlow.open(event.workflow, event.mode);
    });
    this.$root.$on('show-work-comments', (work, comments) => {
      this.work = work;
      this.workComments = comments;
      this.$root.$emit('displayTaskComment');
    });
    this.$root.$on('create-work-draft', event => {
      this.createWorkDraft(event.draft, event.preSave);
    });
    this.$root.$on('update-work-draft', draft => {
      this.updateWorkDraft(draft);
    });
    this.$root.$on('work-filter-changed', event => {
      this.status = event.status;
      this.query = event.query;
      if (this.status === 'drafts') {
        this.works = [];
        this.getWorkDrafts();
      } else if (this.status === null) {
        this.getWorkDrafts();
        this.getWorks();
      } else {
        this.workDrafts = [];
        this.getWorks();
      }
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
      filter.status = this.status;
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
    getWorkDrafts() {
      const filter = {};
      if (this.query) {
        filter.query = this.query;
      }
      const expand = '';
      this.limit = this.limit || this.pageSize;
      this.loading = true;
      return this.$processesService.getWorkDrafts(filter, this.offset, this.limit , expand).then(drafts => {
        this.workDrafts = drafts || [];
      }).finally(() => this.loading = false);
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
          this.$root.$emit('workflow-added', {workflow: workflow, filter: this.enabled});
          this.displayMessage({type: 'success', message: this.$t('processes.workflow.add.success.message')});
        }
      }).catch(() => {
        this.displayMessage( {type: 'error', message: this.$t('processes.workflow.add.error.message')});
      });
    },

    addWork(work) {
      this.$processesService.addWork(work).then(newWork => {
        if (newWork) {
          this.$root.$emit('work-added', {work: newWork, draftId: work.draftId});
          this.works.push(newWork);
          if (work.draftId) {
            this.workDrafts.splice(this.workDrafts.indexOf(work.draftId), 1);
          }
          this.displayMessage({type: 'success', message: this.$t('processes.work.add.success.message')});
        }
      }).catch(() => {
        this.displayMessage({type: 'error', message: this.$t('processes.work.add.error.message')});
      });
    },
    createWorkDraft(workDraft, preSave) {
      this.$processesService.createWorkDraft(workDraft).then(draft => {
        if (draft) {
          if (preSave) {
            this.$root.$emit('pre-save-work-draft-added', draft);
          } else {
            this.$root.$emit('work-draft-added', draft);
          }
          this.workDrafts.unshift(draft);
          this.displayMessage({type: 'success', message: this.$t('processes.workDraft.add.success.message')});
        }
      }).catch(() => {
        this.displayMessage({type: 'error', message: this.$t('processes.workDraft.save.error.message')});
      });
    },
    updateWorkDraft(workDraft) {
      this.$processesService.updateWorkDraft(workDraft).then(draft => {
        if (draft) {
          this.$root.$emit('work-draft-updated', draft);
          this.displayMessage({type: 'success', message: this.$t('processes.workDraft.update.success.message')});
        }
      }).catch(() => {
        this.displayMessage({type: 'error', message: this.$t('processes.workDraft.save.error.message')});
      });
    },
    showConfirmDialog(model, reason){
      this.dialogAction = reason;
      this.targetModel = model;
      if (reason === 'delete_workflow') {
        this.confirmMessage = this.$t('processes.workflow.delete.confirmDialog.message', {0: `<strong>${model.title}</strong>`});
      }
      if (reason === 'delete_work') {
        this.confirmMessage = this.$t('processes.work.delete.confirmDialog.message');
      }
      if (reason === 'delete_work_draft') {
        this.confirmMessage = this.$t('processes.work.draft.delete.confirmDialog.message');
      }
      this.$refs.confirmDialog.open();
    },
    confirmAction() {
      if (this.dialogAction && this.dialogAction === 'delete_workflow') {
        this.deleteWorkflowById(this.targetModel);
      }
      if (this.dialogAction && this.dialogAction === 'delete_work') {
        this.deleteWorkById(this.targetModel);
      }
      if (this.dialogAction && this.dialogAction === 'delete_work_draft') {
        this.deleteWorkDraftById(this.targetModel);
      }
    },
    deleteWorkDraftById(workDraft) {
      this.$processesService.deleteWorkDraftById(workDraft.id).then(value => {
        if (value === 'ok') {
          this.$root.$emit('work-draft-removed', workDraft);
          this.displayMessage({type: 'success', message: this.$t('processes.work.draft.delete.success.message')});
        }
      }).catch(() => {
        this.displayMessage({type: 'error', message: this.$t('processes.work.draft.delete.error.message')});
      });
    },
    deleteWorkflowById(workflow) {
      this.$processesService.deleteWorkflowById(workflow.id).then(value => {
        if (value === 'ok') {
          this.$root.$emit('workflow-removed', workflow);
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
          this.$root.$emit('workflow-updated', newWorkflow);
          this.displayMessage({type: 'success', message: this.$t('processes.workflow.update.success.message')});
        }
      }).catch(() => {
        this.displayMessage({type: 'error', message: this.$t('processes.workflow.update.error.message')});
      });
    },
    onDialogClosed() {
      this.dialogAction = null;
      this.targetModel = null;
    },
    deleteWorkById(work) {
      this.$processesService.deleteWorkById(work.id).then(value => {
        if (value === 'ok') {
          this.$root.$emit('work-removed', work);
          this.displayMessage({type: 'success', message: this.$t('processes.work.delete.success.message')});
        }
      }).catch(() => {
        this.displayMessage({type: 'error', message: this.$t('processes.work.delete.error.message')});
      });
    },
  }
};
</script>
