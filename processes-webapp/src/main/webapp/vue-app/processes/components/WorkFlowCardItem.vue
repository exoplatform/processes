<template>
  <div>
    <v-card
      :class="isMobile ? 'mt-2 mb-2 ml-n3':'mt-2 mb-2 mr-8'"
      height="230"
      max-height="230"
      class="workflow-card"
      @click="open"
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
            :class="isMobile ? 'three-dots mr-n2 mt-3' : 'three-dots mt-1'"
            dark
            icon
            v-bind="attrs"
            v-on="!isMobileMenu && on"
            @click.stop.prevent="openMobileDrawer">
            <v-icon size="18">mdi-dots-vertical</v-icon>
          </v-btn>
        </template>
        <v-list>
          <v-list-item
            @click="editWorkflow">
            <v-list-item-title small>
              <v-icon size="13" class="processes-work-menu-icon mt-1">
                mdi-square-edit-outline
              </v-icon>
              <span class="card-edit-text-size">{{ $t('processes.workflow.edit.label') }}</span>
            </v-list-item-title>
          </v-list-item>
          <v-list-item
            @click="deleteWorkflow">
            <v-list-item-title>
              <v-icon size="13" class="processes-work-menu-icon mt-1">
                mdi-trash-can-outline
              </v-icon>
              <span class="card-edit-text-size">{{ $t('processes.workflow.delete.label') }}</span>
            </v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
      <div>
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
        <div class="d-flex justify-center font-weight-bold">
          {{ workflow.title }}
        </div>
      </div>
      <div class="text-center text-truncate-3 px-4">
        {{ workflow.description }}
      </div>
      <v-card-footer class="d-inline-flex" elevation="0">
        <v-card elevation="0" class="mb-5 width-full transparent align-center card-footer-request">
          <span
            :class="isMobile ? 'ml-8':''">
            <v-chip
              :class="completedWorksCount == 0 ? 'no-pending-requests':'pending-requests'"
              v-if="showPending"
              color="white"
              :href="projectLink(workflow.projectId)"
              @click.stop
              target="_blank"
              :loading="!completedWorksCount">
              <v-icon small>mdi-clock-time-four-outline</v-icon>
              {{ completedWorksCount }} {{ $t('processes.workflow.label.pending') }}
            </v-chip>
          </span>
        </v-card>
      </v-card-footer>
    </v-card>
    <process-mobile-action-drawer
      @editWorkflow="editWorkflow"
      @deleteWorkflow="deleteWorkflow"
      ref="mobileActionDrawer" />
  </div> 
</template>

<script>

export default {
  data () {
    return {
      MOBILE_WIDTH: 600,
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
    isMobileMenu() {
      return this.$vuetify.breakpoint.width < this.MOBILE_WIDTH;
    },
    showPending() {
      return this.workflow && this.workflow.canShowPending ;
    },
  },
  methods: {
    openMobileDrawer() {
      if (this.isMobileMenu){
        this.$refs.mobileActionDrawer.open();
      }
    },
    editWorkflow() {
      this.$root.$emit('open-workflow-drawer', {workflow: this.workflow, mode: 'edit_workflow'});
    },
    deleteWorkflow() {
      this.$root.$emit('show-confirm-action', {model: this.workflow, reason: 'delete_workflow'});
    },
    open() {
      if (this.workflow.enabled && this.workflow.acl.canAddRequest) {
        this.$root.$emit('open-add-work-drawer', {object: this.workflow, mode: 'create_work'});
      }
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
