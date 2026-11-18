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
    id="myRequestsFilterDrawer"
    ref="drawer"
    v-model="drawer"
    allow-expand
    right>
    <template #title>
      {{ $t('processes.myRequests.filter.drawerTitle') }}
    </template>
    <template v-if="drawer" #content>
      <div class="d-flex flex-column">
        <v-switch
          id="myRequestsGroupByStatus"
          v-model="grouped"
          :label="$t('processes.myRequests.filter.groupByStatus')"
          class="mx-4 mt-4"
          inset
          hide-details />
        <div class="text-header mx-4 mt-4">
          {{ $t('processes.myRequests.filter.status') }}
        </div>
        <v-radio-group
          id="myRequestsStatusFilter"
          v-model="status"
          class="mt-2 ms-3"
          mandatory>
          <v-radio
            v-for="item in statusItems"
            :id="`myRequestsStatusFilterItem-${item.value}`"
            :key="item.value"
            :value="item.value"
            class="mt-0">
            <template #label>
              <div class="text-body">{{ item.text }}</div>
            </template>
          </v-radio>
        </v-radio-group>
        <div class="text-header mx-4 mt-2">
          {{ $t('processes.myRequests.filter.process') }}
        </div>
        <v-radio-group
          id="myRequestsProcessFilter"
          v-model="workflowId"
          class="mt-2 ms-3"
          mandatory>
          <v-radio
            :id="`myRequestsProcessFilterItem-${allValue}`"
            :value="allValue"
            class="mt-0">
            <template #label>
              <div class="text-body">{{ $t('processes.myRequests.filter.allProcesses') }}</div>
            </template>
          </v-radio>
          <v-radio
            v-for="process in processes"
            :id="`myRequestsProcessFilterItem-${process.id}`"
            :key="process.id"
            :value="process.id"
            class="mt-0">
            <template #label>
              <div class="text-body text-truncate">
                {{ $t('processes.myRequests.filter.processItem', {0: process.title, 1: process.count}) }}
              </div>
            </template>
          </v-radio>
        </v-radio-group>
      </div>
    </template>
    <template #footer>
      <div class="d-flex">
        <v-btn
          id="myRequestsFilterReset"
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
          id="myRequestsFilterApply"
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
 * The advanced filter of « My requests »: the grouping by status, the status and the process, chosen
 * in a drawer and applied together, as social's spaces directory does (`SpacesListFilterDrawer`).
 */
export default {
  props: {
    statusItems: {
      type: Array,
      default: () => [],
    },
    processes: {
      type: Array,
      default: () => [],
    },
    allValue: {
      type: String,
      default: 'all',
    },
  },
  data: () => ({
    drawer: false,
    grouped: false,
    status: null,
    workflowId: null,
  }),
  methods: {
    /**
     * Opens the drawer on the grouping, the status and the process applied now.
     *
     * @param {String} status the applied status
     * @param {String|Number} workflowId the applied process
     * @param {Boolean} grouped whether the requests are grouped by status
     * @returns {void}
     */
    open(status, workflowId, grouped) {
      this.grouped = !!grouped;
      this.status = status;
      this.workflowId = workflowId;
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
     * Chooses the flat list, all statuses and all processes again, applied with Apply.
     *
     * @returns {void}
     */
    reset() {
      this.grouped = false;
      this.status = this.allValue;
      this.workflowId = this.allValue;
    },
    /**
     * Sends the chosen grouping, status and process, then closes the drawer.
     *
     * @returns {void}
     */
    apply() {
      this.$emit('apply', {grouped: this.grouped, status: this.status, workflowId: this.workflowId});
      this.close();
    },
  },
};
</script>
