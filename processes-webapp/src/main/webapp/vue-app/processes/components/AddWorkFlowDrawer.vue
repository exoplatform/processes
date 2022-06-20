<template>
  <v-app>
    <exo-drawer
      :confirm-close="confirmClose"
      :confirm-close-labels="confirmCloseLabels"
      @closed="close()"
      ref="workFlow"
      id="addWorkFlowDrawer"
      right>
      <template #title>
        <span v-if="!editMode">
          {{ $t('processes.works.label.creatProcessType') }}
        </span>
        <span v-if="editMode">
          {{ $t('processes.works.label.editProcessType') }}
        </span>
      </template>
      <template #content>
        <v-stepper
          class="pa-4"
          v-model="stp"
          vertical>
          <v-stepper-step
            class="text-uppercase"
            :complete="stp > 1"
            step="1">
            {{ $t('processes.workflow.form.label.description') }}
          </v-stepper-step>
          <v-stepper-content step="1">
            <v-form
              v-model="valid"
              ref="form1">
              <v-label for="name">
                {{ $t('processes.works.form.label.title') }}
              </v-label>
              <v-text-field
                :rules="[rules.required, rules.maxLength(titleMaxLength)]"
                class="mt-n3 mb-1"
                name="name"
                dense
                outlined
                v-model="workflow.title"
                :placeholder="$t('processes.works.form.placeholder.addTitle')" />
              <custom-counter
                :max-length="titleMaxLength"
                :value="workflow.title" />
              <v-label for="description">
                {{ $t('processes.works.form.label.presentation') }}
              </v-label>
              <v-textarea
                class="mt-n3 mb-1"
                dense
                :rules="[rules.required, rules.maxLength(descriptionMaxLength)]"
                name="description"
                v-model="workflow.description"
                rows="10"
                outlined
                auto-grow
                row-height="8"
                :placeholder="$t('processes.works.form.placeholder.addPresentation')" />
              <custom-counter
                :max-length="descriptionMaxLength"
                :value="workflow.description" />
              <v-label for="summary">
                {{ $t('processes.works.form.label.summary') }}
              </v-label>
              <v-textarea
                :rules="[rules.required, rules.maxLength(summaryMaxLength)]"
                class="mt-n3 mb-1"
                v-model="workflow.summary"
                name="summary"
                rows="12"
                outlined
                auto-grow
                row-height="10"
                :placeholder="$t('processes.works.form.placeholder.addSummary')" />
              <custom-counter
                :max-length="summaryMaxLength"
                :value="workflow.summary" />
              <v-label for="illustrative">
                {{ $t('processes.works.form.label.illustration') }}
                <small class="grey--text">
                  ( {{ $t('processes.workflow.illustrative.imageSize.info.message1') }} )
                </small>
              </v-label>
              <small class="grey--text">
                {{ $t('processes.workflow.illustrative.imageSize.info.message2') }}
              </small>
              <div
                class="addIllustrative d-flex ma-auto">
                <a
                  v-if="!illustrativeImage"
                  class="addIllustrativeLabel my-auto d-flex primary--text not-clickable font-weight-bold mb-5">
                  <v-icon size="14" color="primary">
                    fa-paperclip
                  </v-icon>
                  <a class="text-decoration-underline ma-auto ms-2" @click="uploadFile">{{ $t('processes.workflow.illustrative.add') }}</a>
                  <v-file-input
                    id="avatarInput"
                    v-model="illustrativeInput"
                    show-size
                    ref="avatarInput"
                    prepend-icon="fa-plus"
                    color="primary"
                    size="16"
                    class="addIllustrativeButton clickable primary--text ma-0 mt-0 pt-0"
                    accept="image/*"
                    :rules="fileRules"
                    clearable
                    @change="handleUpload" />
                </a>
                <div v-if="illustrativeImage" class="d-flex width-full mb-5">
                  <div class="d-flex illustrativeName">
                    <v-icon
                      size="20">
                      fas fa-file-image
                    </v-icon>
                    <div
                      v-sanitized-html="illustrativeImage.fileName"
                      class="text--secondary text-truncate ms-2">
                    </div>
                  </div>
                  <v-spacer />
                  <div class="ma-auto">
                    <v-btn
                      class="d-flex"
                      outlined
                      x-small
                      height="24"
                      width="24"
                      @click="deleteIllustrative">
                      <v-icon
                        small
                        class="fas fa-unlink error--text" />
                    </v-btn>
                  </div>
                </div>
              </div>
              <v-label
                class="mt-1"
                for="status">
                {{ $t('processes.works.form.label.status') }}
              </v-label>
              <span class="mt-2 float-e-inverse grey--text text--darken-1">
                {{ requestStatus }}
              </span>
              <v-switch
                class="mt-n1 float-e pr-2"
                color="primary"
                value
                name="status"
                v-model="workflowEnabled" />
              <div class="mt-10">
                <v-btn
                  :disabled="!valid"
                  class="btn btn-primary v-btn--outlined float-e"
                  color="primary"
                  @click="nextStep">
                  {{ $t('processes.works.form.label.continue') }}
                </v-btn>
              </div>
            </v-form>
          </v-stepper-content>
          <v-stepper-step
            class="text-uppercase"
            :complete="stp > 2"
            step="2">
            {{ $t('processes.works.form.label.documents') }}
          </v-stepper-step>
          <v-stepper-content step="2">
            <processes-attachments
              v-model="attachments"
              :workflow-parent-space="workflowParentSpace"
              :allow-doc-form-creation="true"
              :edit-mode="editMode"
              :entity-id="workflow.id"
              entity-type="workflow" />
            <v-btn
              class="btn mt-4"
              @click="previousStep"
              text>
              <v-icon size="18" class="me-2">
                {{ $vuetify.rtl && 'fa-caret-right' || 'fa-caret-left' }}
              </v-icon>
              {{ $t('processes.works.form.label.back') }}
            </v-btn>
          </v-stepper-content>
        </v-stepper>
      </template>
      <template #footer>
        <v-btn
          :disabled="!valid"
          v-if="!editMode"
          :loading="saving"
          @click="addNewWorkFlow"
          class="btn btn-primary float-e"
          color="primary">
          {{ $t('processes.works.form.label.save') }}
        </v-btn>
        <v-btn
          :disabled="!valid"
          v-if="editMode"
          :loading="saving"
          @click="updateWorkFlow"
          class="btn btn-primary float-e"
          color="primary">
          {{ $t('processes.workflow.label.update') }}
        </v-btn>
        <v-btn
          @click="close"
          class="btn me-4 float-e">
          {{ $t('processes.workflow.cancel.label') }}
        </v-btn>
      </template>
    </exo-drawer>
  </v-app>
</template>

<script>

export default {
  data () {
    return {
      stp: 1,
      saving: false,
      oldWorkflow: {},
      workflow: {
        title: '',
        description: '',
        summary: '',
        enabled: true,
        helpUrl: '',
        illustrativeAttachment: null,
        projectId: null,
        permissions: null,
      },
      illustrativeImage: {},
      illustrativeInput: {},
      confirmCloseLabels: {
        title: this.$t('processes.workflow.action.confirmation.label'),
        message: this.$t('processes.workflow.cancelCreation.confirm.message'),
        ok: this.$t('processes.workflow.ok.label'),
        cancel: this.$t('processes.workflow.cancel.label')
      },
      attachments: [],
      workflowEnabled: true,
      editMode: false,
      valid: false,
      titleMaxLength: 50,
      descriptionMaxLength: 250,
      summaryMaxLength: 250,
      rules: {
        maxLength: len => v => (v || '').length <= len || this.$t('processes.work.form.description.maxLength.message', {0: len}),
        required: v => !!v || this.$t('processes.work.form.required.error.message'),
      },
      fileRules: [
        value => !value || value.size < 100000 || this.$t('processes.workflow.illustrative.imageSize.error.message'),
      ],
    };
  },
  created(){
    this.$root.$on('workflow-added', () => {
      this.saving = false;
      this.resetInputs();
      this.close();
    });
    this.$root.$on('workflow-updated', () => {
      this.saving = false;
      this.resetInputs();
      this.close();
    });
  },
  computed: {
    requestStatus() {
      return this.workflowEnabled ? this.$t('processes.works.form.label.enabled') : this.$t('processes.works.form.label.disabled');
    },
    workflowParentSpace() {
      return this.workflow && this.workflow.parentSpace;
    },
    confirmClose() {
      return this.valid && JSON.stringify(this.workflow) !== JSON.stringify(this.oldWorkflow);
    }
  },
  watch: {
    workflowEnabled(value) {
      this.workflow.enabled = value;
    },
    illustrativeInput(value) {
      this.handleUpload(value);
    }
  },
  methods: {
    uploadFile(){
      const fileUpload = document.getElementById('avatarInput');
      if (fileUpload != null) {
        fileUpload.click();
      }
    },
    formInitialized() {
      this.originalWorkflowString = JSON.stringify(this.workflow);
    },
    handleUpload(image) {
      if (!image) {
        this.illustrativeImage = null;
        return;
      }
      const reader = new FileReader();
      reader.onload = e => {
        this.illustrativeImage = {
          id: this.workflow.illustrativeAttachment
           && this.workflow.illustrativeAttachment.id
           || null,
          fileName: image.name,
          fileBody: e.target.result
                 && e.target.result !== 'data:'
                 && e.target.result || null,
          mimeType: image.type,
          fileSize: image.size,
          lastUpdated: this.workflow.illustrativeAttachment
                    && this.workflow.illustrativeAttachment.lastUpdated
                    || null,
        };
      };
      reader.readAsDataURL(image);
    },
    toIllustrativeInput(object) {
      if (!object.fileName) {
        this.illustrativeInput = null;
        return;
      }
      this.illustrativeInput = new File([], object.fileName, {
        type: object.mimeType,
      });
    },
    open(workflow, mode) {
      if (workflow) {
        this.workflow = Object.assign({}, workflow);
        this.workflowEnabled = workflow.enabled;
        this.editMode = mode === 'edit_workflow';
        if (this.editMode) {
          this.oldWorkflow = Object.assign({}, this.workflow);
          this.toIllustrativeInput(workflow.illustrativeAttachment);
        }
      } else {
        this.resetInputs();
        this.editMode = false;
      }
      this.stp = 1;
      this.$root.$emit('init-list-attachments', {entityId: workflow && workflow.id || null, entityType: 'workflow'});
      this.$refs.workFlow.open();
    },
    close() {
      this.$root.$emit('reset-list-attachments');
      this.$refs.workFlow.closeEffectively();
    },
    nextStep() {
      if (this.valid) {
        this.stp++;
      }
    },
    previousStep() {
      this.stp--;
    },
    resetInputs() {
      this.workflow = {};
      this.workflow.enabled = true;
      this.illustrativeInput = null;
    },
    addNewWorkFlow() {
      this.saving = true;
      this.workflow.attachments = this.attachments;
      this.workflow.illustrativeAttachment = this.illustrativeImage;
      this.$root.$emit('add-workflow',this.workflow);
    },
    updateWorkFlow() {
      this.saving = true;
      this.workflow.illustrativeAttachment = this.illustrativeImage;
      this.$root.$emit('update-workflow',this.workflow);
    },
    deleteIllustrative(){
      this.illustrativeInput = null;
      this.illustrativeImage = null;
    }
  },
};
</script>