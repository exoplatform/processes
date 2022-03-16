<template>
  <v-app>
    <div>
      <v-icon
        color="primary"
        small>
        fa-paperclip
      </v-icon>
      <a
        @click="openAttachmentDrawer"
        class="viewAllAttachments primary--text font-weight-bold text-decoration-underline">
        {{ $t('processes.work.add.attachment.label') }} {{ attachmentsLength }}
      </a>
      <v-icon
        class="plus-process-attachment"
        color="primary">
        mdi-plus-thick
      </v-icon>
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
    workflowParentSpaceId: {
      type: Number,
      default: null
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
        spaceId: this.workflowParentSpaceId,
        attachToEntity: this.editMode,
      };
    },
    attachmentsLength() {
      return this.attachments.length > 0 ? `(${this.attachments.length})` : '';
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
      if (event.entityId && event.entityId !== -1) {
        this.entityId = event.entityId;
      }
      if (event.entityType) {
        this.entityType = event.entityType;
      }
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
      if (this.entityType && this.entityId && this.entityId !== -1) {
        this.$processesService.getEntityAttachments(this.entityType, this.entityId).then(attachments => {
          attachments.forEach(attachments => {
            attachments.name = attachments.title;
          });
          this.attachments = attachments;
        });
      }
    },
  }
};
</script>