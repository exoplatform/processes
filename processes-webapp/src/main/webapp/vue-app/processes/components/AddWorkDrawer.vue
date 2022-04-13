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
            class="pa-1 ml-2 mr-2 font-weight-regular text-truncate-3 text-break grey--text darken-1 font-italic">
            {{ work.workFlow.summary }}
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
              <v-stepper
                class="pe-4"
                v-model="stp"
                vertical>
                <v-stepper-step
                  class="text-uppercase"
                  :complete="stp > 1"
                  step="1">
                  {{ $t('processes.works.form.label.description') }}
                </v-stepper-step>
                <v-stepper-content
                  class="pe-6"
                  step="1">
                  <request-editor
                    required
                    ref="requestEditor"
                    id="request-editor"
                    :placeholder="$t('processes.works.form.placeholder.workDetail')"
                    v-model="work.description" />
                  <custom-counter
                    class="mt-n4 me-4"
                    :value="work.description"
                    :max-length="maxLength" />
                  <v-btn
                    :disabled="!validWorkDescription"
                    :loading="preSaving"
                    class="mt-1 btn btn-primary v-btn--outlined float-e"
                    color="primary"
                    @click="nextStep">
                    {{ $t('processes.works.form.label.continue') }}
                  </v-btn>
                </v-stepper-content>
                <v-stepper-step
                  class="text-uppercase"
                  :complete="stp > 2"
                  step="2">
                  {{ $t('processes.works.form.label.documents') }}
                </v-stepper-step>
                <v-stepper-content
                  class="pt-0 pb-0 pe-3"
                  step="2">
                  <p class="font-weight-regular grey--text darken-1 font-italic mt-1">
                    {{ $t('processes.work.add.attachment.info.message') }}
                  </p>
                  <processes-attachments
                    v-model="attachments"
                    :workflow-parent-space="workflowParentSpace"
                    :edit-mode="this.editDraft"
                    :entity-id="work.id"
                    :entity-type="entityType" />
                  <v-btn
                    class="btn mt-4"
                    @click="previousStep"
                    text>
                    <v-icon size="18" class="me-2">
                      {{ $vuetify.rtl && 'fa-caret-right' || 'fa-caret-left' }}
                    </v-icon>
                    {{ $t('processes.works.form.label.back') }}
                  </v-btn>
                </v-stepper-content>
              </v-stepper>
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
          :disabled="!validWorkDescription"
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
      stp: 1,
      work: {
        description: '',
        workFlow: {},
        draftId: null,
        isDraft: false
      },
      preSavedDraftId: null,
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
    this.$root.$on('work-draft-updated', () => {
      this.savingDraft = false;
      this.resetInputs();
      this.close();
    });
    this.$root.$on('pre-save-work-draft-added', (draft) => {
      this.work = draft;
      this.editDraft = true;
      this.$root.$emit('init-list-attachments', {
        entityId: draft.id,
        entityType: this.entityType
      });
      this.preSaving = false;
      this.stp++;
    });
    this.$root.$on('close-work-drawer', () => {
      this.close();
    });
  },
  computed: {
    entityType() {
      return this.editDraft || this.viewDraft ? 'workdraft' : 'task';
    },
    canUpdateDraft() {
      return this.valid && this.work.description && this.work.description.length > 0 && JSON.stringify(this.work) !== JSON.stringify(this.oldWork);
    },
    validWorkDescription() {
      return this.work && this.work.description && this.$utils.htmlToText(this.work.description).length <= this.maxLength;
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
  methods: {
    updateUrlPath(data, path, replace) {
      if (replace) {
        window.history.replaceState(data, '', `${eXo.env.portal.context}/${eXo.env.portal.portalName}/processes${path}`);
      } else {
        window.history.pushState(data, '', `${eXo.env.portal.context}/${eXo.env.portal.portalName}/processes${path}`);
      }
    },
    open(object, mode, isDraft) {
      if (mode === 'create_work') {
        this.work = {};
        this.work.workFlow = object;
        this.editDraft = false;
        this.viewMode = false;
        this.updateUrlPath('createRequest', `/${this.work.workFlow.id}/createRequest`);
      } else if (mode === 'edit_work_draft') {
        this.work = Object.assign({}, object);
        this.oldWork = Object.assign({}, this.work);
        this.viewMode = false;
        this.editDraft = true;
      } else {
        this.viewDraft = isDraft || false;
        this.editDraft = false;
        this.work = object;
        this.viewMode = true;
        if (!this.viewDraft) {
          this.updateUrlPath('requestDetails', `/myRequests/requestDetails/${this.work.id}`);
        }
      }
      this.$root.$emit('init-list-attachments', {
        entityId: this.work && this.work.id || null,
        entityType: this.entityType
      });
      this.initEditor();
      this.stp = 1;
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
        this.updateUrlPath('myRequest', '/myRequests');
      } else {
        this.updateUrlPath('processes', '');
      }
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
    nextStep() {
      if (this.valid) {
        this.preSaveWork();
      }
    },
    previousStep() {
      this.stp--;
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
    updateDraft() {
      if (!this.viewMode && this.canUpdateDraft) {
        this.savingDraft = true;
        this.toWorkDraft(this.work);
        if (this.workDraft.id && this.workDraft.id !== 0) {
          this.$root.$emit('update-work-draft', this.workDraft);
        } else {
          this.$root.$emit('create-work-draft', {draft: this.workDraft});
        }
      }
    },
    preSaveWork() {
      this.preSaving = true;
      this.toWorkDraft(this.work);
      if (this.workDraft.id === 0) {
        this.$root.$emit('create-work-draft', {draft: this.workDraft, preSave: true});
      } else {
        this.preSaving = false;
        this.stp ++;
      }
    }
  }
};
</script>