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

  it('所属行业通过 INDUSTRY 字典展示中文，并在每次列表查询时刷新字典', () => {
    expect(source).toContain("import { useDict } from '@/composables/useDict'");
    expect(source).toContain("useDict('INDUSTRY')");
    expect(source).toContain('industryLabelOf(row.industry)');
    expect(source).toMatch(/async function load\(\)[\s\S]*reloadIndustry\(\)/);
  });

  it('页面不展示对用户无意义的线索编号', () => {
    expect(source).not.toContain('prop="leadNo"');
    expect(source).not.toContain('detail.lead.leadNo');
    expect(source).not.toContain('线索编号');
  });

  it('四张分类卡片互斥筛选，并分别获取精确总数', () => {
    expect(source).toContain('listLeadApprovalOverview');
    expect(source).toMatch(/activeStatus\.value === ''[\s\S]*listLeadApprovalOverview\(params\)/);
    expect(source).toMatch(/listLeadApprovalPending\(\{ pageNo: 1, pageSize: 1 \}\)/);
    expect(source).toMatch(/listLeadApprovalHistory\(\{ result: 'APPROVED', pageNo: 1, pageSize: 1 \}\)/);
    expect(source).toMatch(/listLeadApprovalHistory\(\{ result: 'REJECTED', pageNo: 1, pageSize: 1 \}\)/);
    expect(source).toContain(':aria-pressed="activeStatus === item.status"');
    expect(source).toMatch(/function switchStatus\(nextStatus\)[\s\S]*activeStatus\.value = nextStatus/);
    expect(source).toContain("label: '总览'");
    expect(source).toContain("label: '待审批'");
    expect(source).toContain("label: '已通过'");
    expect(source).toContain("label: '已退回'");
  });

  it('状态筛选栏与卡片同步互斥，重置会恢复全部和总览', () => {
    expect(source).toContain('v-model="statusFilter"');
    expect(source).toContain('label="全部"');
    expect(source).toMatch(/@change="filterByStatus"/);
    expect(source).toMatch(/function filterByStatus\(nextStatus\)[\s\S]*activeStatus\.value = nextStatus/);
    expect(source).toMatch(/function switchStatus\(nextStatus\)[\s\S]*statusFilter\.value = nextStatus/);
    expect(source).toMatch(/function resetFilter\(\)[\s\S]*query\.keyword = ''[\s\S]*statusFilter\.value = ''[\s\S]*activeStatus\.value = ''/);
    expect(source).toMatch(/\.approval-toolbar[\s\S]*display:\s*flex[\s\S]*flex-wrap:\s*nowrap/);
    expect(source).toContain('<el-form-item class="approval-filter-actions">');
  });

  it('总览中的待审批行按行状态展示通过和退回，并要求存在可办理任务', () => {
    expect(source).toContain('function isPendingRow(row)');
    expect(source).toMatch(/function isPendingRow\(row\)[\s\S]*row\?\.leadStatus[\s\S]*row\?\.task\?\.processStatus[\s\S]*taskId/);
    expect(source).toMatch(/<el-button v-if="isPendingRow\(row\)" link type="success" @click="decide\(row, true\)">通过<\/el-button>/);
    expect(source).toMatch(/<el-button v-if="isPendingRow\(row\)" link type="danger" @click="decide\(row, false\)">退回<\/el-button>/);
    expect(source).not.toMatch(/<el-button v-if="activeStatus === 'IN_APPROVAL'" link type="success"/);
    expect(source).not.toMatch(/<el-button v-if="activeStatus === 'IN_APPROVAL'" link type="danger"/);
  });

  it('详情抽屉底部办理按钮与当前选中行状态保持一致', () => {
    expect(source).toContain("<el-button v-if=\"isPendingRow(selectedRow)\" type=\"danger\" plain @click=\"decide(selectedRow, false)\">退回</el-button>");
    expect(source).toContain("<el-button v-if=\"isPendingRow(selectedRow)\" type=\"success\" @click=\"decide(selectedRow, true)\">通过</el-button>");
    expect(source).toMatch(/drawer-footer[\s\S]*isPendingRow\(selectedRow\)/);
  });
});
