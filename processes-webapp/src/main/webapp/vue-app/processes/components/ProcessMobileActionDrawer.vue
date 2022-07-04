<template>
  <v-app>
    <exo-drawer
      :confirm-close="confirmClose"
      :confirm-close-labels="confirmCloseLabels"
      @closed="close()"
      ref="actionDrawer"
      :bottom="true">
      <template #content>
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
