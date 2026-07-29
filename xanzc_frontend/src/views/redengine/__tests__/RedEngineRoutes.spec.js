// @vitest-environment happy-dom
import { describe, it, expect } from 'vitest';

describe('红色引擎平台统一路由入口', () => {
  it('不再注册独立登录路由，业务路由继续使用红色引擎布局', async () => {
    const { default: router } = await import('@/router');

    expect(router.hasRoute('RedEngineLogin')).toBe(false);
    const dashboard = router.getRoutes().find((route) => route.name === 'RedEngineDashboard');
    expect(dashboard).toBeTruthy();
    expect(dashboard.path).toBe('/redengine/dashboard');
    expect(dashboard.meta.public).not.toBe(true);
  });
});
