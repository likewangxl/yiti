import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const source = readFileSync(
  fileURLToPath(new URL('../AvailablePool.vue', import.meta.url)),
  'utf8',
);

describe('待认领线索池展示契约', () => {
  it('隐藏客户编号，并且中文展示字段不回退到英文编码', () => {
    expect(source).not.toContain('prop="custNo" label="客户编号"');
    expect(source).toContain('row.industryName || \'-\'');
    expect(source).toContain('row.customerTypeName || \'-\'');
    expect(source).toContain('row.ownerOrgName || \'-\'');
    expect(source).not.toContain('row.industryName || row.industry');
    expect(source).not.toContain('row.ownerOrgName || row.ownerOrgId');
    expect(source).not.toContain('prop="customerType"');
  });

  it('详情按当前线索编号优先、旧线索编号兜底，并复用详情抽屉', () => {
    expect(source).toContain('row.currentLeadId || row.leadId');
    expect(source).toContain('getAvailableCustomerLeadDetail');
    expect(source).toContain('<LeadDetailDrawer');
    expect(source).toContain('>详情</el-button>');
    expect(source).toContain('>认领</el-button>');
  });
});
