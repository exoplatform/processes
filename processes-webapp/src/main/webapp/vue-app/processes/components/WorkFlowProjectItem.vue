<!--
Copyright (C) 2026 eXo Platform SAS.
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.
You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <v-list-item-content class="processProjectItem">
    <v-list-item-title>{{ project.name }}</v-list-item-title>
    <v-list-item-subtitle>{{ processCount }}</v-list-item-subtitle>
  </v-list-item-content>
</template>

<script>
export default {
  props: {
    project: {
      type: Object,
      default: null,
    },
  },
  computed: {
    processCount() {
      const count = this.project && this.project.workflows && this.project.workflows.length || 0;
      if (count === 0) {
        return this.$t('processes.workflow.project.processCount.none');
      }
      return count === 1 ? this.$t('processes.workflow.project.processCount.one')
        : this.$t('processes.workflow.project.processCount.many', {0: count});
    },
  },
};
</script>
