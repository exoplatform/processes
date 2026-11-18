import Processes from './components/Processes.vue';
import WorkFlowList from './components/WorkFlowList.vue';
import WorkFlowCardItem from './components/WorkFlowCardItem.vue';
import WorkFlowListToolbar from './components/WorkFlowListToolbar.vue';
import WorkFlowCardMenu from './components/WorkFlowCardMenu.vue';
import WorkFlowEnableToggle from './components/WorkFlowEnableToggle.vue';
import WorkFlowEmptyState from './components/WorkFlowEmptyState.vue';
import MyWorkList from './components/MyWorkList.vue';
import Work from './components/Work.vue';
import AddWorkFlowDrawer from './components/AddWorkFlowDrawer.vue';
import WorkFlowSuggesterSpace from './components/WorkFlowSuggesterSpace.vue';
import WorkFlowSuggesterRequest from './components/WorkFlowSuggesterRequest.vue';
import WorkFlowProjectChoice from './components/WorkFlowProjectChoice.vue';
import WorkFlowLabelsField from './components/WorkFlowLabelsField.vue';
import WorkFlowProjectItem from './components/WorkFlowProjectItem.vue';
import WorkflowFormAttendeeItem from './components/WorkflowFormAttendeeItem.vue';
import AddWorkDrawer from './components/AddWorkDrawer.vue';
import RequestEditor from './components/RequestEditor.vue';
import MyWorkListToolbar from './components/my-requests/MyWorkListToolbar.vue';
import MyRequestsFilterDrawer from './components/my-requests/MyRequestsFilterDrawer.vue';
import MyRequestsTable from './components/my-requests/MyRequestsTable.vue';
import MyRequestStatusChip from './components/my-requests/MyRequestStatusChip.vue';
import MyWorkListEmpty from './components/my-requests/MyWorkListEmpty.vue';
import WorkRow from './components/my-requests/WorkRow.vue';
import WorkActionsMenu from './components/my-requests/WorkActionsMenu.vue';
import WorkDetailDrawer from './components/my-requests/WorkDetailDrawer.vue';
import WorkDetailTitle from './components/my-requests/WorkDetailTitle.vue';
import ProcessesExtensionSections from './components/ProcessesExtensionSections.vue';

import EmptyOrLoading from './components/commons/EmptyOrLoading.vue';
import CustomCounter from './components/commons/CustomCounter.vue';
import CustomDateFormat from './components/commons/CustomDateFormat.vue';
import RequestStatus from './components/commons/RequestStatus.vue';

import CreateDocumentForm from './components/attachments-integration/CreateDocumentForm.vue';
import ProcessesAttachments from './components/attachments-integration/ProcessesAttachments.vue';

import ProcessMobileActionDrawer from './components/mobile/ProcessMobileActionDrawer.vue';

import * as processesService from './js/ProcessesService.js';
import * as ProcessesAttachmentService from './js/ProcessesAttachmentService.js';
import * as utils from './js/utils.js';

const components = {
  'processes': Processes,
  'workflow-list': WorkFlowList,
  'workflow-card-item': WorkFlowCardItem,
  'workflow-list-toolbar': WorkFlowListToolbar,
  'workflow-card-menu': WorkFlowCardMenu,
  'workflow-enable-toggle': WorkFlowEnableToggle,
  'workflow-empty-state': WorkFlowEmptyState,
  'my-work-list': MyWorkList,
  'work': Work,
  'add-workflow-drawer': AddWorkFlowDrawer,
  'workflow-suggester-space': WorkFlowSuggesterSpace,
  'workflow-suggester-request': WorkFlowSuggesterRequest,
  'workflow-project-choice': WorkFlowProjectChoice,
  'workflow-labels-field': WorkFlowLabelsField,
  'workflow-project-item': WorkFlowProjectItem,
  'workflow-form-attendee-item': WorkflowFormAttendeeItem,
  'add-work-drawer': AddWorkDrawer,
  'request-editor': RequestEditor,
  'my-work-list-toolbar': MyWorkListToolbar,
  'my-requests-filter-drawer': MyRequestsFilterDrawer,
  'my-requests-table': MyRequestsTable,
  'my-request-status-chip': MyRequestStatusChip,
  'my-work-list-empty': MyWorkListEmpty,
  'work-row': WorkRow,
  'work-actions-menu': WorkActionsMenu,
  'work-detail-drawer': WorkDetailDrawer,
  'work-detail-title': WorkDetailTitle,
  'processes-extension-sections': ProcessesExtensionSections,
  'processes-attachments': ProcessesAttachments,
  'empty-or-loading': EmptyOrLoading,
  'custom-counter': CustomCounter,
  'custom-date-format': CustomDateFormat,
  'request-status': RequestStatus,
  'create-document-from': CreateDocumentForm,
  'process-mobile-action-drawer': ProcessMobileActionDrawer
};

for (const key in components) {
  Vue.component(key, components[key]);
}

if (!Vue.prototype.$processesService) {
  window.Object.defineProperty(Vue.prototype, '$processesService', {
    value: processesService,
  });
}
if (!Vue.prototype.$processesAttachmentService) {
  window.Object.defineProperty(Vue.prototype, '$processesAttachmentService', {
    value: ProcessesAttachmentService,
  });
}
if (!Vue.prototype.$utils) {
  window.Object.defineProperty(Vue.prototype, '$utils', {
    value: utils,
  });
}
