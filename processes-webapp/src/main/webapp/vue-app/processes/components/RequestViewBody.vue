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
      :expanded="expanded"
      readonly
      @open="$emit('open-document', $event)"
      @open-original="$emit('open-original', $event)" />
    <div id="requestDetailMessage" class="requestViewBody-message mb-5">
      <div class="text-header mb-2">{{ $t('processes.work.message.to.manager') }}</div>
      <div
        v-if="work.description"
        v-sanitized-html="work.description"
        class="work-description rich-editor-content"></div>
      <div v-else class="text-light-color">{{ $t('processes.work.description.empty') }}</div>
      <div v-if="messageFiles.length" class="text-subtitle mt-3">{{ $t('processes.request.attachedFiles') }}</div>
      <!-- the request's files but its documents to fill in, listed with no add link: the request is sent -->
      <processes-attachments
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
  created() {
    // Loads the modules whose name contains ProcessRequestDetailExtension, which register the forms' sections
    this.$utils.includeExtensions('ProcessRequestDetailExtension');
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
