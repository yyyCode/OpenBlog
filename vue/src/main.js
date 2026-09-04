import { createApp } from 'vue'
import './style.css'
import './assets/blog.css'
import App from './App.vue'
import router from './router/index'
import { applyStoredTheme } from './theme'
import { getDeviceFingerprint } from './utils/deviceFingerprint'
import { getDeviceToken } from './utils/deviceToken'

applyStoredTheme()
getDeviceFingerprint()
// 预热设备令牌：首次 API 调用时通常已缓存，避免首个请求等待签发（失败静默，网关回退纯 IP）
getDeviceToken()

createApp(App).use(router).mount('#app')
