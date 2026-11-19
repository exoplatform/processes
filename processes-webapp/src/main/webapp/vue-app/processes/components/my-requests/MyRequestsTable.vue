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
  <v-data-table
    :headers="headers"
    :items="items"
    :options.sync="options"
    :custom-sort="keepOrder"
    :mobile-breakpoint="0"
    class="data-table-light-border my-requests-table"
    item-key="key"
    must-sort
    disable-pagination
    disable-filtering
    hide-default-footer>
    <!-- the Comments column is 72px wide: its header shows the icon its cells show, and the th
         keeps the label as its aria-label -->
    <template #[`header.comments`]="{ header }">
      <v-icon
        :title="header.text"
        class="my-request-comments-header"
        size="16">
        mdi-chat-outline
      </v-icon>
    </template>
    <template #item="{ item }">
      <!-- each row keyed: Vuetify sets no key on rows an #item slot renders -->
      <tr
        v-if="item.type === 'header'"
        :key="item.key"
        :id="`myRequestsGroup-${item.hook}`"
        :class="`my-request-group-${item.hook}`"
        class="my-request-group-row">
        <td :colspan="headers.length" class="my-request-group-header text-light-color subtitle-2">
          {{ item.label }} ({{ item.count }})
        </td>
      </tr>
      <tr
        v-else-if="item.type === 'more'"
        :key="item.key"
        class="my-request-more-row">
        <td :colspan="headers.length">
          <v-btn
            :id="`myRequestsShowMore-${item.hook}`"
            class="my-requests-show-more text-none"
            color="primary"
            small
            text
            @click="expand(item.groupKey)">
            {{ $t('processes.myRequests.showMore', {0: item.hidden}) }}
          </v-btn>
        </td>
      </tr>
      <tr
        v-else-if="item.type === 'older'"
        :key="item.key"
        class="my-request-older-row">
        <td :colspan="headers.length">
          <v-btn
            id="myRequestsShowOlderCompleted"
            class="my-requests-show-older text-none"
            color="primary"
            small
            text
            @click="$root.$emit('load-more-completed-works')">
            {{ $t('processes.myRequests.showOlderCompleted') }}
          </v-btn>
        </td>
      </tr>
      <tr
        v-else-if="item.type === 'hint'"
        :key="item.key"
        class="my-request-hint-row">
        <td
          id="myRequestsDraftsOnlyHint"
          :colspan="headers.length"
          class="caption text-light-color">
          {{ item.text }}
        </td>
      </tr>
      <work
        v-else
        :key="item.key"
        :work="item.work"
        :is-draft="item.isDraft" />
    </template>
  </v-data-table>
</template>

<script>
import {LARGE_WIDTH, WIDE_WIDTH, hookKeyOf, sortWorks} from '../../js/MyRequestsFilters.js';

export default {
  props: {
    groups: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    options: {sortBy: ['date'], sortDesc: [true]},
    expanded: {},
  }),
  computed: {
    /**
     * Tells whether the screen is wide enough for the comments and the status columns.
     *
     * @returns {Boolean} true from 600px
     */
    wide() {
      return this.$vuetify.breakpoint.width >= WIDE_WIDTH;
    },
    /**
     * Lists the columns shown at the screen's width: from 600px the comments and the status, from
     * 960px the process. A hidden column is left out rather than hidden with a class: the table
     * renders one `col` per header, and under its fixed layout a `col` whose cells are hidden would
     * still take a share of the width from the Request column.
     *
     * @returns {Array} the headers
     */
    headers() {
      const wide = this.wide;
      const large = this.$vuetify.breakpoint.width >= LARGE_WIDTH;
      return [
        {text: this.$t('processes.myRequests.column.request'), value: 'request', sortable: false, class: 'my-request-col-request text-no-wrap'},
        wide && {text: this.$t('processes.myRequests.column.comments'), value: 'comments', sortable: false, width: 72, class: 'text-no-wrap'},
        large && {text: this.$t('processes.myRequests.column.process'), value: 'process', sortable: false, width: 200, class: 'text-no-wrap'},
        {text: this.$t('processes.myRequests.column.date'), value: 'date', width: 110, class: 'text-no-wrap'},
        wide && {text: this.$t('processes.myRequests.column.status'), value: 'status', width: 130, class: 'text-no-wrap'},
        {text: '', value: 'actions', sortable: false, width: 48},
      ].filter(Boolean);
    },
    /**
     * Flattens the groups into the table's rows: each group's header row (none for the flat list's
     * single group), its requests sorted by the chosen column, a « Show N more » row while a capped
     * group hides some, a « Show older completed requests » row while older completed requests
     * remain to load, and its hint row. A header counts the server's total when the group gives
     * one. A request of the flat list is a draft when its group's `drafts` holds it.
     *
     * @returns {Array} the rows, each with a unique key and a type
     */
    items() {
      const sortBy = this.options.sortBy[0] || 'date';
      const sortDesc = !!this.options.sortDesc[0];
      const rows = [];
      this.groups.forEach(group => {
        const hook = hookKeyOf(group.key);
        const isDraft = work => !!group.isDraft || !!(group.drafts && group.drafts.has(work));
        const statusLabelOf = work => (isDraft(work) ? this.$t('processes.myWorks.status.draftLabel') : this.statusLabelOf(work));
        const works = sortWorks(group.works, sortBy, sortDesc, statusLabelOf);
        const shown = group.cap && !this.expanded[group.key] ? works.slice(0, group.cap) : works;
        if (!group.flat) {
          rows.push({key: `header-${group.key}`, type: 'header', hook, label: group.label, count: group.total == null ? works.length : group.total});
        }
        shown.forEach(work => rows.push({key: `${isDraft(work) ? 'draft' : 'work'}-${work.id}`, type: 'work', work, isDraft: isDraft(work)}));
        if (shown.length < works.length) {
          rows.push({key: `more-${group.key}`, type: 'more', hook, groupKey: group.key, hidden: works.length - shown.length});
        }
        if (group.hasMore) {
          rows.push({key: `older-${group.key}`, type: 'older'});
        }
        if (group.hint) {
          rows.push({key: `hint-${group.key}`, type: 'hint', text: group.hint});
        }
      });
      return rows;
    },
  },
  watch: {
    /**
     * Sorts by date again when the Status column leaves on a narrowing screen, so the order never
     * follows a column the table no longer shows.
     *
     * @param {Boolean} wide whether the Status column shows
     * @returns {void}
     */
    wide(wide) {
      if (!wide && this.options.sortBy[0] === 'status') {
        this.options = {...this.options, sortBy: ['date'], sortDesc: [true]};
      }
    },
  },
  methods: {
    /**
     * Keeps the rows in the order `items` built: the table sorts within each group itself.
     *
     * @param {Array} items the rows
     * @returns {Array} the same rows
     */
    keepOrder(items) {
      return items;
    },
    /**
     * Shows every request of a capped group.
     *
     * @param {String} groupKey the group
     * @returns {void}
     */
    expand(groupKey) {
      this.$set(this.expanded, groupKey, true);
    },
    /**
     * Reads a request's translated status, to sort by status: the drafts share their own group.
     *
     * @param {Object} work the request
     * @returns {String} the label
     */
    statusLabelOf(work) {
      const key = `tasks.status.${work.status}`;
      const translation = this.$t(key);
      return translation === key && (work.status || '') || translation;
    },
  },
};
</script>
