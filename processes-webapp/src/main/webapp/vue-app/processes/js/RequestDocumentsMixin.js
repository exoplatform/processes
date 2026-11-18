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
 * The request drawer's documents: opening and filling in the process's
 * documents and their copies in the requester's draft, and the attach button
 * of the message editor. The drawer provides RequestDraftMixin and
 * RequestLayoutMixin, and its messageRow ref, the message row component
 * holding the attachments list.
 */
export default {
  methods: {
    /**
     * @param {Object} attachment a document to fill in
     * @returns {Boolean} true when the editor may open it for writing
     */
    isDocumentEditable(attachment) {
      return !!this.$refs.messageRow && this.$refs.messageRow.isFileEditable(attachment);
    },
    /**
     * @param {Object} attachment a document to fill in
     * @returns {Boolean} true when it is a form to fill
     */
    isDocumentFillable(attachment) {
      return !!this.$refs.messageRow && this.$refs.messageRow.isFileFillable(attachment);
    },
    /**
     * @param {Object} attachment a process document
     * @returns {Boolean} true when the editor supports it, so it opens there in view mode
     */
    isDocumentReadable(attachment) {
      return !!this.$refs.messageRow && this.$refs.messageRow.isFileSupported(attachment);
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
      if (!this.viewMode && this.$refs.work && this.$refs.work.drawer && this.$refs.messageRow) {
        this.$refs.messageRow.openAddDocumentDrawer();
      }
    },
    /**
     * @param {Object} copy the request's copy of a process document
     * @returns {Object} the process's original of it, the copy itself when none matches
     */
    originalOf(copy) {
      return this.processDocuments.find(attachment => this.storedName(attachment) === this.storedName(copy)) || copy;
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
