/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Sizes the menu of an autocomplete referenced as `menuField` to the field:
 * the menu stays detached from the drawer, as the platform's suggesters keep
 * theirs, and opens under the field at its width. The width is read whenever
 * the field's size changes (its drawer opening included), never as the menu
 * opens: menu props that change while the menu opens re-render and move it.
 */
export default {
  data: () => ({
    menuWidth: null,
    observedField: null,
    fieldObserver: null,
  }),
  computed: {
    menuProps() {
      return this.menuWidth && {offsetY: true, minWidth: this.menuWidth, maxWidth: this.menuWidth} || {offsetY: true};
    },
  },
  mounted() {
    this.observeMenuField();
  },
  updated() {
    this.observeMenuField();
  },
  beforeDestroy() {
    if (this.fieldObserver) {
      this.fieldObserver.disconnect();
    }
  },
  methods: {
    /**
     * Watches the size of the field, which may be mounted later than its component (behind a v-if).
     *
     * @returns {void}
     */
    observeMenuField() {
      const field = this.$refs.menuField && this.$refs.menuField.$el || null;
      if (field === this.observedField) {
        return;
      }
      if (!this.fieldObserver && window.ResizeObserver) {
        this.fieldObserver = new window.ResizeObserver(this.measureMenuWidth);
      }
      if (this.fieldObserver) {
        if (this.observedField) {
          this.fieldObserver.unobserve(this.observedField);
        }
        if (field) {
          this.fieldObserver.observe(field);
        }
      }
      this.observedField = field;
      this.measureMenuWidth();
    },
    /**
     * Reads the field's width; a hidden field (a closed drawer) has none, and keeps the last one read.
     *
     * @returns {void}
     */
    measureMenuWidth() {
      const width = this.observedField && this.observedField.offsetWidth;
      if (width && width !== this.menuWidth) {
        this.menuWidth = width;
      }
    },
  },
};
