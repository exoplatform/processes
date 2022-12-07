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
  <exo-drawer
    ref="mobileFilter"
    :bottom="true">
    <template slot="content">
      <v-list dense>
        <v-list-item-group
          v-if="displayOptions">
          <v-list-item
            v-for="(option, index) in filterOptions"
            :key="index"
            @click="showOption(option.value)">
            <v-list-item-icon>
              <v-icon
                size="20">
                mdi-filter-menu
              </v-icon>
            </v-list-item-icon>
            <v-list-item-content class="wmf-list-item-content">
              <v-list-item-title v-text="option.label" />
            </v-list-item-content>
          </v-list-item>
        </v-list-item-group>
        <v-list-item-group
          v-if="displayQuickFilter"
          v-model="selectedItem"
          no-action
          mandatory
          color="primary">
          <v-list-item
            v-for="(item, index) in items"
            :key="index"
            :class="isMobile && 'mobile-menu-item'"
            @click="updateFilter(item.value)"
            class="px-2 text-left">
            <v-list-item-icon>
              <v-icon
                v-if="selectedItem === index"
                size="medium">
                fa-check
              </v-icon>
            </v-list-item-icon>
            <v-list-item-content class="wmf-list-item-content">
              <v-list-item-title v-text="item.label" />
            </v-list-item-content>
          </v-list-item>
        </v-list-item-group>
      </v-list>
      <exo-drawer />
    </template>
  </exo-drawer>
</template>
<script>

export default {
  data () {
    return {
      selectedItem: 0,
      displayQuickFilter: false,
      displayOptions: true,
      filterOptions: [{label: this.$t('processes.quick.filter.label'), value: 'quick_filter'}]
    };
  },
  props: {
    items: {
      type: Array,
      default: () => []
    }
  },
  watch: {
    selectedItem(value) {
      this.$emit('activated-filters-update', {
        filterType: 'quick_filter',
        enabled: value >= 0 && true || false
      });
    }
  },
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
  },
  methods: {
    updateFilter(value) {
      this.$emit('filter-changed', {filter: value});
    },
    showOption(option) {
      if (option === 'quick_filter') {
        this.showQuickFilter();
      }
      this.displayOptions = false;
    },
    showQuickFilter() {
      this.displayQuickFilter = !this.displayQuickFilter;
    },
    close() {
      this.$refs.mobileFilter.close();
    },
    open() {
      this.reset();
      this.$refs.mobileFilter.open();
    },
    reset(){
      this.displayOptions = true;
      this.displayQuickFilter = false;
    }
  }
};
</script>
