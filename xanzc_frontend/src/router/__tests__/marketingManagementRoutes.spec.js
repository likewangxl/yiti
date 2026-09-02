// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';

describe('六页面营销管理路由', () => {
  it('注册六个独立页面并声明后端资源', async () => {
    const { default: router } = await import('@/router');
    const routes = router.getRoutes();
    const expected = [
      ['MarketingCustomerList', '/customers/manage', '营销客户列表', '/api/marketing/customers'],
      ['CustomerList', '/customers/list', '我的客户', '/api/marketing/customers/mine'],
      ['LeadEntry', '/customers/leads/new', '线索录入', '/api/marketing/leads'],
      ['LeadApproval', '/customers/leads/approval', '线索审批', '/api/marketing/lead-approvals/**'],
      ['CustomerTags', '/customers/tags', '营销客户标签', '/api/marketing/customer-tags'],
      ['CustomerTagApproval', '/customers/tags/approval', '标签客户审核', '/api/marketing/customer-tag-approvals/**'],
    ];

    expected.forEach(([name, path, title, resource]) => {
      const route = routes.find(item => item.name === name);
      expect(route?.path).toBe(path);
      expect(route?.meta?.title).toBe(title);
      expect(route?.meta?.requiredResource).toBe(resource);
    });
  });

  it('六页面使用新的目标视图，不再挂载旧客户营销页面', async () => {
    const { default: router } = await import('@/router');
    const routes = router.getRoutes();
    const sources = await Promise.all(['MarketingCustomerList', 'CustomerList', 'LeadEntry'].map(name =>
      routes.find(item => item.name === name)?.components?.default?.()));
    expect(sources.every(Boolean)).toBe(true);
    expect(routes.find(item => item.name === 'MarketingCustomerList')?.components?.default?.toString()).not.toContain('/CustomerList.vue');
    expect(routes.find(item => item.name === 'CustomerList')?.components?.default?.toString()).toContain('/MyCustomers.vue');
    expect(routes.find(item => item.name === 'LeadEntry')?.components?.default?.toString()).toContain('/MarketingLeadEntry.vue');
  });
});
