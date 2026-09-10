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
});
