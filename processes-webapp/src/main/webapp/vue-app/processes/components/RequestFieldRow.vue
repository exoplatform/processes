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
  <v-row
    class="requestFieldRow text-start"
    align="start"
    no-gutters>
    <v-col
      :style="stacked ? `line-height: ${textLineHeight}px; ${firstLineStyle}` : `${labelColumnWidth}; line-height: ${textLineHeight}px; ${firstLineStyle}`"
      :cols="stacked ? 12 : 4"
      class="d-flex align-start text-start pe-3">
      <span
        :style="`width: ${typeIconWidth}px; height: ${textLineHeight}px; margin-inline-end: ${typeIconGap}px`"
        class="d-inline-flex align-center justify-center flex-shrink-0">
        <v-icon
          :size="typeIconSize"
          class="icon-default-color"
          aria-hidden="true">
          {{ icon }}
        </v-icon>
      </span>
      <span class="text-light-color text-break">{{ label }}</span>
      <template v-if="stacked && $slots.action">
        <v-spacer />
        <slot name="action"></slot>
      </template>
    </v-col>
    <v-col
      :style="stacked ? `padding-inline-start: ${typeIconWidth + typeIconGap}px` : valueColumnWidth"
      :cols="stacked ? 12 : 8"
      class="py-0">
      <div
        v-if="!stacked && $slots.action"
        :style="firstLineStyle"
        class="d-flex flex-wrap align-center">
        <slot name="action"></slot>
        <span v-if="hint" class="text-caption text-light-color ms-2">{{ hint }}</span>
      </div>
      <div
        v-else-if="hint"
        :class="!stacked && 'px-2'"
        class="text-caption text-light-color text-start mb-1">
        {{ hint }}
      </div>
      <slot></slot>
    </v-col>
  </v-row>
</template>

<script>
// The grid of Forms' FormsFieldRow (forms-object/components/FormsFieldRow.vue), so the drawer's own rows line up
// with the form's fields: a row's first line, a line of text, the type icon's size, box and gap, and the label
// width of the full-screen table
const LINE_HEIGHT = 40;
const TABLE_LINE_HEIGHT = 48;
const TEXT_LINE_HEIGHT = 24;
const TYPE_ICON_SIZE = 18;
const TYPE_ICON_WIDTH = 21;
const TYPE_ICON_GAP = 10;
const TABLE_LABEL_WIDTH = 160;

/**
 * A row of the request drawer's « Your request » section, on the grid Forms
 * draws a field with: a label column of a third of the row, its type icon
 * included, then the value column, whose content starts 8px in (px-2), and an
 * optional hint under it. In full screen (table), the label column takes the
 * fixed width of Forms' full-screen table. Stacked, the label takes a line
 * of its own, its action (the action slot) at the line's end, then the hint
 * and the content start where the label's text does. On the grid, the action
 * and the hint share the value column's first line.
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
    table: {
      type: Boolean,
      default: false,
    },
    stacked: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    textLineHeight: TEXT_LINE_HEIGHT,
    typeIconSize: TYPE_ICON_SIZE,
    typeIconWidth: TYPE_ICON_WIDTH,
    typeIconGap: TYPE_ICON_GAP,
  }),
  computed: {
    /** @returns {String} the label column's style, a third of the row or the table's fixed width, its icon included */
    labelColumnWidth() {
      const width = this.table
        ? `${TABLE_LABEL_WIDTH + TYPE_ICON_WIDTH + TYPE_ICON_GAP}px`
        : `calc(100% / 3 + ${TYPE_ICON_WIDTH + TYPE_ICON_GAP}px)`;
      return `flex: 0 0 ${width}; max-width: ${width}`;
    },
    /** @returns {String} the value column's style: two thirds of the row, or what the table's label leaves */
    valueColumnWidth() {
      if (this.table) {
        return 'flex: 1 1 0; min-width: 0';
      }
      const width = `calc(200% / 3 - ${TYPE_ICON_WIDTH + TYPE_ICON_GAP}px)`;
      return `flex: 0 0 ${width}; max-width: ${width}`;
    },
    /** @returns {String} the first line's height, the label's text centred in it */
    firstLineStyle() {
      const height = this.table ? TABLE_LINE_HEIGHT : LINE_HEIGHT;
      const padding = (height - TEXT_LINE_HEIGHT) / 2;
      return `min-height: ${height}px; padding-top: ${padding}px; padding-bottom: ${padding}px`;
    },
  },
};
</script>
