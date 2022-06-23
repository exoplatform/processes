<template>
  <exo-identity-suggester
    ref="workFlowOwnerSuggester"
    v-model="workFlowOwner"
    :labels="workFlowSuggesterLabels"
    :include-users="false"
    :searchOptions="searchOptions"
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
        searchPlaceholder: this.$t('processes.works.form.chooseCalendar'),
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
      if (this.workFlowOwner) {
        this.workflow.parentSpace = {
          id: this.workFlowOwner.spaceId,
          displayName: this.workFlowOwner.profile.fullName,
          groupId: `/spaces/${this.workFlowOwner.remoteId}`,
          name: this.workFlowOwner.remoteId,
          prettyName: this.workFlowOwner.remoteId,
          url: this.workFlowOwner.remoteId,
          avatarUrl: this.workFlowOwner.profile.avatar,
        };
      } else {
        this.workflow.parentSpace = null;
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