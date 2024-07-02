/*
* Copyright (C) 2022 eXo Platform SAS.
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Affero General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program. If not, see <!--<http://www.gnu.org/licenses/>.-->
*/
<template>
  <exo-drawer
    @closed="close()"
    ref="actionDrawer"
    :bottom="true">
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
