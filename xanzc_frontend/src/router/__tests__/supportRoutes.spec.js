// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';

describe('中台支持路由', () => {
  it('正式菜单路径、创建和详情路径使用中台支持资源契约', async () => {
    const { default: router } = await import('@/router');
    const routes = router.getRoutes();
    const list = routes.find(item => item.name === 'SupportRequests');
    const create = routes.find(item => item.name === 'SupportRequestCreate');
    const detail = routes.find(item => item.name === 'SupportRequestDetail');

    expect(list?.path).toBe('/bizexec/supports');
    expect(list?.meta?.requiredMenu).toBe('/bizexec/supports');
    expect(list?.meta?.requiredResource).toBe('/api/support-requests');
    expect(create?.path).toBe('/bizexec/supports/new');
    expect(create?.meta?.hideInMenu).toBe(true);
    expect(detail?.path).toBe('/bizexec/supports/:id');
    expect(detail?.meta?.hideInMenu).toBe(true);
  });
});
