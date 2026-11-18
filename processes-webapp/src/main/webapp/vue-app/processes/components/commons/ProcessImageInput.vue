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
<!--
  The image choice of an App Center application, copied from its editor row
  (ApplicationImageInput and its ApplicationIcon preview: preview, Choose and
  Upload) until the platform shares one. With nothing chosen, the preview shows
  the icon App Center gives a new application. The upload is cropped to a
  square by the platform's crop drawer; choosing an icon replaces the image,
  and uploading replaces the icon.
-->
<template>
  <div id="processImageInput" class="d-flex flex-nowrap align-center">
    <v-avatar
      :height="previewSize + 4"
      :min-width="previewSize"
      :width="previewSize + 8"
      class="d-flex justify-center align-center flex-grow-0 flex-shrink-0 me-4"
      tile>
      <v-img
        v-if="previewUrl"
        :src="previewUrl"
        :max-height="previewSize"
        :height="previewSize"
        :max-width="previewSize"
        contain
        eager />
      <v-icon
        v-else
        :size="previewSize"
        class="icon-default-color">
        {{ localIcon || 'fa-dot-circle' }}
      </v-icon>
    </v-avatar>
    <font-icon-input
      v-model="localIcon"
      class="my-auto me-4"
      button-label="processes.workflow.image.chooseIcon"
      no-default
      no-label
      no-icon />
    <v-btn
      id="processImageUploadButton"
      class="btn primary"
      border
      outlined
      @click="$refs.imageCropDrawer.open()">
      {{ $t('processes.workflow.image.upload') }}
    </v-btn>
    <image-crop-drawer
      ref="imageCropDrawer"
      :crop-options="cropOptions"
      :max-file-size="maxFileSize"
      :max-image-width="512"
      drawer-title="processes.workflow.image.cropTitle"
      @apply="applyImage" />
  </div>
</template>

<script>
export default {
  props: {
    /** The process being edited, whose stored image the preview shows */
    workflow: {
      type: Object,
      default: null,
    },
    /** The process's icon name, null when it has none */
    icon: {
      type: String,
      default: null,
    },
    /**
     * The image to save: null to remove it, the stored one to keep it, or a
     * new one carrying its content
     */
    illustration: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    previewSize: 34,
    maxFileSize: 1048576,
    cropOptions: {
      aspectRatio: 1,
      viewMode: 1,
    },
    localIcon: null,
    imageData: null,
  }),
  computed: {
    /** @returns {String} the image the preview shows: the new one, else the stored one kept, else none */
    previewUrl() {
      if (this.imageData) {
        return this.imageData;
      }
      const stored = this.illustration && this.illustration.id && this.illustration.fileName && this.workflow && this.workflow.id;
      return stored
        && `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/illustration/${this.workflow.id}?v=${this.illustration.lastUpdated || ''}`
        || null;
    },
  },
  watch: {
    icon: {
      immediate: true,
      handler(value) {
        this.localIcon = value || null;
      },
    },
    localIcon(value) {
      if (value === (this.icon || null)) {
        return;
      }
      this.$emit('update:icon', value);
      if (value) {
        // An icon chosen replaces the image
        this.imageData = null;
        this.$emit('update:illustration', null);
      }
    },
  },
  created() {
    this.$root.$on('alert-message', this.showAlert);
  },
  beforeDestroy() {
    this.$root.$off('alert-message', this.showAlert);
  },
  methods: {
    /**
     * Takes the cropped image as the process's new image, in place of its icon.
     *
     * @param {Object} image the crop drawer's result: its data URL and mime type
     * @returns {void}
     */
    applyImage(image) {
      if (!image || !image.src) {
        return;
      }
      const mimeType = image.mimetype || image.src.substring(5, image.src.indexOf(';')) || 'image/png';
      this.imageData = image.src;
      this.localIcon = null;
      this.$emit('update:illustration', {
        id: this.illustration && this.illustration.id || null,
        fileName: `illustration.${mimeType.split('/')[1]}`,
        fileBody: image.src,
        mimeType,
        fileSize: Math.round((image.src.length - image.src.indexOf(',') - 1) * 3 / 4),
      });
    },
    /**
     * Shows the crop drawer's alerts, such as a file too big, in the
     * application's alert.
     *
     * @param {String} message the alert's text
     * @param {String} type the alert's type
     * @returns {void}
     */
    showAlert(message, type) {
      this.$root.$emit('show-alert', {type: type || 'error', message});
    },
  },
};
</script>
