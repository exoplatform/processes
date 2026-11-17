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
  <section
    :class="`my-request-group-${groupKey}`"
    class="my-request-group">
    <h4 class="my-request-group-header d-flex align-center text-light-color font-weight-regular subtitle-2 mt-4 mb-2">
      <span class="flex-shrink-0 me-3">{{ label }} ({{ works.length }})</span>
      <v-divider />
    </h4>
    <work
      v-for="work in works"
      :key="work.id"
      :work="work"
      :is-draft="isDraft" />
  </section>
</template>

<script>
export default {
  props: {
    groupKey: {
      type: String,
      default: null,
    },
    label: {
      type: String,
      default: null,
    },
    works: {
      type: Array,
      default: () => [],
    },
    isDraft: {
      type: Boolean,
      default: false,
    },
  },
};
</script>
