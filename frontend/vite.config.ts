import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const rawTarget = env.VITE_BACKEND_PROXY_TARGET ?? env.VITE_BACKEND_URL ?? 'http://localhost:8080'
  const proxyTarget = rawTarget.replace(/\/api\/?$/, '')

  return {
    plugins: [react()],
    server: {
      host: '0.0.0.0',
      port: 3000,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
          secure: false,
        },
      },
      hmr: {
        clientPort: 3000,
      },
      watch: {
        usePolling: true,
        interval: 100,
      },
    },
  }
})
