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

  it('详情按待认领关系的来源线索优先、旧字段兜底，并复用详情抽屉', () => {
    expect(source).toContain('row.sourceLeadId || row.leadId || row.currentLeadId');
    expect(source).toContain('getAvailableCustomerLeadDetail');
    expect(source).toContain('<LeadDetailDrawer');
    expect(source).toContain('>详情</el-button>');
    expect(source).toContain('>认领</el-button>');
  });

  it('只查询后端可见的 PUBLIC 线索并展示完整线索字段', () => {
    expect(source).toContain("sourceType: 'PUBLIC'");
    expect(source).not.toContain('const sourceType = ref');
    expect(source).not.toContain('v-model="sourceType"');
    expect(source).not.toContain('sourceType.value');
    expect(source).toContain("row.distributionMode || row.sourceType || 'PUBLIC'");
    expect(source).toContain('线索编号');
    expect(source).toContain('线索类型');
    expect(source).toContain('所属集团类型');
    expect(source).toContain('是否基石客户');
    expect(source).toContain('是否开户');
    expect(source).toContain('客户标签');
    expect(source).toContain('授信敞口');
    expect(source).toContain('可认领范围');
    expect(source).toContain('已认领人数');
    expect(source).toContain('下发时间');
    expect(source).not.toContain('prop="custId"');
    expect(source).not.toContain('prop="id" label="客户');
  });
});
