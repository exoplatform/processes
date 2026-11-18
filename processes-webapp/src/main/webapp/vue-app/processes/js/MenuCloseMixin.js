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
 * none of a portlet's own DOM, so a click in this application never reaches it. The document is
 * listened to while the menu is open, on `click`, never `mousedown`, which would close the menu
 * before an item's click: the menus using this mixin have a Vuetify activator opened on click,
 * which stops its own click before it reaches the document, as social's `ActivityHeadMenu.vue` does.
 * Since another menu's button stops its click the same way, an opening menu also tells the
 * others, which close.
 *
 * The component binds its menu with `v-model="showMenu"` and `ref="menu"`: a click inside its own
 * content is left to the menu, which closes on an item click.
 */
const MENU_OPENED_EVENT = 'processes-menu-opened';

export default {
  data: () => ({
    showMenu: false,
  }),
  watch: {
    showMenu(value) {
      if (value) {
        document.dispatchEvent(new CustomEvent(MENU_OPENED_EVENT, {detail: this._uid}));
        document.addEventListener('click', this.closeMenuOnOutsideClick);
        document.addEventListener('keydown', this.closeMenuOnEscape);
        document.addEventListener(MENU_OPENED_EVENT, this.closeMenuOnOtherOpened);
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
      const content = this.$refs.menu && this.$refs.menu.$refs && this.$refs.menu.$refs.content;
      const inContent = !!(content && content.contains(target));
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
     * Closes the menu when another menu opens.
     *
     * @param {CustomEvent} event the opening, which names the opened menu's component
     * @returns {void}
     */
    closeMenuOnOtherOpened(event) {
      if (event.detail !== this._uid) {
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
      document.removeEventListener(MENU_OPENED_EVENT, this.closeMenuOnOtherOpened);
    },
  },
};
