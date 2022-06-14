<template>
  <v-app>
    <v-toolbar
      flat
      class="mt-4 pr-4 pl-4 mb-1">
      <v-tabs
        v-model="tab">
        <v-tabs-slider />
        <v-tab>
          {{ $t('processes.toolbar.label.workflow') }}
        </v-tab>
        <v-tab>
          {{ $t('processes.toolbar.label.myWorks') }}
        </v-tab>
      </v-tabs>
    </v-toolbar>
    <v-card
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
            :loading-more="loadingMore"
            :loading="loading"
            :show-workflow-filter="showProcessFilter" />
        </v-tab-item>
        <v-tab-item>
          <my-work-list
            :available-work-statuses="availableWorkStatuses"
            :works="works"
            :work-drafts="workDrafts"
            :completed-works="completedWorks"
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
      :ok-label="$t('processes.workflow.ok.label')"
      :cancel-label="$t('processes.workflow.cancel.label')"
      @ok="confirmAction" />
    <add-workflow-drawer
      ref="addWorkFlow" />
    <add-work-drawer
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
      completedWorks: [],
      query: null,
      enabled: true,
      status: null,
      offset: 0,
      limit: 0,
      loading: false,
      loadingMore: false,
      hasMoreTypes: false,
      isManager: false,
      confirmTitle: this.$t('processes.workflow.action.confirmation.label'),
      confirmMessage: '',
      dialogAction: null,
      targetModel: null,
      myRequestsTabVisited: null,
      showProcessFilter: false,
    };
  },
  beforeCreate() {
    this.$processesService.isProcessesManager().then(value => {
      this.isManager = value === 'true';
    });
    this.$processesService.getAvailableWorkStatuses().then(statuses => {
      this.availableWorkStatuses = statuses;
    });
  },
  watch: {
    tab(value) {
      this.updateState(value);
    }
  },
  created() {
    this.getWorkFlows();
    this.handleTabChanges();
    window.addEventListener('popstate', this.handleTabChanges);
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
      this.workflows = [];
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
      const url = `${eXo.env.portal.context}/${eXo.env.portal.portalName}/processes/myRequests/requestDetails/${this.work.id}/comments`;
      window.history.pushState('requestComments', '', url);
    });
    this.$root.$on('create-work-draft', event => {
      this.createWorkDraft(event.draft, event.preSave);
    });
    this.$root.$on('update-work-draft', draft => {
      this.updateWorkDraft(draft);
    });
    this.$root.$on('work-filter-changed', event => {
      this.completedWorks = [];
      this.works = [];
      this.workDrafts = [];
      this.status = event.status;
      this.query = event.query;
      if (this.status === 'drafts') {
        this.getWorkDrafts();
      } else if (this.status === null) {
        this.getWorkDrafts();
        this.getWorks();
      } else if (this.status === 'completed') {
        this.getCompletedWorks();
      } else {
        this.getWorks();
      }
    });
    document.addEventListener('Task-comments-drawer-closed', () => {
      const url = `${eXo.env.portal.context}/${eXo.env.portal.portalName}/processes/myRequests`;
      window.history.pushState('myRequests', '', url);
    });
    this.$root.$on('processes-attachments-notification-alert', event => {
      this.displayMessage(event);
    });
    this.$root.$on('load-more-workflows', () => {
      this.loadingMore = true;
      this.getWorkFlows();
    });
    this.$root.$on('update-work-completed', work => {
      this.updateWorkCompleted(work, !work.completed);
    });
    this.showFilter();
  },
  mounted() {
    window.setTimeout(() => {
      document.dispatchEvent(new CustomEvent('exo-statistic-message', {
        detail: {
          module: 'processes',
          subModule: 'application access',
          userId: eXo.env.portal.userIdentityId,
          userName: eXo.env.portal.userName,
          operation: 'accessProcessesApp',
          name: 'access to processes application',
          timestamp: Date.now()
        }
      }));
    }, 300);
  },
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    pageSize() {
      return this.isMobile && 4 || 8;
    }
  },
  methods: {
    showFilter(){
      if ((this.enabled == null && !this.query)||this.workflows.length){
        this.showProcessFilter = this.workflows.length > 0;
      }
      else {
        this.$processesService.getWorkFlows().then(workflows =>{
          this.showProcessFilter = workflows.length > 0;});
      }
    },
    handleTabChanges() {
      const path = document.location.pathname;
      if (path.endsWith('/processes')) {
        this.tab = 0;
        this.$root.$emit('close-work-drawer');
      }
      if (path.endsWith('/myRequests')) {
        this.tab = 1;
        this.$root.$emit('close-work-drawer');
      }
      if (path.includes('/requestDetails') && !path.endsWith('/comments')) {
        this.tab = 1;
        const workId = path.split('requestDetails/')[1].split(/\D/)[0];
        this.openWorkDetails(workId);
      }
      if (path.endsWith('/createRequest')) {
        this.tab = 0;
        const workflowId = path.split('processes/')[1].split(/\D/)[0];
        this.openCreateWork(workflowId);
      }
      if (path.includes('/myRequests/requestDetails') && path.endsWith('/comments')) {
        this.tab = 1;
        const workId = path.split('requestDetails/')[1].split(/\D/)[0];
        this.openWorkComments(workId);
      }
    },
    openCreateWork(workflowId) {
      this.$processesService.getWorkflowById(workflowId, '').then(workflow => {
        this.$root.$emit('open-add-work-drawer', {object: workflow, mode: 'create_work'});
      });
    },
    updateState(value) {
      if (value === 1) {
        window.history.pushState('myRequests', '', `${eXo.env.portal.context}/${eXo.env.portal.portalName}/processes/myRequests`);
        if (!this.myRequestsTabVisited) {
          this.getWorkDrafts();
          this.getWorks();
          this.myRequestsTabVisited = 1;
        }
      } else {
        window.history.replaceState('processes', '', `${eXo.env.portal.context}/${eXo.env.portal.portalName}/processes`);
      }
    },
    openWorkDetails(workId) {
      this.$processesService.getWorkById(workId, 'workFlow').then(work => {
        if (work) {
          this.$processesService.getWorkComments(work.id).then(comments => {
            work.comments = comments;
            this.$root.$emit('open-add-work-drawer', {object: work, mode: 'view_work', isDraft: false});
          });
        }
      });
    },
    openWorkComments(workId) {
      this.$processesService.getWorkById(workId, 'workFlow').then(work => {
        if (work) {
          this.$processesService.getWorkComments(work.id).then(comments => {
            work.comments = comments;
            const mappedWork = Object.assign({}, work);
            mappedWork.status = {
              project: {}
            };
            this.$root.$emit('show-work-comments', mappedWork, comments);
          });
        }
      });
    },
    getWorkFlows() {
      const filter = {};
      if (this.query) {
        filter.query = this.query;
      }
      filter.enabled = this.enabled;
      const expand = '';
      this.limit = this.limit || this.pageSize;
      this.offset = this.workflows.length || 0;
      this.loading = !this.loadingMore;
      return this.$processesService.getWorkFlows(filter, this.offset, this.limit + 1, expand).then(workflows => {
        this.hasMoreTypes = workflows && workflows.length > this.limit;
        if (this.hasMoreTypes) {
          this.workflows.push(...workflows.slice(0, -1));
        } else {
          this.workflows.push(...workflows || []);
        }
      })
        .finally(() => {
          this.loading = false;
          this.loadingMore = false;
        });
    },
    getWorks() {
      const filter = {};
      if (this.query) {
        filter.query = this.query;
      }
      filter.status = this.status;
      filter.completed = this.status === 'completed';
      if (filter.completed) {
        filter.status = null;
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
    getCompletedWorks() {
      const expand = 'workFlow';
      const filter = {
        completed: true
      };
      this.$processesService.getWorks(filter, 0, 0, expand).then(works => {
        works.forEach(work => {
          work.status = 'completed';
        });
        this.completedWorks = works || [];
      });
    },
    getWorkDrafts() {
      const filter = {};
      if (this.query) {
        filter.query = this.query;
      }
      const expand = '';
      this.limit = this.limit || this.pageSize;
      this.loading = true;
      return this.$processesService.getWorkDrafts(filter, 0, 0, expand).then(drafts => {
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
          this.showProcessFilter = true;

        }
      }).catch(() => {
        this.displayMessage( {type: 'error', message: this.$t('processes.workflow.add.error.message')});
      });
    },
    addWork(work) {
      this.$processesService.addWork(work).then(newWork => {
        if (newWork) {
          this.$root.$emit('work-added', {work: newWork, draftId: work.draftId});
          this.works.unshift(newWork);
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
      return this.$processesService.createWorkDraft(workDraft).then(draft => {
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
      return this.$processesService.updateWorkDraft(workDraft).then(draft => {
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
      if (reason === 'cancel_work') {
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
      if (this.dialogAction && this.dialogAction === 'cancel_work') {
        this.updateWorkCompleted(this.targetModel, true, true);
      }
      this.onDialogClosed();
    },
    deleteWorkDraftById(workDraft) {
      return this.$processesService.deleteWorkDraftById(workDraft.id).then(value => {
        if (value === 'ok') {
          this.$root.$emit('work-draft-removed', workDraft);
          this.displayMessage({type: 'success', message: this.$t('processes.work.draft.delete.success.message')});
        }
      }).catch(() => {
        this.displayMessage({type: 'error', message: this.$t('processes.work.draft.delete.error.message')});
      });
    },
    deleteWorkflowById(workflow) {
      return this.$processesService.deleteWorkflowById(workflow.id).then(value => {
        if (value === 'ok') {
          this.$root.$emit('workflow-removed', workflow);
          this.displayMessage({type: 'success', message: this.$t('processes.workflow.delete.success.message')});
          this.showFilter();
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
      return this.$processesService.deleteWorkById(work.id).then(value => {
        if (value === 'ok') {
          this.$root.$emit('work-removed', work);
          this.displayMessage({type: 'success', message: this.$t('processes.work.delete.success.message')});
        }
      }).catch(() => {
        this.displayMessage({type: 'error', message: this.$t('processes.work.delete.error.message')});
      });
    },
    updateWorkStatus(work, status) {
      work.status = status;
      return this.$processesService.updateWork(work);
    },
    updateWorkCompleted(work, completed, cancel) {
      return this.$processesService.updateWorkCompleted(work.id, completed).then(patchedWork => {
        if (patchedWork) {
          if (completed && cancel) {
            this.updateWorkStatus(patchedWork, 'Canceled').then((newWork) => {
              this.$root.$emit('work-canceled', newWork);
              this.displayMessage({type: 'success', message: this.$t('processes.work.cancel.success.message')});
            });
          } else if (completed) {
            this.$root.$emit('work-completed', patchedWork);
            this.displayMessage({type: 'success', message: this.$t('processes.work.complete.success.message')});
          } else {
            this.$root.$emit('work-uncanceled', patchedWork);
            this.displayMessage({type: 'success', message: this.$t('processes.work.unComplete.success.message')});
          }
        }
      }).catch(() => {
        if (cancel) {
          this.displayMessage({type: 'error', message: this.$t('processes.work.cancel.error.message')});
        } else {
          this.displayMessage({type: 'error', message: this.$t('processes.work.complete.error.message')});
        }
      });
    },
  }
};
</script>
