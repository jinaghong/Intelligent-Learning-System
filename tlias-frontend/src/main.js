import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElLoading } from 'element-plus/es/components/loading/index'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import 'element-plus/es/components/loading/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import '@/styles/index.css'

import App from './App.vue'
import router from './router'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.directive('loading', ElLoading.directive)
app.use(createPinia())
app.use(router)
app.mount('#app')
