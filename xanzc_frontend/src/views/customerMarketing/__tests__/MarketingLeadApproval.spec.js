import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
const source = readFileSync(new URL('../MarketingLeadApproval.vue', import.meta.url), 'utf8');
describe('营销线索审批页面契约', () => {
  it('沿用我的客户页面的统一页头、说明、筛选卡片和分页视觉契约', () => {
    expect(source).toMatch(/<header class="page-head">[\s\S]*<div>[\s\S]*<PageTitle[^>]*\/>[\s\S]*<span>待审批展示当前可办理的线索；审批记录仅展示当前登录人的审批记录。<\/span>[\s\S]*<\/div>[\s\S]*<\/header>/);
    expect(source).toMatch(/\.page-head h1[\s\S]*font-size:\s*18px/);
    expect(source).toMatch(/\.page-head span[\s\S]*font-size:\s*12px[\s\S]*margin-top:\s*4px/);
    expect(source).toContain('<el-card shadow="never" class="filter-card">');
    expect(source).toContain('class="lead-approval-table"');
    expect(source).toContain('background layout="total, sizes, prev, pager, next"');
    expect(source).toContain(':page-sizes="[10, 20, 50, 100]"');
  });

  it('区分待审批和本人审批记录，并逐线索办理', () => {
    expect(source).toContain('listLeadApprovalPending');
    expect(source).toContain('listLeadApprovalHistory');
    expect(source).toContain('approveLead');
    expect(source).toContain('rejectLead');
    expect(source).toContain('开户状态');
    expect(source).toContain('当前主办');
  });
});
