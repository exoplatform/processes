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
  <exo-drawer
    id="processesFilterDrawer"
    ref="drawer"
    v-model="drawer"
    right>
    <template #title>
      {{ $t('processes.workflow.filter.drawerTitle') }}
    </template>
    <template v-if="drawer" #content>
      <div class="d-flex flex-column">
        <!-- the label on the left, the switch on the right, as « My requests » groups by status -->
        <div class="d-flex align-center mx-4 mt-4">
          <div class="text-header">
            {{ $t('processes.workflow.filter.groupByCategory') }}
          </div>
          <div class="spacer"></div>
          <v-switch
            id="processesGroupByCategory"
            v-model="grouped"
            :aria-label="$t('processes.workflow.filter.groupByCategory')"
            class="mt-0"
            hide-details />
        </div>
        <template v-if="statusItems.length > 1">
          <div class="text-header mx-4 mt-4">
            {{ $t('processes.workflow.filter.status') }}
          </div>
          <v-radio-group
            id="processesStatusFilter"
            v-model="status"
            class="mt-2 ms-3"
            mandatory>
            <v-radio
              v-for="item in statusItems"
              :id="`processesStatusFilterItem-${item.value}`"
              :key="item.value"
              :value="item.value"
              class="mt-0">
              <template #label>
                <div class="text-body">{{ item.text }}</div>
              </template>
            </v-radio>
          </v-radio-group>
        </template>
        <div class="text-header mx-4 mt-4">
          {{ $t('processes.workflow.filter.category') }}
        </div>
        <div class="mx-4 mt-2">
          <v-autocomplete
            v-if="categoryChoices.length"
            id="processesCategoryFilter"
            v-model="categoryId"
            :items="categoryChoices"
            :aria-label="$t('processes.workflow.filter.category')"
            :placeholder="$t('processes.workflow.filter.anyCategory')"
            item-text="text"
            item-value="value"
            class="pt-0"
            dense
            outlined
            clearable
            hide-details />
          <p
            v-else
            id="processesCategoryFilterNone"
            class="text-sub-title mb-0">
            {{ $t('processes.workflow.filter.noCategoryToChoose') }}
          </p>
        </div>
      </div>
    </template>
    <template #footer>
      <div class="d-flex">
        <v-btn
          id="processesFilterReset"
          class="btn"
          text
          @click="reset">
          {{ $t('processes.myRequests.filter.reset') }}
        </v-btn>
        <v-btn
          class="btn ms-auto me-2"
          @click="close">
          {{ $t('processes.myRequests.filter.cancel') }}
        </v-btn>
        <v-btn
          id="processesFilterApply"
          color="primary"
          elevation="0"
          @click="apply">
          {{ $t('processes.myRequests.filter.apply') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
/**
 * The filter of the Processes tab: the grouping by category, the status and the category, chosen in a
 * drawer and applied together, as « My requests » does. « Reset » brings back the status the page
 * opens with, any category and no grouping.
 */
export default {
  props: {
    statusItems: {
      type: Array,
      default: () => [],
    },
    defaultStatus: {
      type: String,
      default: null,
    },
    categories: {
      type: Map,
      default: () => new Map(),
    },
  },
  data: () => ({
    drawer: false,
    grouped: false,
    status: null,
    categoryId: null,
  }),
  computed: {
    categoryChoices() {
      return [...this.categories.values()]
        .map(category => ({value: category.id, text: category.path.join(' › ')}))
        .sort((a, b) => a.text.localeCompare(b.text));
    },
  },
  methods: {
    /**
     * Opens the drawer on the grouping, the status and the category applied now.
     *
     * @param {Object} filters the applied filters: status, categoryId and grouped
     * @returns {void}
     */
    open(filters) {
      this.grouped = !!filters.grouped;
      this.status = filters.status;
      this.categoryId = filters.categoryId || null;
      this.$refs.drawer.open();
    },
    /**
     * Closes the drawer, leaving the applied filters as they were.
     *
     * @returns {void}
     */
    close() {
      this.$refs.drawer.close();
    },
    /**
     * Chooses the page's opening status, any category and no grouping again, applied with Apply.
     *
     * @returns {void}
     */
    reset() {
      this.grouped = false;
      this.status = this.defaultStatus;
      this.categoryId = null;
    },
    /**
     * Sends the chosen grouping, status and category, then closes the drawer.
     *
     * @returns {void}
     */
    apply() {
      this.$emit('apply', {grouped: this.grouped, status: this.status, categoryId: this.categoryId || null});
      this.close();
    },
  },
};
</script>
