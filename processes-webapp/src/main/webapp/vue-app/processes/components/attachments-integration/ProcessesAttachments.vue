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
        <v-btn
          @click.stop.prevent="openAddDocumentDrawer"
          class="ms-n2"
          icon>
          <v-icon
            size="15"
            color="primary">
            fa-plus
          </v-icon>
        </v-btn>
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
            :open-in-editor="!isMobileDevice"
            :is-file-editable="isFileEditable(attachment)"
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
      isLoading: false,
      subscribedDocuments: new Map(),
      supportedDocuments: null
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
        openAttachmentsInEditor: true
      };
    },
    attachmentsLength() {
      return this.attachments && this.attachments.length > 0 ? `(${this.attachments.length})` : '';
    },
    isMobileDevice() {
      return (/Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent));
    }
  },
  created() {
    this.initEntityAttachmentsList();
    if (this.workflowParentSpace) {
      this.$spaceService.getSpaceByPrettyName(this.workflowParentSpace.prettyName)
        .then(space => {
          this.drive = {
            name: `${space.groupId.replaceAll('/', '.')}`,
            title: space.displayName,
            isSelected: true
          };
        });
    }
    document.addEventListener('attachment-added', event => {
      if (this.editMode) {
        this.initEntityAttachmentsList();
      } else {
        this.attachments.push(event.detail.attachment);
      }
      this.subscribeDocument(event.detail.attachment.id);
      this.$root.$emit('attachments-updated');
    });
    this.$root.$on('add-new-created-form-document', (doc) => {
      this.attachments.push(doc);
      this.subscribeDocument(doc.id);
      this.$root.$emit('attachments-updated', this.attachments);
    });
    document.addEventListener('attachment-removed', event => {
      const index = this.attachments.findIndex(attachment => attachment.id === event.detail.id);
      if (index >= 0) {
        this.attachments.splice(index, 1);
      }
      this.unsubscribeDocument(event.detail.id);
      this.$root.$emit('attachments-updated', this.attachments);
    });
    this.$root.$on('reset-list-attachments', () => {
      this.attachments = [];
    });
    this.$root.$on('init-list-attachments', event => {
      this.entityIdVal = event.entityId;
      this.entityTypeVal = event.entityType;
      this.initEntityAttachmentsList();
    });
    document.addEventListener('documents-supported-document-types-updated', this.refreshSupportedDocumentExtensions);
    this.refreshSupportedDocumentExtensions();
  },
  beforeDestroy() {
    for (const docId of this.subscribedDocuments.keys()){
      this.unsubscribeDocument(docId);
    }
  },
  methods: {
    isFileEditable(attachment) {
      const type = attachment && attachment.mimetype || '';
      return this.supportedDocuments && this.supportedDocuments.filter(doc => doc.edit && doc.mimeType === type
                                                                                       && !attachment.cloudDriveFile).length > 0;
    },
    refreshSupportedDocumentExtensions () {
      this.supportedDocuments = extensionRegistry.loadExtensions('documents', 'supported-document-types');
    },
    unsubscribeDocument(docId) {
      const self = this;
      const subscription = this.subscribedDocuments.get(docId);
      cCometd.unsubscribe(subscription, {}, function (unsubscribeReply) {
        if (unsubscribeReply.successful) {
          self.subscribedDocuments.delete(docId);
        }
      });
    },
    subscribeDocument(docId) {
      const cometdContext = eXo.env.portal.cometdContext;
      const self = this;
      const subscription = cCometd.subscribe(`/eXo/Application/Onlyoffice/editor/${docId}`, function (message) {
        if (message.data && message.data.type === 'DOCUMENT_CHANGED') {
          self.$root.$emit('attachment-content-updated', docId);
        }
      }, cometdContext, function (subscribeReply) {
        if (subscribeReply.successful) {
          self.subscribedDocuments.set(docId, subscription);
        }
      });
    },
    openAddDocumentDrawer() {
      document.dispatchEvent(new CustomEvent('open-attachments-app-drawer', {detail: this.attachmentDrawerParams}));
    },
    openAttachmentDrawer() {
      if (this.attachments.length === 0) {
        this.openAddDocumentDrawer();
      } else {
        document.dispatchEvent(new CustomEvent('open-attachments-list-drawer', {detail: this.attachmentDrawerParams}));
      }
    },
    initEntityAttachmentsList() {
      if (this.entityTypeVal && this.entityIdVal) {
        this.isLoading = true;
        this.$processesAttachmentService.getEntityAttachments(this.entityTypeVal, this.entityIdVal).then(attachments => {
          attachments.forEach(attachment => {
            attachment.name = attachment.title;
            this.subscribeDocument(attachment.id);
          });
          this.attachments = attachments;
          this.isLoading = false;
        });
      }
    },
  }
};
</script>