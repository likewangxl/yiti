// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';

describe('代码化大屏受保护路由', () => {
  it('uses a protected template route and keeps old runtime/designer routes out of the center link', async () => {
    const { default: router } = await import('@/router');
    const route = router.getRoutes().find(item => item.name === 'CodeScreenPage');
    expect(route?.path).toBe('/screen-pages/:template');
    expect(route?.meta).toMatchObject({
      title: '代码化大屏',
      requiredResource: '/api/screen/view/*'
    });
    expect(router.resolve('/screen-pages/retail-overview-v1').name).toBe('CodeScreenPage');
  });

  it('redirects the legacy branch test screen to the protected parent flow', async () => {
    const { default: router } = await import('@/router');
    const legacy = router.getRoutes().find(item => item.name === 'LegacyBranchOperatingRedirect');
    const withOrg = router.resolve(legacy.redirect(router.resolve('/screen/SCR_BRANCH_OPERATING_TEST?orgCode=105&cityCode=610100&sourceScreenCode=evil')));
    expect(withOrg.name).toBe('BranchOperatingPage');
    expect(withOrg.fullPath).toBe('/branch-operating?orgCode=105&cityCode=610100');
    const withoutOrg = router.resolve(legacy.redirect(router.resolve('/screen/SCR_BRANCH_OPERATING_TEST?source=test&orgCode=')));
    expect(withoutOrg.name).toBe('CodeScreenPage');
    expect(withoutOrg.fullPath).toBe('/screen-pages/branch-overview-v1');
  });
});
