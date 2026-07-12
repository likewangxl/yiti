import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

// 与运行时 vite.config.js 解耦,不卷入其未提交改动,故独立声明所需 plugins/resolve。
// Task 8(widgets 注册表)起测试会静态 import .vue 组件(未挂载,仅取模块导出),
// 需要 vue() 插件做 SFC 转译;环境仍用 node(无需挂载 DOM,无需 jsdom)。
export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  test: {
    environment: 'node',
    include: ['src/**/__tests__/**/*.spec.js'],
    globals: false
  }
});
