// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';

describe('大屏路由资源契约', () => {
  it('运行、数据源、机构画像、机构组和设计器均声明对应后端资源', async () => {
    const { default: router } = await import('@/router');
    const expected = {
      ScreenView: '/api/screen/view/*',
      ScreenAdminDs: '/api/screen/admin/datasources',
      ScreenAdminOrgProfiles: '/api/admin/org-profiles',
      ScreenAdminOrgGroups: '/api/admin/org-groups',
      ScreenAdminDesigner: '/api/screen/admin/screens'
    };
    for (const [name, requiredResource] of Object.entries(expected)) {
      expect(router.getRoutes().find(route => route.name === name)?.meta.requiredResource, name)
        .toBe(requiredResource);
    }
  }, 20000);

  it('设计器是顶层独立窗口路由，不再经过 DefaultLayout', async () => {
    const { default: router } = await import('@/router');
    const resolved = router.resolve('/screen-admin/designer');

    expect(resolved.name).toBe('ScreenAdminDesigner');
    expect(resolved.matched).toHaveLength(1);
    expect(resolved.matched[0].path).toBe('/screen-admin/designer');
    expect(resolved.meta.public).not.toBe(true);
  });

  it('设计器入口改为大屏管理并隐藏历史菜单，保留原 requiredResource guard', async () => {
    const { default: router } = await import('@/router');
    const route = router.getRoutes().find(item => item.name === 'ScreenAdminDesigner');
    expect(route.meta.title).toBe('大屏管理');
    expect(route.meta.hideInMenu).toBe(true);
    expect(route.meta.requiredResource).toBe('/api/screen/admin/screens');
  });

  it('本地演示路由只在开发环境注册且公开可直达', async () => {
    const { default: router } = await import('@/router');
    const route = router.getRoutes().find(item => item.name === 'ScreenPreview');
    if (import.meta.env.DEV) {
      expect(route?.path).toBe('/screen-preview');
      expect(route?.meta.public).toBe(true);
      expect(route?.meta.hideInMenu).toBe(true);
    } else {
      expect(route).toBeUndefined();
    }
  });
  it('零售预览同样仅开发环境公开注册，正式运行仍需原资源', async () => {
    const { default: router } = await import('@/router');
    const route = router.getRoutes().find(item => item.name === 'RetailScreenPreview');
    if (import.meta.env.DEV) {
      expect(route?.path).toBe('/screen-preview/retail');
      expect(route?.meta.public).toBe(true);
    } else expect(route).toBeUndefined();
  });

});
