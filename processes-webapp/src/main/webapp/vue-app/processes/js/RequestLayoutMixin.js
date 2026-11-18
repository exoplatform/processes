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
 * The request drawer's layout state: the process's own documents, which the
 * guide lists and which tell the request's documents to fill in (a draft
 * document named as a process document is the requester's copy of it) from
 * its supporting documents; the fill state of the whole « Your request »
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
    /** @returns {Array} the names of the process's documents */
    processDocumentNames() {
      return this.processDocuments.map(attachment => attachment.title);
    },
    /** @returns {Array} the request's copies of the process's documents, for the requester to fill in */
    documentsToFill() {
      return (this.attachments || []).filter(attachment => this.processDocumentNames.includes(attachment.title));
    },
    /** @returns {Number} how many documents the requester added */
    supportingDocumentsCount() {
      return (this.attachments || []).length - this.documentsToFill.length;
    },
    /**
     * The fill state of the whole section: the form's fields, plus the
     * message and the supporting documents, neither of them required.
     *
     * @returns {Object} {total, filled, missing}
     */
    requestFill() {
      const fill = this.sectionsFill;
      return {
        total: fill.total + 2,
        filled: fill.filled + (this.descriptionHasText ? 1 : 0) + (this.supportingDocumentsCount > 0 ? 1 : 0),
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
      this.$nextTick(this.watchContentScroll);
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
     * Follows the scroll of the drawer's content, which the drawer mounts
     * once.
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
      const guide = this.$refs.guide;
      this.titleLineShown = !!guide && this.scrolledContent.scrollTop > guide.nameRowBottom();
    },
  },
};
