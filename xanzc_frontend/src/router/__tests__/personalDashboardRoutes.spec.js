// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';

describe('个人经营驾驶舱路由', () => {
  it('是独立的登录态全屏路由，隐藏菜单但复用工作台菜单门禁', async () => {
    const { default: router } = await import('@/router');
    const route = router.getRoutes().find(item => item.name === 'PersonalDashboard');

    expect(route?.path).toBe('/personal-dashboard');
    expect(route?.meta?.requiredMenu).toBe('/workspace');
    expect(route?.meta?.hideInMenu).toBe(true);
    expect(route?.meta?.public).not.toBe(true);
    expect(router.resolve('/personal-dashboard').matched).toHaveLength(1);
  });
});
