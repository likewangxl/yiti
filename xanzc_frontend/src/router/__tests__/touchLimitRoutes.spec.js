// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';

describe('客户触达周期管理路由', () => {
  it('注册客户营销下的触达周期管理页面并声明资源权限', async () => {
    const { default: router } = await import('@/router');
    const route = router.getRoutes().find(item => item.name === 'CustomerTouchLimits');

    expect(route?.path).toBe('/customers/touch-limits');
    expect(route?.meta).toEqual(expect.objectContaining({
      title: '客户触达周期管理',
      group: '客户营销',
      requiredResource: '/api/touch-limit-rules',
    }));
  });
});
