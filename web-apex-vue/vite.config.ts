import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq, req) => {
            if (req.url?.includes('/chat/send')) {
              // 禁用代理层面的压缩，否则 http-proxy 会缓冲整个流
              proxyReq.setHeader('Accept-Encoding', 'identity')
            }
          })
          proxy.on('proxyRes', (proxyRes, req, res) => {
            if (req.url?.includes('/chat/send')) {
              // 立即刷出响应头，释放 chunk 逐块传递
              res.flushHeaders()
            }
          })
        },
      },
    },
  },
})
