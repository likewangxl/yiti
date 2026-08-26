import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../MyCustomers.vue', import.meta.url), 'utf8');

describe('我的客户页面契约', () => {
  it('固定使用本人主办查询接口，并仅允许转交给其他客户经理', () => {
    expect(source).toContain('listMyCustomers');
    expect(source).toContain('MarketingCustomerOwnerDialog');
    expect(source).toContain('allowUnassign');
    expect(source).toContain('false');
    expect(source).toContain('transferAction: \'TRANSFER\'');
    expect(source).not.toContain('restoreCustomerOwnershipAuto');
  });

  it('展示查询、详情和主办客户字段', () => {
    expect(source).toContain('客户名称');
    expect(source).toContain('统一社会信用代码');
    expect(source).toContain('主办机构');
    expect(source).toContain('getMarketingCustomer');
  });
});
