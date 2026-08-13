import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

const DEFAULT_DEV_SERVER_OPTIONS = Object.freeze({
  host: '0.0.0.0',
  port: 8091,
  strictPort: false,
  proxyTarget: 'http://localhost:18081'
});

function hasEnvironmentVariable(environment, name) {
  return Object.prototype.hasOwnProperty.call(environment, name);
}

function parseDevPort(value) {
  if (typeof value !== 'string' || !/^[1-9]\d{0,4}$/.test(value)) {
    throw new Error('VITE_DEV_PORT 必须是 1 到 65535 的十进制整数。');
  }

  const port = Number(value);
  if (port > 65535) {
    throw new Error('VITE_DEV_PORT 必须是 1 到 65535 的十进制整数。');
  }

  return port;
}

function parseStrictPort(value) {
  if (value === 'true') {
    return true;
  }
  if (value === 'false') {
    return false;
  }
  throw new Error('VITE_DEV_STRICT_PORT 只能是 true 或 false。');
}

function parseProxyTarget(value) {
  if (typeof value !== 'string' || value.length === 0 || value.trim() !== value) {
    throw new Error('VITE_PROXY_TARGET 必须是明确的 http(s) URL。');
  }

  try {
    const target = new URL(value);
    if (!['http:', 'https:'].includes(target.protocol) || !target.hostname) {
      throw new Error('不支持的代理协议');
    }
  } catch {
    throw new Error('VITE_PROXY_TARGET 必须是明确的 http(s) URL。');
  }

  return value;
}

/**
 * 将 Vite 环境变量解析为开发服务器选项，避免隔离联调端口被 Vite 静默改写。
 *
 * @param {Record<string, string | undefined>} environment Vite 加载的环境变量。
 * @returns {{host: string, port: number, strictPort: boolean, proxyTarget: string}} 开发服务器选项。
 */
export function resolveDevServerOptions(environment = {}) {
  const host = hasEnvironmentVariable(environment, 'VITE_DEV_HOST')
    ? environment.VITE_DEV_HOST
    : DEFAULT_DEV_SERVER_OPTIONS.host;
  if (typeof host !== 'string' || host.length === 0 || host.trim() !== host) {
    throw new Error('VITE_DEV_HOST 必须是非空主机名或 IP 地址。');
  }

  const port = hasEnvironmentVariable(environment, 'VITE_DEV_PORT')
    ? parseDevPort(environment.VITE_DEV_PORT)
    : DEFAULT_DEV_SERVER_OPTIONS.port;
  const strictPort = hasEnvironmentVariable(environment, 'VITE_DEV_STRICT_PORT')
    ? parseStrictPort(environment.VITE_DEV_STRICT_PORT)
    : DEFAULT_DEV_SERVER_OPTIONS.strictPort;
  const proxyTarget = hasEnvironmentVariable(environment, 'VITE_PROXY_TARGET')
    ? parseProxyTarget(environment.VITE_PROXY_TARGET)
    : DEFAULT_DEV_SERVER_OPTIONS.proxyTarget;

  return { host, port, strictPort, proxyTarget };
}

// 注：element-plus 走全量注册（main.js: app.use(ElementPlus) + 全量 css），
// 不再用 unplugin-vue-components 的 ElementPlusResolver 做按需。
// 因为按需会让 vite 在导航时"懒发现"新依赖触发 full reload，
// 表现是「点新菜单 URL 闪一下却回到原页面，再点一次才进去」。
const createViteConfig = (devServerOptions) => ({
  base: '/',
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    host: devServerOptions.host,
    port: devServerOptions.port,
    // 允许通过花生壳/内网穿透域名访问（否则 Vite 校验 Host 头会返回 "Blocked request. This host is not allowed."）
    allowedHosts: ['1916dn17xs12.vicp.fun'],
    // 默认保留原有的自动选端口行为；隔离联调通过 VITE_DEV_STRICT_PORT=true 显式 fail-fast。
    strictPort: devServerOptions.strictPort,
    open: false,
    proxy: {
      '/api': {
        target: devServerOptions.proxyTarget,
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

export default defineConfig(({ mode }) => {
  const devServerOptions = resolveDevServerOptions(loadEnv(mode, process.cwd(), 'VITE_'));
  return createViteConfig(devServerOptions);
});
