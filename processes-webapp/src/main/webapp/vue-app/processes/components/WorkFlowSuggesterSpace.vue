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
  computed: {
    workFlowSuggesterLabels() {
      return {
        searchPlaceholder: this.$t('processes.works.form.chooseCalendar'),
        placeholder: this.$t('processes.works.form.searchPlaceholder'),
        noDataLabel: this.$t('processes.works.form.noDataLabel'),
      };
    },
  },
  watch: {
    workFlowOwner() {
      this.resetCustomValidity();

      if (this.workFlowOwner) {
        this.workflow.manager = {
          remoteId: this.workFlowOwner.remoteId,
          providerId: this.workFlowOwner.providerId,
        };
        if (this.workFlowOwner.profile) {
          this.workflow.manager.profile = {
            avatarUrl: this.workFlowOwner.profile.avatarUrl,
            fullName: this.workFlowOwner.profile.fullName,
          };
        }
      } else {
        this.workflow.manager = null;
      }
      this.$emit('initialized');
    },
  },
  methods: {
    resetCustomValidity() {
      if (this.$refs.workFlowOwnerSuggester) {
        this.$refs.workFlowOwnerSuggester.$el.querySelector('input').setCustomValidity('');
      }
    },
    reset() {
      if (this.workflow.id ||  (this.workflow && this.workflow.manager && (this.workflow.manager.id || (this.workflow.manager.remoteId && this.workflow.manager.providerId)))) { // In case of edit existing event
        this.workFlowOwner = this.$suggesterService.convertIdentityToSuggesterItem(this.workflow.manager);

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