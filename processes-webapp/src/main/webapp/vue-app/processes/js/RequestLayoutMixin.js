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
 * The file name a document is stored under, the last segment of its path: a
 * draft's copy of a process document keeps its original's, while its title
 * gets the requester's name and the date.
 *
 * @param {Object} attachment a listed document
 * @returns {String} its stored file name
 */
function storedName(attachment) {
  const path = attachment && attachment.path || '';
  return path.substring(path.lastIndexOf('/') + 1);
}

/**
 * The request drawer's layout state: the process's own documents, which the
 * guide lists and which tell the request's documents to fill in (a draft
 * document stored under a process document's file name is the requester's
 * copy of it) from its supporting documents; the fill state of the whole « Your request »
 * section; and the header's process line, shown once the content is scrolled
 * past the guide's name row. The drawer provides work, attachments,
 * sectionsFill, descriptionHasText, viewMode and openKey, and calls
 * initLayoutState() from each opening.
 */
export default {
  data: () => ({
    processDocuments: [],
    titleLineShown: false,
  }),
  computed: {
    /** @returns {Array} the request's copies of the process's documents, for the requester to fill in */
    documentsToFill() {
      const names = this.processDocuments.map(storedName);
      return (this.attachments || []).filter(attachment => names.includes(storedName(attachment)));
    },
    /** @returns {Array} the ids of the documents to fill in, which the supporting documents' list leaves out */
    documentsToFillIds() {
      return this.documentsToFill.map(attachment => attachment.id);
    },
    /** @returns {Number} how many documents the requester added */
    supportingDocumentsCount() {
      return (this.attachments || []).length - this.documentsToFill.length;
    },
    /**
     * A copy keeps its original's dates until it is written, so a copy whose
     * update date differs from its original's was filled in.
     *
     * @returns {Boolean} true once the requester wrote in a document to fill in
     */
    documentFilled() {
      return this.hasDraft && this.documentsToFill.some(this.isCopyFilled);
    },
    /**
     * The fill state of the whole section: the form's fields, plus the
     * message, the documents to fill in when the process has any, and the
     * supporting documents, none of them required.
     *
     * @returns {Object} {total, filled, missing}
     */
    requestFill() {
      const fill = this.sectionsFill;
      const hasDocuments = this.processDocuments.length > 0;
      return {
        total: fill.total + 2 + (hasDocuments ? 1 : 0),
        filled: fill.filled + (this.descriptionHasText ? 1 : 0) + (this.documentFilled ? 1 : 0)
          + (this.supportingDocumentsCount > 0 ? 1 : 0),
        missing: fill.missing || [],
      };
    },
  },
  beforeDestroy() {
    if (this.scrolledContent) {
      this.scrolledContent.removeEventListener('scroll', this.onContentScroll);
    }
  },
  methods: {
    /**
     * Starts the layout state of a newly opened drawer, and reads the
     * process's documents, which the requester's session may read.
     *
     * @returns {void}
     */
    initLayoutState() {
      const openKey = this.openKey;
      const workflowId = this.work && this.work.workFlow && this.work.workFlow.id;
      this.processDocuments = [];
      this.titleLineShown = false;
      if (this.viewMode || !workflowId) {
        return;
      }
      this.$processesAttachmentService.getEntityAttachments('workflow', workflowId)
        .then(attachments => {
          if (openKey === this.openKey) {
            this.processDocuments = (attachments || []).filter(attachment => attachment.acl && attachment.acl.canAccess)
              .map(attachment => Object.assign(attachment, {name: attachment.title}));
          }
        })
        .catch(() => null);
    },
    /**
     * @param {Object} copy the request's copy of a process document
     * @returns {Boolean} true once the requester wrote in it: a copy keeps its original's update date until then
     */
    isCopyFilled(copy) {
      const original = this.processDocuments.find(attachment => storedName(attachment) === storedName(copy));
      return !!original && !!copy.updated && copy.updated !== original.updated;
    },
    /**
     * Follows the scroll of the drawer's content, which the drawer mounts on
     * its first opening: called once it is opened.
     *
     * @returns {void}
     */
    watchContentScroll() {
      const content = this.$refs.work && this.$refs.work.$el && this.$refs.work.$el.querySelector('.drawerContent');
      if (content && content !== this.scrolledContent) {
        // Not reactive: the element whose scroll is followed
        this.scrolledContent = content;
        content.addEventListener('scroll', this.onContentScroll, {passive: true});
      }
    },
    /**
     * Shows the header's process line once the guide's name row scrolled out.
     *
     * @returns {void}
     */
    onContentScroll() {
      const row = this.$refs.guide && this.$refs.guide.$refs.nameRow;
      this.titleLineShown = !!row && row.getBoundingClientRect().bottom < this.scrolledContent.getBoundingClientRect().top;
    },
  },
};
