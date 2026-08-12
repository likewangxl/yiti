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
  });
});
