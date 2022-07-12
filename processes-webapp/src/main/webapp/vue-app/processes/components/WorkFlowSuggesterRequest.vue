/*
* Copyright (C) 2022 eXo Platform SAS.
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Affero General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program. If not, see <!--<http://www.gnu.org/licenses/>.-->
*/
<template>
  <v-flex class="user-suggester">
    <exo-identity-suggester
      ref="invitedAttendeeAutoComplete"
      v-model="invitedAttendee"
      :labels="workFlowSuggesterLabels"
      :include-users="false"
      :search-options="searchOptions"
      class="user-suggester workFlowOwnerAutocomplete"
      name="inviteAttendee"
      include-spaces
      include-groups
      required />
    <div v-if="workflowRequest" class="identitySuggester no-border mt-0">
      <workflow-form-attendee-item
        v-for="attendee in workflowRequest"
        :key="attendee.identity.id"
        :attendee="attendee"
        @remove-attendee="removeAttendee" />
    </div>
  </v-flex>
</template>

<script>
export default {
  props: {
    workflowRequest: {
      type: Array,
      default: () => [],
    },
  },
  data() {
    return {
      invitedAttendee: [],
    };
  },
  computed: {
    workFlowSuggesterLabels() {
      return {
        searchPlaceholder: this.$t('processes.works.form.request.chooseSpacer'),
        placeholder: this.$t('processes.works.form.request.searchPlaceholder'),
        noDataLabel: this.$t('processes.works.form.request.noDataLabel'),
      };
    },
    searchOptions() {
      return {
        filterType: 'all',
      };
    },
  },
  watch: {
    invitedAttendee() {
      if (!this.invitedAttendee) {
        this.$nextTick(this.$refs.invitedAttendeeAutoComplete.$refs.selectAutoComplete.deleteCurrentItem);
        return;
      }
      if (!this.workflowRequest) {
        this.workflowRequest = [];
      }

      const found = this.workflowRequest.find(attendee => {
        return attendee.identity.remoteId === this.invitedAttendee.remoteId
            && attendee.identity.providerId === this.invitedAttendee.providerId;
      });
      if (!found) {
        this.workflowRequest.push({
          identity: this.$suggesterService.convertSuggesterItemToIdentity(this.invitedAttendee),
        });
      }
      this.invitedAttendee = null;
      this.$emit('initialized',this.workflowRequest);
    },
  },
  methods: {
    reset() {
      if  (!this.workflowRequest || !this.workflowRequest?.length) {
        this.workflowRequest = [];
      }
      this.$emit('initialized');
    },
    removeAttendee(attendee) {
      const index = this.workflowRequest.findIndex(addedAttendee => {
        return attendee.identity.remoteId === addedAttendee.identity.remoteId
            && attendee.identity.providerId === addedAttendee.identity.providerId;
      });
      if (index >= 0) {
        this.workflowRequest.splice(index, 1);
      }
    },
  }
};
</script>
