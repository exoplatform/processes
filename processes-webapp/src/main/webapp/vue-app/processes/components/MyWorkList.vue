<template>
  <div id="myWorks">
    <v-expansion-panels
      v-model="panel"
      multiple>
      <v-expansion-panel
        v-for="(item, index) in items"
        :key="item.status"
        class="elevation-0 ml-n5">
        <v-expansion-panel-header
          class="text-md-body-1 font-weight-regular grey--text text--darken-1">
          <v-icon class="text-md-body-5" v-if="!panel.includes(index)">mdi-chevron-up</v-icon>
          <v-icon class="text-md-body-5" v-if="panel.includes(index)">mdi-chevron-down</v-icon>
          {{ item.status }} (1)
          <hr class="line-panel-work">
          <template v-slot:actions>
            <v-icon />
          </template>
        </v-expansion-panel-header>
        <v-expansion-panel-content
          class="elevation-0 work-panel-content">
          <work
            v-for="work in item.works"
            :key="work.id"
            :work="work" />
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
    works: {
      type: Array,
      default: null,
    },
  },

  computed: {
    items() {
      return this.groupByKey(this.works, 'status');
    },
  },
  methods: {
    groupByKey(arr, prop) {
      const statuses = [];
      const map = new Map(Array.from(arr, obj => [obj[prop], []]));
      arr.forEach(obj => map.get(obj[prop]).push(obj));
      for (const x of map.keys()) {
        statuses.push({status: x,works: map.get(x)});
      }
      return statuses;
    }
  },

};
</script>