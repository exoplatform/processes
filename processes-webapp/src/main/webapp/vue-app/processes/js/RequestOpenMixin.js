/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Opening the request drawer, the message editor's life cycle, and the
 * events of the app that close it or report attachments. The drawer provides
 * RequestDraftMixin, RequestDrawerExtrasMixin, RequestLayoutMixin and
 * RequestCloseMixin, its work and messageRow refs, and the attachments the
 * request holds.
 */
export default {
  data: () => ({
    attachmentsUpdated: false,
    updatedAttachments: []
  }),
  computed: {
    /** @returns {Boolean} true when the requester added or removed a document: the copies to fill in do not count */
    attachmentsChanged() {
      const added = list => JSON.stringify((list || []).filter(attachment => !this.documentsToFillIds.includes(attachment.id)));
      return this.attachmentsUpdated && added(this.updatedAttachments) !== added(this.attachments);
    }
  },
  created() {
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
            this.$refs?.messageRow?.setFocus(true);
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
      this.initViewState();
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
    /**
     * Creates the message editor again, once the opening is rendered.
     *
     * @returns {void}
     */
    initEditor() {
      this.$nextTick().then(() => this.$refs.messageRow && this.$refs.messageRow.initEditor());
    },
    /**
     * Saves the draft when the message editor loses the focus, once per
     * editor instance the rich editor creates.
     *
     * @returns {void}
     */
    bindEditorBlur() {
      const editor = this.$refs.messageRow && this.$refs.messageRow.getEditor();
      if (editor && editor !== this.blurBoundEditor) {
        // Not reactive: the editor instance whose blur is followed
        this.blurBoundEditor = editor;
        editor.on('blur', this.blurOnDescriptionComposer);
      }
    },
    /**
     * Empties the request and its message editor.
     *
     * @returns {void}
     */
    resetInputs() {
      this.work = {
        description: '',
        workFlow: {},
      };
      if (this.$refs.messageRow) {
        this.$refs.messageRow.setEditorData('');
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
    }
  }
};
