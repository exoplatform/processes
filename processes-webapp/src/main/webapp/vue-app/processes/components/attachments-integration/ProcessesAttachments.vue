<!--
Copyright (C) 2021 eXo Platform SAS.
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
  <v-app>
    <div
      class="ma-auto"
      v-if="isLoading">
      <v-progress-circular
        :size="20"
        color="primary"
        indeterminate />
    </div>
    <div v-else>
      <v-icon
        color="primary"
        small>
        fa-paperclip
      </v-icon>
      <a
        @click="openAttachmentDrawer"
        class="viewAllAttachments primary--text font-weight-bold">
        {{ $t('processes.work.add.attachment.label') }} {{ attachmentsLength }}
        <v-icon
          size="medium"
          color="primary">
          fa-plus
        </v-icon>
      </a>
      <create-document-from
        v-if="allowDocFormCreation"
        :drive="drive"
        :entity-type="entityType"
        :entity-id="entityId"
        :edit-mode="editMode" />
      <v-list dense>
        <v-list-item-group>
          <attachment-item
            v-for="attachment in attachments.slice(0, 2)"
            :key="attachment.id"
            :attachment="attachment"
            :can-access="attachment.acl && attachment.acl.canAccess"
            :allow-to-detach="false"
            :allow-to-edit="false"
            allow-to-preview
            small-attachment-icon />
        </v-list-item-group>
      </v-list>
    </div>
  </v-app>
</template>

<script>
export default {
  data () {
    return {
      attachments: this.files,
      entityIdVal: this.entityId,
      entityTypeVal: this.entityType,
      isLoading: false
    };
  },
  props: {
    editMode: {
      type: Boolean,
      default: false,
    },
    entityType: {
      type: String,
      default: null
    },
    entityId: {
      type: Number,
      default: null
    },
    workflowParentSpace: {
      type: Number,
      default: null
    },
    allowDocFormCreation: {
      type: Boolean,
      default: false,
    },
    files: {
      type: Array,
      default: () => {
        return [];
      }
    }
  },
  model: {
    prop: 'files',
    event: 'processes-attachments-updated'
  },
  watch: {
    attachments(value) {
      this.$emit('processes-attachments-updated', value);
    },
  },
  computed: {
    attachmentDrawerParams() {
      return {
        entityType: this.entityType,
        entityId: this.entityId,
        defaultFolder: 'Documents',
        sourceApp: 'processesApp',
        attachments: JSON.parse(JSON.stringify(this.attachments)),
        spaceId: this.workflowParentSpace && this.workflowParentSpace.id,
        attachToEntity: this.editMode,
      };
    },
    attachmentsLength() {
      return this.attachments && this.attachments.length > 0 ? `(${this.attachments.length})` : '';
    },
    drive() {
      if (this.workflowParentSpace) {
        const spaceGroupId = this.workflowParentSpace.groupId.split('/spaces/')[1];
        return {
          name: `.spaces.${spaceGroupId}`,
          title: this.workflowParentSpace.prettyName,
          isSelected: true
        };
      }
      return null;
    }
  },
  created() {
    this.initEntityAttachmentsList();
    document.addEventListener('attachment-added', event => {
      if (this.editMode) {
        this.initEntityAttachmentsList();
      } else {
        this.attachments.push(event.detail.attachment);
      }
    });
    this.$root.$on('add-new-created-form-document', (doc) => {
      this.attachments.push(doc);
    });
    document.addEventListener('attachment-removed', event => {
      const index = this.attachments.findIndex(attachment => attachment.id === event.detail.id);
      if (index >= 0) {
        this.attachments.splice(index, 1);
      }
    });
    this.$root.$on('reset-list-attachments', () => {
      this.attachments = [];
    });
    this.$root.$on('init-list-attachments', event => {
      this.entityIdVal = event.entityId;
      this.entityTypeVal = event.entityType;
      this.initEntityAttachmentsList();
    });
  },
  methods: {
    openAttachmentDrawer() {
      if (this.attachments.length === 0) {
        document.dispatchEvent(new CustomEvent('open-attachments-app-drawer', {detail: this.attachmentDrawerParams}));
      } else {
        document.dispatchEvent(new CustomEvent('open-attachments-list-drawer', {detail: this.attachmentDrawerParams}));
      }
    },
    initEntityAttachmentsList() {
      this.isLoading = true;
      if (this.entityTypeVal && this.entityIdVal) {
        this.$processesAttachmentService.getEntityAttachments(this.entityTypeVal, this.entityIdVal).then(attachments => {
          attachments.forEach(attachment => {
            attachment.name = attachment.title;
          });
          this.attachments = attachments;
          this.isLoading = false;
        });
      }
    },
  }
};
</script>