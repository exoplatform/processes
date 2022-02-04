<template>
  <div id="myRequests">
    <v-expansion-panels
      v-model="panel"
      multiple>
      <v-expansion-panel
        class="elevation-0">
        <v-expansion-panel-header
          class="text-md-body-1 font-weight-regular grey--text text--darken-1">
          <v-icon class="text-md-body-5" v-if="!panel.includes(0)">mdi-chevron-up</v-icon>
          <v-icon class="text-md-body-5" v-if="panel.includes(0)">mdi-chevron-down</v-icon>
          {{ $t('processes.myRequests.status.refused') }} (2)
          <hr class="me-8 ml-5 line-panel-request">
          <template v-slot:actions>
            <v-icon />
          </template>
        </v-expansion-panel-header>
        <v-expansion-panel-content
          class="elevation-0 ml-6">
          <request
            title="Title of project"
            created="01/01/2023"
            total-comments="2"
            last-comment-date="31/12/2022"
            description="Bonjour je soumis ce profile pour bla bla"
            status="refused" />
          <request
            title="Title of project"
            created="01/01/2023"
            total-comments="2"
            last-comment-date="31/12/2022"
            description="Bonjour je soumis ce profile pour bla bla"
            status="refused" />
        </v-expansion-panel-content>
      </v-expansion-panel>
      <v-expansion-panel
        class="elevation-0">
        <v-expansion-panel-header
          class="text-md-body-1 font-weight-regular grey--text text--darken-1">
          <v-icon class="text-md-body-5" v-if="!panel.includes(1)">mdi-chevron-up</v-icon>
          <v-icon class="text-md-body-5" v-if="panel.includes(1)">mdi-chevron-down</v-icon>
          {{ $t('processes.myRequests.status.accepted') }} (1)
          <hr class="me-8 ml-5 line-panel-request">
          <template v-slot:actions>
            <v-icon />
          </template>
        </v-expansion-panel-header>
        <v-expansion-panel-content
          class="elevation-0 ml-6">
          <request
            title="Title of project"
            created="01/01/2023"
            total-comments="2"
            last-comment-date="31/12/2022"
            description="Bonjour je soumis ce profile pour bla bla"
            status="accepted" />
        </v-expansion-panel-content>
      </v-expansion-panel>
      <v-expansion-panel
        class="elevation-0">
        <v-expansion-panel-header
          class="text-md-body-1 font-weight-regular grey--text text--darken-1">
          <v-icon class="text-md-body-5" v-if="!panel.includes(2)">mdi-chevron-up</v-icon>
          <v-icon class="text-md-body-5" v-if="panel.includes(2)">mdi-chevron-down</v-icon>
          {{ $t('processes.myRequests.status.draft') }} (1)
          <hr class="me-8 ml-5 line-panel-request">
          <template v-slot:actions>
            <v-icon />
          </template>
        </v-expansion-panel-header>
        <v-expansion-panel-content
          class="elevation-0 ml-6">
          <request
            title="Title of project"
            created="01/01/2023"
            total-comments="2"
            last-comment-date="31/12/2022"
            description="Bonjour je soumis ce profile pour bla bla"
            status="draft" />
        </v-expansion-panel-content>
      </v-expansion-panel>
      <v-expansion-panel
        class="elevation-0">
        <v-expansion-panel-header
          class="text-md-body-1 font-weight-regular grey--text text--darken-1">
          <v-icon class="text-md-body-5" v-if="!panel.includes(3)">mdi-chevron-up</v-icon>
          <v-icon class="text-md-body-5" v-if="panel.includes(3)">mdi-chevron-down</v-icon>
          {{ $t('processes.myRequests.status.pending') }} (1)
          <hr class="me-8 ml-5 line-panel-request">
          <template v-slot:actions>
            <v-icon />
          </template>
        </v-expansion-panel-header>
        <v-expansion-panel-content
          class="elevation-0 ml-6">
          <request
            title="Title of project"
            created="01/01/2023"
            total-comments="2"
            last-comment-date="31/12/2022"
            description="Bonjour je soumis ce profile pour bla bla"
            status="pending" />
        </v-expansion-panel-content>
      </v-expansion-panel>
    </v-expansion-panels>
  </div>
</template>

<script>
export default {
  data () {
    return {
      panel: [0,1],
    };
  },
  props: {
    requests: {
      type: Object,
      default: null
    },
  }
};
</script>