import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
const source = readFileSync(new URL('../MarketingLeadApproval.vue', import.meta.url), 'utf8');
describe('营销线索审批页面契约', () => {
  it('区分待审批和本人审批记录，并逐线索办理', () => {
    expect(source).toContain('listLeadApprovalPending');
    expect(source).toContain('listLeadApprovalHistory');
    expect(source).toContain('approveLead');
    expect(source).toContain('rejectLead');
    expect(source).toContain('开户状态');
    expect(source).toContain('当前主办');
  });
});
