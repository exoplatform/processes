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
  <div class="ms-1 pe-1 mt-4">
    <v-label>
      <span class="text-color body-2">{{ $t('processes.workflow.labels.label') }}</span>
    </v-label>
    <v-autocomplete
      id="processLabelsField"
      ref="menuField"
      :value="value"
      :items="items"
      :item-value="itemKey"
      :search-input.sync="search"
      :placeholder="$t('processes.workflow.labels.placeholder')"
      :error-messages="nameError"
      :menu-props="menuProps"
      class="processLabelsField pt-0 mt-1"
      item-text="name"
      return-object
      multiple
      small-chips
      outlined
      dense
      hide-selected
      @change="select">
      <template #no-data>
        <v-list-item class="pa-0">
          <v-list-item-title class="px-2 text-wrap">
            {{ $t('processes.workflow.labels.noLabel') }}
          </v-list-item-title>
        </v-list-item>
      </template>
      <template #selection="{ item }">
        <v-chip
          class="processLabelChip"
          small
          close
          @click:close="remove(item)">
          {{ item.name }}
        </v-chip>
      </template>
      <template #item="{ item }">
        <v-list-item-content :class="item.toAdd && 'processLabelAddItem' || 'processLabelItem'">
          <v-list-item-title>
            {{ item.toAdd ? $t('processes.workflow.labels.add', {0: item.name}) : item.name }}
          </v-list-item-title>
        </v-list-item-content>
      </template>
    </v-autocomplete>
  </div>
</template>

<script>
import FieldMenuWidthMixin from '../js/FieldMenuWidthMixin.js';

export default {
  mixins: [FieldMenuWidthMixin],
  props: {
    value: {
      type: Array,
      default: () => [],
    },
    projectId: {
      type: Number,
      default: null,
    },
    spaceId: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    projectLabels: [],
    search: null,
    nameMaxLength: 50,
  }),
  computed: {
    typedName() {
      return (this.search || '').trim();
    },
    nameError() {
      return this.typedName.length > this.nameMaxLength
        && this.$t('processes.workflow.labels.nameTooLong', {0: this.nameMaxLength})
        || null;
    },
    items() {
      const items = this.value.concat(this.projectLabels.filter(label => !this.value.some(chosen => chosen.id === label.id)));
      const name = this.typedName;
      if (name && name.length <= this.nameMaxLength && !items.some(item => this.sameName(item.name, name))) {
        items.push({name, toAdd: true});
      }
      return items;
    },
  },
  watch: {
    projectId() {
      this.listProjectLabels();
    },
    spaceId() {
      this.listProjectLabels();
    },
  },
  created() {
    this.listProjectLabels();
  },
  methods: {
    /**
     * Lists the labels of the chosen project; a new project, or a project
     * whose labels the user may not list, offers adding names only.
     *
     * @returns {void}
     */
    listProjectLabels() {
      this.projectLabels = [];
      const projectId = this.projectId;
      if (!projectId || !this.spaceId) {
        return;
      }
      this.$processesService.getProjectLabels(projectId, this.spaceId)
        .then(labels => {
          if (projectId === this.projectId) {
            this.projectLabels = labels || [];
          }
        })
        .catch(() => this.projectLabels = []);
    },
    /**
     * Emits the chosen labels: an existing one as {id, name}, an added one
     * as {name}.
     *
     * @param {Array} items the selected items
     * @returns {void}
     */
    select(items) {
      this.search = null;
      this.$emit('input', items.map(item => item.id && {id: item.id, name: item.name} || {name: item.name}));
    },
    /**
     * Removes a chosen label.
     *
     * @param {Object} item the label of the chip
     * @returns {void}
     */
    remove(item) {
      const key = this.itemKey(item);
      this.$emit('input', this.value.filter(label => this.itemKey(label) !== key));
    },
    /**
     * Gives the key of a label: its id, or its name for a label to add.
     *
     * @param {Object} item a label
     * @return {String} the key
     */
    itemKey(item) {
      return item.id && `id:${item.id}` || `name:${item.name.toLowerCase()}`;
    },
    /**
     * Compares two label names as the server does, ignoring case.
     *
     * @param {String} first a name
     * @param {String} second another name
     * @return {Boolean} whether they name the same label
     */
    sameName(first, second) {
      return first.trim().toLowerCase() === second.toLowerCase();
    },
  },
};
</script>
