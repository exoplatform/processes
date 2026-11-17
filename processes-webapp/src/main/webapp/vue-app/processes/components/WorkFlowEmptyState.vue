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
  <div
    id="processesEmptyState"
    class="d-flex flex-column align-center text-center text-light-color px-4 py-12">
    <v-icon
      size="60"
      class="primary--text mb-4">
      fa-project-diagram
    </v-icon>
    <template v-if="filtered">
      <p class="text-header-title font-weight-regular mb-0">
        {{ $t('processes.workflow.empty.noResult') }}
      </p>
    </template>
    <template v-else-if="canAdd">
      <p class="text-header-title text-color font-weight-bold mb-1">
        {{ $t('processes.workflow.empty.title') }}
      </p>
      <p class="workflow-empty-description mb-4">
        {{ $t('processes.workflow.empty.description') }}
      </p>
      <v-btn
        id="processesEmptyStateAddButton"
        class="btn btn-primary text-none"
        @click="$emit('add')">
        <v-icon
          size="16"
          dark
          class="me-2">
          fa-plus
        </v-icon>
        {{ $t('processes.works.label.addRequestType') }}
      </v-btn>
    </template>
    <template v-else>
      <p class="text-header-title font-weight-regular mb-0">
        {{ $t('processes.workflow.empty.notAllowed') }}
      </p>
    </template>
  </div>
</template>

<script>
export default {
  props: {
    canAdd: {
      type: Boolean,
      default: false,
    },
    filtered: {
      type: Boolean,
      default: false,
    },
  },
};
</script>
