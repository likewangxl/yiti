import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
const source = readFileSync(new URL('../MarketingLeadApproval.vue', import.meta.url), 'utf8');
describe('营销线索审批页面契约', () => {
  it('参考 V2_DEMO 使用统一页头、审批分类卡片和卡片式工作区', () => {
    expect(source).toMatch(/<header class="page-head">[\s\S]*<div>[\s\S]*<PageTitle[^>]*\/>[\s\S]*<span>集中处理待审批线索，查看完整业务字段、分配范围、附件和当前客户主档。<\/span>[\s\S]*<\/div>[\s\S]*<\/header>/);
    expect(source).toMatch(/\.page-head h1[\s\S]*font-size:\s*18px/);
    expect(source).toMatch(/\.page-head span[\s\S]*font-size:\s*12px[\s\S]*margin-top:\s*4px/);
    expect(source).toContain('class="approval-stat-grid"');
    expect(source).toContain('class="approval-stat-card"');
    expect(source).toContain('class="approval-card-section"');
    expect(source).toContain('class="lead-approval-table"');
    expect(source).toContain('background layout="total, sizes, prev, pager, next"');
    expect(source).toContain(':page-sizes="[10, 20, 50, 100]"');
  });

  it('区分待审批、已通过和已退回，并逐线索办理', () => {
    expect(source).toContain('listLeadApprovalPending');
    expect(source).toContain('listLeadApprovalHistory');
    expect(source).toContain('approveLead');
    expect(source).toContain('rejectLead');
    expect(source).toContain('MarketingLeadReadonlyDetail');
    expect(source).toContain('通过线索');
    expect(source).toContain('退回线索');
    expect(source).toContain('退回原因不能为空');
  });

  it('列表与详情按 DEMO 展示类型、经营属性、分配资料和审批信息', () => {
    ['线索类型', '所属行业', '分配方式', '提交人', '状态', '提交时间'].forEach(label => expect(source).toContain(label));
    expect(source).toContain("import MarketingLeadReadonlyDetail from '@/components/MarketingLeadReadonlyDetail.vue'");
    expect(source).toContain('<MarketingLeadReadonlyDetail :detail="detail" />');
    expect(source).toContain('审批信息');
    expect(source).toContain('detail-banner');
    expect(source).toContain('detail.profileChanged');
    expect(source).toContain('detail.currentCustomer?.isAccountOpened === 1');
  });

  it('三张分类卡片互斥筛选，并分别获取精确总数', () => {
    expect(source).toMatch(/listLeadApprovalPending\(\{ pageNo: 1, pageSize: 1 \}\)/);
    expect(source).toMatch(/listLeadApprovalHistory\(\{ result: 'APPROVED', pageNo: 1, pageSize: 1 \}\)/);
    expect(source).toMatch(/listLeadApprovalHistory\(\{ result: 'REJECTED', pageNo: 1, pageSize: 1 \}\)/);
    expect(source).toContain(':aria-pressed="activeStatus === item.status"');
    expect(source).toMatch(/function switchStatus\(nextStatus\)[\s\S]*activeStatus\.value = nextStatus/);
    expect(source).toContain("label: '待审批'");
    expect(source).toContain("label: '已通过'");
    expect(source).toContain("label: '已退回'");
  });
});
