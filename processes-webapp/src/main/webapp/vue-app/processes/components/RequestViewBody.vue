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
  <div>
    <div class="pb-4">
      <request-status
        class="float-e"
        :is-draft="editDraft || viewDraft"
        :status="work.status" />
      <v-label class="mb-1">
        {{ $t('processes.works.form.label.requestDate') }}
      </v-label>
      <div class="text-caption text-light-color mt-2">
        <custom-date-format :timestamp="work.createdTime.time" />
      </div>
    </div>
    <v-divider />
    <div class="mt-5 mb-6">
      <v-label for="description">
        {{ $t('processes.works.form.label.workDetail') }}
      </v-label>
      <v-divider class="mt-5" />
      <div
        class="text-truncate-8 pa-2 mt-3 work-description"
        v-sanitized-html="work.description"></div>
    </div>
    <v-divider />
    <div class="mt-4">
      <v-label for="attachment">
        {{ $t('processes.works.form.label.attachment') }}
      </v-label>
      <p class="text-caption text-light-color mt-1">
        {{ $t('processes.work.add.attachment.info.message') }}
      </p>
      <processes-attachments
        v-model="attachmentList"
        :workflow-parent-space="workflowParentSpace"
        :edit-mode="true"
        :entity-id="work.id"
        :entity-type="entityType" />
      <div v-if="!viewDraft" class="taskCommentsAndChanges">
        <v-divider class="mt-1" />
        <task-view-all-comments
          class="mt-4"
          :task="work"
          :comments="work.comments" />
      </div>
    </div>
  </div>
</template>

<script>
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
    }
  }
};
</script>
