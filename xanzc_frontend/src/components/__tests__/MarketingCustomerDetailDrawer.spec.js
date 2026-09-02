import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../MarketingCustomerDetailDrawer.vue', import.meta.url), 'utf8');

describe('营销客户详情抽屉契约', () => {
  it('展示企业、联系人、授信和主办权详情', () => {
    expect(source).toContain('企业名称');
    expect(source).toContain('统一社会信用代码');
    expect(source).toContain('企业联系人');
    expect(source).toContain('授信敞口');
    expect(source).toContain('主办客户经理');
  });

  it('所属行业使用 INDUSTRY 字典显示中文', () => {
    expect(source).toContain("useDict('INDUSTRY')");
    expect(source).toContain('industryLabelOf(customer.industry)');
    expect(source).not.toContain("customer.industry || '-'");
  });
});
