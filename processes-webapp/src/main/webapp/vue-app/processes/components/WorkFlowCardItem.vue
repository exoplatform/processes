<template>
  <v-card
    min-height="317px"
    class="mt-2 mb-2 me-3"
    outlined>
    <v-btn
      outlined
      color="primary"
      class="mb-0 invisible"
      icon>
      <v-icon>mdi-information-outline</v-icon>
    </v-btn>
    <v-menu
      v-model="showMenu"
      transition="slide-x-reverse-transition"
      v-if="isProcessesManager"
      :ref="'menu' + workflow.id"
      :close-on-content-click="false"
      bottom
      offset-y
      left>
      <template #activator="{ on, attrs }">
        <v-btn
          class="float-e"
          color="primary"
          dark
          icon
          v-bind="attrs"
          v-on="on">
          <v-icon>mdi-dots-vertical</v-icon>
        </v-btn>
      </template>
      <v-list>
        <v-list-item
          @click="editWorkflow">
          <v-list-item-title>
            <v-icon class="processes-work-menu-icon">
              mdi-square-edit-outline
            </v-icon>
            <span>{{ $t('processes.workflow.edit.label') }}</span>
          </v-list-item-title>
        </v-list-item>
        <v-list-item
          @click="deleteWorkflow">
          <v-list-item-title>
            <v-icon class="processes-work-menu-icon">
              mdi-trash-can-outline
            </v-icon>
            <span>{{ $t('processes.workflow.delete.label') }}</span>
          </v-list-item-title>
        </v-list-item>
      </v-list>
    </v-menu>
    <v-card-title
      class="text-center d-block mt-0">
      <v-avatar
        v-if="avatarUrl"
        size="40px">
        <v-img
          class="workflow-avatar-img"
          :src="avatarUrl" />
      </v-avatar>
      {{ workflow.title }}
    </v-card-title>
    <v-card-text
      class="text-center card-content">
      <div>
        <p
          class="text-truncate-4 workflow-card-desc">
          {{ workflow.description }}
        </p>
      </div>
      <div>
        <v-chip
          v-if="completedWorksCount>0 && isProcessesManager"
          class="text-truncate"
          color="orange"
          :href="projectLink(workflow.projectId)"
          target="_blank"
          :loading="!completedWorksCount"
          text-color="white">
          {{ completedWorksCount }} {{ $t('processes.works.status.inProgress') }}
        </v-chip>
      </div>
    </v-card-text>
    <v-divider />
    <v-card-actions
      class="d-inline-block work-card-actions ma-2">
      <v-btn
        class="me-8 btn card-footer-btn mt-2 mb-2 float-e"
        right
        plain
        :disabled="!workflow.enabled"
        depressed
        @click="open"
        color="deep-grey lighten-3">
        {{ $t('processes.works.label.makeRequest') }}
      </v-btn>
    </v-card-actions>
  </v-card>
</template>

<script>

export default {
  data () {
    return {
      showMenu: false,
      completedWorksCount: null,
    };
  },
  props: {
    workflow: {
      type: Object,
      default: function() {
        return {};
      },
    },
    isProcessesManager: {
      type: Boolean,
      default: false
    }
  },
  created() {
    $(document).on('mousedown', () => {
      if (this.showMenu) {
        window.setTimeout(() => {
          this.showMenu = false;
        }, 200);
      }
    });
    this.countWorksByWorkflow(false);
  },
  computed: {
    avatarUrl() {
      return this.workflow && this.workflow.illustrativeAttachment
                           && this.workflow.illustrativeAttachment.id
                           && `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/illustration/${this.workflow.id}?v=${this.workflow.illustrativeAttachment.lastUpdated}`;
    }
  },
  methods: {
    editWorkflow() {
      this.$root.$emit('open-workflow-drawer', {workflow: this.workflow, mode: 'edit_workflow'});
    },
    deleteWorkflow() {
      this.$root.$emit('show-confirm-action', {model: this.workflow, reason: 'delete_workflow'});
    },
    open() {
      this.$root.$emit('open-add-work-drawer', {object: this.workflow, mode: 'create_work'});
    },
    countWorksByWorkflow(isCompleted) {
      if (this.workflow) {
        return this.$processesService.countWorksByWorkflow(this.workflow.projectId, isCompleted).then(value => {
          this.completedWorksCount = value;
        });
      }
    },
    projectLink(projectId) {
      return `${eXo.env.portal.context}/${eXo.env.portal.portalName}/tasks/projectDetail/${projectId}`;
    }
  }
};
</script>