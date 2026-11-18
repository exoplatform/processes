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
  <div class="request-field-row d-flex align-start">
    <div class="request-field-label d-flex align-start text-light-color py-2 pe-3">
      <v-icon
        class="icon-default-color me-2"
        size="18">
        {{ icon }}
      </v-icon>
      <span class="text-break">{{ label }}</span>
    </div>
    <div class="request-field-value flex-grow-1">
      <slot></slot>
      <div v-if="hint" class="text-caption text-light-color px-2 mb-1">{{ hint }}</div>
    </div>
  </div>
</template>

<script>
/**
 * A row of the request drawer's « Your request » section, drawn as Forms
 * draws a field: a label column with the field's icon, then the value and an
 * optional hint under it.
 */
export default {
  props: {
    label: {
      type: String,
      required: true,
    },
    icon: {
      type: String,
      default: 'fas fa-align-left',
    },
    hint: {
      type: String,
      default: null,
    },
  },
};
</script>
