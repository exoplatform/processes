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
  <v-avatar
    :class="!showImage && 'workflow-card-avatar-placeholder primary--text'"
    :size="size">
    <v-img
      v-if="showImage"
      :src="avatarUrl"
      @error="imageFailed = true" />
    <v-icon
      v-else-if="icon"
      :size="iconSize"
      class="primary--text">
      {{ icon }}
    </v-icon>
    <v-icon
      v-else
      :size="iconSize"
      class="icon-default-color">
      {{ defaultIcon }}
    </v-icon>
  </v-avatar>
</template>

<script>
import {PROCESS_DEFAULT_ICON} from '../../js/ProcessDefaultIcon.js';

export default {
  props: {
    /** The URL of the process's image, null when it has none */
    avatarUrl: {
      type: String,
      default: null,
    },
    /** The process's icon name, null when it has none */
    icon: {
      type: String,
      default: null,
    },
    size: {
      type: [Number, String],
      default: 40,
    },
  },
  data: () => ({
    imageFailed: false,
    defaultIcon: PROCESS_DEFAULT_ICON,
  }),
  computed: {
    /** @returns {Boolean} true when the image shows, as the process has one that loads */
    showImage() {
      return !!this.avatarUrl && !this.imageFailed;
    },
    /** @returns {Number} the icon's size, a little smaller than the avatar */
    iconSize() {
      return Math.round(Number(this.size) * 0.55);
    },
  },
  watch: {
    avatarUrl() {
      this.imageFailed = false;
    },
  },
};
</script>
