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
 * theirs, and opens under the field at its measured width.
 */
export default {
  data: () => ({
    menuWidth: null,
  }),
  computed: {
    menuProps() {
      return this.menuWidth && {offsetY: true, minWidth: this.menuWidth, maxWidth: this.menuWidth} || {offsetY: true};
    },
  },
  methods: {
    /**
     * Reads the field's width when it gets the focus, before its menu opens.
     *
     * @returns {void}
     */
    measureMenuWidth() {
      const field = this.$refs.menuField && this.$refs.menuField.$el;
      this.menuWidth = field && field.offsetWidth || null;
    },
  },
};
