import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { setAuthLostHandler } from './api/request'
import { useAuth } from './stores/auth'

import './style.css'
import './assets/components.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

// 任何请求发现登录态已失效（token 过期 / 已被登出 / Redis 拉黑）时统一处理：
// 清本地登录态 → 带上回跳地址去登录页。这样不用在每个页面里重复写 401 处理。
setAuthLostHandler(() => {
  const auth = useAuth(pinia)
  auth.clear()
  const current = router.currentRoute.value
  if (current.name === 'login') return
  router.replace({ name: 'login', query: { redirect: current.fullPath } })
})

app.mount('#app')