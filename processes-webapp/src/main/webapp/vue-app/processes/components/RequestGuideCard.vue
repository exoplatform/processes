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
    id="requestGuideCard"
    :class="expanded ? 'white' : 'light-grey-background-color'"
    class="request-guide application-border-radius pa-3">
    <div ref="nameRow" class="d-flex align-center mb-2">
      <v-avatar
        :class="!showIllustration && 'workflow-card-avatar-placeholder primary--text'"
        class="workflow-card-avatar me-3 flex-shrink-0"
        size="32">
        <v-img
          v-if="showIllustration"
          :src="avatarUrl"
          @error="illustrationFailed = true" />
        <span v-else class="font-weight-bold">{{ initial }}</span>
      </v-avatar>
      <div class="text-start text-truncate">
        <div v-if="!expanded || !spaceName" class="font-weight-bold text-break">{{ workflow.title }}</div>
        <div v-if="spaceName" class="text-caption text-light-color text-truncate">{{ spaceName }}</div>
      </div>
    </div>
    <p
      v-if="workflow.description"
      v-sanitized-html="workflow.description"
      class="text-body-2 text-break mb-2"></p>
    <details
      v-if="workflow.summary"
      id="requestGuideHelp"
      :open="expanded"
      class="mb-2">
      <summary class="primary--text font-weight-bold clickable">{{ $t('processes.work.complete.request.question') }}</summary>
      <p v-sanitized-html="workflow.summary" class="text-body-2 text-break mt-2 mb-0"></p>
    </details>
    <div
      v-if="documents.length"
      id="requestReferenceDocuments"
      class="text-body-2">
      <span class="text-light-color">{{ $t('processes.request.guide.referenceDocuments') }}</span>
      <v-list class="request-documents pa-0 transparent" dense>
        <attachment-item
          v-for="attachment in documents"
          :key="attachment.id"
          :attachment="attachment"
          :attachments="documents"
          :can-access="attachment.acl && attachment.acl.canAccess"
          :allow-to-detach="false"
          :entity-id="workflow.id"
          entity-type="workflow"
          :open-in-editor="true"
          :is-file-readable="readable(attachment)"
          allow-to-preview
          small-attachment-icon />
      </v-list>
    </div>
  </div>
</template>

<script>
/**
 * The read-only guide at the top of the request drawer, and its full-screen
 * left pane, where the pane's title already names the process: the process
 * with its managing space, its description, a
 * « How to complete my request? » block holding its summary, open in full
 * screen, and the process's own documents, which the requester may read.
 */
export default {
  props: {
    workflow: {
      type: Object,
      default: () => ({}),
    },
    avatarUrl: {
      type: String,
      default: null,
    },
    documents: {
      type: Array,
      default: () => [],
    },
    expanded: {
      type: Boolean,
      default: false,
    },
    // Tells whether the editor supports a document, which then opens there in view mode
    readable: {
      type: Function,
      default: () => false,
    },
  },
  data: () => ({illustrationFailed: false}),
  computed: {
    /** @returns {Boolean} true when the process's illustration is shown, its initial otherwise, as on the home cards */
    showIllustration() {
      return !!this.avatarUrl && !this.illustrationFailed;
    },
    /** @returns {String} the managing space's name, null when the process has none */
    spaceName() {
      return this.workflow.parentSpace && this.workflow.parentSpace.displayName || null;
    },
    /** @returns {String} the process title's first letter, for a process with no illustration */
    initial() {
      return (this.workflow.title || '?').trim().charAt(0).toUpperCase();
    },
  },
  watch: {
    avatarUrl() {
      this.illustrationFailed = false;
    },
  },
};
</script>
