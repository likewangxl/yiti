import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
const source = readFileSync(new URL('../MarketingCustomerTags.vue', import.meta.url), 'utf8');
describe('营销客户标签页面契约', () => {
  it('支持标签客户群、新增、追加与全量替换导入', () => {
    expect(source).toContain('listMarketingCustomerTags');
    expect(source).toContain('listMarketingCustomerTagCustomers');
    expect(source).toContain('createMarketingCustomerTag');
    expect(source).toContain('APPEND');
    expect(source).toContain('REPLACE');
    expect(source).toContain('createCustomerTagImportBatch');
  });
});
