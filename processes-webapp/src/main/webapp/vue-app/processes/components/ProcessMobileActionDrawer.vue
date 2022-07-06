<template>
  <v-app>
    <exo-drawer
      :confirm-close="confirmClose"
      :confirm-close-labels="confirmCloseLabels"
      @closed="close()"
      ref="actionDrawer"
      :bottom="true"
      class="mobile-drawer-height">
      <template #content>
        <v-list dense>
            <v-list-item
            @click="editWorkflow">
            <v-list-item-title>
                <v-icon dense class="processes-work-menu-icon">
                mdi-square-edit-outline
                </v-icon>
                <span>{{ $t('processes.workflow.edit.label') }}</span>
            </v-list-item-title>
            </v-list-item>
            <v-list-item
            @click="deleteWorkflow">
            <v-list-item-title>
                <v-icon dense class="processes-work-menu-icon">
                mdi-delete
                </v-icon>
                <span>{{ $t('processes.workflow.delete.label') }}</span>
            </v-list-item-title>
            </v-list-item>
        </v-list>
      </template>
    </exo-drawer>
  </v-app>
</template>

<script>

export default {
  methods: {
    open() {
      this.$refs.actionDrawer.open();
    },
    editWorkflow() {
      this.$emit('editWorkflow');
      this.close();
    },
    deleteWorkflow(){
      this.$emit('deleteWorkflow');
      this.close();
    },
    close() {
      this.$refs.actionDrawer.close();
    }
  }
};
</script>
