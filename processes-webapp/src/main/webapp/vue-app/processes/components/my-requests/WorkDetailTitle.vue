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
  <div class="work-detail-title d-flex flex-column text-truncate">
    <span>{{ $t('processes.works.work.label') }}</span>
    <span class="work-detail-subtitle d-flex align-center caption text-light-color text-truncate">
      <v-avatar
        :class="!avatarUrl && 'workflow-card-avatar-placeholder primary--text'"
        class="flex-shrink-0 me-1"
        size="18">
        <v-img v-if="avatarUrl" :src="avatarUrl" />
        <span v-else class="font-weight-bold">{{ processTitle.charAt(0).toUpperCase() }}</span>
      </v-avatar>
      <!-- the process only: the requester is not shown the process's managing space -->
      <span class="text-truncate">{{ processTitle }}</span>
    </span>
  </div>
</template>

<script>
export default {
  props: {
    workFlow: {
      type: Object,
      default: null,
    },
  },
  computed: {
    /**
     * Reads the title of the request's process.
     *
     * @returns {String} the title
     */
    processTitle() {
      return this.workFlow && this.workFlow.title || '';
    },
    /**
     * Builds the URL of the process illustration, when the process has one.
     *
     * @returns {String} the URL, or null
     */
    avatarUrl() {
      return this.workFlow && this.workFlow.illustrativeAttachment && this.workFlow.illustrativeAttachment.id
        && `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/illustration/${this.workFlow.id}` || null;
    },
  },
};
</script>
