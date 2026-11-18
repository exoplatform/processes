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
 * Vuetify 2's own outside-click detection listens on the page's first `[data-app]`, which holds
 * none of a portlet's own DOM, so a click in this application never reaches it
 * (`domains/platform-ui.md` §14). The document is listened to while the menu is open, on
 * `click`, never `mousedown` (`best-practices/frontend-vue.md`, *Closing a menu*): the menus
 * using this mixin have a Vuetify activator opened on click, which stops its own click before
 * it reaches the document, as social's `ActivityHeadMenu.vue` does.
 *
 * The component binds its menu with `v-model="showMenu"` and names its menu content with the
 * `menu-close-content` class: a click inside it is left to the menu, which closes on an item click.
 */
export default {
  data: () => ({
    showMenu: false,
  }),
  watch: {
    showMenu(value) {
      if (value) {
        document.addEventListener('click', this.closeMenuOnOutsideClick);
        document.addEventListener('keydown', this.closeMenuOnEscape);
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
     * Closes the menu when the click is neither on its button nor in its content.
     *
     * @param {MouseEvent} event the click
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
      document.removeEventListener('click', this.closeMenuOnOutsideClick);
      document.removeEventListener('keydown', this.closeMenuOnEscape);
    },
  },
};
