<template>
  <v-app id="addWorkDrawer">
    <exo-drawer
      @closed="close"
      ref="work"
      right>
      <template slot="title">
        <v-container
          class="pa-0"
          no-gutters>
          <v-row
            class="pa-0"
            align="center">
            <v-col
              cols="3">
              <span>{{ $t('processes.works.work.label') }}</span>
            </v-col>
            <v-col
              cols="8"
              class="pa-0 text-align-start">
              <p class="work-title white--text text-truncate pa-2">
                <v-avatar
                  v-if="workflowAvatarUrl"
                  class="me-1"
                  color="blue"
                  size="18px">
                  <v-img :src="workflowAvatarUrl" />
                </v-avatar>
                <span
                  :style="textDecoration"
                  class="white--text">
                  {{ this.work.workFlow.title }}
                </span>
              </p>
            </v-col>
          </v-row>
        </v-container>
      </template>
      <template slot="content">
        <div class="pa-4">
          <p
            v-if="!viewMode"
            class="ml-2 mr-2 font-weight-bold">
            {{ $t('processes.work.complete.request.question') }}
          </p>
          <p
            v-if="!viewMode"
            v-sanitized-html="work.workFlow.summary"
            class="pa-1 ml-2 mr-2 font-weight-regular text-caption text-truncate-6 text-break grey--text darken-1 font-italic">
          </p>
          <div
            class="pb-4"
            v-if="viewMode">
            <request-status
              class="float-e"
              :is-draft="editDraft || viewDraft"
              :status="work.status" />
            <v-label class="mb-1">
              {{ $t('processes.works.form.label.requestDate') }}
            </v-label>
            <div class="grey--text mt-2">
              <custom-date-format :timestamp="work.createdTime.time" />
            </div>
          </div>
          <v-divider />
          <div
            v-if="!viewMode"
            class="mt-5 mb-8">
            <v-form
              v-model="valid"
              ref="form"
              id="add-work-form">
              <div class="work-message">
                <p class="mt-5 font-weight-bold">{{ $t('processes.work.message.to.manager') }}</p>
                <p
                  v-if="!showEditor"
                  @click="showRequestEditor"
                  v-sanitized-html="workDescription || $t('processes.work.message.to.manager.placeholder')"
                  :class="workDescription?'':'grey--text'"
                  class="text--darken-1 text-break">
                </p>
                <div v-else>
                  <request-editor
                    @blur="blurOnDescriptionComposer"
                    class="ml-2 mr-2"
                    required
                    ref="requestEditor"
                    id="request-editor"
                    :auto-focus="true"
                    :placeholder="$t('processes.works.form.placeholder.workDetail')"
                    v-model="work.description" />
                  <custom-counter
                    class="mt-n4 me-4"
                    :value="work.description"
                    :max-length="maxLength" />
                </div>
              </div>
              <div class="mt-7">
                <v-divider />
                <p class="mt-5 font-weight-bold">{{ $t('processes.works.form.label.documents') }}</p>
                <p class="font-weight-regular text-caption grey--text darken-1 font-italic">
                  {{ $t('processes.work.add.attachment.info.message') }}
                </p>
                <processes-attachments
                  v-model="attachments"
                  :workflow-parent-space="workflowParentSpace"
                  :edit-mode="this.editDraft"
                  :entity-id="work.id"
                  :entity-type="entityType" />
              </div>
            </v-form>
          </div>
          <div
            v-if="viewMode"
            class="mt-5 mb-6">
            <v-label for="description">
              {{ $t('processes.works.form.label.workDetail') }}
            </v-label>
            <v-divider class="mt-5" />
            <div
              class="text-truncate-8 pa-2 mt-3 work-description"
              v-if="viewMode"
              v-sanitized-html="work.description"></div>
          </div>
          <v-divider v-if="viewMode" />
          <div
            v-if="viewMode"
            class="mt-4">
            <v-label for="attachment">
              {{ $t('processes.works.form.label.attachment') }}
            </v-label>
            <p class="font-weight-regular grey--text darken-1 font-italic mt-1">
              {{ $t('processes.work.add.attachment.info.message') }}
            </p>
            <processes-attachments
              v-model="attachments"
              :workflow-parent-space="workflowParentSpace"
              :edit-mode="viewMode"
              :entity-id="work.id"
              :entity-type="entityType" />
            <div v-if="viewMode && !viewDraft" class="taskCommentsAndChanges">
              <v-divider class="mt-1" />
              <task-view-all-comments
                class="mt-4"
                :task="this.work"
                :comments="this.work.comments" />
            </div>
          </div>
        </div>
      </template>
      <template slot="footer" v-if="!viewMode">
        <v-btn
          :loading="saving"
          @click="addWork"
          :disabled="!validWorkDescription && !attachmentsChanged"
          class="btn btn-primary float-e">
          {{ $t('processes.work.sendWork.label') }}
        </v-btn>
      </template>
    </exo-drawer>
  </v-app>
</template>

<script>

export default {

  data () {
    return {
      work: {
        description: '',
        workFlow: {},
        draftId: null,
        isDraft: false,
        attachments: []
      },
      preSaving: false,
      attachments: [],
      oldWork: {},
      workDraft: {},
      viewMode: false,
      viewDraft: false,
      editDraft: false,
      maxLength: 1250,
      valid: false,
      saving: false,
      savingDraft: false,
      rules: {
        maxLength: len => v => (v || '').length <= len || this.$t('processes.work.form.description.maxLength.message', {0: len}),
      },
      showEditor: false,
      firstCreation: false,
      attachmentsUpdated: false,
      updatedAttachments: []
    };
  },
  created(){
    this.$root.$on('work-added', () => {
      this.saving = false;
      this.resetInputs();
      this.close();
    });
    this.$root.$on('work-draft-added', () => {
      this.savingDraft = false;
      this.resetInputs();
      this.close();
    });
    this.$root.$on('work-draft-updated', (draft) => {
      this.savingDraft = false;
      const draftObject = JSON.parse(draft);
      this.work.description = draftObject.description;
      this.oldWork = Object.assign({}, this.work);
    });
    this.$root.$on('pre-save-work-draft-added', (draft) => {
      this.work = draft;
      this.work.attachments = draft.attachments || [];
      this.oldWork = Object.assign({}, this.work);
      this.editDraft = true;
      this.$root.$emit('init-list-attachments', {
        entityId: draft.id,
        entityType: this.entityType
      });
      this.preSaving = false;
      this.firstCreation = true;
    });
    this.$root.$on('close-work-drawer', () => {
      this.close();
    });
    this.$root.$on('attachments-updated', (updatedAttachments) => {
      this.attachmentsUpdated = true;
      this.updatedAttachments = updatedAttachments;
    });
    this.$root.$on('attachment-content-updated', (docId) => {
      const index = this.attachments.findIndex(attachment => attachment.id === docId);
      if (index !== -1) {
        this.attachmentsUpdated = true;
      }
    });
  },
  computed: {
    hasChanges() {
      return JSON.stringify(this.work) !== JSON.stringify(this.oldWork);
    },
    attachmentsChanged() {
      return this.attachmentsUpdated && JSON.stringify(this.updatedAttachments) !== JSON.stringify(this.attachments);
    },
    workDescription() {
      return this.work && this.work.description || '';
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    entityType() {
      return this.editDraft || this.viewDraft ? 'workdraft' : 'task';
    },
    canUpdateDraft() {
      return this.valid && this.emptyOrValidWorkDescription && this.hasChanges;
    },
    validWorkDescription() {
      return this.work?.description?.length > 0 && this.$utils.htmlToText(this.work.description).length <= this.maxLength;
    },
    emptyOrValidWorkDescription() {
      return this.validWorkDescription || !this.work?.description?.length;
    },
    workflowParentSpace() {
      return this.work && this.work.workflow && this.work.workflow.parentSpace;
    },
    textDecoration() {
      return this.work && this.work.completed && 'text-decoration:line-through' || '';
    },
    workflowAvatarUrl() {
      return this.work.workFlow && this.work.workFlow.illustrativeAttachment
                                && this.work.workFlow.illustrativeAttachment.id
                                && `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/illustration/${this.work.workFlow.id}`;
    }
  },
  watch: {
    showEditor(value) {
      if (value) {
        setTimeout(() => {
          this.$refs.requestEditor.setFocus();
        }, 200);
      }
    }
  },
  methods: {
    showRequestEditor() {
      this.showEditor = !this.showEditor;
    },
    open(object, mode, isDraft) {
      if (mode === 'create_work') {
        this.work = {};
        this.work.workFlow = object;
        this.editDraft = false;
        this.viewMode = false;
        this.$root.$emit('update-url-path', 'createRequest', `/${this.work.workFlow.id}/createRequest` );
        this.preSaveWork();
      } else if (mode === 'edit_work_draft') {
        object.attachments = [];
        this.work = Object.assign({}, object);
        this.oldWork = Object.assign({}, this.work);
        this.viewMode = false;
        this.editDraft = true;
        this.firstCreation = false;
        this.$root.$on('can-show-request-editor',() => {
          this.showEditor = true;
          window.setTimeout(() => {
            this.$refs?.requestEditor?.setFocus();
          }, 200);
        });
      } else {
        this.viewDraft = isDraft || false;
        this.editDraft = false;
        this.work = object;
        this.viewMode = true;
        if (!this.viewDraft) {
          this.$root.$emit('update-url-path', 'requestDetails', `/myRequests/requestDetails/${this.work.id}`);
        }
      }
      this.$root.$emit('init-list-attachments', {
        entityId: this.work && this.work.id || null,
        entityType: this.entityType
      });
      this.initEditor();
      this.attachmentsUpdated = false;
      this.$refs.work.open();
    },
    initEditor() {
      this.$nextTick().then(() => {
        if (this.$refs.requestEditor) {
          this.$refs.requestEditor.initCKEditor(false);
        }
      });
    },
    close() {
      this.updateDraft();
      this.$refs.work.close();
      this.$root.$emit('reset-list-attachments');
      if (document.location.pathname.includes('myRequests')) {
        this.$root.$emit('update-url-path', 'myRequest', '/myRequests');
      } else {
        this.$root.$emit('update-url-path', 'processes', '');
      }
      this.showEditor = false;
    },
    resetInputs() {
      this.work = {
        description: '',
        workFlow: {},
      };
      if (this.$refs.requestEditor) {
        this.$refs.requestEditor.initCKEditorData('');
      }
    },
    addWork() {
      this.saving = true;
      if (this.editDraft) {
        this.work.draftId = this.work.id;
        this.work.id = 0;
      }
      this.$root.$emit('add-work', this.work);
    },
    toWorkDraft(work) {
      this.workDraft.id = this.work.id || 0;
      this.workDraft.title = work.title || '';
      this.workDraft.createdTime = work.createdTime;
      this.workDraft.creatorId = work.creatorId;
      this.workDraft.description = work.description || '';
      this.workDraft.workFlow = work.workFlow;
      this.workDraft.isDraft = true;
    },
    blurOnDescriptionComposer() {
      if (!this.viewMode && this.canUpdateDraft) {
        this.savingDraft = true;
        this.toWorkDraft(this.work);
        if (this.workDraft.id && this.workDraft.id !== 0) {
          this.$root.$emit('update-work-draft', this.workDraft);
          this.firstCreation = false;
        }
      }
      this.showEditor = false;
    },
    updateDraft() {
      if (!this.viewMode && this.canUpdateDraft) {
        this.savingDraft = true;
        this.toWorkDraft(this.work);
        if (this.workDraft.id && this.workDraft.id !== 0) {
          this.$root.$emit('update-work-draft', this.workDraft);
          this.firstCreation = false;
        } else if (this.workDraft && this.workDraft.workflow) {
          this.$root.$emit('create-work-draft', {draft: this.workDraft});
        }
      } else if (this.firstCreation && !this.hasChanges && !this.attachmentsChanged) {
        this.toWorkDraft(this.work);
        this.$root.$emit('show-alert', {
          type: 'warning',
          messageTargetModel: this.workDraft,
          messageAction: 'keep-work-draft',
          messageActionLabel: this.$t('processes.work.keep.draft.label'),
          message: this.isMobile ? this.$t('processes.work.keep.draft.warn.message.question')
            : this.$t('processes.work.keep.draft.warn.message')
        });
      }
    },
    preSaveWork() {
      this.preSaving = true;
      this.toWorkDraft(this.work);
      if (this.workDraft.id === 0) {
        this.$root.$emit('create-work-draft', {draft: this.workDraft, preSave: true});
      } else {
        this.preSaving = false;
      }
    }
  }
};
</script>