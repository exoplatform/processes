<template>
  <v-card outlined class="ml-2">
    <v-container class="pa-0 work-bg-color">
      <request-status
        type="indicator"
        :is-draft="isDraft"
        :status="work.status" />
      <v-row
        align="center"
        class="pl-4 pr-4"
        cols="12">
        <v-col
          @click="openRequest"
          cols="6"
          class="request-title pa-0 ma-0 text-align-start text-subtitle-2 font-weight-regular text-truncate"
          md="3"
          lg="2">
          <v-avatar
            class="work-avatar"
            color="blue"
            size="30px">
            <v-icon dark>
              mdi-clock
            </v-icon>
          </v-avatar>
          <span>{{ work.workFlow.title }}</span>
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
          <v-btn
            v-if="!isDraft"
            @click="openCommentsDrawer"
            color="grey"
            icon>
            <v-icon>mdi-chat-outline</v-icon>
          </v-btn>
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
          class="pa-0 ma-0 text-align-center text-truncate"
          lg="1">
          <request-status
            :is-draft="isDraft"
            :status="work.status" />
        </v-col>
        <v-col
          cols="6"
          md="4"
          class="pa-0 ma-0 text-align-end"
          lg="2">
          <v-btn
            v-if="isDraft"
            @click="openDraft"
            class="custom-icon-color"
            icon>
            <v-icon
              class="custom-icon-size">
              mdi-square-edit-outline
            </v-icon>
          </v-btn>
          <v-btn
            @click="deleteWork"
            class="custom-icon-color"
            icon>
            <v-icon
              class="custom-icon-size">
              mdi-trash-can-outline
            </v-icon>
          </v-btn>
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
    }
  },

};
</script>
