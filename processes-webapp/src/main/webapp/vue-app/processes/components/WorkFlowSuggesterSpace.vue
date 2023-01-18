<!--
Copyright (C) 2022 eXo Platform SAS.
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.
You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <exo-identity-suggester
    ref="workFlowOwnerSuggester"
    v-model="workFlowOwner"
    :labels="workFlowSuggesterLabels"
    :include-users="false"
    :search-options="searchOptions"
    :width="220"
    name="workFlowOwnerAutocomplete"
    class="user-suggester workFlowOwnerAutocomplete"
    include-spaces
    required />
</template>

<script>
export default {
  props: {
    workflow: {
      type: Object,
      default: () => null,
    },
  },
  data() {
    return {
      workFlowOwner: null,
    };
  },
  created(){
    this.$root.$on('set-workflow-space', (space) => {
      if (space){
        this.workFlowOwner = {
          remoteId: space.prettyName,
          providerId: 'space',
          spaceId: space.id,
          profile: {
            avatarUrl: space.avatarUrl,
            fullName: space.displayName,
          }
        };
      } else {
        this.workFlowOwner = {};
      }
    });
  },
  computed: {
    workFlowSuggesterLabels() {
      return {
        searchPlaceholder: this.$t('processes.works.form.request.chooseSpacer'),
        placeholder: this.$t('processes.works.form.searchPlaceholder'),
        noDataLabel: this.$t('processes.works.form.noDataLabel'),
      };
    },
    searchOptions() {
      return {
        filterType: 'all',
      };
    },
  },
  watch: {
    workFlowOwner() {
      this.resetCustomValidity();
      if (this.workFlowOwner && this.workFlowOwner.spaceId) {
        this.$spaceService.getSpaceById(this.workFlowOwner.spaceId)
          .then(space => {
            this.workflow.parentSpace = space;
            this.$emit('initialized');
          });
      } else {
        this.workflow.parentSpace = null;
        this.$emit('initialized');
      }
    },
  },
  methods: {
    resetCustomValidity() {
      if (this.$refs.workFlowOwnerSuggester) {
        this.$refs.workFlowOwnerSuggester.$el.querySelector('input').setCustomValidity('');
      }
    },
    reset() {
      if (this.workflow.id ||  (this.workflow && this.workflow.space && (this.workflow.space.id || (this.workflow.space.remoteId && this.workflow.space.providerId)))) { // In case of edit existing event
        this.workFlowOwner = this.$suggesterService.convertIdentityToSuggesterItem(this.workflow.space);

        window.setTimeout(() => {
          if (this.$refs.workFlowOwnerSuggester) {
            this.$refs.workFlowOwnerSuggester.items = [this.workFlowOwner];
          }
          this.$emit('initialized');
        }, 200);
      } else {
        if (this.$refs.workFlowOwnerSuggester) {
          this.$refs.workFlowOwnerSuggester.items = [this.workFlowOwner];
        } else {
          if (this.$refs.workFlowOwnerSuggester) {
            this.$refs.workFlowOwnerSuggester.items = [];
          }
          this.workFlowOwner = {};
        }
        this.$emit('initialized');
      }
      this.$forceUpdate();
    },
  }
};
</script>