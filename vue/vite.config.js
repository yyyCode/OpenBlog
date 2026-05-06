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
    // 当 VITE_API_BASE 为空时，开发环境把 /api 转到本机后端，与生产 Nginx 反代行为一致
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true,
      },
    },
  },
})
