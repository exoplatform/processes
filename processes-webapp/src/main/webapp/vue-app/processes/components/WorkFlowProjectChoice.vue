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
  <div id="processProjectChoice" class="ms-1 pe-1">
    <v-label>
      <span class="text-color body-2">{{ $t('processes.workflow.project.label') + ' *' }}</span>
    </v-label>
    <v-radio-group
      :value="mode"
      :disabled="readOnly"
      class="mt-1 mb-0"
      row
      dense
      hide-details
      @change="$emit('update:mode', $event)">
      <v-radio
        id="processProjectModeNew"
        :label="$t('processes.workflow.project.mode.new')"
        value="NEW" />
      <v-radio
        id="processProjectModeExisting"
        :label="$t('processes.workflow.project.mode.existing')"
        value="EXISTING" />
    </v-radio-group>
    <template v-if="mode === 'EXISTING'">
      <v-autocomplete
        id="processProjectSelector"
        ref="menuField"
        :value="projectId"
        :items="projects"
        :loading="loading"
        :disabled="readOnly || !spaceId"
        :placeholder="$t('processes.workflow.project.placeholder')"
        :menu-props="menuProps"
        item-text="name"
        item-value="id"
        class="pt-0 mt-1"
        outlined
        dense
        hide-details
        @change="$emit('update:projectId', $event)">
        <template #no-data>
          <v-list-item class="pa-0">
            <v-list-item-title class="px-2 text-wrap">
              {{ $t('processes.workflow.project.noProject') }}
            </v-list-item-title>
          </v-list-item>
        </template>
        <template #item="{ item }">
          <workflow-project-item :project="item" />
        </template>
        <template v-if="hasMore && !readOnly" #append-item>
          <v-list-item
            id="processProjectLoadMore"
            class="primary--text"
            @click="loadProjects()">
            {{ $t('processes.workflow.project.loadMore') }}
          </v-list-item>
        </template>
      </v-autocomplete>
      <div
        v-if="otherProcesses.length"
        id="processProjectAlsoFeeds"
        class="caption text-sub-title mt-1">
        {{ $t('processes.workflow.project.alsoFeeds', {0: otherProcesses.join(', ')}) }}
      </div>
    </template>
  </div>
</template>

<script>
import FieldMenuWidthMixin from '../js/FieldMenuWidthMixin.js';

export default {
  mixins: [FieldMenuWidthMixin],
  props: {
    mode: {
      type: String,
      default: 'NEW',
    },
    projectId: {
      type: Number,
      default: null,
    },
    spaceId: {
      type: String,
      default: null,
    },
    workflowId: {
      type: Number,
      default: null,
    },
    readOnly: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    projects: [],
    size: 0,
    loading: false,
    pageSize: 20,
    maxPagesToFindStored: 5,
    listedSpaceId: null,
    loadSequence: 0,
  }),
  computed: {
    hasMore() {
      return this.projects.length < this.size;
    },
    selectedProject() {
      return this.projects.find(project => project.id === this.projectId);
    },
    /**
     * @returns {String} the name of the project chosen, once it is listed;
     *          null otherwise
     */
    selectedProjectName() {
      return this.selectedProject && this.selectedProject.name || null;
    },
    otherProcesses() {
      const workflows = this.selectedProject && this.selectedProject.workflows || [];
      return workflows.filter(workflow => workflow.id !== this.workflowId).map(workflow => workflow.title);
    },
  },
  watch: {
    spaceId: 'listProjects',
    mode: 'listProjects',
    // The host names the project with it, such as the request forms section,
    // on create and, once the stored project is listed, on edit
    selectedProjectName: {
      immediate: true,
      handler(name) {
        this.$emit('update:projectName', name);
      },
    },
  },
  created() {
    this.listProjects();
  },
  methods: {
    /**
     * Lists the projects of the managing space from the first page, once the
     * existing-project mode is chosen; with a project already chosen, the
     * stored one on edit, pages on until it is listed, so the field can name
     * it.
     *
     * @returns {void}
     */
    listProjects() {
      if (this.mode !== 'EXISTING' || !this.spaceId) {
        return;
      }
      if (this.listedSpaceId === this.spaceId) {
        return;
      }
      this.listedSpaceId = this.spaceId;
      this.projects = [];
      this.size = 0;
      this.loadSequence++;
      this.loadProjects(this.projectId && this.maxPagesToFindStored);
    },
    /**
     * Loads the next page of eligible projects. A response to a load started
     * before the latest listing is ignored.
     *
     * @param {Number} pagesToFindStored how many more pages to load while the
     *          stored project is not listed, or a falsy value for one page; a
     *          stored project the space's processes may not feed is never
     *          listed, so the field shows empty and the id is still sent back
     * @return {Promise} the load
     */
    loadProjects(pagesToFindStored) {
      const sequence = this.loadSequence;
      this.loading = true;
      return this.$processesService.getSpaceProjects(this.spaceId, this.projects.length, this.pageSize)
        .then(page => {
          if (sequence !== this.loadSequence) {
            return;
          }
          this.projects.push(...(page && page.projects || []));
          this.size = page && page.size || 0;
          if (pagesToFindStored > 1 && this.hasMore && !this.selectedProject) {
            return this.loadProjects(pagesToFindStored - 1);
          }
        })
        .catch(() => {
          // A user who may edit the process but not list the space's projects
          // sees the field empty: the stored project is kept and sent back
          if (sequence === this.loadSequence) {
            this.size = this.projects.length;
          }
        })
        .finally(() => {
          if (sequence === this.loadSequence) {
            this.loading = false;
          }
        });
    },
  },
};
</script>
