import { describe, expect, it } from 'vitest';
import { resolveDevServerOptions } from '../../../vite.config.js';

describe('Vite 开发服务器隔离配置', () => {
  it('未设置覆盖变量时保持现有默认值', () => {
    expect(resolveDevServerOptions({})).toEqual({
      host: '0.0.0.0',
      port: 8092,
      strictPort: true,
      proxyTarget: 'http://localhost:18089'
    });
  });

  it('显式变量可切换到隔离联调端点', () => {
    expect(resolveDevServerOptions({
      VITE_DEV_HOST: '127.0.0.1',
      VITE_DEV_PORT: '18090',
      VITE_DEV_STRICT_PORT: 'true',
      VITE_PROXY_TARGET: 'http://127.0.0.1:18091'
    })).toEqual({
      host: '127.0.0.1',
      port: 18090,
      strictPort: true,
      proxyTarget: 'http://127.0.0.1:18091'
    });
  });

  it.each(['', '0', '65536', '8092.5', '8092abc', ' 8092'])(
    '拒绝非法 VITE_DEV_PORT=%j',
    (port) => {
      expect(() => resolveDevServerOptions({ VITE_DEV_PORT: port })).toThrow(/VITE_DEV_PORT/);
    }
  );

  it('拒绝不是 true 或 false 的严格端口开关', () => {
    expect(() => resolveDevServerOptions({ VITE_DEV_STRICT_PORT: 'yes' })).toThrow(
      /VITE_DEV_STRICT_PORT/
    );
  });

  it.each(['127.0.0.1:18091', 'ftp://127.0.0.1:18091', 'http://'])(
    '拒绝非法 VITE_PROXY_TARGET=%j',
    (target) => {
      expect(() => resolveDevServerOptions({ VITE_PROXY_TARGET: target })).toThrow(
        /VITE_PROXY_TARGET/
      );
    }
  );
});
