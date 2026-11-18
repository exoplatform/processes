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
    v-if="noMatch"
    id="myRequestsNoMatch"
    class="d-flex flex-column align-center text-center text-light-color py-10 px-4">
    <h3 class="text-color text-h6 mb-1">
      {{ $t('processes.myRequests.empty.noMatch') }}
    </h3>
    <p class="mb-2">{{ $t('processes.myRequests.empty.noMatchHint') }}</p>
    <v-btn
      id="myRequestsClearFilters"
      class="text-none"
      color="primary"
      text
      @click="$emit('clear')">
      {{ $t('processes.myRequests.clearFilters') }}
    </v-btn>
  </div>
  <div
    v-else
    id="myRequestsEmptyState"
    class="d-flex flex-column align-center text-center text-light-color py-10 px-4">
    <v-avatar
      class="my-requests-empty-icon"
      size="120">
      <v-icon color="primary" size="56">mdi-inbox-outline</v-icon>
    </v-avatar>
    <h3 class="text-color text-h6 mt-4 mb-1">
      {{ $t('processes.myRequests.empty.title') }}
    </h3>
    <p class="my-requests-empty-description mb-4">
      {{ $t('processes.myRequests.empty.description') }}
    </p>
    <v-btn
      id="myRequestsBrowseProcesses"
      class="btn btn-primary text-none"
      @click="$root.$emit('processes-show-tab', 0)">
      {{ $t('processes.myRequests.empty.browse') }}
    </v-btn>
  </div>
</template>

<script>
export default {
  props: {
    noMatch: {
      type: Boolean,
      default: false,
    },
  },
};
</script>
