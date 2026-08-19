// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';

describe('浦爱云盾路由', () => {
  it('人员违规和信贷风险页面归属浦爱云盾菜单', async () => {
    const { default: router } = await import('@/router');
    const routes = router.getRoutes();
    const accountability = routes.find(route => route.name === 'YundunAccountabilityViolations');
    const credit = routes.find(route => route.name === 'YundunCreditViolations');

    expect(accountability?.path).toBe('/yundun/accountability-violations');
    expect(accountability?.meta?.group).toBe('浦爱云盾');
    expect(credit?.path).toBe('/yundun/credit-violations');
    expect(credit?.meta?.group).toBe('浦爱云盾');
  });
});
