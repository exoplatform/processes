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
  <div id="requestComments" class="requestComments">
    <div class="d-flex align-center mb-3">
      <span class="text-header">{{ $t('processes.request.comments') }}</span>
      <span v-if="comments.length" class="text-light-color ms-2">{{ comments.length }}</span>
    </div>
    <task-comment-item
      v-for="comment in comments"
      :key="comment.comment.id"
      :comment="comment"
      :comments="comments"
      class="mb-3"
      show-only />
    <!-- the task's composer takes the focus when it mounts: a prompt stands in for it until the viewer
         chooses to write, so that opening a sent request keeps its top in view -->
    <div
      v-if="!composing"
      id="requestCommentPrompt"
      class="requestCommentPrompt text-light-color border-color px-3 py-2"
      role="button"
      tabindex="0"
      @click="composing = true"
      @keydown.enter.prevent="composing = true"
      @keydown.space.prevent="composing = true">
      {{ $t('processes.request.comments.placeholder') }}
    </div>
    <!-- the task's composer, mounted afresh after each post; its own button gives way to the drawer's
         « Comment » footer action -->
    <task-comment-editor
      v-else
      ref="editor"
      :key="`requestCommentEditor${editorKey}`"
      v-model="message"
      :task="task"
      :placeholder="$t('processes.request.comments.placeholder')"
      :id="`requestCommentEditor-${task.id}`"
      class="requestCommentEditor"
      show-comment-editor
      @validity-updated="valid = $event"
      @attachments-edited="attachmentsEdited = true"
      @addNewComment="post" />
  </div>
</template>

<script>
/**
 * A sent request's comments, those of its task: the list, and a composer whose comment the drawer
 * posts from its footer.
 */
export default {
  props: {
    /** The request, whose task holds the comments */
    task: {
      type: Object,
      required: true,
    },
  },
  data: () => ({
    comments: [],
    message: '',
    valid: true,
    attachmentsEdited: false,
    posting: false,
    editorKey: 0,
    composing: false,
  }),
  computed: {
    /**
     * Reads the id of the request's task, which holds the comments.
     *
     * @returns {Number} the task's id
     */
    taskId() {
      return this.task.taskId || this.task.id;
    },
    /**
     * Tells whether a comment can be posted, as the task's own composer does: a valid message, with text
     * or an image, and no post under way.
     *
     * @returns {Boolean} true when the footer's « Comment » action is enabled
     */
    canPost() {
      const written = !!this.$utils.htmlToText(this.message || '').trim();
      return this.composing && !this.posting && this.valid && (written || this.attachmentsEdited);
    },
  },
  watch: {
    canPost: {
      immediate: true,
      handler(value) {
        this.$emit('can-post', value);
      },
    },
  },
  created() {
    this.load();
  },
  methods: {
    /**
     * Loads the task's comments.
     *
     * @returns {Promise} settled once they are loaded
     */
    load() {
      const taskId = this.taskId;
      return this.$taskDrawerApi.getTaskComments(taskId)
        .then(comments => {
          if (taskId === this.taskId) {
            this.comments = comments || [];
          }
        })
        .catch(() => this.comments = []);
    },
    /**
     * Posts the written comment on the task, with the images it holds, as the task's comments drawer does,
     * lists it as soon as the task holds it, then mounts an empty composer: the task composer's own reset
     * would remove it for good here.
     *
     * @returns {Promise} settled once the comment is posted
     */
    post() {
      if (!this.canPost) {
        return Promise.resolve();
      }
      this.posting = true;
      const editor = this.$refs.editor;
      const text = editor.getMessage() || '';
      const comment = text.length && this.$taskDrawerApi.urlVerify(text) || '';
      let posted = false;
      return this.$taskDrawerApi.addTaskComments(this.taskId, comment)
        .then(created => {
          posted = true;
          this.comments.push(created);
          this.message = '';
          this.attachmentsEdited = false;
          this.$root.$emit('update-task-comments', this.comments.length, this.taskId);
          return editor.saveAttachments(created.comment.id);
        })
        .catch(() => {
          // a comment the task holds is listed even when its images failed: posting it again would repeat it
          const key = posted ? 'processes.request.comments.imagesError' : 'processes.request.comments.error';
          this.$root.$emit('show-alert', {type: 'error', message: this.$t(key)});
        })
        .finally(() => {
          this.posting = false;
          if (posted) {
            this.editorKey++;
          }
        });
    },
  },
};
</script>
