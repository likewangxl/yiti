// @vitest-environment happy-dom
import { describe, it, expect } from 'vitest';

describe('红色引擎路由入口', () => {
  it('注册独立公开登录路由，业务路由继续使用红色引擎布局', async () => {
    const { default: router } = await import('@/router');

    const login = router.getRoutes().find((route) => route.name === 'RedEngineLogin');
    expect(login).toBeTruthy();
    expect(login.path).toBe('/redengine/login');
    expect(login.meta.public).toBe(true);
    expect((await login.components.default()).default).toBeTruthy();

    const dashboard = router.getRoutes().find((route) => route.name === 'RedEngineDashboard');
    expect(dashboard).toBeTruthy();
    expect(dashboard.path).toBe('/redengine/dashboard');
    expect(dashboard.meta.public).not.toBe(true);
  });
});
