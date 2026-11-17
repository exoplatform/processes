<template>
  <exo-drawer
    :confirm-close="confirmClose"
    :confirm-close-labels="confirmCloseLabels"
    @closed="close()"
    ref="workFlow"
    id="addWorkFlowDrawer"
    class="ProcessesApplication"
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
          class="primary--text"
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
                <a
                  class="text-decoration-underline ma-auto ms-2"
                  @click="uploadFile">{{ $t('processes.workflow.illustrative.add') }}</a>
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
            <div class="d-flex mt-2">
              <div>
                {{ requestStatus }}
              </div>
              <v-switch
                class="mt-n1 ms-auto pe-2"
                color="primary"
                value
                name="status"
                v-model="workflowEnabled" />
            </div>
            <div class="mt-10">
              <v-btn
                :disabled="!valid"
                class="btn btn-primary v-btn--outlined float-e"
                color="primary"
                @click="setSpace(); nextStep()">
                {{ $t('processes.works.form.label.continue') }}
              </v-btn>
            </div>
          </v-form>
        </v-stepper-content>
        <v-stepper-step
          class="primary--text width-full d-flex"
          :complete="stp > 2"
          step="2">
          <div class="width-full d-flex">
            <div class="width-full">{{ $t('processes.works.form.label.manage') }}</div>
            <v-spacer />
            <v-tooltip bottom>
              <template #activator="{ on, attrs }">
                <v-icon
                  dark
                  v-bind="attrs"
                  v-on="on"
                  size="17"
                  class="primary--text"
                  @mouseenter="applyItemClass(infoManage)">
                  fa-info-circle
                </v-icon>
              </template>
              <span class="center">{{ infoManage }}</span>
            </v-tooltip>
          </div>
        </v-stepper-step>
        <v-stepper-content step="2">
          <v-label>
            <span class="ms-1 pe-1">
              {{ $t('processes.works.form.label.add.manage') + ' *' }}
            </span>
          </v-label>
          <div class="d-flex flex-row">
            <workflow-suggester-space
              ref="workFLowOwner"
              :workflow="workflow"
              class="ms-1 pe-1"
              @initialized="formInitialized"
              @space-changed="changeSpace" />
          </div>
          <workflow-project-choice
            :key="`projectChoice${formKey}`"
            class="mt-4"
            :mode="projectMode"
            :project-id="projectId"
            :space-id="managingSpaceId"
            :workflow-id="workflow.id || null"
            :read-only="editMode"
            @update:mode="changeProjectMode"
            @update:projectId="changeProject" />
          <workflow-labels-field
            :key="`labelsField${formKey}`"
            v-model="labels"
            :project-id="projectMode === 'EXISTING' && projectId || null"
            :space-id="managingSpaceId" />
          <v-card-actions class="d-flex flex-row mt-4 ms-2 px-0">
            <v-btn class="btn" @click="previousStep">
              <v-icon size="18" class="me-2">
                {{ $vuetify.rtl && 'fa-caret-right' || 'fa-caret-left' }}
              </v-icon>
              {{ $t('processes.works.form.label.back') }}
            </v-btn>
            <v-spacer />
            <v-btn
              class="btn btn-primary me-4"
              outlined
              :disabled="!workflowChanged || !validSpace || !projectChosen"
              @click="setCreators(); nextStep()">
              {{ $t('processes.works.form.label.continue') }}
              <v-icon size="18" class="ms-2">
                {{ $vuetify.rtl && 'fa-caret-left' || 'fa-caret-right' }}
              </v-icon>
            </v-btn>
          </v-card-actions>
        </v-stepper-content>
        <v-stepper-step
          :complete="stp > 3"
          step="3">
          <div class="width-full d-flex">
            <div class="width-full">{{ $t('processes.works.form.label.request') }}</div>
            <v-spacer />
            <v-tooltip bottom>
              <template #activator="{ on, attrs }">
                <v-icon
                  dark
                  v-bind="attrs"
                  v-on="on"
                  size="17"
                  class="primary--text"
                  @mouseenter="applyItemClass(infoRequest)">
                  fa-info-circle
                </v-icon>
              </template>
              <span class="center">{{ infoRequest }}</span>
            </v-tooltip>
          </div>
        </v-stepper-step>
        <v-stepper-content step="3">
          <v-label>
            <span class="text-color body-2">
              {{ $t('processes.works.form.label.add.request') + ' *' }}
            </span>
          </v-label>
          <div class="d-flex flex-row">
            <workflow-suggester-request
              ref="workFLowOwner"
              :workflow-request="workflowRequest"
              class="ms-1 pe-1"
              @initialized="formInitialized" />
          </div>
          <v-card-actions class="d-flex flex-row mt-4 ms-2 px-0">
            <v-btn class="btn" @click="previousStep">
              <v-icon size="18" class="me-2">
                {{ $vuetify.rtl && 'fa-caret-right' || 'fa-caret-left' }}
              </v-icon>
              {{ $t('processes.works.form.label.back') }}
            </v-btn>
            <v-spacer />
            <v-btn
              class="btn btn-primary me-4"
              outlined
              :disabled="!workflowRequestChanged"
              @click="nextStep">
              {{ $t('processes.works.form.label.continue') }}
              <v-icon size="18" class="ms-2">
                {{ $vuetify.rtl && 'fa-caret-left' || 'fa-caret-right' }}
              </v-icon>
            </v-btn>
          </v-card-actions>
        </v-stepper-content>
        <v-stepper-step
          :complete="stp > 4"
          step="4">
          {{ $t('processes.works.form.label.documents') }}
        </v-stepper-step>
        <v-stepper-content step="4">
          <processes-attachments
            v-model="attachments"
            :workflow-parent-space="workflowParentSpace"
            :allow-doc-form-creation="true"
            :edit-mode="editMode"
            :edit-workflow="true"
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
        :disabled="!(valid && workflowChanged && workflowRequestChanged)"
        v-if="!editMode"
        :loading="saving"
        @click="addNewWorkFlow"
        class="btn btn-primary float-e"
        color="primary">
        {{ $t('processes.works.form.label.save') }}
      </v-btn>
      <v-btn
        :disabled="!(valid && workflowChanged && workflowRequestChanged)"
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
        requests: [],
        space: {},
        parentSpace: null,
        projectId: null,
        permissions: null,
      },
      workflowRequest: [],
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
      originalWorkflowString: null,
      validSpace: false,
      showSpaceAlertMessage: false,
      projectMode: 'NEW',
      projectId: null,
      labels: [],
      labelsRead: false,
      formKey: 0,
      originalProjectChoice: null,
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
    this.$root.$on('workflow-save-failed', () => {
      this.saving = false;
    });
  },
  computed: {
    requestStatus() {
      return this.workflowEnabled ? this.$t('processes.works.form.label.enabled') : this.$t('processes.works.form.label.disabled');
    },
    infoRequest() {
      return this.$t('processes.works.form.label.associate.request');
    },
    infoManage() {
      return this.$t('processes.works.form.label.associate.process');
    },
    workflowParentSpace() {
      return this.workflow && this.workflow.parentSpace;
    },
    confirmClose() {
      return this.valid && (JSON.stringify(this.workflow) !== JSON.stringify(this.oldWorkflow)
        || this.projectChoiceString() !== this.originalProjectChoice);
    },
    workflowChanged() {
      return  this.workflow && this.originalWorkflowString && this.workflow.parentSpace;
    },
    workflowRequestChanged(){
      return  this.workflow && this.originalWorkflowString && this.workflowRequest.length;
    },
    managingSpaceId() {
      return this.workflow && this.workflow.parentSpace && this.workflow.parentSpace.id || null;
    },
    projectChosen() {
      return this.editMode || this.projectMode === 'NEW' || !!this.projectId;
    },
  },
  watch: {
    workflowEnabled(value) {
      this.workflow.enabled = value;
    },
    illustrativeInput(value) {
      this.handleUpload(value);
    },
    originalWorkflowString() {
      this.showSpaceAlertMessage = false;
      this.validSpace = false;
      if (this.workflow.parentSpace){
        this.validSpace = true;
      }
    },
    showSpaceAlertMessage(val){
      if (val){
        this.$root.$emit('show-alert', {type: 'warning',message: this.$t('processes.alert.createProcess.manageSpaceNotValid.warning')});
      }
      else {
        this.$root.$emit('hide-alert');
      }
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
      if (image.size > 100000) {
        this.$root.$emit('show-alert',{type: 'error', message: this.$t('processes.workflow.illustrative.imageSize.error.message')});
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
                 && e.target.result.length >23
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
    /**
     * Opens the drawer: on edit, from the process as read by its id, which
     * carries its labels, rather than from the list object, which does not.
     *
     * @param {Object} workflow the process to edit, or null to add one
     * @param {String} mode the drawer mode
     * @returns {void}
     */
    open(workflow, mode) {
      if (workflow && workflow.id && mode === 'edit_workflow') {
        this.$processesService.getWorkflowById(workflow.id, '')
          // The read by id names the illustration by its id only, so the list
          // object's illustration is kept, or the save would drop it
          .then(storedWorkflow => this.openWorkflow(Object.assign({}, storedWorkflow, {
            illustrativeAttachment: workflow.illustrativeAttachment,
          }), mode))
          .catch(() => this.openWorkflow(workflow, mode));
      } else {
        this.openWorkflow(workflow, mode);
      }
    },
    /**
     * Fills the form with a process, or empties it, and opens the drawer.
     * Labels are taken only from a process read with them, so a process
     * opened without them sends none back and keeps its stored labels.
     *
     * @param {Object} workflow the process, or null
     * @param {String} mode the drawer mode
     * @returns {void}
     */
    openWorkflow(workflow, mode) {
      this.labels = workflow && Array.isArray(workflow.labels) && workflow.labels.slice() || [];
      this.labelsRead = !!workflow && Array.isArray(workflow.labels);
      this.projectMode = workflow && workflow.id ? 'EXISTING' : 'NEW';
      this.projectId = workflow && workflow.projectId || null;
      this.originalProjectChoice = this.projectChoiceString();
      // A new key recreates the project choice and the labels field, so each
      // opening lists the projects and labels afresh
      this.formKey++;
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
    setSpace() {
      this.$root.$emit('set-workflow-space',this.workflow.parentSpace );
    },
    setCreators() {
      if (this.workflow.requestsCreators){
        this.$root.$emit('set-workflow-creators',this.workflow.requestsCreators );
        this.workflowRequest = this.workflow.requestsCreators ;
      }
    },
    previousStep() {
      this.stp--;
    },
    resetInputs() {
      // parentSpace is declared here so the space the suggester sets later is
      // reactive and reaches the project choice
      this.workflow = {enabled: true, parentSpace: null};
      this.illustrativeInput = null;
      this.workflowRequest = [];
      this.projectMode = 'NEW';
      this.projectId = null;
      this.labels = [];
      this.labelsRead = false;
    },
    /**
     * Switches between a new and an existing project, which resets the
     * project and the labels.
     *
     * @param {String} mode NEW or EXISTING
     * @returns {void}
     */
    changeProjectMode(mode) {
      if (mode !== this.projectMode) {
        this.projectMode = mode;
        this.resetProjectChoice();
      }
    },
    /**
     * Chooses an existing project, which resets the labels of the former one.
     *
     * @param {Number} projectId the project id
     * @returns {void}
     */
    changeProject(projectId) {
      if (projectId !== this.projectId) {
        this.projectId = projectId;
        this.labels = [];
      }
    },
    /**
     * Follows a change of managing space. On create it resets the project and
     * the labels; on edit the project stays read-only and the server refuses
     * the change with its own message, so nothing is reset.
     *
     * @returns {void}
     */
    changeSpace() {
      if (!this.editMode) {
        this.resetProjectChoice();
      }
    },
    /**
     * Gives the project choice and the labels as text, to tell whether the
     * form changed since it was opened.
     *
     * @return {String} the project mode, the project and the labels
     */
    projectChoiceString() {
      return JSON.stringify({mode: this.projectMode, projectId: this.projectId, labels: this.labels});
    },
    /**
     * Empties the project and the labels chosen.
     *
     * @returns {void}
     */
    resetProjectChoice() {
      this.projectId = null;
      this.labels = [];
    },
    /**
     * Builds the body of a save, the one place the form's fields are put
     * together. On create it states the project mode, the project and the
     * labels; on edit it states no project mode, so the process keeps its
     * project, and labels only when they were read.
     *
     * @return {Object} the process to save
     */
    buildSavePayload() {
      const payload = Object.assign({}, this.workflow, {
        requestsCreators: this.workflowRequest,
        illustrativeAttachment: this.illustrativeImage,
      });
      delete payload.labels;
      delete payload.projectMode;
      if (!this.editMode) {
        payload.attachments = this.attachments;
        payload.projectMode = this.projectMode;
        payload.projectId = this.projectMode === 'EXISTING' && this.projectId || null;
        payload.labels = this.labels;
      } else if (this.labelsRead) {
        payload.labels = this.labels;
      }
      return payload;
    },
    /**
     * Asks the application to create the process from the form.
     *
     * @returns {void}
     */
    addNewWorkFlow() {
      this.saving = true;
      this.$root.$emit('add-workflow', this.buildSavePayload());
    },
    /**
     * Asks the application to update the process from the form.
     *
     * @returns {void}
     */
    updateWorkFlow() {
      this.saving = true;
      this.$root.$emit('update-workflow', this.buildSavePayload());
    },
    deleteIllustrative(){
      this.illustrativeInput = null;
      this.illustrativeImage = null;
    },
    applyItemClass(messageInfo){
      window.setTimeout(() => {
        const elements = document.getElementsByClassName('v-tooltip__content');
        for (let i = 0; i < elements.length; i++){
          if (elements[i].innerText.includes(messageInfo)){
            elements[i].style.left = '880px';
          }
        }
      }, 100);
    },
  },
};
</script>
