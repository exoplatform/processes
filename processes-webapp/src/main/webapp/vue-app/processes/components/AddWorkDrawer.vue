<template>
  <exo-drawer
    @closed="close"
    @opened="watchContentScroll"
    @expand-updated="expanded = $event"
    ref="work"
    :allow-expand="!viewMode"
    full-app-left-class="request-full-left"
    class="ProcessesApplication"
    right>
    <template v-if="expanded && !viewMode" #fullAppLeftTitle>
      <div class="d-flex align-center align-self-stretch flex-grow-1 text-truncate">
        {{ work.workFlow.title }}
      </div>
    </template>
    <template v-if="!viewMode && !$vuetify.breakpoint.smAndDown" #expandAction>
      <v-btn
        id="requestExpandButton"
        :title="$refs.work && $refs.work.expandTooltip"
        icon
        @click="$refs.work.toogleExpand()">
        <v-icon v-text="$refs.work && $refs.work.expandIcon" size="20" />
      </v-btn>
    </template>
    <template v-if="expanded && !viewMode" #fullAppLeftContent>
      <request-guide-card
        :workflow="work.workFlow"
        :avatar-url="workflowAvatarUrl"
        class="ma-4"
        expanded />
    </template>
    <template slot="title">
      <div class="d-flex flex-column text-truncate">
        <span>{{ $t(viewMode ? 'processes.works.work.label' : 'processes.request.newTitle') }}</span>
        <div
          v-if="!expanded"
          id="requestTitleLine"
          :class="{'request-title-line-shown': viewMode || titleLineShown}"
          class="request-title-line d-flex align-center text-caption text-light-color text-truncate">
          <v-avatar
            v-if="workflowAvatarUrl"
            class="me-1 flex-shrink-0"
            size="18">
            <v-img :src="workflowAvatarUrl" />
          </v-avatar>
          <span :style="textDecoration" class="text-truncate">{{ titleLine }}</span>
        </div>
      </div>
    </template>
    <template slot="content">
      <div class="pa-4">
        <request-guide-card
          v-if="!viewMode && !expanded"
          ref="guide"
          :workflow="work.workFlow"
          :avatar-url="workflowAvatarUrl"
          class="mb-5" />
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
          <div class="text-caption text-light-color mt-2">
            <custom-date-format :timestamp="work.createdTime.time" />
          </div>
        </div>
        <v-divider v-if="viewMode" />
        <v-form
          v-if="!viewMode"
          v-model="valid"
          ref="form"
          id="add-work-form">
          <request-fields-section :fill="requestFill">
            <processes-extension-sections
              ref="requestSections"
              :key="`requestSections${openKey}`"
              :params="{workflowId: work.workFlow && work.workFlow.id || null, draftId: hasDraft && work.id || null, hideHeader: true}"
              hook-id="requestDrawerSections"
              name="ProcessRequestDrawer"
              type="request-drawer-section"
              @changed="onSectionsChanged"
              @fill="onSectionsFill" />
            <request-documents-card
              v-if="processDocuments.length"
              :documents="hasDraft ? documentsToFill : processDocuments"
              :has-draft="hasDraft"
              :is-filled="isCopyFilled"
              :name-of="copy => hasDraft ? originalOf(copy).title : null"
              :expanded="expanded"
              @open="openDocument($event)"
              @fill="fillDocument($event)"
              @open-original="openDocument(originalOf($event))" />
            <request-field-row
              id="requestMessageRow"
              :label="$t('processes.work.message.to.manager')"
              :hint="$t('processes.request.message.hint')"
              icon="fas fa-align-left"
              stacked>
              <rich-editor
                id="request-editor"
                ref="requestEditor"
                v-model="work.description"
                :max-length="maxLength"
                :placeholder="$t('processes.works.form.placeholder.workDetail')"
                :tag-enabled="false"
                ck-editor-type="processesRequest"
                class="full-width"
                content-link-enabled
                disable-suggester
                @ready="bindEditorBlur" />
              <processes-attachments
                ref="supportingDocuments"
                v-model="attachments"
                :workflow-parent-space="workflowParentSpace"
                :edit-mode="this.editDraft"
                :entity-id="work.id"
                :entity-type="entityType"
                :hidden-ids="documentsToFillIds"
                :preview-workflow-id="!hasDraft && work.workFlow && work.workFlow.id || null"
                :prepare-entity="createDraftForAttachment"
                class="request-documents full-width mt-1"
                list-only />
            </request-field-row>
          </request-fields-section>
        </v-form>
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
          <p class="text-caption text-light-color mt-1">
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
      <request-drawer-actions
        :fill="requestFill"
        :unfilled-documents="hasDraft ? documentsToFill.filter(copy => !isCopyFilled(copy)).length : processDocuments.length"
        :expanded="expanded"
        :saving="saving"
        :saving-draft="savingAsDraft"
        :can-save-draft="canSaveAsDraft"
        :can-submit="validWorkDescription || attachmentsChanged || sectionsChanged || sectionsFilled && hasDraft"
        @save-draft="saveAsDraft"
        @submit="addWork" />
    </template>
  </exo-drawer>
</template>

<script>
import RequestDraftMixin from '../js/RequestDraftMixin.js';
import RequestDrawerExtrasMixin from '../js/RequestDrawerExtrasMixin.js';
import RequestLayoutMixin from '../js/RequestLayoutMixin.js';
import '../js/RequestEditorExtension.js';

export default {
  mixins: [RequestDraftMixin, RequestDrawerExtrasMixin, RequestLayoutMixin],

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
    document.addEventListener('open-request-attachments', this.openRequestAttachments);
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
  beforeDestroy() {
    document.removeEventListener('open-request-attachments', this.openRequestAttachments);
  },
  computed: {
    /** @returns {Boolean} true when the requester added or removed a document: the copies to fill in do not count */
    attachmentsChanged() {
      const added = list => JSON.stringify((list || []).filter(attachment => !this.documentsToFillIds.includes(attachment.id)));
      return this.attachmentsUpdated && added(this.updatedAttachments) !== added(this.attachments);
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
    /** @returns {String} the header's process line: the process, then its managing space */
    titleLine() {
      const workflow = this.work && this.work.workFlow || {};
      const space = workflow.parentSpace && workflow.parentSpace.displayName;
      return space ? `${workflow.title} · ${space}` : workflow.title;
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
  },
  methods: {
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
          window.setTimeout(() => {
            this.$refs?.requestEditor?.setFocus(true);
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
      this.initExtrasState();
      this.initLayoutState();
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
        window.setTimeout(() => this.$refs?.requestEditor?.setFocus(true), 200);
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
    },
    /**
     * Saves the draft when the message editor loses the focus, once per
     * editor instance the rich editor creates.
     *
     * @returns {void}
     */
    bindEditorBlur() {
      const editor = this.$refs.requestEditor && this.$refs.requestEditor.editor;
      if (editor && editor !== this.blurBoundEditor) {
        // Not reactive: the editor instance whose blur is followed
        this.blurBoundEditor = editor;
        editor.on('blur', this.blurOnDescriptionComposer);
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
    /**
     * Sends the request.
     *
     * @returns {void}
     */
    addWork() {
      this.submitWork();
    },
    /**
     * Saves the draft when the message editor loses the focus.
     *
     * @returns {void}
     */
    blurOnDescriptionComposer() {
      if (!this.viewMode && this.hasDraft) {
        this.saveDraft().catch(() => null);
      }
    },
    /**
     * Creates the draft the attachments need, the user's first input being
     * opening them.
     *
     * @returns {Promise} settled once the draft exists
     */
    /**
     * @param {Object} attachment a document to fill in
     * @returns {Boolean} true when the editor may open it for writing
     */
    isDocumentEditable(attachment) {
      return !!this.$refs.supportingDocuments && this.$refs.supportingDocuments.isFileEditable(attachment);
    },
    /**
     * @param {Object} attachment a document to fill in
     * @returns {Boolean} true when it is a form to fill
     */
    isDocumentFillable(attachment) {
      return !!this.$refs.supportingDocuments && this.$refs.supportingDocuments.isFileFillable(attachment);
    },
    /**
     * @param {Object} attachment a process document
     * @returns {Boolean} true when the editor supports it, so it opens there in view mode
     */
    isDocumentReadable(attachment) {
      return !!this.$refs.supportingDocuments && this.$refs.supportingDocuments.isFileSupported(attachment);
    },
    /**
     * Fills in a document: before the draft exists, creates it, which copies
     * the process's documents into it; then opens the requester's copy in
     * the editor.
     *
     * @param {Object} attachment a process document, or the draft's copy of it
     * @returns {void}
     */
    fillDocument(attachment) {
      if (!this.hasDraft) {
        this.createDraftForAttachment().catch(() => null);
        return;
      }
      if (this.isDocumentFillable(attachment)) {
        this.openDocument(attachment, 'fillform');
      } else if (this.isDocumentEditable(attachment)) {
        this.openDocument(attachment, 'edit');
      } else {
        this.openDocument(attachment);
      }
    },
    /**
     * Opens a document as the platform's attachments do: in the editor, for
     * writing or filling when asked and allowed, in its view mode when the
     * editor supports the type, else in the platform's preview.
     *
     * @param {Object} attachment the document
     * @param {String} [mode] edit or fillform, none to read it
     * @returns {void}
     */
    openDocument(attachment, mode) {
      const writable = mode && this.hasDraft && attachment.acl && attachment.acl.canEdit;
      if (this.$documentsUtils && (writable || this.isDocumentReadable(attachment))) {
        window.open(this.$documentsUtils.getEditorUrl(attachment, writable ? (mode === 'fillform' ? 'fillform' : '') : 'view'), '_blank');
        return;
      }
      document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {
        attachments: [{id: attachment.id, filename: attachment.title, mimetype: attachment.mimetype, downloadUrl: attachment.downloadUrl}],
        id: attachment.id,
      }}));
    },
    /**
     * Opens the drawer adding a document, the message editor's attach button
     * being pressed: the draft is created first when there is none.
     *
     * @returns {void}
     */
    openRequestAttachments() {
      if (!this.viewMode && this.$refs.work && this.$refs.work.drawer && this.$refs.supportingDocuments) {
        this.$refs.supportingDocuments.openAddDocumentDrawer();
      }
    },
    /**
     * @param {Object} copy the request's copy of a process document
     * @returns {Object} the process's original of it, the copy itself when none matches
     */
    originalOf(copy) {
      return this.processDocuments.find(attachment => this.storedName(attachment) === this.storedName(copy)) || copy;
    },
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
