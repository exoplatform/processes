<template>
  <exo-drawer
    :confirm-close="confirmClose"
    :confirm-close-labels="confirmCloseLabels"
    @closed="close()"
    @expand-updated="onExpandUpdated"
    ref="workFlow"
    id="addWorkFlowDrawer"
    class="ProcessesApplication"
    allow-expand
    right>
    <template #title>
      <span v-if="!editMode">
        {{ $t('processes.works.label.creatProcessType') }}
      </span>
      <span v-if="editMode">
        {{ $t('processes.works.label.editProcessType') }}
      </span>
    </template>
    <template v-if="!$vuetify.breakpoint.smAndDown" #expandAction>
      <v-btn
        id="processEditorExpandButton"
        :title="$refs.workFlow && $refs.workFlow.expandTooltip"
        icon
        @click="$refs.workFlow.toogleExpand()">
        <v-icon v-text="$refs.workFlow && $refs.workFlow.expandIcon" size="20" />
      </v-btn>
    </template>
    <template #content>
      <!-- In full screen the steps are sections, in two columns: step 1, the description, then step 2, the management
           and the form, step 3, the request makers, and step 4, the documents; each under the drawer's numbered step
           and each field as the drawer shows it -->
      <component
        :is="expanded ? 'div' : 'v-stepper'"
        :id="expanded && 'processEditorFull' || null"
        :class="expanded && 'processEditorFull'"
        :vertical="!expanded"
        class="pa-4"
        v-model="stp">
        <v-stepper-step
          v-if="!expanded"
          class="primary--text"
          :complete="stp > 1"
          step="1">
          {{ $t('processes.workflow.form.label.description') }}
        </v-stepper-step>
        <component
          :is="expanded ? 'section' : 'v-stepper-content'"
          id="processEditorDescriptionSection"
          :class="expanded && 'processEditorFullDescription'"
          step="1">
          <v-stepper
            v-if="expanded"
            :value="1"
            class="processEditorStepHeading elevation-0 transparent">
            <v-stepper-header class="elevation-0">
              <v-stepper-step class="primary--text" step="1">
                {{ $t('processes.workflow.form.label.description') }}
              </v-stepper-step>
              <v-divider />
            </v-stepper-header>
          </v-stepper>
          <v-form
            v-model="valid"
            ref="form1">
            <translation-text-field
              ref="titleTranslation"
              :key="`title-${formKey}`"
              v-model="translations.title"
              :field-value.sync="workflow.title"
              :object-id="translationFetchId('title')"
              :maxlength="titleMaxLength"
              object-type="process"
              field-name="title"
              drawer-title="processes.workflow.form.translateTitle"
              back-icon
              required
              @initialized="keepOpenedTranslation('title')">
              <template #title>
                <v-label for="name">
                  {{ $t('processes.works.form.label.title') }}
                </v-label>
              </template>
              <v-text-field
                :rules="[rules.required, rules.maxLength(titleMaxLength)]"
                class="mt-n3 mb-1"
                name="name"
                dense
                outlined
                v-model="workflow.title"
                :placeholder="$t('processes.works.form.placeholder.addTitle')" />
            </translation-text-field>
            <custom-counter
              :max-length="titleMaxLength"
              :value="workflow.title" />
            <translation-text-field
              ref="descriptionTranslation"
              :key="`description-${formKey}`"
              v-model="translations.description"
              :field-value.sync="workflow.description"
              :object-id="translationFetchId('description')"
              :maxlength="descriptionMaxLength"
              object-type="process"
              field-name="description"
              drawer-title="processes.workflow.form.translateDescription"
              back-icon
              required
              @initialized="keepOpenedTranslation('description')">
              <template #title>
                <v-label for="description">
                  {{ $t('processes.works.form.label.presentation') }}
                </v-label>
              </template>
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
            </translation-text-field>
            <custom-counter
              :max-length="descriptionMaxLength"
              :value="workflow.description" />
            <translation-text-field
              ref="summaryTranslation"
              :key="`summary-${formKey}`"
              v-model="translations.summary"
              :field-value.sync="workflow.summary"
              :object-id="translationFetchId('summary')"
              :maxlength="summaryMaxLength"
              object-type="process"
              field-name="summary"
              drawer-title="processes.workflow.form.translateSummary"
              back-icon
              required
              @initialized="keepOpenedTranslation('summary')">
              <template #title>
                <v-label for="summary">
                  {{ $t('processes.works.form.label.summary') }}
                </v-label>
              </template>
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
            </translation-text-field>
            <custom-counter
              :max-length="summaryMaxLength"
              :value="workflow.summary" />
            <v-label for="processImageInput">
              {{ $t('processes.workflow.image.label') }}
            </v-label>
            <process-image-input
              :key="`image-${formKey}`"
              :workflow="workflow"
              :icon="workflow.image"
              :illustration="illustrativeImage"
              class="mt-2 mb-5"
              @update:icon="setIcon"
              @update:illustration="illustrativeImage = $event" />
            <div class="d-flex mt-1">
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
            <div v-if="!expanded" class="mt-10">
              <v-btn
                :disabled="!valid"
                class="btn btn-primary v-btn--outlined float-e"
                color="primary"
                @click="setSpace(); nextStep()">
                {{ $t('processes.works.form.label.continue') }}
              </v-btn>
            </div>
          </v-form>
        </component>
        <v-stepper-step
          v-if="!expanded"
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
        <component
          :is="expanded ? 'section' : 'v-stepper-content'"
          id="processEditorManagementSection"
          :class="expanded && 'processEditorFullManagement'"
          step="2">
          <v-stepper
            v-if="expanded"
            :value="2"
            class="processEditorStepHeading elevation-0 transparent">
            <v-stepper-header class="elevation-0">
              <v-stepper-step class="primary--text" step="2">
                <div class="d-flex align-center">
                  {{ $t('processes.works.form.label.manage') }}
                  <v-tooltip bottom>
                    <template #activator="{ on, attrs }">
                      <v-icon
                        dark
                        v-bind="attrs"
                        v-on="on"
                        size="17"
                        class="primary--text ms-2">
                        fa-info-circle
                      </v-icon>
                    </template>
                    <span class="center">{{ infoManage }}</span>
                  </v-tooltip>
                </div>
              </v-stepper-step>
              <v-divider />
            </v-stepper-header>
          </v-stepper>
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
            @update:mode="changeProjectMode"
            @update:projectId="changeProject"
            @update:projectName="projectName = $event" />
          <div
            v-if="moveWarning"
            id="processProjectMoveWarning"
            class="d-flex align-center caption ms-1 pe-1 mt-1">
            <v-icon
              color="warning"
              class="me-2"
              size="14">
              fas fa-exclamation-triangle
            </v-icon>
            <span>{{ moveWarning }}</span>
          </div>
          <workflow-labels-field
            :key="`labelsField${formKey}`"
            v-model="labels"
            :project-id="projectMode === 'EXISTING' && projectId || null"
            :space-id="managingSpaceId" />
          <processes-extension-sections
            ref="editorSections"
            :key="`editorSections${formKey}`"
            :params="editorSectionsParams"
            hook-id="processEditorSections"
            name="ProcessEditor"
            type="process-editor-section"
            class="mt-4"
            @changed="sectionsChanged = $event" />
          <v-card-actions v-if="!expanded" class="d-flex flex-row mt-4 ms-2 px-0">
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
        </component>
        <v-stepper-step
          v-if="!expanded"
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
        <component
          :is="expanded ? 'section' : 'v-stepper-content'"
          id="processEditorRequestersSection"
          :class="expanded && 'processEditorFullRequesters'"
          step="3">
          <v-stepper
            v-if="expanded"
            :value="3"
            class="processEditorStepHeading elevation-0 transparent">
            <v-stepper-header class="elevation-0">
              <v-stepper-step class="primary--text" step="3">
                <div class="d-flex align-center">
                  {{ $t('processes.works.form.label.request') }}
                  <v-tooltip bottom>
                    <template #activator="{ on, attrs }">
                      <v-icon
                        dark
                        v-bind="attrs"
                        v-on="on"
                        size="17"
                        class="primary--text ms-2">
                        fa-info-circle
                      </v-icon>
                    </template>
                    <span class="center">{{ infoRequest }}</span>
                  </v-tooltip>
                </div>
              </v-stepper-step>
              <v-divider />
            </v-stepper-header>
          </v-stepper>
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
          <v-card-actions v-if="!expanded" class="d-flex flex-row mt-4 ms-2 px-0">
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
        </component>
        <v-stepper-step
          v-if="!expanded"
          :complete="stp > 4"
          step="4">
          {{ $t('processes.works.form.label.documents') }}
        </v-stepper-step>
        <component
          :is="expanded ? 'section' : 'v-stepper-content'"
          id="processEditorDocumentsSection"
          :class="expanded && 'processEditorFullDocuments'"
          step="4">
          <v-stepper
            v-if="expanded"
            :value="4"
            class="processEditorStepHeading elevation-0 transparent">
            <v-stepper-header class="elevation-0">
              <v-stepper-step class="primary--text" step="4">
                {{ $t('processes.works.form.label.documents') }}
              </v-stepper-step>
              <v-divider />
            </v-stepper-header>
          </v-stepper>
          <processes-attachments
            v-model="attachments"
            :workflow-parent-space="workflowParentSpace"
            :allow-doc-form-creation="true"
            :edit-mode="editMode"
            :edit-workflow="true"
            :entity-id="workflow.id"
            entity-type="workflow" />
          <v-btn
            v-if="!expanded"
            class="btn mt-4"
            @click="previousStep"
            text>
            <v-icon size="18" class="me-2">
              {{ $vuetify.rtl && 'fa-caret-right' || 'fa-caret-left' }}
            </v-icon>
            {{ $t('processes.works.form.label.back') }}
          </v-btn>
        </component>
      </component>
    </template>
    <template #footer>
      <v-btn
        :disabled="!(valid && workflowChanged && validSpace && projectChosen && workflowRequestChanged)"
        v-if="!editMode"
        :loading="saving"
        @click="addNewWorkFlow"
        class="btn btn-primary float-e"
        color="primary">
        {{ $t('processes.works.form.label.save') }}
      </v-btn>
      <v-btn
        :disabled="!canUpdate"
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
      illustrativeImage: null,
      openedIllustration: null,
      openedRequestCreators: null,
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
      // The title, description and summary per language, as the translation
      // fields edit them, and as they were when the drawer opened
      translations: {title: {}, description: {}, summary: {}},
      openedTranslations: {},
      rules: {
        maxLength: len => v => (v || '').length <= len || this.$t('processes.work.form.description.maxLength.message', {0: len}),
        required: v => !!v || this.$t('processes.work.form.required.error.message'),
      },
      originalWorkflowString: null,
      validSpace: false,
      showSpaceAlertMessage: false,
      projectMode: 'NEW',
      projectId: null,
      projectName: null,
      labels: [],
      labelsRead: false,
      requestCount: null,
      formKey: 0,
      originalProjectChoice: null,
      sectionsChanged: false,
      // True while the drawer is in full screen
      expanded: false,
      // The form chosen in the Forms section before the layout changed, which the re-mounted section shows again
      pendingFormId: null,
      // The translation fields that read their stored translations since the drawer opened, by field name
      initializedTranslations: {},
    };
  },
  created(){
    // Loads the modules whose name contains ProcessEditorExtension, which register their sections
    this.$utils.includeExtensions('ProcessEditorExtension');
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
    translationObjectId() {
      return this.workflow && this.workflow.id && String(this.workflow.id) || null;
    },
    workflowParentSpace() {
      return this.workflow && this.workflow.parentSpace;
    },
    /**
     * @returns {Boolean} true when closing asks for confirmation: the form
     *          changed, the project choice changed, or a section holds a change
     */
    confirmClose() {
      return this.valid && (JSON.stringify(this.workflow) !== JSON.stringify(this.oldWorkflow)
        || this.projectChoiceString() !== this.originalProjectChoice || this.sectionsChanged);
    },
    /**
     * @returns {Boolean} true when an edited process differs from what the
     *          drawer opened with: its fields, its image, its project choice
     *          and labels, an add-on section, a translation or its request
     *          creators
     */
    editChanged() {
      return this.processString(this.workflow) !== this.processString(this.oldWorkflow)
        || JSON.stringify(this.illustrativeImage) !== this.openedIllustration
        || this.projectChoiceString() !== this.originalProjectChoice
        || this.sectionsChanged
        || this.translationsToSave().changed
        || this.requestCreatorsString(this.workflowRequest) !== this.openedRequestCreators;
    },
    /**
     * @returns {Boolean} true when an edited process can be updated: its
     *          description is valid, something changed, and it keeps a
     *          managing space and request creators, with no step to go through
     */
    canUpdate() {
      return this.valid && this.editChanged && !!this.workflow.parentSpace && this.workflowRequest.length > 0;
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
      return this.projectMode === 'NEW' || !!this.projectId;
    },
    /**
     * @returns {Boolean} true when an edited process is given another task
     *          project: a new one, or another existing one
     */
    projectChanged() {
      return this.editMode
        && (this.projectMode === 'NEW' || !!this.projectId && this.projectId !== (this.workflow.projectId || null));
    },
    /**
     * @returns {String} the warning that the requests of an edited process
     *          will move to the project chosen, once their count is read;
     *          null when the project is kept
     */
    moveWarning() {
      if (!this.projectChanged || this.requestCount === null) {
        return null;
      }
      if (this.requestCount === 0) {
        return this.$t('processes.workflow.project.moveWarning.none');
      }
      return this.requestCount === 1 ? this.$t('processes.workflow.project.moveWarning.one')
        : this.$t('processes.workflow.project.moveWarning.many', {0: this.requestCount});
    },
    /**
     * @returns {Object} the props of the sections other add-ons register:
     *          the process, its managing space, its Task project (the
     *          existing project chosen, the stored one on an edit that keeps
     *          it, none for a new project) and that project's name, once the
     *          project choice lists it,
     *          the form chosen before the layout changed, if any, and in
     *          full screen the preview of the form picked
     */
    editorSectionsParams() {
      return {
        workflow: this.workflow,
        spaceId: this.managingSpaceId,
        projectId: this.editMode && !this.projectChanged ? this.workflow.projectId || null
          : this.projectMode === 'EXISTING' && this.projectId || null,
        projectName: this.projectMode === 'EXISTING' && this.projectName || null,
        selected: this.pendingFormId,
        preview: this.expanded,
      };
    },
  },
  watch: {
    // The form chosen before a change of layout was offered in the former managing space: the first one chosen
    // included, which the space picker does not report as a change. Re-feeding the same space changes nothing
    managingSpaceId() {
      this.pendingFormId = null;
    },
    workflowEnabled(value) {
      this.workflow.enabled = value;
    },
    // A translation field showing its own input in its slot is told when the
    // default-language value is typed there
    'workflow.title'(value) {
      this.$refs.titleTranslation?.setValue(value);
    },
    'workflow.description'(value) {
      this.$refs.descriptionTranslation?.setValue(value);
    },
    'workflow.summary'(value) {
      this.$refs.summaryTranslation?.setValue(value);
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
    formInitialized() {
      this.originalWorkflowString = JSON.stringify(this.workflow);
    },
    /**
     * Sets the process's icon, which the form may not hold yet.
     *
     * @param {String} icon the icon name, null for none
     * @returns {void}
     */
    setIcon(icon) {
      this.$set(this.workflow, 'image', icon || null);
    },
    /**
     * Gives the stored image to keep on save, null when the process has none.
     *
     * @param {Object} illustrativeAttachment the process's stored image
     * @returns {Object} the image to send back unchanged
     */
    toKeptIllustration(illustrativeAttachment) {
      if (!illustrativeAttachment || !illustrativeAttachment.id) {
        return null;
      }
      return {
        id: illustrativeAttachment.id,
        fileName: illustrativeAttachment.fileName || 'illustration',
        fileBody: null,
        mimeType: illustrativeAttachment.mimeType,
        fileSize: illustrativeAttachment.fileSize,
        lastUpdated: illustrativeAttachment.lastUpdated || null,
      };
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
      this.translations = {title: {}, description: {}, summary: {}};
      this.openedTranslations = {};
      this.initializedTranslations = {};
      this.pendingFormId = null;
      if (workflow && workflow.id && mode === 'edit_workflow') {
        // The editor edits the stored default-language texts: the list object
        // carries them in the user's language, so it is never edited
        this.$processesService.getWorkflowById(workflow.id, '', false)
          // The read by id names the illustration by its id only, so the list
          // object's illustration is kept, or the save would drop it
          .then(storedWorkflow => this.openWorkflow(Object.assign({}, storedWorkflow, {
            illustrativeAttachment: workflow.illustrativeAttachment,
          }), mode))
          .catch(() => this.$root.$emit('show-alert', {type: 'error', message: this.$t('processes.workflow.open.error.message')}));
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
      this.projectName = null;
      this.originalProjectChoice = this.projectChoiceString();
      // A new key recreates the project choice and the labels field, so each
      // opening lists the projects and labels afresh
      this.formKey++;
      this.sectionsChanged = false;
      // Each opening starts in the drawer, with no document nor request creator of an earlier one: the list starts
      // from this.attachments when it is mounted, an edited process's documents are read again, and its request
      // creators, kept below for an update, are given to their picker by step 2's Continue or by the change of layout
      this.expanded = false;
      this.attachments = [];
      this.workflowRequest = [];
      if (workflow) {
        this.workflow = Object.assign({}, workflow);
        this.workflowEnabled = workflow.enabled;
        this.editMode = mode === 'edit_workflow';
        this.illustrativeImage = null;
        if (this.editMode) {
          this.oldWorkflow = Object.assign({}, this.workflow);
          this.illustrativeImage = this.toKeptIllustration(workflow.illustrativeAttachment);
          // The process's own request creators, as the server gave them, are
          // what an update saves until the picker changes them, so that an
          // update made before step 2's Continue keeps them
          this.workflowRequest = (workflow.requestsCreators || []).slice();
          this.readRequestCount(workflow.id);
        }
        this.openedIllustration = JSON.stringify(this.illustrativeImage);
        this.openedRequestCreators = this.requestCreatorsString(this.workflowRequest);
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
    /**
     * Gives the request creators picker the creators to show: on edit, those
     * the drawer holds, which start from the process's own and keep what was
     * changed since, in full screen included.
     *
     * @returns {void}
     */
    setCreators() {
      const creators = this.editMode ? this.workflowRequest : this.workflow.requestsCreators;
      if (creators) {
        this.$root.$emit('set-workflow-creators', creators);
        this.workflowRequest = creators;
      }
    },
    previousStep() {
      this.stp--;
    },
    /**
     * Empties the form for a new process: the texts as '', a managing space
     * declared for reactivity, no image, request makers or labels, and a new
     * Task project.
     *
     * @returns {void}
     */
    resetInputs() {
      // parentSpace is declared here so the space the suggester sets later is
      // reactive and reaches the project choice. The texts start empty rather
      // than undefined: a translation field sends back '' once it is mounted,
      // and a change from undefined to '' counts as typed, so the required
      // rules would show their error before anything was typed
      this.workflow = {enabled: true, parentSpace: null, title: '', description: '', summary: '', image: null};
      this.illustrativeImage = null;
      this.workflowRequest = [];
      this.projectMode = 'NEW';
      this.projectId = null;
      this.projectName = null;
      this.labels = [];
      this.labelsRead = false;
      this.requestCount = null;
    },
    /**
     * Reads how many requests, open and completed, an edited process has: the
     * ones a change of its project moves. A count that cannot be read shows
     * no warning.
     *
     * @param {Number} workflowId the process id
     * @returns {void}
     */
    readRequestCount(workflowId) {
      this.requestCount = null;
      this.$processesService.countRequests(workflowId)
        .then(count => {
          if (this.editMode && this.workflow && this.workflow.id === workflowId) {
            this.requestCount = Number(count) || 0;
          }
        })
        .catch(() => {
          this.requestCount = null;
        });
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
     * Follows a change of managing space, which resets the project and the
     * labels: a process takes a project of its new space, and on edit its
     * requests move to it.
     *
     * @returns {void}
     */
    changeSpace() {
      this.resetProjectChoice();
    },
    /**
     * Gives a process as text, its managing space by id only: the space
     * picker replaces the space the server gave with the one it reads, which
     * carries other fields, when step 1's Continue or a change of layout
     * gives it the space.
     *
     * @param {Object} workflow the process
     * @return {String} the process as text
     */
    processString(workflow) {
      return JSON.stringify(Object.assign({}, workflow, {
        parentSpace: workflow && workflow.parentSpace && workflow.parentSpace.id || null,
      }));
    },
    /**
     * Gives request creators as text, by provider and id, to tell whether
     * they changed since the drawer opened.
     *
     * @param {Array} creators the request creators
     * @return {String} their providers and ids, in order
     */
    requestCreatorsString(creators) {
      return JSON.stringify((creators || []).map(creator => creator && creator.identity
        && `${creator.identity.providerId}:${creator.identity.remoteId}`));
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
      this.projectName = null;
      this.labels = [];
    },
    /**
     * Builds the body of a save, the one place the form's fields are put
     * together. On create it states the project mode, the project and the
     * labels; on an edit that changes the project, the same, the labels
     * being those of the new project; on an edit that keeps it, no project
     * mode, so the process keeps its project, and labels only when they were
     * read. The sections other add-ons register add their fields, the request
     * form's included.
     *
     * @return {Object} the process to save
     */
    buildSavePayload() {
      const payload = Object.assign({}, this.workflow, {
        requestsCreators: this.workflowRequest,
        illustrativeAttachment: this.illustrativeImage,
      }, this.$refs.editorSections && this.$refs.editorSections.fields() || {});
      delete payload.labels;
      delete payload.projectMode;
      if (!this.editMode) {
        payload.attachments = this.attachments;
      }
      if (!this.editMode || this.projectChanged) {
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
      this.$root.$emit('add-workflow', this.buildSavePayload(), this.translationsToSave());
    },
    /**
     * Asks the application to update the process from the form.
     *
     * @returns {void}
     */
    updateWorkFlow() {
      this.saving = true;
      this.$root.$emit('update-workflow', this.buildSavePayload(), this.translationsToSave());
    },
    /**
     * Keeps a translation field's values as the drawer opened with them. A
     * field that loaded holds at least its default-language value, which a
     * stored process always has; one whose translations could not be read
     * holds no value, or only the empty one a previous opening's field left,
     * and is never saved, so that its stored translations stay.
     *
     * @param {String} fieldName the translated field
     * @returns {void}
     */
    keepOpenedTranslation(fieldName) {
      if (this.initializedTranslations[fieldName]) {
        // A field re-mounted by a change of layout holds what was typed, not what the drawer opened with
        return;
      }
      this.$set(this.initializedTranslations, fieldName, true);
      const values = this.translations[fieldName] || {};
      if (Object.values(values).some(value => value)) {
        this.openedTranslations[fieldName] = JSON.stringify(values);
      }
    },
    /**
     * Gives the title, description and summary per language to save with the
     * process: only the fields whose translations changed since the drawer
     * opened, each read from the server first, so that a save never replaces
     * translations it did not load. A new process written in one language
     * only has none to save.
     *
     * @return {Object} the values per language of each changed field, by
     *          field name, and whether there is any
     */
    translationsToSave() {
      const values = {};
      Object.keys(this.translations).forEach(fieldName => {
        const fieldValues = this.translations[fieldName] || {};
        const opened = this.openedTranslations[fieldName];
        const changed = this.editMode
          ? !!opened && JSON.stringify(fieldValues) !== opened
          : Object.values(fieldValues).filter(value => value).length > 1;
        if (changed) {
          values[fieldName] = JSON.parse(JSON.stringify(fieldValues));
        }
      });
      return {
        values,
        changed: Object.keys(values).length > 0,
      };
    },
    /**
     * The process a translation field reads its stored translations of: the
     * edited process, until the field read them once since the drawer
     * opened. A field re-mounted by a change of layout then starts from the
     * values typed, which the drawer holds, rather than reading the stored
     * ones over them.
     *
     * @param {String} fieldName the translated field
     * @returns {String} the process id, or null
     */
    translationFetchId(fieldName) {
      return this.initializedTranslations[fieldName] ? null : this.translationObjectId;
    },
    /**
     * Follows the drawer's full screen. Changing the layout re-mounts the
     * settings and the Forms section: the form chosen is kept for the new
     * section, read while the former one is still mounted, and the space and
     * request pickers, which the steps' Continue fill, are given their values
     * once the new ones are mounted. A drawer that is closing keeps its
     * layout, so that nothing is re-mounted while it closes: the next
     * opening starts in the drawer.
     *
     * @param {Boolean} expanded true in full screen
     * @returns {void}
     */
    onExpandUpdated(expanded) {
      if (expanded === this.expanded) {
        return;
      }
      if (!this.$refs.workFlow || !this.$refs.workFlow.drawer) {
        return;
      }
      const fields = this.$refs.editorSections && this.$refs.editorSections.fields() || {};
      this.pendingFormId = fields.requestFormId ?? null;
      this.expanded = expanded;
      this.$nextTick(() => this.syncPickers());
    },
    /**
     * Gives the space and request pickers the process's values, as the
     * steps' Continue do: the managing space, and the request creators the
     * drawer holds, which an edited process starts from its own.
     *
     * @returns {void}
     */
    syncPickers() {
      this.setSpace();
      this.$root.$emit('set-workflow-creators', this.workflowRequest);
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
