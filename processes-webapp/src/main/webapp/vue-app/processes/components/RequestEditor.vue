<!--
Copyright (C) 2021 eXo Platform SAS.
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
  <div class="editorContainer">
    <textarea
      ref="requestEditor"
      :id="id"
      v-model="inputVal"
      :placeholder="placeholder"
      cols="50"
      rows="35"
      autofocus>
      </textarea>
  </div>
</template>

<script>
export default {
  props: {
    id: {
      type: String,
      default: () => {
        return 'request-editor';
      }
    },
    value: {
      type: String,
      default: () => {
        return '';
      }
    },
    placeholder: {
      type: String,
      default: () => {
        return '';
      }
    },
  },
  data() {
    return {
      inputVal: this.value,
      SMARTPHONE_LANDSCAPE_WIDTH: 768,
      editor: null,
    };
  },
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    editorReady() {
      return this.editor && this.editor.status === 'ready';
    },
  },
  watch: {
    inputVal: function (val) {
      this.$emit('input', val);
    },
  },
  mounted() {
    this.initCKEditor(true);
  },
  destroyed() {
    this.destroyCKEditor();
  },
  methods: {
    initCKEditor(reset) {
      this.requestEditor = CKEDITOR.instances[this.id];
      if (this.requestEditor && this.requestEditor.destroy) {
        if (reset) {
          this.requestEditor.destroy(true);
        } else {
          this.initCKEditorData(this.value);
          this.setEditorReady();
          return;
        }
      }
      CKEDITOR.dtd.$removeEmpty['i'] = false;
      let extraPlugins = 'simpleLink,font,justify,widget';
      const removePlugins = 'image,confirmBeforeReload,maximize,resize';
      const windowWidth = $(window).width();
      const windowHeight = $(window).height();
      if (windowWidth > windowHeight && windowWidth < this.SMARTPHONE_LANDSCAPE_WIDTH) {
        extraPlugins = 'simpleLink';
      }
      CKEDITOR.plugins.addExternal('embedsemantic', '/commons-extension/eXoPlugins/embedsemantic/', 'plugin.js');
      CKEDITOR.basePath = '/commons-extension/ckeditor/';
      const self = this;
      $(this.$refs.requestEditor).ckeditor({
        customConfig: '/commons-extension/ckeditorCustom/config.js',
        extraPlugins,
        removePlugins,
        allowedContent: true,
        enterMode: CKEDITOR.ENTER_P,
        shiftEnterMode: CKEDITOR.ENTER_BR,
        toolbar: [
          ['Bold', 'Italic', 'BulletedList', 'NumberedList', 'Blockquote'],
        ],
        height: 150,
        autoGrow_maxHeight: 400,
        autoGrow_minHeight: 160,
        format_tags: 'p;h1;h2;h3',
        dialog_noConfirmCancel: true,
        bodyClass: 'requestEditorContent',
        on: {
          instanceReady: function () {
            self.editor = CKEDITOR.instances[self.id];
            $(self.editor.document.$).find('.atwho_inserted').each(function () {
              $(this).on('click', '.remove', function () {
                $(this).closest('[data-atwho-at-query]').remove();
              });
            });
            self.setEditorReady();
            if (self.value) {
              self.initCKEditorData(self.value);
            }
            if (self.autofocus) {
              window.setTimeout(() => self.setFocus(), 50);
            }
          },
          change: function (event) {
            const newData = event.editor.getData();
            self.inputVal = newData;
          },
          blur: function (event) {
            self.$emit('blur', event);
          },
          destroy: function () {
            self.inputVal = '';
            self.editor = null;
          }
        }
      });
    },
    initCKEditorData(message) {
      if (this.editor) {
        this.editor.setData(message);
      }
    },
    destroyCKEditor: function () {
      if (this.editor) {
        this.editor.destroy(true);
      }
    },
    setEditorReady: function() {
      if (this.editor) {
        this.$set(this.editor, 'status', 'ready');
      }
    },
    setFocus: function() {
      if (this.editorReady) {
        window.setTimeout(() => {
          this.$nextTick().then(() => this.editor.focus());
        }, 200);
      }
    },
    getMessage: function() {
      const newData = this.editor && this.editor.getData();
      return newData ? newData : '';
    },
  }
};
</script>