import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    // 允许通过局域网 IP / 公网 IP 访问（否则 Host 校验会 403 Forbidden）
    host: true,
    port: 5173,
    strictPort: false,
    allowedHosts: true,
  },
})
