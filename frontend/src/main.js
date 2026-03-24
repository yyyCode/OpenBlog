import { createApp } from 'vue'
import './style.css'
import './assets/blog.css'
import App from './App.vue'
import router from './router/index'
import { applyStoredTheme } from './theme'

applyStoredTheme()

createApp(App).use(router).mount('#app')
