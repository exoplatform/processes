<template>
  <v-card
    class="mt-2 mb-2 me-3"
    outlined>
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
          :class=" isMobile ? 'three-dots mt-3' : 'three-dots'"
          color="#5F708A"
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
  <div v-if="!isMobile">
    <div class="mb-n5">
      <v-btn
        outlined
        color="grey"
        class="invisible"
        icon>
        <v-icon>mdi-information</v-icon>
      </v-btn>  
    </div> 
    <div class="d-flex justify-center image-height">
      <v-avatar
        v-if="avatarUrl"
        size="50px">
        <v-img
          :src="avatarUrl" />
      </v-avatar>         
    </div>   
    <v-card-title
      class="text-center d-block card-title title-size">
     <div> {{ workflow.title }}</div>
    </v-card-title>
  </div>
  <div v-else>
    <div class="process-img mt-3 ml-3">
      <v-avatar
        v-if="avatarUrl"
        size="34px">
        <v-img
          :src="avatarUrl" /> 
      </v-avatar>
    </div>  
    <div class="ml-14 mr-5 mt-5">
      {{ workflow.title }} 
    </div>
  </div>
    <v-card-text class="card-content">
      <div>
        <p
          :class="isMobile ? 'text-truncate-2 ml-10 description-size' : 'text-truncate-3 text-center mt-n4 description-size'">
          {{ workflow.description }}
        </p>
      </div>
      <div>
      </div>
    </v-card-text>
    <v-card-footer class="d-inline-flex" elevation ="0">
      <v-card elevation ="0" class="mb-7 ml-3 card-footer-request">
    <v-chip
      :class="isMobile ? 'ml-8':''"
      v-if="isProcessesManager"
      color="white"
      :href="projectLink(workflow.projectId)"
      target="_blank"
      :loading="!completedWorksCount"
      :text-color="completedWorksCount == 0 ? 'grey':'#F89802'">
        <v-icon small>mdi-clock-time-four-outline</v-icon>

      {{ completedWorksCount }} Pending
    </v-chip>
    </v-card >
    <v-card class="card-footer-btn mr-3 mb-4 px-3 py-1" elevation="0" outlined>
      <v-btn 
        outlined
        right
        plain
        :disabled="!workflow.enabled"
        depressed
        @click="open"
        >
        Request
      </v-btn>
      </v-card>
  </v-card-footer>
 </v-card>
</template>

<script>

export default {
  data () {
    return {
      showMenu: false,
      completedWorksCount: 0,
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
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
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