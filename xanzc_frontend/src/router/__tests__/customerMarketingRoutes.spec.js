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

  it('注册标签客户名单管理页面并声明资源权限', async () => {
    const { default: router } = await import('@/router');
    const route = router.getRoutes().find(item => item.name === 'NameListManagement');

    expect(route?.path).toBe('/customers/name-list');
    expect(route?.meta?.title).toBe('标签客户名单');
    expect(route?.meta?.group).toBe('客户营销');
    expect(route?.meta?.requiredResource).toBe('/api/name-list');
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
