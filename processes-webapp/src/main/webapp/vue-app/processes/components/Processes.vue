<template>
  <v-app class="ProcessesApplication">
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
              :completed-total="completedTotal"
              :completed-has-more="completedHasMore"
              :loading="worksLoading || completedLoads > 0"
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
import {COMPLETED_PAGE_SIZE} from '../js/MyRequestsFilters.js';

export default {
  data () {
    return {
      MOBILE_WIDTH: 768,
      workflowSaveErrorCodes: [
        'processes.project.invalidMode',
        'processes.project.notEligible',
        'processes.project.statusNameConflict',
        'processes.label.notInProject',
        'processes.label.invalidName',
        'processes.space.notFound',
        'processes.workflow.formUnsupported',
        'processes.workflow.requesterFormInvalid',
        'forms.form.otherSpace',
      ],
      requestSaveErrorCodes: [
        'processes.request.formValuesInvalid',
        'processes.request.formValuesUnsupported',
        'processes.request.draftMismatch',
      ],
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
      categoryId: null,
      status: null,
      offset: 0,
      limit: 0,
      loading: false,
      loadingMore: false,
      hasMoreTypes: false,
      isManager: false,
      managesWorkflow: null,
      worksLoading: false,
      completedLoads: 0,
      completedTotal: null,
      completedHasMore: false,
      loadingOlderCompleted: false,
      loadAllCompletedPending: false,
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
    this.$root.$on('add-workflow', (workflow, translations, categories) => {
      this.addNewWorkFlow(this.withTranslations(workflow, translations), categories);
    });
    this.$root.$on('update-workflow', (workflow, translations, categories) => {
      this.updateWorkFlow(this.withTranslations(workflow, translations), categories);
    });
    this.$root.$on('refresh-works', () => {
      this.getWorks();
    });
    this.$root.$on('add-work-drawer-closed', () => {
      this.updateState(this.tab);
    });
    this.$root.$on('workflow-filter-changed', event => {
      this.workflows = [];
      this.query = event.query;
      this.categoryId = event.categoryId || null;
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
        } else if (event.filter === 'deactivated') {
          // a user who manages a process finds the disabled ones they manage, to enable them again
          this.manager = true;
          this.enabled = false;
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
      this.createWorkDraft(event.draft);
    });
    this.$root.$on('update-work-draft', draft => {
      this.updateWorkDraft(draft);
    });
    // « My requests » filters the loaded requests itself: only the completed ones load on demand
    this.$root.$on('work-filter-changed', event => {
      this.status = event.status;
      if (this.status === 'completed') {
        this.getCompletedWorks();
      } else {
        this.completedWorks = [];
        this.completedTotal = null;
        this.completedHasMore = false;
      }
    });
    this.$root.$on('load-more-completed-works', all => this.loadOlderCompletedWorks(all));
    this.$root.$on('archive-finished-works', count => this.showConfirmDialog(count, 'archive_finished_works'));
    this.$root.$on('work-uncanceled', () => {
      if (this.completedTotal) {
        this.completedTotal--;
      }
    });
    this.$root.$on('processes-show-tab', tab => this.tab = tab);
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
    // one request, without its process, tells whether the user ever sent one
    this.$processesService.getWorks(null, 0, 1, '').then(list => {
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
        // a view the category narrows to nothing keeps its toolbar, to choose another category
        if ((this.enabled == null && !this.query && !this.categoryId)||this.workflows.length){
          this.showProcessFilter = this.workflows.length > 0;
        }
      } else {
        const filter = {};
        filter.enabled = true;
        filter.manager = false;
        this.query = '';
        // whether the user manages a process is read once: the toolbar then offers its « Disabled » view
        this.managesWorkflow = this.managesWorkflow || this.$processesService.getWorkFlows({manager: true}, 0, 1)
          .then(managedWorkflows => managedWorkflows.length > 0)
          .catch(() => {
            this.managesWorkflow = null;
            return false;
          });
        Promise.all([this.$processesService.getWorkFlows(filter), this.managesWorkflow])
          .then(([workflows, managesWorkflow]) => {
            this.showProcessFilter = workflows.length > 0 || managesWorkflow;
          });
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
        const match = path.match(/\/(\d+)\/createRequest$/);
        const workflowId = match ? match[1] : null;
        if (workflowId) {
          this.openCreateWork(workflowId);
        }
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
        if (workflow.acl.canAddRequest) {
          this.$root.$emit('open-add-work-drawer', {object: workflow, mode: 'create_work'});
        }
      });
    },
    updateState(value) {
      const spacePath = eXo.env.portal.spaceName ? `${eXo.env.portal.context}/g/:spaces:${eXo.env.portal.spaceGroup}/${eXo.env.portal.selectedNodeUri}` : null;
      const baseProcessPath = `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}`;
      if (value === 1) {
        window.history.pushState('myRequests', '', `${spacePath != null ? spacePath : baseProcessPath}/processes/myRequests`);
        if (!this.myRequestsTabVisited) {
          // « My requests » waits for both loads before it says the list is empty
          this.worksLoading = true;
          Promise.all([this.getWorkDrafts(), this.getWorks()]).finally(() => this.worksLoading = false);
          this.myRequestsTabVisited = 1;
        }
      } else {
        window.history.replaceState('processes', '', `${spacePath != null ? spacePath : baseProcessPath}/processes`);
      }
    },
    openWorkDetails(workId) {
      this.$processesService.getWorkById(workId, 'workFlow').then(work => {
        if (work) {
          this.$root.$emit('open-add-work-drawer', {object: work, mode: 'view_work', isDraft: false});
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
      if (this.categoryId) {
        filter.categoryId = this.categoryId;
      }
      const expand = '';
      this.limit = this.limit || this.pageSize;
      this.offset = this.workflows.length || 0;
      this.loading = !this.loadingMore;
      return this.$processesService.getWorkFlows(filter, this.offset, this.limit + 1, expand).then(workflows => {
        this.hasMoreTypes = workflows && workflows.length > this.limit;
        let page = this.hasMoreTypes ? workflows.slice(0, -1) : workflows || [];
        if (!this.isManager && filter.enabled === false) {
          // a guard: the server's manager scope already lists only the processes the user may edit
          page = page.filter(workflow => workflow.acl && workflow.acl.canEdit);
        }
        this.workflows.push(...page);
      })
        .finally(() => {
          this.loading = false;
          this.loadingMore = false;
        });
    },
    getWorks() {
      const filter = {completed: false};
      const expand = 'workFlow,commentsCount';
      this.limit = this.limit || this.pageSize;
      this.loading = true;
      return this.$processesService
        .getWorks(filter, 0, 0, expand)
        .then(works => {
          this.works = works || [];
        })
        .finally(() => this.loading = false);
    },
    /**
     * Loads the most recent completed requests, a page of them, with the server's count; or, when
     * appending, the next page, and every remaining one when asked for all.
     *
     * @param {Boolean} append whether the page follows the loaded ones
     * @param {Boolean} all whether every remaining page loads
     * @returns {Promise} resolved once the pages are loaded
     */
    getCompletedWorks(append, all) {
      const expand = 'workFlow,commentsCount';
      const filter = {
        completed: true
      };
      const offset = append ? this.completedWorks.length : 0;
      // counted, not flagged: a quick Completed, All, Completed starts a second load before the first answers
      this.completedLoads++;
      const page = this.$processesService.getWorks(filter, offset, COMPLETED_PAGE_SIZE + 1, expand);
      const count = append ? Promise.resolve(this.completedTotal) : this.$processesService.countWorks(filter).catch(() => null);
      return Promise.all([page, count]).then(([works, total]) => {
        if (this.status !== 'completed') {
          // the status changed while the completed requests were loading
          return;
        }
        const list = works || [];
        const hasMore = list.length > COMPLETED_PAGE_SIZE;
        const pageWorks = hasMore ? list.slice(0, COMPLETED_PAGE_SIZE) : list;
        if (append) {
          // a request archived meanwhile shifts the pages: one already shown is not added twice
          const shownIds = new Set(this.completedWorks.map(work => work.id));
          this.completedWorks.push(...pageWorks.filter(work => !shownIds.has(work.id)));
        } else {
          this.completedWorks = pageWorks;
        }
        this.completedTotal = total;
        this.completedHasMore = hasMore;
        if (all && hasMore) {
          return this.getCompletedWorks(true, true);
        }
      }).finally(() => this.completedLoads--);
    },
    /**
     * Loads the next page of completed requests, or every remaining one, one load at a time. Asked
     * for all while a page loads, it loads the rest once that page lands, rather than waiting for a
     * change of `completedHasMore` that a page leaving more to load does not make.
     *
     * @param {Boolean} all whether every remaining page loads
     * @returns {void}
     */
    loadOlderCompletedWorks(all) {
      if (this.loadingOlderCompleted) {
        this.loadAllCompletedPending = this.loadAllCompletedPending || !!all;
        return;
      }
      if (!this.completedHasMore) {
        return;
      }
      this.loadingOlderCompleted = true;
      this.getCompletedWorks(true, all).finally(() => {
        this.loadingOlderCompleted = false;
        if (this.loadAllCompletedPending) {
          this.loadAllCompletedPending = false;
          this.loadOlderCompletedWorks(true);
        }
      });
    },
    /**
     * Archives every validated, refused and canceled request of the user, then reloads the lists
     * the archive changed, after a failure too: the requests archived before it stay archived.
     *
     * @returns {Promise} resolved once the outcome is shown
     */
    archiveFinishedWorks() {
      return this.$processesService.archiveFinishedWorks().then(archived => {
        const message = archived === 1
          ? this.$t('processes.myRequests.archiveAll.success.one')
          : this.$t('processes.myRequests.archiveAll.success.many', {0: archived});
        this.displayMessage({type: 'success', message});
      }).catch(() => {
        this.displayMessage({type: 'error', message: this.$t('processes.myRequests.archiveAll.error')});
      }).finally(() => {
        this.getWorks();
        if (this.status === 'completed') {
          this.getCompletedWorks();
        }
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
        this.workDrafts = this.allWorkDrafts;
      }).finally(() => this.loading = false);
    },
    displayMessage(alert) {
      this.messageAction = alert.messageAction || null;
      document.dispatchEvent(new CustomEvent('alert-message', {
        detail: {
          alertMessage: alert?.message,
          alertType: alert?.type,
          alertLinkText: alert?.messageActionLabel,
          alertLinkCallback: this.messageAction && this.handleMessageAction || null,
        }
      }));
    },
    /**
     * Creates a process, then reports the outcome; a refusal tells the drawer
     * so it can be saved again.
     *
     * @param {Object} workflow the process to create
     * @param {Object} categories the categories to file it under, once created
     * @returns {void}
     */
    addNewWorkFlow(workflow, categories) {
      this.saving = true;
      this.$processesService.addNewWorkFlow(workflow).then(workflow => {
        if (workflow){
          // filed under its categories before the list reads it again, so it shows with them
          return this.saveCategories(workflow, categories).then(() => {
            this.getWorkFlows(workflow);
            this.$root.$emit('workflow-added');
            this.displayMessage({type: 'success', message: this.$t('processes.workflow.add.success.message')});
            this.showProcessFilter = true;
          });
        }
      }).catch(e => {
        this.$root.$emit('workflow-save-failed');
        this.displayMessage( {type: 'error', message: this.saveErrorMessage(e, 'add')});
      });
    },
    /**
     * Words a refused process save: the message of the code the server
     * answered with a 400, worded for an update where its cause differs, the
     * process editor's own wording first, then Forms' label for a Forms code,
     * or the generic message of the save otherwise.
     *
     * @param {Error} error the error of the save
     * @param {String} action add or update
     * @param {Object} workflow the process sent, whose project mode tells
     *          whether the update changed the project
     * @return {String} the message
     */
    saveErrorMessage(error, action, workflow) {
      const code = this.refusal(error).code;
      if (!this.workflowSaveErrorCodes.includes(code)) {
        return this.formsRefusalMessage(code) || this.$t(`processes.workflow.${action}.error.message`);
      }
      // On an update that keeps the project, an ineligible project means the
      // managing space changed with no project of the new space chosen; on one
      // that changes it, the project chosen can no longer receive the requests
      const keptProject = !workflow || !workflow.projectMode;
      return action === 'update' && keptProject && code === 'processes.project.notEligible'
        && this.$t('processes.workflow.updateError.processes.project.spaceNeedsProject')
        || this.$t(`processes.workflow.saveError.${code}`);
    },
    /**
     * Sends a request, then tells the drawer: work-added, or work-add-failed
     * with the refusal's code and field key, each naming the request sent.
     *
     * @param {Object} work the request to send
     * @returns {void}
     */
    addWork(work) {
      this.$processesService.addWork(work).then(newWork => {
        if (newWork) {
          this.$root.$emit('work-added', {work: newWork, draftId: work.draftId, sent: work});
          this.works.unshift(newWork);
          if (work.draftId) {
            const index = this.workDrafts.findIndex(draft => draft.id === work.draftId);
            this.workDrafts.splice(index, 1);
          }
          this.displayMessage({type: 'success', message: this.$t('processes.work.add.success.message')});
          this.requestSubmitted = true;
        }
      }).catch(e => {
        this.$root.$emit('work-add-failed', Object.assign(this.refusal(e), {sent: work}));
        this.displayMessage({type: 'error', message: this.requestRefusalMessage(e, 'processes.work.add.error.message')});
      });
    },
    /**
     * Creates the draft the user's first input in the request drawer starts,
     * silently, then tells the drawer; a refusal tells the drawer too.
     *
     * @param {Object} workDraft the draft to create
     * @returns {Promise} settled once the drawer is told
     */
    createWorkDraft(workDraft) {
      return this.$processesService.createWorkDraft(workDraft).then(draft => {
        if (draft) {
          this.$root.$emit('pre-save-work-draft-added', draft);
          this.workDrafts.unshift(draft);
        }
      }).catch(e => {
        this.$root.$emit('work-draft-save-failed', this.refusal(e));
        this.displayMessage({type: 'error', message: this.requestRefusalMessage(e, 'processes.workDraft.save.error.message')});
      });
    },
    /**
     * Updates a draft, then tells the drawer: work-draft-updated, or
     * work-draft-save-failed with the refusal's code and field key.
     *
     * @param {Object} workDraft the draft to update
     * @returns {Promise} settled once the drawer is told
     */
    updateWorkDraft(workDraft) {
      return this.$processesService.updateWorkDraft(workDraft).then(draft => {
        if (draft) {
          this.$root.$emit('work-draft-updated', draft);
          this.displayMessage({type: 'success', message: this.$t('processes.workDraft.update.success.message')});
        }
      }).catch(e => {
        this.$root.$emit('work-draft-save-failed', this.refusal(e));
        this.displayMessage({type: 'error', message: this.requestRefusalMessage(e, 'processes.workDraft.save.error.message')});
      });
    },
    /**
     * Reads a refused save's error: its message is the code of a 400, then,
     * for a refused field, its form's id and its key (code:formId:fieldKey)
     * or its key alone (code:fieldKey). A code holds no colon and a form id
     * is a number, so splitting on colons tells them apart.
     *
     * @param {Error} error the error of the save
     * @returns {Object} the code, the field's form id and its key, null when none
     */
    refusal(error) {
      const parts = (error && error.message || '').split(':');
      if (parts.length >= 3) {
        return {code: parts[0], formId: parts[1], fieldKey: parts.slice(2).join(':')};
      }
      return {code: parts[0], formId: null, fieldKey: parts.length === 2 ? parts[1] : null};
    },
    /**
     * Gives the label Forms registers for one of its codes: the one worded
     * for the object kind first, under the key Forms' own sections read
     * (formsObject.error.<kind>.<code>), then the general one.
     *
     * @param {String} code the refusal's code
     * @param {String} [kind] the Forms object kind, processes.requestDraft for a request or its draft
     * @returns {String} the label, null for another code or none registered
     */
    formsRefusalMessage(code, kind) {
      if (!code || !code.startsWith('forms.')) {
        return null;
      }
      const key = [kind && `formsObject.error.${kind}.${code}`, `formsObject.error.${code}`].find(candidate => candidate && this.$te(candidate));
      return key && this.$t(key) || null;
    },
    /**
     * Words a refused request or draft save: the label of the code the server
     * answered with a 400, or the generic message of the save otherwise.
     *
     * @param {Error} error the error of the save
     * @param {String} genericKey the generic message's key
     * @returns {String} the message
     */
    requestRefusalMessage(error, genericKey) {
      const code = this.refusal(error).code;
      if (this.requestSaveErrorCodes.includes(code)) {
        return this.$t(`processes.request.saveError.${code}`);
      }
      return this.formsRefusalMessage(code, 'processes.requestDraft') || this.$t(genericKey);
    },
    showConfirmDialog(model, reason){
      this.dialogAction = reason;
      this.targetModel = model;
      if (reason === 'delete_workflow') {
        this.confirmMessage = this.$t('processes.workflow.delete.keepRequests.confirmDialog.message', {0: `<strong>${model.title}</strong>`});
      }
      if (reason === 'cancel_work') {
        this.confirmMessage = this.$t('processes.work.delete.confirmDialog.message');
      }
      if (reason === 'delete_work_draft') {
        this.confirmMessage = this.$t('processes.work.draft.delete.confirmDialog.message');
      }
      if (reason === 'archive_finished_works') {
        // the model is the number of requests the archive takes
        this.confirmMessage = model === 1
          ? this.$t('processes.myRequests.archiveAll.confirm.one')
          : this.$t('processes.myRequests.archiveAll.confirm.many', {0: model});
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
      if (this.dialogAction === 'archive_finished_works') {
        this.archiveFinishedWorks();
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
    /**
     * Updates a process, then reports the outcome; a refusal tells the drawer
     * so it can be saved again.
     *
     * @param {Object} workflow the process to update
     * @param {Object} categories the categories to file it under, and those it leaves
     * @returns {void}
     */
    updateWorkFlow(workflow, categories) {
      this.saving = true;
      this.$processesService.updateWorkflow(workflow).then(newWorkflow => {
        if (newWorkflow) {
          const saved = JSON.parse(newWorkflow);
          return this.saveCategories(saved, categories).then(categoryIds => {
            saved.categoryIds = categoryIds;
            this.$root.$emit('workflow-updated', JSON.stringify(saved));
            this.displayMessage({type: 'success', message: this.$t('processes.workflow.update.success.message')});
          });
        }
      }).catch(e => {
        this.$root.$emit('workflow-save-failed');
        this.displayMessage({type: 'error', message: this.saveErrorMessage(e, 'update', workflow)});
      });
    },
    /**
     * Files a saved process under the categories added in its editor and takes it out of those
     * removed, one at a time: social's link service answers each refusal, which this method reports
     * once, the process staying saved. The category list is read again when anything changed.
     *
     * @param {Object} workflow the saved process
     * @param {Object} categories the chosen, added and removed category ids, or nothing
     * @returns {Promise<Array<Number>>} the categories the process is filed under afterwards
     */
    saveCategories(workflow, categories) {
      const chosen = categories && categories.categoryIds || [];
      if (!categories || !this.$categoryLinkService || (!categories.added.length && !categories.removed.length)) {
        return Promise.resolve(chosen);
      }
      const object = {
        type: 'process',
        id: String(workflow.id),
        spaceId: workflow.parentSpace && Number(workflow.parentSpace.id) || 0,
      };
      const filed = chosen.slice();
      let failed = false;
      const steps = [
        ...categories.added.map(id => () => this.$categoryLinkService.link(id, object)
          .catch(() => {
            failed = true;
            filed.splice(filed.indexOf(id), 1);
          })),
        ...categories.removed.map(id => () => this.$categoryLinkService.unlink(id, object)
          .catch(() => {
            failed = true;
            filed.push(id);
          })),
      ];
      return steps.reduce((done, step) => done.then(step), Promise.resolve())
        .then(() => {
          if (failed) {
            this.displayMessage({type: 'error', message: this.$t('processes.workflow.categories.error')});
          }
          this.$root.$emit('workflow-categories-updated');
          return filed;
        });
    },
    /**
     * Adds to a process to save the title, description and summary per
     * language that changed, so that the process and its translations are
     * saved together.
     *
     * @param {Object} workflow the process to save
     * @param {Object} translations the values per language of each changed
     *          field, and whether there is any
     * @return {Object} the process to send
     */
    withTranslations(workflow, translations) {
      if (!translations || !translations.changed) {
        return workflow;
      }
      return Object.assign({}, workflow, {
        titleTranslations: translations.values.title,
        descriptionTranslations: translations.values.description,
        summaryTranslations: translations.values.summary,
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
