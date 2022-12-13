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
  <work-body
    v-if="!isMobileWork"
    :work-object="workObject"
    :comments="comments"
    :is-draft="isDraft"
    :work-description="workDescription"
    :workflow-avatar-url="workflowAvatarUrl"
    :allow-cancel="allowCancel"
    :last-comment-date="lastCommentDate"
    @update-work-completed="updateCompleted"
    @open-request="openRequest"
    @open-comments-drawer="openCommentsDrawer"
    @open-draft="openDraft"
    @delete-work="deleteWork"
    @cancel-work="cancelWork" />
  <work-body-mobile
    v-else
    :work-object="workObject"
    :comments="comments"
    :is-draft="isDraft"
    :work-description="workDescription"
    :workflow-avatar-url="workflowAvatarUrl"
    :allow-cancel="allowCancel"
    :last-comment-date="lastCommentDate"
    @update-work-completed="updateCompleted"
    @open-request="openRequest"
    @open-comments-drawer="openCommentsDrawer"
    @open-draft="openDraft"
    @delete-work="deleteWork"
    @cancel-work="cancelWork" />
</template>

<script>
export default {
  data () {
    return {
      comments: [],
      MOBILE_WORK_WIDTH: 1023
    };
  },
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
    isMobileWork() {
      return this.$vuetify.breakpoint.width <= this.MOBILE_WORK_WIDTH;
    },
    workDescription() {
      return this.workObject.description && this.$utils.htmlToText(this.workObject.description);
    },
    workObject() {
      return this.work || {};
    },
    lastCommentDate() {
      return this.comments && this.comments[this.comments.length-1] && this.comments[this.comments.length-1].comment.createdTime.time;
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
  created() {
    this.getWorkComments();
  },
  methods: {
    openRequest() {
      if (this.isDraft) {
        this.openDraft();
      } else {
        this.workObject.comments = this.comments;
        this.$root.$emit('open-add-work-drawer', {object: this.workObject, mode: 'view_work', isDraft: this.isDraft});
      }
    },
    openDraft() {
      this.$root.$emit('open-add-work-drawer', {object: this.workObject, mode: 'edit_work_draft'});
    },
    openCommentsDrawer() {
      const mappedWork = Object.assign({}, this.workObject);
      mappedWork.status = {
        project: {}
      };
      this.$root.$emit('show-work-comments', mappedWork, this.comments);
    },
    getWorkComments() {
      if (this.workObject && !this.isDraft) {
        return this.$processesService.getWorkComments(this.workObject.id).then(comments => {
          this.comments = comments;
        });
      }
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
