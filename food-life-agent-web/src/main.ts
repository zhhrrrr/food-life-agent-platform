import { createApp } from 'vue'
import {
  ElAlert, ElAvatar, ElButton, ElCollapse, ElCollapseItem, ElEmpty, ElForm,
  ElFormItem, ElIcon, ElInput, ElInputNumber, ElLoading, ElOption, ElSelect, ElTag, ElTooltip,
} from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { pinia } from './stores'
import './styles/main.css'

const app = createApp(App).use(pinia).use(router)
// Register the components used by this app so unused widgets can be tree-shaken.
for (const component of [ElAlert, ElAvatar, ElButton, ElCollapse, ElCollapseItem, ElEmpty,
  ElForm, ElFormItem, ElIcon, ElInput, ElInputNumber, ElOption, ElSelect, ElTag, ElTooltip]) {
  app.use(component)
}
app.use(ElLoading).mount('#app')
