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

  it('注册 no-access，并为红色引擎敏感路由声明资源要求', async () => {
    const { default: router } = await import('@/router');
    const noAccess = router.getRoutes().find((route) => route.name === 'NoAccess');
    expect(noAccess).toBeTruthy();
    expect(noAccess.path).toBe('/no-access');

    const expected = {
      RedEngineReport: '/api/re/submits',
      RedEngineRecords: '/api/re/submits/my',
      RedEngineBranchReview: '/api/re/reviews/**',
      RedEngineCockpit: '/api/re/cockpit/**',
      RedEngineWarning: '/api/re/cockpit/**',
      RedEngineReview: '/api/re/reviews/**',
      RedEngineArchive: '/api/re/cockpit/**',
      RedEngineExport: '/api/re/export/*',
      RedEngineOrgManage: '/api/re/orgs',
      RedEngineUserMap: '/api/re/user-party-maps'
    };
    for (const [name, requiredResource] of Object.entries(expected)) {
      const route = router.getRoutes().find((item) => item.name === name);
      expect(route?.meta.requiredResource, name).toBe(requiredResource);
    }

    const workspace = router.getRoutes().find((route) => route.name === 'Workspace');
    expect(workspace?.meta.requiredMenu).toBe('/workspace');
    expect(workspace?.meta.fallbackToAuthorizedMenu).toBe(true);
  });
});
