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

// How long the request's copy of a document is waited for after the draft's creation
const COPY_TIMEOUT_MS = 10000;
// How long the draft's documents wait before they are read again when the requester comes back, so the focus and
// visibility events of one return read them once
const RETURN_REFRESH_DELAY_MS = 500;

/**
 * The request drawer's documents: opening and filling in the process's
 * documents and their copies in the requester's draft, and the attach button
 * of the message editor. The drawer provides RequestDraftMixin and
 * RequestLayoutMixin, and its messageRow ref, the message row component
 * holding the attachments list. While the drawer is open on a draft, the
 * draft's documents are read again when the requester comes back to the page,
 * from the editor's tab for instance, so a copy filled there shows filled.
 */
export default {
  data: () => ({returnRefreshTimer: null}),
  mounted() {
    window.addEventListener('focus', this.refreshOnReturn);
    document.addEventListener('visibilitychange', this.refreshOnReturn);
  },
  beforeDestroy() {
    window.removeEventListener('focus', this.refreshOnReturn);
    document.removeEventListener('visibilitychange', this.refreshOnReturn);
    window.clearTimeout(this.returnRefreshTimer);
  },
  methods: {
    /**
     * Reads the draft's documents again once the requester is back on the
     * page, while the drawer is open on a draft: the events of one return
     * are gathered into one read.
     *
     * @returns {void}
     */
    refreshOnReturn() {
      if (document.visibilityState === 'hidden' || this.viewMode || !this.hasDraft || !this.$refs.work?.drawer) {
        return;
      }
      window.clearTimeout(this.returnRefreshTimer);
      this.returnRefreshTimer = window.setTimeout(() => {
        this.returnRefreshTimer = null;
        if (this.hasDraft && this.$refs.work?.drawer) {
          this.$root.$emit('init-list-attachments', {entityId: this.work.id, entityType: this.entityType});
        }
      }, RETURN_REFRESH_DELAY_MS);
    },
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
     * Fills in a document, always in a new tab, the drawer staying open. Once
     * the draft exists, the copy opens in the editor at once, inside the
     * click. Before, the tab is opened blank inside the click, since a popup
     * opened after asynchronous work is blocked; then the draft is created,
     * which copies the process's documents into it, and once the requester's
     * copy of this one shows up the tab goes to it in the editor.
     *
     * @param {Object} attachment a process document, or the draft's copy of it
     * @returns {Promise} settled once the tab shows the document, or was closed on a failure
     */
    fillDocument(attachment) {
      if (this.hasDraft) {
        // The copy is known: the editor opens at once, inside the click, since a blank tab told where to go in the
        // same turn stays blank
        this.showDocumentInTab(null, attachment, this.fillMode(attachment));
        return Promise.resolve();
      }
      const tab = window.open('', '_blank');
      return this.createDraftForAttachment()
        .then(() => this.waitForCopy(attachment))
        .then(target => this.showDocumentInTab(tab, target, this.fillMode(target)))
        .catch(() => {
          this.closeDocumentTab(tab);
          this.$root.$emit('show-alert', {type: 'error', message: this.$t('processes.workDraft.save.error.message')});
        });
    },
    /**
     * @param {Object} attachment the request's copy
     * @returns {String} fillform for a form, edit for another document the editor writes, null to read it
     */
    fillMode(attachment) {
      if (this.isDocumentFillable(attachment)) {
        return 'fillform';
      }
      return this.isDocumentEditable(attachment) ? 'edit' : null;
    },
    /**
     * Opens a document in a new tab, the drawer staying open: in the editor,
     * for writing or filling when asked and allowed, in its view mode when
     * the editor supports the type, else in the platform's preview.
     *
     * @param {Object} attachment the document
     * @param {String} [mode] edit or fillform, none (null) to read it
     * @returns {void}
     */
    openDocument(attachment, mode) {
      // Opened inside the click with its address: a blank tab told where to go in the same turn stays blank
      this.showDocumentInTab(null, attachment, mode);
    },
    /**
     * Shows a document in a tab opened by the click: the editor's url when it
     * opens the document, else the tab is closed and the platform's preview
     * shows it.
     *
     * @param {Window} tab the tab opened inside the click, null when the browser refused it
     * @param {Object} attachment the document
     * @param {String} [mode] edit or fillform, none (null) to read it
     * @returns {void}
     */
    showDocumentInTab(tab, attachment, mode) {
      // A process's original only ever opens to be read: only the request's copy is written
      const isOriginal = this.processDocuments.some(processDocument => processDocument.id === attachment.id);
      const writable = mode && !isOriginal && this.hasDraft && attachment.acl && attachment.acl.canEdit;
      if (this.$documentsUtils && (writable || this.isDocumentReadable(attachment))) {
        const url = this.$documentsUtils.getEditorUrl(attachment, writable ? (mode === 'fillform' ? 'fillform' : '') : 'view');
        if (tab) {
          tab.location.href = url;
        } else {
          window.open(url, '_blank');
        }
        return;
      }
      this.closeDocumentTab(tab);
      document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {
        attachments: [{id: attachment.id, filename: attachment.title, mimetype: attachment.mimetype, downloadUrl: attachment.downloadUrl}],
        id: attachment.id,
      }}));
    },
    /**
     * Closes a tab opened for a document that will not show in it.
     *
     * @param {Window} tab the tab, null when there is none
     * @returns {void}
     */
    closeDocumentTab(tab) {
      if (tab && !tab.closed) {
        tab.close();
      }
    },
    /**
     * Waits for the request's copy of a process document to be among the
     * documents to fill in, which the draft's creation fills in.
     *
     * @param {Object} original the process's document
     * @returns {Promise<Object>} the copy, rejected when it does not show up within ten seconds
     */
    waitForCopy(original) {
      // Right after the draft's creation the list may still hold the process's originals, which are no copy
      const originalIds = this.processDocuments.map(attachment => attachment.id);
      const findCopy = () => this.documentsToFill.find(copy => !originalIds.includes(copy.id)
        && this.storedName(copy) === this.storedName(original));
      const found = findCopy();
      if (found) {
        return Promise.resolve(found);
      }
      return new Promise((resolve, reject) => {
        let unwatch = null;
        const timer = window.setTimeout(() => {
          unwatch();
          reject(new Error('The copy of the document did not show up'));
        }, COPY_TIMEOUT_MS);
        unwatch = this.$watch(findCopy, copy => {
          if (copy) {
            window.clearTimeout(timer);
            unwatch();
            resolve(copy);
          }
        });
      });
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
