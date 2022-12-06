<template>
  <v-card outlined class="ml-2">
    <v-container class="pa-0 work">
      <request-status
        :is-completed="workObject.completed"
        type="indicator"
        :is-draft="isDraft"
        :status="workObject.status" />
      <v-row
        align="center"
        class="pl-4 pr-4"
        cols="12">
        <v-col
          cols="6"
          class="request-title pa-0 ma-0 text-align-start text-subtitle-2 font-weight-regular text-truncate"
          md="3"
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
            :class="`${titleClass} text-truncate`"
            @click="openRequest">
            <v-avatar
              v-if="workflowAvatarUrl"
              class="work-avatar me-n1"
              size="30px">
              <v-img :src="workflowAvatarUrl" />
            </v-avatar>
            <span
              class="work-title-text"
              :style="textDecoration">
              {{ workObject.workFlow.title }}
            </span>
          </div>
        </v-col>
        <v-col
          cols="6"
          md="4"
          class="pa-0 ma-0 grey--text"
          lg="1">
          <custom-date-format :timestamp="workObject.createdTime.time" />
        </v-col>
        <v-col
          cols="6"
          md="2"
          class="pa-0 ma-0"
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
              </v-btn>
            </template>
            <custom-date-format
              v-if="comments.length"
              :message="$t('processes.work.last.comment.message')"
              :timestamp="lastCommentDate" />
            <span v-else>{{ $t('processes.work.write.comment.message') }}</span>
          </v-tooltip>
          <span v-if="!isDraft">{{ comments.length }}</span>
        </v-col>
        <v-col
          cols="6"
          md="3"
          class="pa-0 ma-0 text-truncate grey--text"
          lg="2">
          <custom-date-format
            v-if="comments.length && !isDraft"
            :message="$t('processes.myWorks.label.lastComment')"
            :timestamp="lastCommentDate" />
        </v-col>
        <v-col
          cols="12"
          md="4"
          class="pa-0 ma-0 text-truncate text-caption"
          lg="3">
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
          class="pa-0 ma-0 text-align-center text-truncate work-status-col"
          lg="2">
          <request-status
            :is-completed="workObject.completed"
            :is-draft="isDraft"
            :status="workObject.status" />
        </v-col>
        <v-col
          cols="6"
          md="4"
          class="pa-0 ma-0 text-align-end"
          lg="1">
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
  data () {
    return {
      comments: []
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
    workDescription() {
      return this.workObject.description && this.$utils.htmlToText(this.workObject.description);
    },
    workObject() {
      return this.work || {};
    },
    lastCommentDate() {
      return this.comments && this.comments[this.comments.length-1] && this.comments[this.comments.length-1].comment.createdTime.time;
    },
    textDecoration() {
      return this.workObject && this.workObject.completed && 'text-decoration:line-through' || '';
    },
    allowCancel() {
      return this.workObject && this.workObject.status !== 'Validated' && this.workObject.status !== 'Refused';
    },
    titleClass() {
      return !this.isDraft && this.$vuetify && this.$vuetify.rtl ? 'mr-4':'' || !this.isDraft && 'ml-4' || '';
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
