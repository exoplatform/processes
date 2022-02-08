<template>
  <v-app id="addWorkDrawer">
    <exo-drawer
      @close="close"
      ref="work"
      allow-expand
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
            <span class="white--text">{{ this.workflow.title }}</span>
          </span>
        </div>
      </template>
      <template slot="content">
        <div class="pa-4">
          <p class="font-weight-regular grey--text darken-1 font-italic">{{ $t('processes.work.add.info.message') }}</p>
          <v-divider />
          <div class="mt-5 mb-8">
            <form ref="form">
              <v-label for="description">
                {{ $t('processes.works.form.label.workDetail') }}
              </v-label>
              <textarea
                required
                v-model="work.decsription"
                name="description"
                rows="30"
                maxlength="2000"
                noresize
                class="input-block-level ignore-vuetify-classes my-3 work-detail"
                :placeholder="$t('processes.works.form.placeholder.workDetail')">
            </textarea>
            </form>
          </div>
          <v-divider />
          <div class="mt-8">
            <v-label for="attachment">
              {{ $t('processes.works.form.label.attachment') }}
            </v-label>
            <p class="font-weight-regular grey--text darken-1 font-italic mt-1">
              {{ $t('processes.work.add.attachment.info.message') }}
            </p>
            <a class="viewAllAttachments primary--text font-weight-bold text-decoration-underline">
              {{ $t('processes.work.add.attachment.label') }}(1)
            </a>
          </div>
        </div>
      </template>
      <template slot="footer">
        <v-btn
          class="btn">
          {{ $t('processes.work.saveDraft.label') }}
        </v-btn>
        <v-btn
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
      },
      workflow: {},
      viewMode: false
    };
  },
  methods: {
    open(workflow, mode) {
      this.workflow = workflow;
      this.viewMode = mode !== 'create_work';
      this.$refs.work.open();
    },
    close() {
      this.$refs.work.close();
    },
  }
};
</script>