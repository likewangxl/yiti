// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';

describe('大屏中心路由', () => {
  it('挂在 DefaultLayout 下并使用大屏查看资源守卫', async () => {
    const { default: router } = await import('@/router');
    const route = router.getRoutes().find((item) => item.name === 'ScreenCenter');

    expect(route?.path).toBe('/screens');
    expect(route?.meta).toMatchObject({
      title: '大屏中心',
      requiredResource: '/api/screen/view/*'
    });
    const resolved = router.resolve('/screens');
    expect(resolved.name).toBe('ScreenCenter');
    expect(resolved.matched[0]?.path).toBe('/');
  });
});
