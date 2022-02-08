<template>
  <v-card
    min-height="300px"
    class="mt-2 mb-2 me-3"
    outlined>
    <v-btn
      outlined
      color="primary"
      class="mb-0"
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
      <template v-slot:activator="{ on, attrs }">
        <v-btn
          class="float-right"
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
            <v-icon class="work-menu-icon">
              mdi-square-edit-outline
            </v-icon>
            <span>{{ $t('processes.workflow.edit.label') }}</span>
          </v-list-item-title>
        </v-list-item>
        <v-list-item
          @click="deleteWorkflow">
          <v-list-item-title>
            <v-icon class="work-menu-icon">
              mdi-trash-can-outline
            </v-icon>
            <span>{{ $t('processes.workflow.delete.label') }}</span>
          </v-list-item-title>
        </v-list-item>
      </v-list>
    </v-menu>
    <v-card-title
      class="text-center d-block mt-0 text-truncate">
      <v-avatar
        color="blue"
        size="30px">
        <v-icon dark>
          mdi-clock
        </v-icon>
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
          class="text-truncate"
          color="orange"
          text-color="white">
          1 Request in progress
        </v-chip>
      </div>
    </v-card-text>
    <v-divider />
    <v-card-actions
      class="d-inline-block work-card-actions ma-2">
      <v-btn
        class="float-right me-8 btn card-footer-btn mt-2 mb-2"
        right
        plain
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
  },
  methods: {
    editWorkflow() {
      this.$root.$emit('open-workflow-drawer', {workflow: this.workflow, mode: 'edit_workflow'});
    },
    deleteWorkflow() {
      this.$root.$emit('show-confirm-action', {model: this.workflow, reason: 'delete_workflow'});
    },
    open() {
      this.$root.$emit('open-add-work-drawer', {usedWorkflow: this.workflow, mode: 'create_work'});
    }
  }
};
</script>