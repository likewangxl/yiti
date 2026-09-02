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

  it('与我的客户页面保持标题说明、筛选卡片、表格和分页样式一致', () => {
    expect(source).toContain('aria-labelledby="marketing-tag-customer-approval-title"');
    expect(source).toContain('class="page-head"');
    expect(source).toContain('待审批按标签维度展示');
    expect(source).toContain('class="filter-card"');
    expect(source).toContain('class="approval-table"');
    expect(source).toContain('background');
    expect(source).toContain(':page-sizes="[10, 20, 50, 100]"');
  });
});
