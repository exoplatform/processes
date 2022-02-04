import Processes from './components/Processes.vue';
import RequestsCard from './components/RequestsTypeList.vue';
import RequestCardItem from './components/RequestTypeCardItem.vue';
import MyRequestsList from './components/MyRequestsList.vue';
import Request from './components/Request.vue';
const components = {
  'processes': Processes,
  'requests-type-list': RequestsCard,
  'request-type-card-item': RequestCardItem,
  'my-requests-list': MyRequestsList,
  'request': Request
};

for (const key in components) {
  Vue.component(key, components[key]);
}