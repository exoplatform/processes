import Processes from './components/Processes.vue';
import RequestsCard from './components/RequestsTypeList.vue';
import RequestCardItem from './components/RequestTypeCardItem.vue';
const components = {
  'processes': Processes,
  'requests-type-list': RequestsCard,
  'request-type-card-item': RequestCardItem,
};

for (const key in components) {
  Vue.component(key, components[key]);
}