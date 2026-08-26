import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../MarketingCustomerList.vue', import.meta.url), 'utf8');

describe('营销客户列表页面契约', () => {
  it('使用营销客户主档分页查询并提供详情、资料编辑和主办权管理', () => {
    expect(source).toContain('listMarketingCustomers');
    expect(source).toContain('getMarketingCustomer');
    expect(source).toContain('updateMarketingCustomerProfile');
    expect(source).toContain('transferCustomerOwner');
    expect(source).toContain('restoreCustomerOwnershipAuto');
    expect(source).toContain('MarketingCustomerDetailDrawer');
    expect(source).toContain('MarketingCustomerOwnerDialog');
    expect(source).toContain('transferAction');
    expect(source).toContain('UNASSIGN');
  });

  it('展示客户主档查询字段和企业详细信息，不使用旧客户列表接口', () => {
    expect(source).toContain('统一社会信用代码');
    expect(source).toContain('主办客户经理');
    expect(source).toContain('授信敞口');
    expect(source).toContain('ownershipMaintainMode');
    expect(source).not.toContain("@/api/customerMarketing");
  });
});
