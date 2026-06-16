import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

// 注：element-plus 走全量注册（main.js: app.use(ElementPlus) + 全量 css），
// 不再用 unplugin-vue-components 的 ElementPlusResolver 做按需。
// 因为按需会让 vite 在导航时"懒发现"新依赖触发 full reload，
// 表现是「点新菜单 URL 闪一下却回到原页面，再点一次才进去」。
export default defineConfig({
  base: '/',
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    host: '0.0.0.0',   // 监听所有网卡，允许用本机 IP 从其他机器访问（默认只绑 localhost）
    port: 8091,
    strictPort: true,  // 端口固定 8090：被占用时直接报错，而非静默漂移到 8091/8092
    open: false,
    proxy: {
      '/api': {
        target: 'http://localhost:18081',
        changeOrigin: true
      }
    }
  },
  // 显式预打包，进一步避免运行时再次触发 reload
  optimizeDeps: {
    include: [
      'vue', 'vue-router', 'pinia', 'axios', 'dayjs',
      'element-plus', 'element-plus/dist/locale/zh-cn.mjs',
      '@element-plus/icons-vue',
      'echarts', 'vue-echarts'
    ]
  },
  css: {
    preprocessorOptions: {
      scss: { additionalData: `@use "@/styles/tokens.scss" as *;` }
    }
  }
});
