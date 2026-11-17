<template>
  <exo-drawer
    @closed="close"
    ref="work"
    class="ProcessesApplication"
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
            <processes-extension-sections
              ref="requestSections"
              :key="`requestSections${openKey}`"
              :params="{workflowId: work.workFlow && work.workFlow.id || null, draftId: hasDraft && work.id || null}"
              hook-id="requestDrawerSections"
              name="ProcessRequestDrawer"
              type="request-drawer-section"
              class="mt-5"
              @changed="onSectionsChanged" />
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
                :entity-type="entityType"
                :preview-workflow-id="!hasDraft && work.workFlow && work.workFlow.id || null"
                :prepare-entity="createDraftForAttachment" />
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
        :disabled="!validWorkDescription && !attachmentsChanged && !sectionsChanged"
        id="sendRequestButton"
        class="btn btn-primary float-e">
        {{ $t('processes.work.sendWork.label') }}
      </v-btn>
    </template>
  </exo-drawer>
</template>

<script>
import RequestDraftMixin from '../js/RequestDraftMixin.js';

export default {
  mixins: [RequestDraftMixin],

  data () {
    return {
      work: {
        description: '',
        workFlow: {},
        draftId: null,
        isDraft: false,
        attachments: []
      },
      attachments: [],
      viewMode: false,
      viewDraft: false,
      editDraft: false,
      maxLength: 1250,
      valid: false,
      saving: false,
      rules: {
        maxLength: len => v => (v || '').length <= len || this.$t('processes.work.form.description.maxLength.message', {0: len}),
      },
      showEditor: false,
      openKey: 0,
      // Codes refusing what the drawer sent for a field, not a value the user typed
      unfixableFieldCodes: [
        'forms.value.unknownField',
        'forms.value.unknownReferenceKind',
        'forms.value.nullNotAllowed',
        'forms.value.numberAsString',
        'forms.value.invalidType',
      ],
      attachmentsUpdated: false,
      updatedAttachments: []
    };
  },
  created(){
    // Loads the modules whose name contains ProcessRequestExtension, which register their sections
    this.$utils.includeExtensions('ProcessRequestExtension');
    this.$root.$on('work-added', event => {
      if (event && event.sent && event.sent !== this.work) {
        // The request sent was an earlier opening's: the drawer now shows another one
        return;
      }
      this.resetAfterSubmit();
      this.resetInputs();
      this.close();
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
    attachmentsChanged() {
      return this.attachmentsUpdated && JSON.stringify(this.updatedAttachments) !== JSON.stringify(this.attachments);
    },
    workDescription() {
      return this.work && this.work.description || '';
    },
    entityType() {
      return this.editDraft || this.viewDraft ? 'workdraft' : 'task';
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
    /**
     * Opens the drawer on a new request of a process, a draft to edit, or a
     * request or draft to view.
     *
     * @param {Object} object the process, the draft or the request
     * @param {String} mode create_work, edit_work_draft, or a view mode
     * @param {Boolean} isDraft true when the request viewed is a draft
     * @param {Boolean} allowSave true when the attachments may be saved
     * @returns {void}
     */
    open(object, mode, isDraft, allowSave) {
      // A new key mounts the sections afresh, with the draft id this opening knows
      this.openKey++;
      if (mode === 'create_work') {
        this.work = {description: '', workFlow: object};
        this.editDraft = false;
        this.viewMode = false;
        this.$root.$emit('update-url-path', 'createRequest', `/${this.work.workFlow.id}/createRequest` );
      } else if (mode === 'edit_work_draft') {
        object.attachments = [];
        this.work = Object.assign({}, object);
        this.viewMode = false;
        this.editDraft = true;
        this.$root.$emit('update-url-path', 'draftDetails', `/myRequests/draftDetails/${this.work.id}`);
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
      this.initDraftState();
      if (mode === 'create_work') {
        // Until the first input creates the draft, which gets copies of them, the process's own documents show read-only
        this.$root.$emit('init-list-attachments', {entityId: this.work.workFlow.id, entityType: 'workflow', preview: true});
      } else {
        this.$root.$emit('init-list-attachments', {
          entityId: this.work && this.work.id || null,
          entityType: this.entityType
        });
      }
      this.initEditor();
      this.attachmentsUpdated = allowSave;
      this.$refs.work.open();
    },
    initEditor() {
      this.$nextTick().then(() => {
        if (this.$refs.requestEditor) {
          this.$refs.requestEditor.initCKEditor(false);
        }
      });
    },
    /**
     * Closes the drawer after saving the draft. A drawer still open is closed
     * first, and its closed event comes back here. When a section holds
     * changes, the close waits for the save, and a refused one is answered by
     * afterRefusedClose, which opens the drawer again only on a refusal the
     * user can act on.
     *
     * @returns {void}
     */
    close() {
      if (this.$refs.work.drawer) {
        this.$refs.work.close();
        return;
      }
      const openKey = this.openKey;
      if (this.saving && this.pendingSubmit) {
        // A sent request closes the drawer itself on work-added; a refused one is answered as a refused save
        this.pendingSubmit.catch(refusal => openKey === this.openKey && this.afterRefusedClose(refusal));
        return;
      }
      const waits = !this.viewMode && !this.submitted && this.sectionsChanged;
      const save = this.viewMode ? Promise.resolve() : this.saveDraft();
      if (waits) {
        save.then(() => openKey === this.openKey && this.finishClose())
          .catch(refusal => openKey === this.openKey && this.afterRefusedClose(refusal));
      } else {
        save.catch(() => null);
        this.finishClose();
      }
    },
    /**
     * Answers a close whose save was refused: a refusal the user can act on
     * opens the drawer again with what was typed, any other closes it, since
     * neither typing nor sending again changes it. The user can act on a
     * refusal that names a field whose value they can change, a message over
     * its length limit, a submit the sections' own check stopped, and a
     * concurrent update, which the next save sends again. A field refused for the shape the drawer sent, rather than for
     * its value, closes the drawer.
     *
     * @param {Object} refusal the refusal's code and field key
     * @returns {void}
     */
    afterRefusedClose(refusal) {
      const code = refusal && refusal.code || '';
      const fieldFixable = refusal && refusal.fieldKey && !this.unfixableFieldCodes.includes(code);
      if (!fieldFixable && !['processes.request.draftInvalid', 'processes.request.sectionsInvalid', 'forms.value.concurrentUpdate'].includes(code)) {
        this.finishClose();
        return;
      }
      if (code === 'processes.request.draftInvalid') {
        this.showEditor = true;
        this.$root.$emit('show-alert', {type: 'error', message: this.$t('processes.work.form.description.maxLength.message', {0: this.maxLength})});
      }
      const draftPath = `/myRequests/draftDetails/${this.work.id}`;
      if (this.hasDraft && !document.location.pathname.includes(draftPath)) {
        // The draft was created while the drawer was closed, which left the address unchanged
        this.$root.$emit('update-url-path', 'draftDetails', draftPath);
      }
      this.$refs.work.open();
    },
    /**
     * Ends a close: the attachments are reset and the address names the list the drawer was opened from.
     * @returns {void}
     */
    finishClose() {
      this.$root.$emit('add-work-drawer-closed');
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
    /**
     * Sends the request.
     *
     * @returns {void}
     */
    addWork() {
      this.submitWork();
    },
    /**
     * Saves the draft when the message editor loses the focus, and hides it.
     *
     * @returns {void}
     */
    blurOnDescriptionComposer() {
      if (!this.viewMode && this.hasDraft) {
        this.saveDraft().catch(() => null);
      }
      this.showEditor = false;
    },
    /**
     * Creates the draft the attachments need, the user's first input being
     * opening them.
     *
     * @returns {Promise} settled once the draft exists
     */
    createDraftForAttachment() {
      return this.saveDraft(true).then(() => {
        if (!this.hasDraft) {
          throw new Error('No draft to attach to');
        }
      });
    }
  }
};
</script>
