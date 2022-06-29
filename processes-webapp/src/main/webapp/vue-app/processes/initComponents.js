import Processes from './components/Processes.vue';
import WorkFlowList from './components/WorkFlowList.vue';
import WorkFlowCardItem from './components/WorkFlowCardItem.vue';
import MyWorkList from './components/MyWorkList.vue';
import Work from './components/Work.vue';
import AddWorkFlowDrawer from './components/AddWorkFlowDrawer.vue';
import WorkFlowSuggesterSpace from './components/WorkFlowSuggesterSpace.vue';
import WorkFlowSuggesterRequest from './components/WorkFlowSuggesterRequest.vue';
import WorkflowFormAttendeeItem from './components/WorkflowFormAttendeeItem.vue';
import AddWorkDrawer from './components/AddWorkDrawer.vue';
import RequestEditor from './components/RequestEditor.vue';

import EmptyOrLoading from './components/commons/EmptyOrLoading.vue';
import CustomCounter from './components/commons/CustomCounter.vue';
import CustomDateFormat from './components/commons/CustomDateFormat.vue';
import RequestStatus from './components/commons/RequestStatus.vue';

import CreateDocumentForm from './components/attachments-integration/CreateDocumentForm.vue';
import ProcessesAttachments from './components/attachments-integration/ProcessesAttachments.vue';


import * as processesService from './js/ProcessesService.js';
import * as ProcessesAttachmentService from './js/ProcessesAttachmentService.js';
import * as utils from './js/utils.js';

const components = {
  'processes': Processes,
  'workflow-list': WorkFlowList,
  'workflow-card-item': WorkFlowCardItem,
  'my-work-list': MyWorkList,
  'work': Work,
  'add-workflow-drawer': AddWorkFlowDrawer,
  'workflow-suggester-space': WorkFlowSuggesterSpace,
  'workflow-suggester-request': WorkFlowSuggesterRequest,
  'workflow-form-attendee-item': WorkflowFormAttendeeItem,
  'add-work-drawer': AddWorkDrawer,
  'request-editor': RequestEditor,
  'processes-attachments': ProcessesAttachments,
  'empty-or-loading': EmptyOrLoading,
  'custom-counter': CustomCounter,
  'custom-date-format': CustomDateFormat,
  'request-status': RequestStatus,
  'create-document-from': CreateDocumentForm
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
