<template>
  <v-app>
    <div>
      <v-toolbar
        flat
        class="pr-4 pl-4 application-body">
        <v-tabs
          v-model="tab"
          slider-size="4">
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
        class="pa-5 application-body process-tab-items">
        <v-tabs-items
          v-model="tab">
          <v-tab-item>
            <workflow-list
              v-if="!initializing"
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
              :loading="loading"
              :show-work-filter="requestSubmitted || allWorkDrafts.length > 0" />
          </v-tab-item>
        </v-tabs-items>
      </v-card>
    </div>
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
      MOBILE_WIDTH: 768,
      work: null,
      workComments: [],
      availableWorkStatuses: null,
      tab: 0,
      type: '',
      message: '',
      workflows: [],
      works: [],
      workDrafts: [],
      allWorkDrafts: [],
      completedWorks: [],
      query: null,
      enabled: null,
      manager: false,
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
      requestSubmitted: false,
      initializing: true,
      messageActionLabel: '',
      messageAction: null,
      messageTargetModel: null,
      messageTimer: null,
      draftId: null,
      updated: false,
    };
  },
  beforeCreate() {
    this.$processesService.initCometd();
    this.$processesService.isProcessesManager().then(value => {
      this.isManager = value === 'true';
    }).finally(() => {
      this.initializing = false;
      this.enabled = this.isManager ? null : true;
      this.getWorkFlows({ 'enabled': true});
    });
    this.$processesService.getAvailableWorkStatuses().then(statuses => {
      this.availableWorkStatuses = statuses;
    });
  },
  watch: {
    tab(value) {
      this.updateState(value);
      if (value === 1){
        window.setTimeout(() => {
          document.dispatchEvent(new CustomEvent('exo-statistic-message', {
            detail: {
              module: 'processes',
              subModule: 'application access',
              userId: eXo.env.portal.userIdentityId,
              userName: eXo.env.portal.userName,
              operation: 'accessRequestsPage',
              timestamp: Date.now()
            }
          }));
        }, 300);
      }
    },
    workflows(){
      this.showFilter();
    },
  },
  created() {
    this.handleTabChanges();
    window.addEventListener('popstate', this.handleTabChanges);
    this.$root.$on('show-alert', alert => {
      this.displayMessage(alert);
    });
    this.$root.$on('hide-alert',()=>{
      this.closeAlert();
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
      this.query = event.query;
      if (this.isManager) {
        switch (event.filter)
        {
        case 'deactivated':
          this.manager = false;
          this.enabled = false;
          break;
        case 'manager':
          this.manager = true;
          this.enabled = null;
          break;
        case 'activated':
          this.manager = false;
          this.enabled = true;
          break;
        default:
          this.manager = false;
          this.enabled = null;
        }
      } else {
        if (event.filter === 'manager') {
          this.manager = true;
          this.enabled = true;
        } else {
          this.manager = false;
          this.enabled = true;
        }
      }
      this.getWorkFlows();
    });
    this.$root.$on('show-confirm-action', event => {
      this.showConfirmDialog(event.model, event.reason);
    });
    this.$root.$on('open-add-work-drawer', event => {
      if (this.alert) {
        this.handleMessageClose();
      }
      this.work = event.object;
      this.workComments = event.object.comments;
      this.$refs.addWork.open(event.object, event.mode, event.isDraft,event.allowSave);
    });
    this.$root.$on('open-workflow-drawer', event => {
      this.$refs.addWorkFlow.open(event.workflow, event.mode);
    });
    this.$root.$on('show-work-comments', (work, comments) => {
      this.work = work;
      this.workComments = comments;
      this.$root.$emit('displayTaskComment');
      const spacePath = eXo.env.portal.spaceName ? `${eXo.env.portal.context}/g/:spaces:${eXo.env.portal.spaceGroup}/${eXo.env.portal.selectedNodeUri}` : null;
      const baseProcessPath = `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}`;
      const url = `${spacePath != null ? spacePath : baseProcessPath}/processes/myRequests/requestDetails/${this.work.id}/comments`;
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
      const urlInst = window.location.href;
      if (urlInst.includes('comments')) {
        const tab = urlInst.split('/');
        const workId = tab[tab.length - 2];
        this.openWorkDetails(workId);
      }
      const spacePath = eXo.env.portal.spaceName ? `${eXo.env.portal.context}/g/:spaces:${eXo.env.portal.spaceGroup}/${eXo.env.portal.selectedNodeUri}` : null;
      const baseProcessPath = `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}`;
      const url = `${spacePath != null ? spacePath : baseProcessPath}/processes/myRequests`;
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
    this.$root.$on('update-url-path',  this.handleUpdateUrlPath);
    this.$root.$on('keep-work-draft', () => {
      this.closeAlert();
    });
    document.addEventListener('alert-message-dismissed', this.handleMessageClose);
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
    this.$processesService.getWorks(null,0,0,'workFlow').then(list =>{
      this.requestSubmitted = list.length > 0;
    });
  },
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    isMobileAlert() {
      return this.$vuetify.breakpoint.width < this.MOBILE_WIDTH;
    },
    pageSize() {
      return this.isMobile && 4 || 9;
    }
  },
  methods: {
    handleMessageClose() {
      if (this.messageTargetModel && this.messageTargetModel.id) {
        this.deleteWorkDraftById(this.messageTargetModel);
        this.messageTargetModel = null;
      }
    },
    handleMessageAction() {
      if (this.messageAction) {
        this.$root.$emit(this.messageAction);
      }
    },
    closeAlert() {
      document.dispatchEvent(new CustomEvent('close-alert-message'));
    },
    handleUpdateUrlPath(data, path) {
      const spacePath = eXo.env.portal.spaceName ? `${eXo.env.portal.context}/g/:spaces:${eXo.env.portal.spaceGroup}/${eXo.env.portal.selectedNodeUri}` : null;
      const baseProcessPath = `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}`;
      window.history.pushState(data, '', `${spacePath != null ? spacePath : baseProcessPath }/processes${path}`);
    },
    showFilter(){
      if (this.isManager){
        if ((this.enabled == null && !this.query)||this.workflows.length){
          this.showProcessFilter = this.workflows.length > 0;
        }
      } else {
        const filter = {};
        filter.enabled = true;
        filter.manager = false;
        this.query = '';
        this.$processesService.getWorkFlows(filter).then(workflows =>{
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
        this.$root.$emit('hideTaskComment');
      }
      if (path.includes('/myRequests/draftDetails')) {
        this.tab = 1;
        this.draftId =path.split('draftDetails/')[1].split(/\D/)[0];
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
      if (path.includes('/myRequests/draftDetails')||path.includes('/myRequests/requestDetails')) {
        const currentUrlSearchParams = window.location.search;
        const queryParams = new URLSearchParams(currentUrlSearchParams);
        if (queryParams.has('updated')) {
          this.updated = queryParams.get('updated') === 'true';
        }
      }
    },
    openCreateWork(workflowId) {
      this.$processesService.getWorkflowById(workflowId, '').then(workflow => {
        this.$root.$emit('open-add-work-drawer', {object: workflow, mode: 'create_work'});
      });
    },
    updateState(value) {
      const spacePath = eXo.env.portal.spaceName ? `${eXo.env.portal.context}/g/:spaces:${eXo.env.portal.spaceGroup}/${eXo.env.portal.selectedNodeUri}` : null;
      const baseProcessPath = `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}`;
      if (value === 1) {
        window.history.pushState('myRequests', '', `${spacePath != null ? spacePath : baseProcessPath}/processes/myRequests`);
        if (!this.myRequestsTabVisited) {
          this.getWorkDrafts();
          this.getWorks();
          this.myRequestsTabVisited = 1;
        }
      } else {
        window.history.replaceState('processes', '', `${spacePath != null ? spacePath : baseProcessPath}/processes`);
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
    getWorkFlows(filter) {
      if (!filter) {
        filter = {};
      }
      if (this.query) {
        filter.query = this.query;
      }
      if (!filter.enabled) {
        filter.enabled = this.enabled;
      }
      filter.manager = this.manager;
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
      const expand = '';
      this.limit = this.limit || this.pageSize;
      this.loading = true;
      return this.$processesService.getWorkDrafts(null, 0, 0, expand).then(drafts => {
        this.allWorkDrafts = drafts || [];
        if (this.draftId) {
          const draft = this.allWorkDrafts.find((element) => element.id.toString() === this.draftId.toString());
          if (draft){
            this.$root.$emit('open-add-work-drawer', {object: draft, mode: 'edit_work_draft', allowSave: this.updated});
            this.handleUpdateUrlPath('draftDetails', `/myRequests/draftDetails/${draft.id}`);
          }
        }
        if (this.query){
          this.workDrafts = this.allWorkDrafts.filter(elem=>{
            return elem.description && elem.description.replace(/\s/g,'').replace(/<\/?[^>]+(>|$)/gi, '').includes(this.query.replace(/\s/g,''));
          });
        }
        else {
          this.workDrafts = this.allWorkDrafts;
        }
      }).finally(() => this.loading = false);
    },
    displayMessage(alert) {
      this.messageAction = alert.messageAction || null;
      this.messageTargetModel = alert.messageTargetModel || null;
      document.dispatchEvent(new CustomEvent('alert-message', {
        detail: {
          alertMessage: alert?.message,
          alertType: alert?.type,
          alertLinkText: alert?.messageActionLabel,
          alertLinkCallback: this.messageAction && this.handleMessageAction || null,
        }
      }));
    },
    addNewWorkFlow(workflow) {
      this.saving = true;
      this.$processesService.addNewWorkFlow(workflow).then(workflow => {
        if (workflow){
          this.getWorkFlows(workflow);
          this.$root.$emit('workflow-added');
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
            const index = this.workDrafts.findIndex(draft => draft.id === work.draftId);
            this.workDrafts.splice(index, 1);
          }
          this.displayMessage({type: 'success', message: this.$t('processes.work.add.success.message')});
          this.requestSubmitted = true;
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
            this.displayMessage({type: 'success', message: this.$t('processes.workDraft.created.success.message')});
          } else {
            this.$root.$emit('work-draft-added', draft);
            this.displayMessage({type: 'success', message: this.$t('processes.workDraft.add.success.message')});
          }
          this.workDrafts.unshift(draft);
          this.$root.$emit('update-url-path', 'draftDetails', `/myRequests/draftDetails/${draft.id}`);
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
