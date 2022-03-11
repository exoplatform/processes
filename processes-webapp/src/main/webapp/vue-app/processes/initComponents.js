import Processes from './components/Processes.vue';
import WorkFlowList from './components/WorkFlowList.vue';
import WorkFlowCardItem from './components/WorkFlowCardItem.vue';
import MyWorkList from './components/MyWorkList.vue';
import Work from './components/Work.vue';
import AddWorkFlowDrawer from './components/AddWorkFlowDrawer.vue';
import AddWorkDrawer from './components/AddWorkDrawer.vue';
import RequestEditor from './components/RequestEditor.vue';

import EmptyOrLoading from './components/commons/EmptyOrLoading.vue';
import CustomCounter from './components/commons/CustomCounter.vue';
import CustomDateFormat from './components/commons/CustomDateFormat.vue';
import RequestStatus from './components/commons/RequestStatus.vue';


import * as processesService from './js/ProcessesService.js';
import * as utils from './js/utils.js';

const components = {
  'processes': Processes,
  'workflow-list': WorkFlowList,
  'workflow-card-item': WorkFlowCardItem,
  'my-work-list': MyWorkList,
  'work': Work,
  'add-workflow-drawer': AddWorkFlowDrawer,
  'add-work-drawer': AddWorkDrawer,
  'request-editor': RequestEditor,
  'empty-or-loading': EmptyOrLoading,
  'custom-counter': CustomCounter,
  'custom-date-format': CustomDateFormat,
  'request-status': RequestStatus
};

for (const key in components) {
  Vue.component(key, components[key]);
}

if (!Vue.prototype.$processesService) {
  window.Object.defineProperty(Vue.prototype, '$processesService', {
    value: processesService,
  });
}
if (!Vue.prototype.$utils) {
  window.Object.defineProperty(Vue.prototype, '$utils', {
    value: utils,
  });
}
