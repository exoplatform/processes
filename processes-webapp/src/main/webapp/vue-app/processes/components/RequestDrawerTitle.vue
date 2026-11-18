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
  <div class="d-flex flex-column text-truncate">
    <span>{{ $t(viewMode ? 'processes.works.work.label' : 'processes.request.newTitle') }}</span>
    <div
      v-if="!expanded"
      id="requestTitleLine"
      :class="{'request-title-line-shown': viewMode || lineShown}"
      class="request-title-line d-flex align-center text-caption text-light-color text-truncate">
      <v-avatar
        v-if="avatarUrl"
        class="me-1 flex-shrink-0"
        size="18">
        <v-img :src="avatarUrl" />
      </v-avatar>
      <span :style="textDecoration" class="text-truncate">{{ titleLine }}</span>
    </div>
  </div>
</template>

<script>
export default {
  props: {
    /** The request being shown: its process, and whether it is completed */
    work: {
      type: Object,
      default: null
    },
    /** The process's illustration, none when it has none */
    avatarUrl: {
      type: String,
      default: null
    },
    viewMode: {
      type: Boolean,
      default: false
    },
    /** True once the guide's process name scrolled out of view */
    lineShown: {
      type: Boolean,
      default: false
    },
    expanded: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    /** @returns {String} the header's process line: the process, then its managing space */
    titleLine() {
      const workflow = this.work && this.work.workFlow || {};
      const space = workflow.parentSpace && workflow.parentSpace.displayName;
      return space ? `${workflow.title} · ${space}` : workflow.title;
    },
    /** @returns {String} the line's style, struck through once the request is completed */
    textDecoration() {
      return this.work && this.work.completed && 'text-decoration:line-through' || '';
    }
  }
};
</script>
