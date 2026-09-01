import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/user-api': {
        target: 'http://localhost:8101',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/user-api/, '/api/user'),
      },
      '/business-api': {
        target: 'http://localhost:8201',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/business-api/, '/api'),
      },
      '/trade-api': {
        target: 'http://localhost:8301',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/trade-api/, '/api/trade'),
      },
    },
  },
})
