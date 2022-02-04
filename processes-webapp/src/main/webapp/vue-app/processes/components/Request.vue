<template>
  <v-card outlined class="ml-6">
    <v-container class="pa-0 request_bg_color">
      <v-row align="center pl-4 pr-4" cols="12">
        <v-col
          cols="6"
          class="pa-0 ma-0 text-align-start text-subtitle-2 font-weight-regular"
          md="3"
          lg="2">
          <v-avatar
            color="blue"
            size="30px">
            <v-icon dark>
              mdi-clock
            </v-icon>
          </v-avatar>
          <span>{{ title }}</span>
        </v-col>
        <v-col
          cols="6"
          md="4"
          class="pa-0 ma-0 grey--text"
          lg="1">
          <span>{{ created }}</span>
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
          <span>{{ totalComments }}</span>
        </v-col>
        <v-col
          cols="6"
          md="3"
          class="pa-0 ma-0 text-truncate grey--text"
          lg="2">
          <span>Dernier commentaire {{ lastCommentDate }}</span>
        </v-col>
        <v-col
          cols="6"
          md="4"
          class="pa-0 ma-0 text-truncate text-caption"
          lg="3">
          <span>{{ description }}</span>
        </v-col>
        <v-col
          cols="4"
          md="4"
          class="pa-0 ma-0 text-align-center text-truncate"
          lg="1">
          <v-chip
            class="ma-2"
            :color="statusColor"
            text-color="white">
            {{ status }}
          </v-chip>
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
    title: {
      type: String,
      default: ''
    },
    created: {
      type: String,
      default: ''
    },
    totalComments: {
      type: Number,
      default: 0
    },
    lastCommentDate: {
      type: String,
      default: ''
    },
    description: {
      type: String,
      default: ''
    },
    status: {
      type: String,
      default: ''
    },
  },
  computed: {
    statusColor() {
      return this.status === 'accepted' ? 'green' : this.status === 'pending' ? 'orange' : this.status === 'refused' ? 'red' : 'grey';
    }
  }
};
</script>
