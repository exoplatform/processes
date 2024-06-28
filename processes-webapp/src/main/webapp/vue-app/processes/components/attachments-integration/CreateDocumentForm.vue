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
  <div class="mt-3">
    <v-icon
      class="mb-1"
      color="primary"
      small>
      fa-file-invoice
    </v-icon>
    <a
      @click="showNewFormDocInput"
      class="primary--text font-weight-bold text-decoration-underline">
      {{ $t('processes.create.document.form.label') }}
    </a>
    <v-text-field
      ref="NewFormDocInput"
      v-show="!NewFormDocInputHidden"
      v-model="newDocumentTitle"
      :placeholder="$t('processes.document.form.untitled.name')"
      class="pt-2"
      outlined
      dense
      autofocus
      @keyup.enter="createNewFormDoc">
      <div slot="append" class="d-flex mt-1">
        <span class="text-color me-2">{{ extension }}</span>
        <v-icon
          :class="documentTitleMaxLengthReached && 'not-allowed' || 'clickable'"
          :color="documentTitleMaxLengthReached && 'grey--text' || 'primary'"
          class="px-1"
          small
          @click="createNewFormDoc">
          fa-check
        </v-icon>
        <v-icon
          class="clickable px-0"
          color="red"
          small
          @click="restInput">
          fa-times
        </v-icon>
      </div>
    </v-text-field>
  </div>
</template>

<script>

export default {
  data () {
    return {
      newDocumentTitle: null,
      NewFormDocInputHidden: true,
      extension: '.pdf',
      personalDrive: 'Personal Documents',
      defaultFolder: 'Documents',
      templateName: 'OnlyOfficeDocumentFormPdf',
      MAX_FILE_NAME_LENGTH: 200
    };
  },
  props: {
    drive: {
      type: Object,
      default: () => {
        return null;
      },
    },
    entityType: {
      type: String,
      default: null,
    },
    entityId: {
      type: Number,
      default: null,
    },
    editMode: {
      type: Boolean,
      default: false,
    }
  },
  computed: {
    documentTitleMaxLengthReached() {
      return this.newDocumentTitle && this.newDocumentTitle.length > this.MAX_FILE_NAME_LENGTH;
    },
    untitledNewFormDoc() {
      return `${this.$t('processes.document.form.untitled.name')}${this.extension}`;
    },
    documentName() {
      return this.newDocumentTitle && `${this.newDocumentTitle.trim()}${this.extension}` || this.untitledNewFormDoc;
    },
    driveName(){
      return this.drive && this.drive.name || this.personalDrive;
    },
    targetFolder() {
      return this.editMode && this.entityType && this.entityId && ' ' || this.defaultFolder;
    }
  },
  methods: {
    createNewFormDoc() {
      if (this.documentTitleMaxLengthReached) {
        return;
      }
      return this.$processesAttachmentService.createNewFormDoc(this.documentName, this.templateName, this.driveName, this.targetFolder, this.entityType, this.entityId)
        .then((resp) => {
          if (resp && resp.status && resp.status === 409) {
            this.$root.$emit('processes-attachments-notification-alert', {
              message: this.$t('processes.create.document.form.exist.message'),
              type: 'error',
            });
          } else {
            return resp;
          }
        })
        .then((doc) => this.manageNewCreatedDocument(doc))
        .catch(() => {
          this.$root.$emit('processes-attachments-notification-alert', {
            message: this.$t('processes.create.document.form.error.message'),
            type: 'error',
          });
        });
    },
    manageNewCreatedDocument(doc) {
      if (doc && doc.id) {
        doc.drive = this.driveName;
        doc.date = doc.created;
        this.$root.$emit('add-new-created-form-document', doc);
        this.restInput();
        window.open(`${eXo.env.portal.context}/${eXo.env.portal.portalName}/oeditor?docId=${doc.id}&backTo=${window.location.pathname}`, '_blank');
      }
    },
    restInput() {
      this.NewFormDocInputHidden = true;
      this.newDocumentTitle = '';
    },
    showNewFormDocInput() {
      this.$refs.NewFormDocInput.focus();
      this.NewFormDocInputHidden = false;
    }
  }
};
</script>
