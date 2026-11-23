import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:9080',
        changeOrigin: true,
      },
    },
  },
  resolve: {
    alias: {
      // workspace 包按源码引用（不做各包独立构建），别名直指包内 src，
      // 因此包间导入一律写 @cornerstone/xxx/...，不要再依赖 @ 别名。
      '@cornerstone/core': fileURLToPath(new URL('./packages/core/src', import.meta.url)),
      '@cornerstone/module-organization': fileURLToPath(
        new URL('./packages/module-organization/src', import.meta.url),
      ),
      '@cornerstone/module-education': fileURLToPath(
        new URL('./packages/module-education/src', import.meta.url),
      ),
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
