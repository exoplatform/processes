<template>
  <v-flex :id="id">
    <v-autocomplete
      ref="projectSuggester"
      :items="items"
      :loading="loading"
      chips
      multiple
      deletable-chips
      dense
      :search-input.sync="loadProjects"
      item-value="id"
      item-text="name"
      hide-selected
      flat
      return-object
      persistent-hint
      v-model="value">
      <template slot="no-data">
        ttt
      </template>
      <template slot="selection" slot-scope="{item, selected}">
        {{ item }}
        {{ selected }}
      </template>
      <template slot="item" slot-scope="data">
        {{ data }}
      </template>
    </v-autocomplete>
  </v-flex>
</template>

<script>
export default {
  data () {
    return {
      loading: false
    };
  },
  props: {
    id: {
      type: String,
      default: function() {
        return 'processesProjectSuggester';
      },
    },
    value: {
      type: Object,
      default: function() {
        return false;
      },
    },
    items: {
      type: Array,
      default: function() {
        return [];
      },
    },
  },
  mounted() {
    $(`#${this.id} input`).on('blur', () => {
      this.$refs.projectSuggester.isFocused = false;
    });
  },
  methods: {
    loadProjects() {
      console.log('loading projects');
    }
  }
};
</script>