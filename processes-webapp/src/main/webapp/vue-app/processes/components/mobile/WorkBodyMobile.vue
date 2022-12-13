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
  <v-card outlined class="ml-2">
    <v-container class="pa-0 work full-height">
      <request-status
        :is-completed="workObject.completed"
        type="indicator"
        :is-draft="isDraft"
        :status="workObject.status" />
      <v-row
        class="pl-4 pr-4 full-height"
        cols="12">
        <v-col class="pa-0">
          <v-row
            cols="12"
            no-gutters>
            <v-col cols="10">
              <div
                v-if="!isDraft"
                class="complete-btn complete-btn-mobile ms-1">
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
                class="work-avatar-wrapper text-truncate"
                @click="openRequest">
                <v-avatar
                  v-if="workflowAvatarUrl"
                  class="ms-3 me-n1 work-avatar-mobile position-relative"
                  size="40px">
                  <v-img :src="workflowAvatarUrl" />
                </v-avatar>
                <span
                  v-if="workflowAvatarUrl"
                  class="work-title-text-mobile ms-2 position-relative"
                  :class="workObject.completed? 'text-decoration-line-through': ''">
                  {{ workObject.workFlow.title }}
                </span>
                <span
                  v-else
                  class="work-title-text-no-avatar ms-5 position-relative"
                  :class="workObject.completed? 'text-decoration-line-through': ''">
                  {{ workObject.workFlow.title }}
                </span>
                <v-tooltip
                  bottom>
                  <template #activator="{ on, attrs }">
                    <div
                      :class="!workflowAvatarUrl? 'ms-5 mt-4':''"
                      class="text-truncate description-text position-relative"
                      v-bind="attrs"
                      v-on="on">
                      {{ workDescription }}
                    </div>
                  </template>
                  <span v-sanitized-html="workObject.description"></span>
                </v-tooltip>
              </div>
            </v-col>
            <v-col cols="2">
              <v-tooltip
                v-if="!isDraft && !workObject.completed && allowCancel"
                bottom>
                <template #activator="{ on, attrs }">
                  <v-btn
                    v-bind="attrs"
                    v-on="on"
                    class="float-e"
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
        </v-col>
      </v-row>
      <v-row
        class="pl-4 pr-4 full-height"
        cols="12">
        <v-col class="pa-0">
          <v-divider />
          <v-row
            cols="12"
            class="pa-1 align-items-center"
            no-gutters>
            <v-col
              class="text-caption work-mobile-date"
              cols="4">
              <custom-date-format
                class="ms-2"
                v-if="isDraft"
                :timestamp="workObject.createdTime.time" />
              <v-btn
                v-else
                class="size-icon-btn ms-1"
                @click="openCommentsDrawer"
                color="grey"
                icon>
                <v-icon class="custom-icon-size custom-icon-color">
                  mdi-chat-outline
                </v-icon>
                <span class="custom-icon-color">{{ comments.length }}</span>
              </v-btn>
            </v-col>
            <v-col
              class="text-caption work-mobile-date"
              cols="8">
              <custom-date-format
                class="float-e"
                v-if="!isDraft"
                :timestamp="workObject.createdTime.time" />
              <div
                v-else
                class="float-e">
                <v-btn
                  class="size-icon-btn me-n2"
                  @click="openDraft"
                  icon>
                  <v-icon
                    class="custom-icon-size custom-icon-color">
                    mdi-square-edit-outline
                  </v-icon>
                </v-btn>
                <v-btn
                  class="size-icon-btn"
                  @click="deleteWork"
                  icon>
                  <v-icon
                    class="custom-icon-size custom-icon-color">
                    mdi-trash-can-outline
                  </v-icon>
                </v-btn>
              </div>
            </v-col>
          </v-row>
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