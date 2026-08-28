// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';

describe('资产立项路由', () => {
  it('主菜单使用客户营销正式路径和正式资源契约', async () => {
    const { default: router } = await import('@/router');
    const route = router.getRoutes().find(item => item.name === 'AssetProjects');

    expect(route?.path).toBe('/marketing/asset-projects');
    expect(route?.meta?.requiredMenu).toBe('/marketing/asset-projects');
    expect(route?.meta?.requiredResource).toBe('/api/marketing/asset-projects');
  });

  it('新建和详情入口隐藏于同一业务菜单下，不复制出其他菜单路径', async () => {
    const { default: router } = await import('@/router');
    const routes = router.getRoutes();

    expect(routes.find(item => item.name === 'AssetProjectCreate')?.path).toBe('/marketing/asset-projects/new');
    expect(routes.find(item => item.name === 'AssetProjectDetail')?.path).toBe('/marketing/asset-projects/:id');
    expect(routes.find(item => item.name === 'AssetProjectCreate')?.meta?.hideInMenu).toBe(true);
    expect(routes.find(item => item.name === 'AssetProjectDetail')?.meta?.hideInMenu).toBe(true);
  });
});
