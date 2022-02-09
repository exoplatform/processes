import Processes from './components/Processes.vue';
import WorkFlowList from './components/WorkFlowList.vue';
import WorkFlowCardItem from './components/WorkFlowCardItem.vue';
import MyWorkList from './components/MyWorkList.vue';
import Work from './components/Work.vue';
import AddWorkFlowDrawer from './components/AddWorkFlowDrawer.vue';
import AddWorkDrawer from './components/AddWorkDrawer.vue';
import ProjectSuggester from './components/ProjectSuggester.vue';

import * as processesService from './js/ProcessesService.js';

const components = {
  'processes': Processes,
  'workflow-list': WorkFlowList,
  'workflow-card-item': WorkFlowCardItem,
  'my-work-list': MyWorkList,
  'work': Work,
  'add-workflow-drawer': AddWorkFlowDrawer,
  'add-work-drawer': AddWorkDrawer,
  'project-suggester': ProjectSuggester
};

for (const key in components) {
  Vue.component(key, components[key]);
}

if (!Vue.prototype.$processesService) {
  window.Object.defineProperty(Vue.prototype, '$processesService', {
    value: processesService,
  });
}
