// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';

describe('客户营销路由', () => {
  it('注册客户列表、线索录入和线索审批三个独立页面', async () => {
    const { default: router } = await import('@/router');
    const routes = router.getRoutes();

    expect(routes.find(route => route.name === 'CustomerList')?.path).toBe('/customers/list');
    expect(routes.find(route => route.name === 'LeadEntry')?.path).toBe('/customers/leads/new');
    expect(routes.find(route => route.name === 'LeadApproval')?.path).toBe('/customers/leads/approval');
  });

  it('触达管理只保留触达任务一览入口', async () => {
    const { default: router } = await import('@/router');
    const routes = router.getRoutes();
    const overview = routes.find(route => route.name === 'TouchOverview');

    expect(overview?.path).toBe('/touches/overview');
    expect(overview?.meta?.title).toBe('触达任务一览');
    expect(routes.find(route => route.name === 'TouchRecords')).toBeUndefined();
  });
});
