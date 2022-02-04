<template>
  <div id="myRequests">
    <v-expansion-panels
      v-model="panel"
      multiple>
      <v-expansion-panel
        v-for="item in items"
        :key="item.status"
        class="elevation-0">
        <v-expansion-panel-header
          class="text-md-body-1 font-weight-regular grey--text text--darken-1">
          <v-icon class="text-md-body-5" v-if="!panel.includes(3)">mdi-chevron-up</v-icon>
          <v-icon class="text-md-body-5" v-if="panel.includes(3)">mdi-chevron-down</v-icon>
          {{ item.status }} (1)
          <hr class="me-8 ml-5 line-panel-request">
          <template v-slot:actions>
            <v-icon />
          </template>
        </v-expansion-panel-header>
        <v-expansion-panel-content
          class="elevation-0 ml-6">
          <request
            v-for="request in item.requests"
            :key="request.id"
            :request="request" />
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
      type: Array,
      default: null,
    },
  },

  computed: {
    items() {
      return this.groupByKey(this.requests, 'status');
    },
  },
  methods: {
    groupByKey(arr, prop) {
      const statuses = [];
      const map = new Map(Array.from(arr, obj => [obj[prop], []]));
      arr.forEach(obj => map.get(obj[prop]).push(obj));
      for (const x of map.keys()) {
        statuses.push({status: x,requests: map.get(x)});
      }
      return statuses;
    }
  },

};
</script>