import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
const source = readFileSync(new URL('../MarketingTagCustomerApproval.vue', import.meta.url), 'utf8');
describe('标签客户审核页面契约', () => {
  it('按标签聚合并支持标签前置确认、单条多条全部审批', () => {
    expect(source).toContain('listPendingTagCustomerApprovals');
    expect(source).toContain('getPendingTagCustomers');
    expect(source).toContain('approveCustomerTag');
    expect(source).toContain('approveTagCustomers');
    expect(source).toContain('allPending');
    expect(source).toContain('approveTag: true');
    expect(source).toContain('CUST-40905');
  });
});
