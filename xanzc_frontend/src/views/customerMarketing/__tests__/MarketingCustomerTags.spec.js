import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
const source = readFileSync(new URL('../MarketingCustomerTags.vue', import.meta.url), 'utf8');
describe('营销客户标签页面契约', () => {
  it('沿用我的客户页面的统一页头、说明、筛选卡片和分页视觉契约', () => {
    expect(source).toMatch(/<header class="page-head">[\s\S]*<div>[\s\S]*<PageTitle[^>]*\/>[\s\S]*<span>按标签管理营销客户群；新增标签及客户导入均须审批通过后生效。<\/span>[\s\S]*<\/div>[\s\S]*<el-button[^>]*>新增标签<\/el-button>[\s\S]*<\/header>/);
    expect(source).toMatch(/\.page-head h1[\s\S]*font-size:\s*18px/);
    expect(source).toMatch(/\.page-head span[\s\S]*font-size:\s*12px[\s\S]*margin-top:\s*4px/);
    expect(source).toContain('<el-card shadow="never" class="filter-card">');
    expect(source).toContain('class="customer-tags-table"');
    expect(source).toContain('background layout="total, sizes, prev, pager, next"');
    expect(source).toContain(':page-sizes="[10, 20, 50, 100]"');
  });

  it('支持标签客户群、新增、追加与全量替换导入', () => {
    expect(source).toContain('listMarketingCustomerTags');
    expect(source).toContain('listMarketingCustomerTagCustomers');
    expect(source).toContain('createMarketingCustomerTag');
    expect(source).toContain('APPEND');
    expect(source).toContain('REPLACE');
    expect(source).toContain('createCustomerTagImportBatch');
  });

  it('导入弹窗提供独立的通用模板下载并反馈状态', () => {
    expect(source).toContain('downloadCustomerTagImportTemplate');
    expect(source).toContain('下载导入模板');
    expect(source).toContain('templateDownloading');
    expect(source).toContain('营销客户标签导入模板.xlsx');
    expect(source).toContain('模板下载已开始');
    expect(source).toContain('模板下载失败');
  });

  it('移除标签分类并通过客户标签类型字典统一筛选、录入和反显', () => {
    expect(source).not.toContain('标签分类');
    expect(source).not.toContain('tagCategory');
    expect(source).not.toContain('query.category');
    expect(source).not.toContain('项目类');
    expect(source).not.toContain('认定类');
    expect(source).toContain("useDict('CUSTOMER_TAG_TYPE')");
    expect(source).toMatch(/reload:\s*reloadTagTypes/);
    expect(source).toMatch(/v-model="query\.tagType"[\s\S]*tagTypeOptions/);
    expect(source).toMatch(/v-model="tagForm\.tagType"[\s\S]*tagTypeOptions/);
    expect(source).toContain('{{ tagTypeLabelOf(row.tagType) }}');
    expect(source).toMatch(/async function openCreate\(\)[\s\S]*await reloadTagTypes\(\)/);
    expect(source).toMatch(/function search\(\)[\s\S]*Promise\.all\(\[reloadTagTypes\(\),load\(\)\]\)/);
    expect(source).toMatch(/function resetFilters\(\)[\s\S]*Promise\.all\(\[reloadTagTypes\(\),refresh\(\)\]\)/);
  });

  it('状态列按审批优先级组合显示有效、禁用、待审核和已退回', () => {
    expect(source).toMatch(/label="状态"[\s\S]*tagStatusLabel\(row\)/);
    expect(source).not.toContain('label="审批状态"');
    expect(source).not.toContain('label="启用状态"');
    expect(source).toMatch(/row\.approvalStatus==='PENDING'[\s\S]*row\.approvalStatus==='REJECTED'[\s\S]*row\.status==='ENABLED'/);
    ['有效', '禁用', '待审核', '已退回'].forEach(label => expect(source).toContain(label));
  });

  it('四张统计卡互斥设置 viewStatus 并用无筛选的 pageSize=1 请求总数', () => {
    expect(source).toContain('class="tag-stat-grid"');
    ['标签总览', '有效标签', '待审核标签', '异常标签'].forEach(label => expect(source).toContain(label));
    expect(source).toMatch(/function switchViewStatus\(viewStatus\)[\s\S]*query\.viewStatus=viewStatus[\s\S]*query\.pageNo=1[\s\S]*load\(\)/);
    expect(source).toMatch(/listMarketingCustomerTags\(\{viewStatus,pageNo:1,pageSize:1\}\)/);
    expect(source).toMatch(/const statViewStatuses=\['','ACTIVE','PENDING','EXCEPTION'\]/);
  });

  it('筛选条件、查询和重置固定在同一行，并将按钮放入明确操作项', () => {
    expect(source).toContain('<el-form inline class="filter-form" @submit.prevent>');
    expect(source).toContain('<el-form-item class="filter-actions">');
    expect(source).toMatch(/\.filter-form\s*\{[^}]*display:\s*flex[^}]*flex-wrap:\s*nowrap/);
    expect(source).toMatch(/\.filter-actions\s*\{[^}]*flex:\s*0\s+0\s+auto/);
  });

  it('筛选支持四种状态和重置，新增成功后同步刷新统计', () => {
    ['ACTIVE', 'DISABLED', 'PENDING', 'REJECTED'].forEach(value => expect(source).toContain(`value="${value}"`));
    expect(source).toMatch(/@click="resetFilters"[^>]*>重置</);
    expect(source).toMatch(/function resetFilters\(\)[\s\S]*query\.keyword=''[\s\S]*query\.tagType=''[\s\S]*query\.viewStatus=''[\s\S]*query\.pageNo=1[\s\S]*refresh\(\)/);
    expect(source).toMatch(/createMarketingCustomerTag\(tagForm\)[\s\S]*await refresh\(\)/);
  });
});
