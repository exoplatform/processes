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
  <div>
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
            :attachments="attachments"
            :can-access="attachment.acl && attachment.acl.canAccess"
            :allow-to-detach="false"
            :open-in-editor="true"
            :is-file-editable="!preview && isFileEditable(attachment)"
            :is-file-fillable="!preview && isFileFillable(attachment)"
            :is-file-readable="preview && isFileSupported(attachment)"
            allow-to-preview
            small-attachment-icon />
        </v-list-item-group>
      </v-list>
    </div>
  </div>
</template>

<script>
export default {
  data () {
    return {
      attachments: this.files,
      entityIdVal: this.entityId || this.previewWorkflowId,
      entityTypeVal: this.entityId || !this.previewWorkflowId ? this.entityType : 'workflow',
      // True while the list shows another entity's documents, read-only (the editor's view mode), the readable ones only
      preview: !this.entityId && !!this.previewWorkflowId,
      listRequest: 0,
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
    editWorkflow: {
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
    },
    /**
     * Called before the attachments drawer opens while there is no entity:
     * it resolves once the entity exists, the drawer then attaching to it,
     * and rejects to keep the drawer closed.
     */
    prepareEntity: {
      type: Function,
      default: null
    },
    /**
     * A process whose documents the list shows read-only while there is no
     * entity, when the list is created; afterwards init-list-attachments
     * names what to show.
     */
    previewWorkflowId: {
      type: Number,
      default: null
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
        openAttachmentsInEditor: true,
        canFillAttachments: !this.editWorkflow
      };
    },
    attachmentsLength() {
      return this.attachments && this.attachments.length > 0 ? `(${this.attachments.length})` : '';
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
      this.attachments.push(event.detail.attachment);
      this.subscribeDocument(event.detail.attachment.id);
      this.$root.$emit('attachments-updated',this.attachments);
    });
    this.$root.$on('add-new-created-form-document', (doc) => {
      doc.acl.canEdit = true;
      doc.acl.canView = true;
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
      this.preview = !!event.preview;
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
    /**
     * @param {Object} attachment a listed document
     * @returns {Boolean} true when the editor supports the document's type, so it opens there in view mode: the
     *          test attachment-item makes before leaving a document out of its preview
     */
    isFileSupported(attachment) {
      const type = attachment && attachment.mimetype || '';
      return !!this.supportedDocuments && this.supportedDocuments.some(doc => doc.mimeType === type);
    },
    isFileFillable(attachment) {
      const type = attachment && attachment.mimetype || '';
      return !this.editWorkflow && type === 'application/pdf';
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
    /**
     * Opens the drawer that adds a document to the entity, once it exists.
     *
     * @returns {void}
     */
    openAddDocumentDrawer() {
      this.withEntity(() => document.dispatchEvent(new CustomEvent('open-attachments-app-drawer', {detail: this.attachmentDrawerParams})));
    },
    /**
     * Opens the list of the entity's documents, or the drawer that adds one
     * when it has none. The choice waits for the entity, whose creation may
     * give it documents.
     *
     * @returns {void}
     */
    openAttachmentDrawer() {
      this.withEntity(() => document.dispatchEvent(new CustomEvent(this.attachments.length === 0 ? 'open-attachments-app-drawer'
        : 'open-attachments-list-drawer', {detail: this.attachmentDrawerParams})));
    },
    /**
     * Runs an action once the attachments have an entity: at once when there
     * is one or nothing prepares it, else after prepareEntity resolved, the
     * new entity reached the props and its documents were listed.
     *
     * @param {Function} action the action
     * @returns {void}
     */
    withEntity(action) {
      if (this.entityId || !this.prepareEntity) {
        action();
        return;
      }
      this.prepareEntity()
        .then(() => this.$nextTick())
        .then(() => this.attachmentsLoaded && this.attachmentsLoaded.catch(() => null))
        .then(action)
        .catch(() => {
          // The preparation was refused, and its caller shows why
        });
    },
    /**
     * Lists the entity's documents. Only the latest read is applied, with the
     * preview state it was asked with: a preview keeps the readable documents
     * only and follows no editor change, since they are another entity's.
     * attachmentsLoaded settles once the list is applied, which withEntity
     * waits for.
     *
     * @returns {void}
     */
    initEntityAttachmentsList() {
      if (this.entityTypeVal && this.entityIdVal) {
        this.isLoading = true;
        // Only the latest read is applied, with the preview state it was asked with
        const request = ++this.listRequest;
        const preview = this.preview;
        // Not reactive: settled once the documents are listed, which withEntity waits for
        this.attachmentsLoaded = this.$processesAttachmentService.getEntityAttachments(this.entityTypeVal, this.entityIdVal).then(attachments => {
          if (request !== this.listRequest) {
            return;
          }
          if (preview) {
            attachments = attachments.filter(attachment => attachment.acl && attachment.acl.canAccess);
          }
          attachments.forEach(attachment => {
            attachment.name = attachment.title;
            if (!preview) {
              this.subscribeDocument(attachment.id);
            }
          });
          this.attachments = attachments;
          this.isLoading = false;
        });
      }
    },
  }
};
</script>
