<template>
  <v-app id="addWorkDrawer">
    <exo-drawer
      @close="close"
      ref="work"
      right>
      <template slot="title">
        <div>
          <span class="ml-2 mr-2">{{ $t('processes.works.work.label') }}</span>
          <span class="work-title pa-2">
            <v-avatar
              class="me-1"
              color="blue"
              size="30px">
              <v-icon
                dark>
                mdi-clock
              </v-icon>
            </v-avatar>
            <span class="white--text">{{ this.work.workFlow.title }}</span>
          </span>
        </div>
      </template>
      <template slot="content">
        <div class="pa-4">
          <p
            v-if="!viewMode"
            class="font-weight-regular grey--text darken-1 font-italic">
            {{ $t('processes.work.add.info.message') }}
          </p>
          <div
            class="pb-4"
            v-if="viewMode">
            <request-status
              class="float-right"
              :status="work.status" />
            <v-label class="mb-1">
              {{ $t('processes.works.form.label.requestDate') }}
            </v-label>
            <div class="grey--text mt-2">
              <custom-date-format :timestamp="work.createdTime.time" />
            </div>
          </div>
          <v-divider />
          <div class="mt-5 mb-8">
            <v-form
              v-model="valid"
              ref="form"
              id="add-work-form">
              <v-label for="description">
                {{ $t('processes.works.form.label.workDetail') }}
              </v-label>
              <v-textarea
                :readonly="viewMode"
                :rules="[rules.maxLength(maxLength)]"
                v-model="work.description"
                name="description"
                outlined
                auto-grow
                rows="30"
                row-height="15"
                class="work-detail"
                :placeholder="$t('processes.works.form.placeholder.workDetail')" />
              <custom-counter
                v-if="!viewMode"
                :value="work.description"
                :max-length="maxLength" />
            </v-form>
          </div>
          <v-divider />
          <div class="mt-8">
            <v-label for="attachment">
              {{ $t('processes.works.form.label.attachment') }}
            </v-label>
            <p class="font-weight-regular grey--text darken-1 font-italic mt-1">
              {{ $t('processes.work.add.attachment.info.message') }}
            </p>
            <div class="workAttachments">
              <attachment-app
                :entity-id="work.id"
                :space-id="processesSpaceId"
                entity-type="task" />
            </div>
            <div v-if="viewMode" class="taskCommentsAndChanges">
              <v-divider />
              <task-view-all-comments
                class="mt-4"
                :task="this.work"
                :comments="this.work.comments" />
            </div>
          </div>
        </div>
      </template>
      <template slot="footer">
        <v-btn
          class="btn">
          {{ $t('processes.work.saveDraft.label') }}
        </v-btn>
        <v-btn
          :loading="saving"
          @click="addWork"
          :disabled="!valid"
          class="btn btn-primary float-right">
          {{ $t('processes.work.sendWork.label') }}
        </v-btn>
      </template>
    </exo-drawer>
  </v-app>
</template>

<script>
export default {

  data () {
    return {
      work: {
        description: '',
        workFlow: {},
      },
      viewMode: false,
      maxLength: 1250,
      valid: false,
      rules: {
        maxLength: len => v => (v || '').length <= len || this.$t('processes.work.form.description.maxLength.message', {0: len}),
      },
    };
  },
  props: {
    processesSpaceId: {
      type: Number,
      default: null
    }
  },
  created(){
    this.$root.$on('work-added', () => {
      this.saving = false;
      this.resetInputs();
      this.close();
    });
  },
  methods: {
    open(object, mode) {
      if (mode === 'create_work') {
        this.work = {};
        this.work.workFlow = object;
        this.viewMode = false;
      } else {
        this.work = object;
        this.viewMode = true;
      }
      this.$refs.work.open();
    },
    close() {
      this.$refs.work.close();
    },
    resetInputs() {
      this.work = {
        description: '',
        workFlow: {},
      };
    },
    addWork() {
      this.$root.$emit('add-work',this.work);
    }
  }
};
</script>