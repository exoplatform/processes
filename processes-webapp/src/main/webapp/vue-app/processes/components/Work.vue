<template>
  <v-card outlined class="ml-2">
    <v-container class="pa-0 work-bg-color">
      <request-status
        type="indicator"
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
            color="grey"
            icon>
            <v-icon>mdi-chat-outline</v-icon>
          </v-btn>
          <span>{{ work.totalComments }}</span>
        </v-col>
        <v-col
          cols="6"
          md="3"
          class="pa-0 ma-0 text-truncate grey--text"
          lg="2">
          <span>{{ $t('processes.myWorks.label.lastComment') }} {{ work.lastCommentDate }}</span>
        </v-col>
        <v-col
          cols="6"
          md="4"
          class="pa-0 ma-0 text-truncate text-caption"
          lg="3">
          <span v-sanitized-html="work.description"></span>
        </v-col>
        <v-col
          cols="4"
          md="4"
          class="pa-0 ma-0 text-align-center text-truncate"
          lg="1">
          <request-status :status="work.status" />
        </v-col>
        <v-col
          cols="2"
          md="4"
          class="pa-0 ma-0 text-align-end"
          lg="2">
          <v-btn
            color="blue"
            icon>
            <v-icon>mdi-trash-can-outline</v-icon>
          </v-btn>
        </v-col>
      </v-row>
    </v-container>
  </v-card>
</template>

<script>
export default {
  props: {
    work: {
      type: Object,
      default: null,
    },
  },
  methods: {
    openRequest() {
      this.$root.$emit('open-add-work-drawer', {object: this.work, mode: 'view_work'});
    }
  }
};
</script>
