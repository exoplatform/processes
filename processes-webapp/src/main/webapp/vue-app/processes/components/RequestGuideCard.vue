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
    :class="expanded ? 'pa-1' : 'request-guide light-grey-background-color application-border-radius pa-3'">
    <div
      v-if="!expanded"
      ref="nameRow"
      class="d-flex align-center mb-2">
      <process-avatar
        :avatar-url="avatarUrl"
        :icon="workflow.image"
        :title="workflow.title || '?'"
        class="workflow-card-avatar me-3 flex-shrink-0"
        size="32" />
      <div class="text-start font-weight-bold text-break">{{ workflow.title }}</div>
    </div>
    <p
      v-if="workflow.description"
      v-sanitized-html="workflow.description"
      class="text-body-2 text-break mb-2"></p>
    <div v-if="expanded && workflow.summary" id="requestGuideHelp" class="mb-2">
      <div class="text-subtitle-2 font-weight-bold mb-1">{{ $t('processes.work.complete.request.question') }}</div>
      <p v-sanitized-html="workflow.summary" class="text-body-2 text-break mb-0"></p>
    </div>
    <details
      v-else-if="workflow.summary"
      id="requestGuideHelp"
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
 * The read-only guide at the top of the request drawer: the process with its
 * illustration (its name row, which the header's line replaces once scrolled
 * out), its description, a « How to complete my request? » block holding its
 * summary, and the process's own documents, which the requester may read. The
 * requester needs no managing space, so none is named. In the full-screen
 * left pane, whose title already names the process, the guide sits on the
 * pane itself, no card and no name row, and its help is shown open.
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
};
</script>
