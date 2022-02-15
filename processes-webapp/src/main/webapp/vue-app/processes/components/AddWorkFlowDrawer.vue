<template>
  <v-app>
    <exo-drawer
      @close="close()"
      ref="workFlow"
      allow-expand
      right>
      <template slot="title">
        <span v-if="!editMode">
          {{ $t('processes.works.label.creatProcessType') }}
        </span>
        <span v-if="editMode">
          {{ $t('processes.works.label.editProcessType') }}
        </span>
      </template>
      <template slot="content">
        <v-stepper
          class="pa-4"
          v-model="stp"
          vertical>
          <v-stepper-step
            class="text-uppercase"
            :complete="stp > 1"
            step="1">
            {{ $t('processes.works.form.label.description') }}
          </v-stepper-step>
          <v-stepper-content step="1">
            <form ref="form1" @submit="nextStep">
              <v-label for="name">
                {{ $t('processes.works.form.label.title') }}
              </v-label>
              <input
                class="input-block-level ignore-vuetify-classes my-3"
                type="text"
                name="name"
                v-model="workflow.title"
                :placeholder="$t('processes.works.form.placeholder.addTitle')"
                required>
              <v-label for="description">
                {{ $t('processes.works.form.label.presentation') }}
              </v-label>
              <textarea
                required
                name="description"
                v-model="workflow.description"
                rows="20"
                maxlength="2000"
                noresize
                class="input-block-level ignore-vuetify-classes my-3"
                :placeholder="$t('processes.works.form.placeholder.addPresentation')">
              </textarea>
              <p class="font-italic">
                <v-icon
                  color="primary">
                  mdi-information-outline
                </v-icon>
                <small class="grey--text text--darken-1">
                  {{ $t('processes.works.form.label.presentation.info') }}
                </small>
              </p>
              <v-label for="summary">
                {{ $t('processes.works.form.label.summary') }}
              </v-label>
              <textarea
                required
                v-model="workflow.summary"
                name="summary"
                rows="20"
                maxlength="2000"
                noresize
                class="input-block-level ignore-vuetify-classes my-3"
                :placeholder="$t('processes.works.form.placeholder.addSummary')">
              </textarea>
              <p class="font-italic">
                <v-icon
                  color="primary">
                  mdi-information-outline
                </v-icon>
                <small class="grey--text text--darken-1">
                  {{ $t('processes.works.form.label.summary.info') }}
                </small>
              </p>
              <v-label
                class="mt-1"
                for="status">
                {{ $t('processes.works.form.label.status') }}
              </v-label>
              <span class="mt-2 float-left grey--text text--darken-1">
                {{ workflow.enabled? $t('processes.works.form.label.enabled'): $t('processes.works.form.label.disabled') }}
              </span>
              <v-switch
                class="float-right mt-n1"
                color="primary"
                value
                name="status"
                v-model="workflow.enabled" />
              <v-divider class="clear-both mb-2" />
              <v-label for="helpUrl">
                {{ $t('processes.works.form.label.help') }}
              </v-label>
              <input
                class="input-block-level ignore-vuetify-classes my-3"
                type="text"
                name="helpUrl"
                v-model="workflow.helpUrl"
                :placeholder="$t('processes.works.form.placeholder.addHelpLink')">
              <v-btn
                class="mb-2"
                link
                color="primary"
                text>
                {{ $t('processes.works.form.label.illustrative') }}
                <v-icon right>mdi-plus-thick</v-icon>
              </v-btn>
              <!-- <v-btn
                class="btn btn-primary"
                color="primary"
                @click="nextStep">
                {{ $t('processes.works.form.label.continue') }}
              </v-btn> -->
            </form>
          </v-stepper-content>
          <!-- <v-stepper-step
            class="text-uppercase"
            :complete="stp > 2"
            step="2">
            {{ $t('processes.works.form.label.tasksProject') }}
          </v-stepper-step>
          <v-stepper-content step="2">
            <form ref="form2" @submit="nextStep">
              <v-label for="taskProject">
                {{ $t('processes.works.form.label.addProject') }}
              </v-label>
              <input
                class="input-block-level ignore-vuetify-classes my-3"
                type="text"
                name="taskProject"
                v-model="workflow.projectId"
                :placeholder="$t('processes.works.form.placeholder.addTaskProject')"
                required>
            </form>
            <v-btn
              class="btn"
              @click="previousStep"
              text>
              <v-icon size="18" class="me-2">
                {{ $vuetify.rtl && 'fa-caret-right' || 'fa-caret-left' }}
              </v-icon>
              {{ $t('processes.works.form.label.back') }}
            </v-btn>
            <v-btn
              color="primary"
              class="btn btn-primary"
              @click="nextStep">
              {{ $t('processes.works.form.label.continue') }}
            </v-btn>
          </v-stepper-content>
          <v-stepper-step
            class="text-uppercase"
            :complete="stp > 3"
            step="3">
            {{ $t('processes.works.form.label.permissions') }}
          </v-stepper-step>
          <v-stepper-content step="3">
            <form ref="form3" @submit="nextStep">
              <v-label for="permissions">
                {{ $t('processes.works.form.label.selectPermissions') }}
              </v-label>
              <input
                class="input-block-level ignore-vuetify-classes my-3"
                type="text"
                name="permissions"
                v-model="workflow.permissions"
                :placeholder="$t('processes.works.form.placeholder.selectPermissions')">
            </form>
            <v-btn
              class="btn"
              @click="previousStep"
              text>
              <v-icon size="18" class="me-2">
                {{ $vuetify.rtl && 'fa-caret-right' || 'fa-caret-left' }}
              </v-icon>
              {{ $t('processes.works.form.label.back') }}
            </v-btn>
            <v-btn
              color="primary"
              class="btn btn-primary"
              @click="nextStep">
              {{ $t('processes.works.form.label.continue') }}
            </v-btn>
          </v-stepper-content>
          <v-stepper-step
            class="text-uppercase"
            step="4">
            {{ $t('processes.works.form.label.documents') }}
          </v-stepper-step>
          <v-stepper-content step="4">
            <v-btn
              class="mb-2"
              link
              color="primary"
              text>
              {{ $t('processes.works.form.label.addModel') }}
              <v-icon right>mdi-plus-thick</v-icon>
            </v-btn>
            <br>
            <v-btn
              class="btn"
              @click="previousStep"
              text>
              <v-icon size="18" class="me-2">
                {{ $vuetify.rtl && 'fa-caret-right' || 'fa-caret-left' }}
              </v-icon>
              {{ $t('processes.works.form.label.back') }}
            </v-btn>
          </v-stepper-content> -->
        </v-stepper>
      </template>
      <template slot="footer">
        <v-btn
          v-if="!editMode"
          :loading="saving"
          @click="addNewWorkFlow"
          class="btn btn-primary"
          color="primary">
          {{ $t('processes.works.form.label.save') }}
        </v-btn>
        <v-btn
          v-if="editMode"
          :loading="saving"
          @click="updateWorkFlow"
          class="btn btn-primary"
          color="primary">
          {{ $t('processes.workflow.label.update') }}
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
      valid: true,
      saving: false,
      workflow: {
        title: '',
        description: '',
        summary: '',
        enabled: true,
        helpUrl: '',
        projectId: null,
        permissions: null,
        editMode: false
      }
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
    currentForm() {
      return this.$refs && this.$refs[`form${this.stp}`];
    },
  },
  methods: {
    open(workflow, mode) {
      if (workflow) {
        this.workflow = Object.assign({}, workflow);
        this.editMode = mode === 'edit_workflow';
      } else {
        this.resetInputs();
        this.editMode = false;
      }
      this.stp = 1;
      this.$refs.workFlow.open();
    },
    close() {
      this.$refs.workFlow.close();
    },
    nextStep() {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }

      if (this.currentForm && this.currentForm.reportValidity()) {
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
      this.$root.$emit('add-workflow',this.workflow);
    },
    updateWorkFlow() {
      this.$root.$emit('update-workflow',this.workflow);
    }
  }
};
</script>