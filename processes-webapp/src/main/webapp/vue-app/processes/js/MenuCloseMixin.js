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
 * Closes a `v-menu` on a click outside it and on Escape, wherever on the page they happen.
 *
 * The platform's Vuetify (2.3) listens for a menu's outside click on the page's first
 * `[data-app]` only. The page holds one Vuetify application per portlet, so a click in this
 * application, outside the first one, never reaches it and the menu stays open. As social's
 * menus do, the document is listened to while the menu is open.
 *
 * The component binds its menu with `v-model="showMenu"`, and may name its menu content
 * with the `menu-close-content` class, so a click inside it keeps the menu open.
 */
export default {
  data: () => ({
    showMenu: false,
  }),
  watch: {
    showMenu(value) {
      if (value) {
        // the click that opened the menu is still propagating: listen once it is done
        window.setTimeout(() => {
          if (this.showMenu) {
            document.addEventListener('mousedown', this.closeMenuOnOutsideClick, true);
            document.addEventListener('keydown', this.closeMenuOnEscape, true);
          }
        });
      } else {
        this.removeMenuCloseListeners();
      }
    },
  },
  beforeDestroy() {
    this.removeMenuCloseListeners();
  },
  methods: {
    /**
     * Closes the menu when the press is neither on its button nor in its content.
     *
     * @param {MouseEvent} event the press
     * @returns {void}
     */
    closeMenuOnOutsideClick(event) {
      const target = event.target;
      const inButton = this.$el && this.$el.contains(target);
      const inContent = target && target.closest && target.closest('.menu-close-content');
      if (!inButton && !inContent) {
        this.showMenu = false;
      }
    },
    /**
     * Closes the menu on Escape.
     *
     * @param {KeyboardEvent} event the key press
     * @returns {void}
     */
    closeMenuOnEscape(event) {
      if (event.key === 'Escape' || event.key === 'Esc') {
        this.showMenu = false;
      }
    },
    /**
     * Stops listening to the document.
     *
     * @returns {void}
     */
    removeMenuCloseListeners() {
      document.removeEventListener('mousedown', this.closeMenuOnOutsideClick, true);
      document.removeEventListener('keydown', this.closeMenuOnEscape, true);
    },
  },
};
