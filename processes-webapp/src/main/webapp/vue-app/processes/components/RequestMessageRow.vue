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
  <div
    id="requestMessageRow"
    :aria-label="$t('processes.work.message.to.manager')"
    class="mb-4"
    role="group">
    <rich-editor
      id="request-editor"
      ref="requestEditor"
      v-model="description"
      :max-length="maxLength"
      :placeholder="$t('processes.works.form.placeholder.workDetail')"
      :tag-enabled="false"
      ck-editor-type="processesRequest"
      :aria-label="$t('processes.work.message.to.manager')"
      class="full-width"
      content-link-enabled
      disable-suggester
      @ready="$emit('ready')" />
    <processes-attachments
      ref="supportingDocuments"
      v-model="attachmentList"
      :workflow-parent-space="workflowParentSpace"
      :edit-mode="editDraft"
      :entity-id="entityId"
      :entity-type="entityType"
      :hidden-ids="hiddenIds"
      :preview-workflow-id="previewWorkflowId"
      :prepare-entity="prepareEntity"
      class="request-documents full-width mt-1"
      list-only />
  </div>
</template>

<script>
export default {
  props: {
    /** The request's message, v-model */
    value: {
      type: String,
      default: ''
    },
    /** The request's attachments, synced through the attachments event */
    attachments: {
      type: Array,
      default: () => []
    },
    maxLength: {
      type: Number,
      default: 1250
    },
    workflowParentSpace: {
      type: Object,
      default: null
    },
    editDraft: {
      type: Boolean,
      default: false
    },
    entityId: {
      type: [String, Number],
      default: null
    },
    entityType: {
      type: String,
      default: 'task'
    },
    /** The ids of the documents to fill in, which the list does not repeat */
    hiddenIds: {
      type: Array,
      default: () => []
    },
    previewWorkflowId: {
      type: [String, Number],
      default: null
    },
    /** Creates the draft the attachments need */
    prepareEntity: {
      type: Function,
      default: null
    }
  },
  computed: {
    description: {
      /** @returns {String} the request's message */
      get() {
        return this.value;
      },
      /**
       * @param {String} message the message the editor reports
       * @returns {void}
       */
      set(message) {
        this.$emit('input', message);
      }
    },
    attachmentList: {
      /** @returns {Array} the request's attachments */
      get() {
        return this.attachments;
      },
      /**
       * @param {Array} list the attachments the list reports
       * @returns {void}
       */
      set(list) {
        this.$emit('update:attachments', list);
      }
    }
  },
  methods: {
    /**
     * @returns {Object} the CKEditor instance of the message, none before it is ready
     */
    getEditor() {
      return this.$refs.requestEditor && this.$refs.requestEditor.editor;
    },
    /**
     * Gives the focus to the message editor.
     *
     * @param {Boolean} focus true to focus it
     * @returns {void}
     */
    setFocus(focus) {
      this.$refs.requestEditor?.setFocus(focus);
    },
    /**
     * Creates the editor again, the row being rendered.
     *
     * @returns {void}
     */
    initEditor() {
      if (this.$refs.requestEditor) {
        this.$refs.requestEditor.initCKEditor(false);
      }
    },
    /**
     * Sets the content of the message editor.
     *
     * @param {String} data the message
     * @returns {void}
     */
    setEditorData(data) {
      if (this.$refs.requestEditor) {
        this.$refs.requestEditor.initCKEditorData(data);
      }
    },
    /**
     * @param {Object} attachment a document
     * @returns {Boolean} true when the editor may open it for writing
     */
    isFileEditable(attachment) {
      return !!this.$refs.supportingDocuments && this.$refs.supportingDocuments.isFileEditable(attachment);
    },
    /**
     * @param {Object} attachment a document
     * @returns {Boolean} true when it is a form to fill
     */
    isFileFillable(attachment) {
      return !!this.$refs.supportingDocuments && this.$refs.supportingDocuments.isFileFillable(attachment);
    },
    /**
     * @param {Object} attachment a document
     * @returns {Boolean} true when the editor supports it
     */
    isFileSupported(attachment) {
      return !!this.$refs.supportingDocuments && this.$refs.supportingDocuments.isFileSupported(attachment);
    },
    /**
     * Opens the drawer adding a document, when the list is there.
     *
     * @returns {void}
     */
    openAddDocumentDrawer() {
      if (this.$refs.supportingDocuments) {
        this.$refs.supportingDocuments.openAddDocumentDrawer();
      }
    }
  }
};
</script>
