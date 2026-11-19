<!--
/*
 * Copyright (C) 2022 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
-->
<template>
  <work-row
    :work="workObject"
    :is-draft="isDraft"
    :comments-count="commentsCount"
    :avatar-url="workflowAvatarUrl"
    :allow-cancel="allowCancel"
    @open="openRequest"
    @open-comments="openCommentsDrawer"
    @delete="deleteWork"
    @cancel="cancelWork"
    @archive="updateCompleted" />
</template>

<script>
export default {
  data: () => ({
    comments: null,
  }),
  props: {
    work: {
      type: Object,
      default: () => {
        return {};
      },
    },
    isDraft: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    workObject() {
      return this.work || {};
    },
    /**
     * Reads the request's comments count: the list carries it, so no request is read per row;
     * once the comments drawer opened, the count follows the comments it adds and deletes.
     *
     * @returns {Number} the count, 0 for a draft or when the list did not carry it
     */
    commentsCount() {
      if (this.isDraft) {
        return 0;
      }
      return this.comments ? this.comments.length : this.workObject.commentsCount || 0;
    },
    allowCancel() {
      return this.workObject && this.workObject.status !== 'Validated' && this.workObject.status !== 'Refused';
    },
    workflowAvatarUrl() {
      return this.workObject.workFlow && this.workObject.workFlow.illustrativeAttachment
                                      && this.workObject.workFlow.illustrativeAttachment.id
                                      && `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/illustration/${this.workObject.workFlow.id}`;
    }
  },
  methods: {
    /**
     * Opens a draft to resume it, or a sent request in the request drawer, read-only.
     *
     * @returns {void}
     */
    openRequest() {
      if (this.isDraft) {
        this.openDraft();
      } else {
        this.$root.$emit('open-add-work-drawer', {object: this.workObject, mode: 'view_work', isDraft: false});
      }
    },
    openDraft() {
      this.$root.$emit('open-add-work-drawer', {object: this.workObject, mode: 'edit_work_draft'});
    },
    /**
     * Reads the request's comments, then opens them in the comments drawer, which edits the same
     * list, so the row's count follows it.
     *
     * @returns {Promise} resolved once the drawer is asked to open
     */
    openCommentsDrawer() {
      return this.$processesService.getWorkComments(this.workObject.id).then(comments => {
        this.comments = comments || [];
        const mappedWork = Object.assign({}, this.workObject);
        mappedWork.status = {
          project: {}
        };
        this.$root.$emit('show-work-comments', mappedWork, this.comments);
      });
    },
    deleteWork() {
      if (this.isDraft) {
        this.$root.$emit('show-confirm-action', {model: this.workObject, reason: 'delete_work_draft'});
      } else {
        this.$root.$emit('show-confirm-action', {model: this.workObject, reason: 'delete_work'});
      }
    },
    cancelWork() {
      this.$root.$emit('show-confirm-action', {model: this.workObject, reason: 'cancel_work'});
    },
    updateCompleted() {
      this.$root.$emit('update-work-completed', this.workObject);
    }
  },

};
</script>
