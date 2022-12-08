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
  <v-card outlined class="ml-2 work-card">
    <v-container class="pa-0 work full-height">
      <request-status
        :is-completed="workObject.completed"
        type="indicator"
        :is-draft="isDraft"
        :status="workObject.status" />
      <v-row
        align="center"
        class="pl-4 pr-4 full-height"
        cols="12">
        <v-col
          cols="6"
          class="request-title pa-0 ma-0 text-align-start text-subtitle-2 font-weight-regular text-truncate"
          md="3"
          xl="2"
          lg="2">
          <div
            v-if="!isDraft"
            class="complete-btn">
            <v-tooltip
              bottom>
              <template #activator="{ on, attrs }">
                <btn
                  v-bind="attrs"
                  v-on="on"
                  @click="updateCompleted"
                  icon>
                  <v-icon
                    class="custom-icon-size"
                    color="primary"
                    v-if="!workObject.completed">
                    mdi-checkbox-blank-circle-outline
                  </v-icon>
                  <v-icon
                    class="custom-icon-size"
                    color="primary"
                    v-if="workObject.completed">
                    mdi-check-circle
                  </v-icon>
                </btn>
              </template>
              <span v-if="!workObject.completed">
                {{ $t('processes.work.complete.request.message') }}
              </span>
              <span v-if="workObject.completed">
                {{ $t('processes.work.unComplete.request.message') }}
              </span>
            </v-tooltip>
          </div>
          <div
            :class="!isDraft? 'ms-6':''"
            class="text-truncate"
            @click="openRequest">
            <v-avatar
              v-if="workflowAvatarUrl"
              class="ms-2 me-n1"
              size="35px">
              <v-img :src="workflowAvatarUrl" />
            </v-avatar>
            <span
              class="work-title-text ms-2 position-relative"
              :class="workObject.completed? 'text-decoration-line-through': ''">
              {{ workObject.workFlow.title }}
            </span>
          </div>
        </v-col>
        <v-col
          cols="6"
          md="4"
          class="pa-0 ma-0 grey--text text-caption text-sm-body-2"
          xl="1"
          lg="1">
          <custom-date-format :timestamp="workObject.createdTime.time" />
        </v-col>
        <v-col
          cols="6"
          md="3"
          class="pa-0 ma-0"
          xl="1"
          lg="1">
          <v-tooltip
            v-if="!isDraft"
            bottom>
            <template #activator="{ on, attrs }">
              <v-btn
                v-bind="attrs"
                v-on="on"
                @click="openCommentsDrawer"
                color="grey"
                icon>
                <v-icon class="custom-icon-size custom-icon-color">
                  mdi-chat-outline
                </v-icon>
                <span
                  class="custom-icon-color"
                  v-if="!isDraft">{{ comments.length }}</span>
              </v-btn>
            </template>
            <custom-date-format
              v-if="comments.length"
              :message="$t('processes.work.last.comment.message')"
              :timestamp="lastCommentDate" />
            <span v-else>{{ $t('processes.work.write.comment.message') }}</span>
          </v-tooltip>
        </v-col>
        <v-col
          cols="12"
          md="5"
          class="pa-0 ma-0 text-truncate text-caption"
          xl="4"
          lg="4">
          <v-tooltip
            bottom>
            <template #activator="{ on, attrs }">
              <span
                v-bind="attrs"
                v-on="on">
                {{ workDescription }}
              </span>
            </template>
            <span v-sanitized-html="workObject.description"></span>
          </v-tooltip>
        </v-col>
        <v-col
          cols="6"
          md="4"
          class="pa-0 ma-0 text-align-center text-truncate work-status-col font-weight-bold"
          :lg="isDraft? '2': '3'"
          :xl="isDraft? '2': '3'">
          <request-status
            :is-completed="workObject.completed"
            :is-draft="isDraft"
            :status="workObject.status" />
        </v-col>
        <v-col
          cols="6"
          md="4"
          class="pa-0 ma-0 text-align-end"
          :xl="isDraft?'2':'1'"
          :lg="isDraft?'2':'1'">
          <v-btn
            v-if="isDraft"
            @click="openDraft"
            icon>
            <v-icon
              class="custom-icon-size custom-icon-color">
              mdi-square-edit-outline
            </v-icon>
          </v-btn>
          <v-btn
            v-if="isDraft"
            @click="deleteWork"
            icon>
            <v-icon
              class="custom-icon-size custom-icon-color">
              mdi-trash-can-outline
            </v-icon>
          </v-btn>
          <v-tooltip
            v-else-if="!workObject.completed && allowCancel"
            bottom>
            <template #activator="{ on, attrs }">
              <v-btn
                v-bind="attrs"
                v-on="on"
                @click="cancelWork"
                icon>
                <v-icon
                  class="custom-icon-size custom-icon-color">
                  mdi-close-circle
                </v-icon>
              </v-btn>
            </template>
            <span>{{ $t('processes.work.cancel.request.message') }}</span>
          </v-tooltip>
        </v-col>
      </v-row>
    </v-container>
  </v-card>
</template>

<script>
export default {
  props: {
    workObject: {
      type: Object,
      default: () => {
        return {};
      },
    },
    isDraft: {
      type: Boolean,
      default: false
    },
    comments: {
      type: Array,
      default: () => {
        return [];
      }
    },
    workflowAvatarUrl: {
      type: String,
      default: null
    },
    workDescription: {
      type: String,
      default: null
    },
    lastCommentDate: {
      type: Number,
      default: null
    },
    allowCancel: {
      type: Boolean,
      default: false
    }
  },
  methods: {
    updateCompleted() {
      this.$emit('update-work-completed');
    },
    openRequest() {
      this.$emit('open-request');
    },
    openCommentsDrawer() {
      this.$emit('open-comments-drawer');
    },
    openDraft() {
      this.$emit('open-draft');
    },
    deleteWork() {
      this.$emit('delete-work');
    },
    cancelWork() {
      this.$emit('cancel-work');
    }
  }
};
</script>
