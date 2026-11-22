<!--
Copyright (C) 2026 eXo Platform SAS.
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.
You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <div class="requestViewBody">
    <!-- the state, the day it was sent, and its task, linked when the viewer may open it -->
    <div id="requestDetailStatus" class="d-flex align-center flex-wrap mb-5">
      <my-request-status-chip
        :status="work.status"
        :is-draft="viewDraft"
        :is-completed="work.completed"
        class="me-3" />
      <span class="text-caption text-light-color">
        <custom-date-format
          :timestamp="work.createdTime && work.createdTime.time"
          :message="$t('processes.myRequests.sentOn')" />
        <template v-if="!viewDraft && taskId">
          ·
          <a
            v-if="taskLink"
            id="requestDetailTaskLink"
            :href="taskLink"
            target="_blank"
            rel="noopener">{{ $t('processes.request.taskNumber', {0: taskId}) }}</a>
          <span v-else>{{ $t('processes.request.taskNumber', {0: taskId}) }}</span>
        </template>
      </span>
    </div>
    <!-- Extension point ProcessRequestDetail / request-detail-section: the request's forms, read-only, the
         requester seeing a form it does not fill in only through its filled values ({view: 'requester'}).
         Its modules load by name: it contains ProcessRequestDetailExtension -->
    <div id="requestDetailFormValues">
      <!-- keyed per opening: the registry reads its parameters only when created -->
      <extension-registry-components
        :key="`requestDetailSections${openKey}`"
        :params="extensionParams"
        name="ProcessRequestDetail"
        type="request-detail-section"
        strict-type />
    </div>
    <request-documents-card
      v-if="documents.length"
      :documents="documents"
      :is-filled="isFilled"
      :name-of="nameOf"
      :can-open-original="canOpenOriginal"
      :expanded="expanded"
      readonly
      @open="$emit('open-document', $event)"
      @open-original="$emit('open-original', $event)" />
    <!-- the message and its files; with neither, no block shows, but the list stays mounted, since it
         loads the request's files, among them the documents to fill in the card shows -->
    <div
      id="requestDetailMessage"
      :class="hasMessage && 'mb-5'"
      class="requestViewBody-message">
      <template v-if="hasMessageText">
        <div class="text-header mb-2">{{ $t('processes.work.message.to.manager') }}</div>
        <div
          v-sanitized-html="work.description"
          class="work-description rich-editor-content"></div>
      </template>
      <!-- the message's files as the task drawer shows a task's attachments, read-only: the platform's link to
           the list drawer, then its cards; the request is sent, and its documents to fill in stay in their card -->
      <template v-if="attachmentAppReady && messageFiles.length">
        <attachment-app
          :key="`requestFiles${openKey}`"
          :entity-id="String(work.id)"
          :entity-type="entityType"
          :excluded-ids="documentIds"
          :class="hasMessageText && 'mt-2'"
          readonly>
          <template #attachmentsList></template>
          <template #attachedFilesList="{attachments}">
            <div class="attachmentsPreview v-card__text ms-3 pa-0">
              <a
                v-if="attachments.length"
                class="viewAllAttachments primary--text font-weight-bold text-decoration-underline">
                {{ $t('attachments.view.all') }} ({{ attachments.length }})
              </a>
            </div>
          </template>
        </attachment-app>
        <notes-attachment-carousel
          :key="`requestCards${openKey}`"
          :entity-id="String(work.id)"
          :entity-type="entityType"
          :space-id="workflowParentSpace && workflowParentSpace.id || null"
          :excluded-ids="documentIds"
          readonly />
      </template>
      <!-- not shown: it loads the request's files, which the document card and the platform list read -->
      <processes-attachments
        v-show="false"
        ref="attachments"
        v-model="attachmentList"
        :workflow-parent-space="workflowParentSpace"
        :entity-id="work.id"
        :entity-type="entityType"
        :hidden-ids="documentIds"
        list-only
        readonly />
    </div>
    <request-comments
      v-if="!viewDraft && work.id"
      ref="comments"
      :key="`requestComments${openKey}`"
      :task="work"
      @can-post="$emit('can-comment', $event)" />
  </div>
</template>

<script>
/**
 * A sent request, read-only, in the request drawer: its state, its forms, its documents to fill in,
 * its message and files, and its comments.
 */
export default {
  props: {
    /** The request viewed */
    work: {
      type: Object,
      default: null
    },
    /** The request's attachments, v-model */
    value: {
      type: Array,
      default: () => []
    },
    viewDraft: {
      type: Boolean,
      default: false
    },
    editDraft: {
      type: Boolean,
      default: false
    },
    entityType: {
      type: String,
      default: 'task'
    },
    workflowParentSpace: {
      type: Object,
      default: null
    },
    /** The request's copies of its process's documents to fill in */
    documents: {
      type: Array,
      default: () => []
    },
    /** Tells whether the requester wrote in a copy */
    isFilled: {
      type: Function,
      default: () => false
    },
    /** Gives the name a copy shows: its original's */
    nameOf: {
      type: Function,
      default: () => null
    },
    /** Tells whether the viewer may open a copy's original */
    canOpenOriginal: {
      type: Function,
      default: () => true
    },
    /** The task's link, null while the viewer may not open the task */
    taskLink: {
      type: String,
      default: null
    },
    expanded: {
      type: Boolean,
      default: false
    },
    /** The drawer's opening, which mounts the forms' sections and the comments afresh */
    openKey: {
      type: Number,
      default: 0
    }
  },
  computed: {
    attachmentList: {
      /** @returns {Array} the request's attachments */
      get() {
        return this.value;
      },
      /**
       * @param {Array} list the attachments the list reports
       * @returns {void}
       */
      set(list) {
        this.$emit('input', list);
      }
    },
    /**
     * Lists the ids of the documents to fill in, which the card shows rather than the message's files.
     *
     * @returns {Array} the ids
     */
    documentIds() {
      return this.documents.map(document => document.id);
    },
    /**
     * Lists the files attached with the message: the request's files but its documents to fill in.
     *
     * @returns {Array} the files
     */
    messageFiles() {
      return (this.value || []).filter(attachment => !this.documentIds.includes(attachment.id));
    },
    /**
     * Reads the id of the request's task.
     *
     * @returns {Number} the task's id, null for a draft
     */
    taskId() {
      return this.work && (this.work.taskId || this.work.id) || null;
    },
    /**
     * Tells whether the request has a message block to show: its text, or files attached with it.
     *
     * @returns {Boolean} true when the request has either
     */
    hasMessage() {
      return this.hasMessageText || this.messageFiles.length > 0;
    },
    /**
     * Tells whether the message holds text, an image or an embed: an editor left empty stores an empty
     * paragraph, which takes no heading.
     *
     * @returns {Boolean} true when the message has content to show
     */
    hasMessageText() {
      const description = this.work && this.work.description || '';
      return /<(img|iframe|video|oembed)\b/i.test(description) || !!description.replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim();
    },
    /**
     * Gives the forms' sections what they need: the process, the request, its task, and that the
     * requester views it.
     *
     * @returns {Object} the parameters
     */
    extensionParams() {
      const workflow = this.work && (this.work.workFlow || this.work.workflow);
      return {
        workflowId: workflow && workflow.id || null,
        workId: this.work && this.work.id || null,
        taskId: this.taskId,
        view: 'requester',
      };
    }
  },
  data: () => ({
    attachmentAppReady: !!Vue.options.components.AttachmentApp && !!Vue.options.components['notes-attachment-carousel'],
  }),
  /**
   * Loads the forms' sections and, when the page has not loaded them yet, the Documents modules that show
   * the request's files.
   *
   * @returns {void}
   */
  created() {
    // Loads the modules whose name contains ProcessRequestDetailExtension, which register the forms' sections
    this.$utils.includeExtensions('ProcessRequestDetailExtension');
    // the task drawer's attachments: the list's link, registered by the Documents integration, and the cards,
    // registered by the Documents attachment application
    if (!this.attachmentAppReady) {
      window.require(['SHARED/AttachmentIntegration', 'PORTLET/documents-portlet/AttachmentApp'], () => this.attachmentAppReady = true);
    }
  },
  methods: {
    /**
     * Tells whether the platform's editor opens a file, so that a sent request's document opens there in
     * view mode rather than in the preview.
     *
     * @param {Object} attachment a document
     * @returns {Boolean} true when the editor supports it
     */
    isFileSupported(attachment) {
      return !!this.$refs.attachments && this.$refs.attachments.isFileSupported(attachment);
    },
    /**
     * Posts the comment written in the composer.
     *
     * @returns {Promise} settled once it is posted
     */
    postComment() {
      return this.$refs.comments ? this.$refs.comments.post() : Promise.resolve();
    }
  }
};
</script>
