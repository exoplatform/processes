<template>
  <v-app>
    <exo-drawer
      :confirm-close="confirmClose"
      :confirm-close-labels="confirmCloseLabels"
      @closed="close()"
      ref="workFlow"
      id="addWorkFlowDrawer"
      right>
      <template v-slot:title>
        <span v-if="!editMode">
          {{ $t('processes.works.label.creatProcessType') }}
        </span>
        <span v-if="editMode">
          {{ $t('processes.works.label.editProcessType') }}
        </span>
      </template>
      <template v-slot:content>
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
              <v-label
                class="mt-1"
                for="status">
                {{ $t('processes.works.form.label.status') }}
              </v-label>
              <span class="mt-2 float-e-inverse grey--text text--darken-1">
                {{ requestStatus }}
              </span>
              <v-switch
                class="mt-n1 float-e"
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
      <template v-slot:footer>
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
        projectId: null,
        permissions: null,
      },
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
    }
  },
  methods: {
    open(workflow, mode) {
      if (workflow) {
        this.workflow = Object.assign({}, workflow);
        this.workflowEnabled = workflow.enabled;
        this.editMode = mode === 'edit_workflow';
        if (this.editMode) {
          this.oldWorkflow = Object.assign({}, this.workflow);
        }
      } else {
        this.resetInputs();
        this.editMode = false;
      }
      this.stp = 1;
      this.$root.$emit('init-list-attachments', {entityId: workflow && workflow.id || null});
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
    },
    addNewWorkFlow() {
      this.saving = true;
      this.workflow.attachments = this.attachments;
      this.$root.$emit('add-workflow',this.workflow);
    },
    updateWorkFlow() {
      this.saving = true;
      this.$root.$emit('update-workflow',this.workflow);
    }
  },
};
</script>