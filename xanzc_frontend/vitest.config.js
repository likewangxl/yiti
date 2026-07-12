import { defineConfig } from 'vitest/config';
import { fileURLToPath, URL } from 'node:url';

// 仅测纯函数 utils(node 环境,无需 jsdom);与运行时 vite.config.js 解耦,不卷入其未提交改动。
export default defineConfig({
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  test: {
    environment: 'node',
    include: ['src/**/__tests__/**/*.spec.js'],
    globals: false
  }
});
