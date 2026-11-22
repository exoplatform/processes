<template>
  <exo-drawer
    @closed="close"
    @opened="watchContentScroll"
    @expand-updated="expanded = $event"
    ref="work"
    :allow-expand="!viewDraft"
    full-app-left-class="request-full-left grey-lighten1-background-opacity-5"
    class="ProcessesApplication"
    right>
    <template v-if="expanded && !viewMode" #fullAppLeftTitle>
      <div class="d-flex align-center align-self-stretch flex-grow-1 text-truncate">
        {{ work.workFlow.title }}
      </div>
    </template>
    <template v-else-if="expanded && viewMode" #fullAppLeftTitle>
      <div class="d-flex align-center align-self-stretch flex-grow-1 text-truncate">
        {{ work.workFlow && work.workFlow.title }}
      </div>
    </template>
    <template v-if="!viewDraft && !$vuetify.breakpoint.smAndDown" #expandAction>
      <v-btn
        id="requestExpandButton"
        :title="$refs.work && $refs.work.expandTooltip"
        icon
        @click="$refs.work.toogleExpand()">
        <v-icon v-text="$refs.work && $refs.work.expandIcon" size="20" />
      </v-btn>
    </template>
    <template v-if="expanded && !viewMode" #fullAppLeftContent>
      <request-guide-card
        :workflow="work.workFlow"
        :avatar-url="workflowAvatarUrl"
        class="ma-4"
        expanded />
      <request-progress-checklist
        :fill="requestFill"
        class="mx-4 mb-4" />
    </template>
    <!-- a sent request in full screen: the process's guide on the grey pane, its help open -->
    <template v-else-if="expanded && viewMode" #fullAppLeftContent>
      <request-guide-card
        :workflow="work.workFlow"
        :avatar-url="workflowAvatarUrl"
        class="ma-4"
        expanded />
    </template>
    <!-- a sent request's actions: Copy link, Open the task, Archive, Cancel request while pending -->
    <template v-if="viewMode && !viewDraft" #titleIcons>
      <work-actions-menu
        :work="work"
        :allow-cancel="allowCancel"
        :task-link="taskLink"
        @archive="archiveViewedWork"
        @cancel="cancelViewedWork" />
    </template>
    <template slot="title">
      <request-drawer-title
        :work="work"
        :avatar-url="workflowAvatarUrl"
        :view-mode="viewMode"
        :line-shown="titleLineShown"
        :expanded="expanded" />
    </template>
    <template slot="content">
      <div class="pa-4">
        <request-guide-card
          v-if="!viewMode && !expanded"
          ref="guide"
          :workflow="work.workFlow"
          :avatar-url="workflowAvatarUrl"
          class="mb-5" />
        <request-view-body
          v-if="viewMode"
          ref="viewBody"
          v-model="attachments"
          :work="work"
          :view-draft="viewDraft"
          :edit-draft="editDraft"
          :entity-type="entityType"
          :workflow-parent-space="workflowParentSpace"
          :documents="documentsToFill"
          :is-filled="isCopyFilled"
          :name-of="copy => originalOf(copy).title"
          :can-open-original="canOpenOriginal"
          :task-link="taskLink"
          :expanded="expanded"
          :open-key="openKey"
          @open-document="openDocument($event)"
          @open-original="openOriginal($event)"
          @can-comment="canComment = $event" />
        <v-form
          v-if="!viewMode"
          v-model="valid"
          ref="form"
          id="add-work-form">
          <request-fields-section :fill="requestFill" :hide-header="expanded">
            <processes-extension-sections
              ref="requestSections"
              :key="`requestSections${openKey}`"
              :params="{workflowId: work.workFlow && work.workFlow.id || null, draftId: hasDraft && work.id || null, hideHeader: true}"
              hook-id="requestDrawerSections"
              name="ProcessRequestDrawer"
              type="request-drawer-section"
              @changed="onSectionsChanged"
              @fill="onSectionsFill" />
            <request-documents-card
              v-if="processDocuments.length"
              :documents="hasDraft ? documentsToFill : processDocuments"
              :has-draft="hasDraft"
              :is-filled="isCopyFilled"
              :name-of="copy => hasDraft ? originalOf(copy).title : null"
              :can-open-original="canOpenOriginal"
              :expanded="expanded"
              @open="openDocumentToFill($event)"
              @fill="fillDocument($event)"
              @open-original="openOriginal($event)" />
            <request-message-row
              ref="messageRow"
              v-model="work.description"
              :attachments.sync="attachments"
              :max-length="maxLength"
              :workflow-parent-space="workflowParentSpace"
              :edit-draft="editDraft"
              :entity-id="work.id"
              :entity-type="entityType"
              :hidden-ids="documentsToFillIds"
              :preview-workflow-id="!hasDraft && work.workFlow && work.workFlow.id || null"
              :prepare-entity="createDraftForAttachment"
              @ready="bindEditorBlur" />
          </request-fields-section>
        </v-form>
      </div>
    </template>
    <template v-if="viewMode && !viewDraft" #footer>
      <div class="d-flex justify-end">
        <v-btn
          id="requestCommentButton"
          :disabled="!canComment"
          color="primary"
          elevation="0"
          @click="postViewComment">
          {{ $t('processes.request.comments.post') }}
        </v-btn>
      </div>
    </template>
    <template slot="footer" v-if="!viewMode">
      <request-drawer-actions
        :fill="requestFill"
        :saving="saving"
        :saving-draft="savingAsDraft"
        :can-save-draft="canSaveAsDraft"
        :can-submit="validWorkDescription || attachmentsChanged || sectionsChanged || sectionsFilled && hasDraft"
        @save-draft="saveAsDraft"
        @submit="addWork" />
    </template>
  </exo-drawer>
</template>

<script>
import RequestCloseMixin from '../js/RequestCloseMixin.js';
import RequestDocumentsMixin from '../js/RequestDocumentsMixin.js';
import RequestDraftMixin from '../js/RequestDraftMixin.js';
import RequestDrawerExtrasMixin from '../js/RequestDrawerExtrasMixin.js';
import RequestLayoutMixin from '../js/RequestLayoutMixin.js';
import RequestOpenMixin from '../js/RequestOpenMixin.js';
import RequestViewMixin from '../js/RequestViewMixin.js';
import '../js/RequestEditorExtension.js';

export default {
  mixins: [RequestDraftMixin, RequestDrawerExtrasMixin, RequestLayoutMixin, RequestDocumentsMixin, RequestCloseMixin, RequestOpenMixin, RequestViewMixin],

  data () {
    return {
      work: {
        description: '',
        workFlow: {},
        draftId: null,
        isDraft: false,
        attachments: []
      },
      attachments: [],
      viewMode: false,
      viewDraft: false,
      editDraft: false,
      maxLength: 1250,
      valid: false,
      saving: false,
      rules: {
        maxLength: len => v => (v || '').length <= len || this.$t('processes.work.form.description.maxLength.message', {0: len}),
      },
      openKey: 0
    };
  },
  created(){
    document.addEventListener('open-request-attachments', this.openRequestAttachments);
    // Loads the modules whose name contains ProcessRequestExtension, which register their sections
    this.$utils.includeExtensions('ProcessRequestExtension');
  },
  beforeDestroy() {
    document.removeEventListener('open-request-attachments', this.openRequestAttachments);
  },
  computed: {
    workDescription() {
      return this.work && this.work.description || '';
    },
    entityType() {
      return this.editDraft || this.viewDraft ? 'workdraft' : 'task';
    },
    validWorkDescription() {
      return this.work?.description?.length > 0 && this.$utils.htmlToText(this.work.description).length <= this.maxLength;
    },
    emptyOrValidWorkDescription() {
      return this.validWorkDescription || !this.work?.description?.length;
    },
    workflowParentSpace() {
      return this.work && this.work.workflow && this.work.workflow.parentSpace;
    },
    workflowAvatarUrl() {
      return this.work.workFlow && this.work.workFlow.illustrativeAttachment
                                && this.work.workFlow.illustrativeAttachment.id
                                && `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/illustration/${this.work.workFlow.id}`;
    }
  },
};
</script>
