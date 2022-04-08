<template>
  <v-card outlined class="ml-2">
    <v-container class="pa-0 work">
      <request-status
        :is-completed="work.completed"
        type="indicator"
        :is-draft="isDraft"
        :status="work.status" />
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
              <template v-slot:activator="{ on, attrs }">
                <btn
                  v-bind="attrs"
                  v-on="on"
                  @click="updateCompleted"
                  icon>
                  <v-icon
                    class="custom-icon-size"
                    color="primary"
                    v-if="!work.completed">
                    mdi-checkbox-blank-circle-outline
                  </v-icon>
                  <v-icon
                    class="custom-icon-size"
                    color="primary"
                    v-if="work.completed">
                    mdi-check-circle
                  </v-icon>
                </btn>
              </template>
              <span v-if="!work.completed">
                {{ $t('processes.work.complete.request.message') }}
              </span>
              <span v-if="work.completed">
                {{ $t('processes.work.unComplete.request.message') }}
              </span>
            </v-tooltip>
          </div>
          <div
            :class="titleClass"
            @click="openRequest">
            <v-avatar
              v-if="false"
              class="work-avatar"
              color="blue"
              size="30px">
              <v-icon dark>
                mdi-clock
              </v-icon>
            </v-avatar>
            <span
              class="work-title-text"
              :style="textDecoration">
              {{ work.workFlow.title }}
            </span>
          </div>
        </v-col>
        <v-col
          cols="6"
          md="4"
          class="pa-0 ma-0 grey--text"
          lg="1">
          <custom-date-format :timestamp="work.createdTime.time" />
        </v-col>
        <v-col
          cols="6"
          md="2"
          class="pa-0 ma-0"
          lg="1">
          <v-tooltip
            v-if="!isDraft"
            bottom>
            <template v-slot:activator="{ on, attrs }">
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
            <span>{{ $t('processes.work.request.comment.message') }}</span>
          </v-tooltip>
          <span v-if="!isDraft">{{ comments.length }}</span>
        </v-col>
        <v-col
          cols="6"
          md="3"
          class="pa-0 ma-0 text-truncate grey--text"
          lg="2">
          <span
            v-if="!isDraft && lastCommentDate">
            {{ $t('processes.myWorks.label.lastComment') }}
            <custom-date-format
              :timestamp="lastCommentDate" />
          </span>
        </v-col>
        <v-col
          cols="12"
          md="4"
          class="pa-0 ma-0 text-truncate text-caption"
          lg="3">
          <span>
            {{ this.$utils.htmlToText(work.description) }}
          </span>
        </v-col>
        <v-col
          cols="6"
          md="4"
          class="pa-0 ma-0 text-align-center text-truncate work-status-col"
          lg="2">
          <request-status
            :is-completed="work.completed"
            :is-draft="isDraft"
            :status="work.status" />
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
            v-else-if="!work.completed && allowCancel"
            bottom>
            <template v-slot:activator="{ on, attrs }">
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
      default: null,
    },
    isDraft: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    lastCommentDate() {
      return this.comments && this.comments[this.comments.length-1] && this.comments[this.comments.length-1].comment.createdTime.time;
    },
    textDecoration() {
      return this.work && this.work.completed && 'text-decoration:line-through' || '';
    },
    allowCancel() {
      return this.work && this.work.status !== 'Validated' && this.work.status !== 'Refused';
    },
    titleClass() {
      return !this.isDraft && this.$vuetify && this.$vuetify.rtl ? 'mr-5':'' || !this.isDraft && 'ml-5' || '';
    }
  },
  created() {
    this.getWorkComments();
  },
  methods: {
    openRequest() {
      this.work.comments = this.comments;
      this.$root.$emit('open-add-work-drawer', {object: this.work, mode: 'view_work', isDraft: this.isDraft});
    },
    openDraft() {
      this.$root.$emit('open-add-work-drawer', {object: this.work, mode: 'edit_work_draft'});
    },
    openCommentsDrawer() {
      const mappedWork = Object.assign({}, this.work);
      mappedWork.status = {
        project: {}
      };
      this.$root.$emit('show-work-comments', mappedWork, this.comments);
    },
    getWorkComments() {
      if (this.work && !this.isDraft) {
        this.$processesService.getWorkComments(this.work.id).then(comments => {
          this.comments = comments;
        });
      }
    },
    deleteWork() {
      if (this.isDraft) {
        this.$root.$emit('show-confirm-action', {model: this.work, reason: 'delete_work_draft'});
      } else {
        this.$root.$emit('show-confirm-action', {model: this.work, reason: 'delete_work'});
      }
    },
    cancelWork() {
      this.$root.$emit('show-confirm-action', {model: this.work, reason: 'cancel_work'});
    },
    updateCompleted() {
      this.$root.$emit('update-work-completed', this.work);
    }
  },

};
</script>
