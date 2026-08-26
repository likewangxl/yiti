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
});
