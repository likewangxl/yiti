import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../MarketingLeadReadonlyDetail.vue', import.meta.url), 'utf8');

describe('营销线索共享只读详情', () => {
  it('按录入表单的四个分节完整展示业务字段', () => {
    ['基础信息', '经营属性', '分配信息', '补充资料'].forEach(label => expect(source).toContain(label));
    [
      '线索类型', '客户名称', '统一社会信用代码', '是否开户', '客户号',
      '所属行业', '所属集团类型', '所属集团名称', '客户类型', '是否基石客户',
      '企业类型', '客户标签', '分配方式', '客户经理范围', '主办客户经理',
      '客户说明', '授信金额（万元）', '授信敞口金额（万元）', '附件', '是否触达限制'
    ].forEach(label => expect(source).toContain(label));
  });

  it('复用字典显示中文并统一主办姓名（工号）口径', () => {
    ['INDUSTRY', 'GROUP_TYPE', 'CUSTOMER_TYPE', 'ENTERPRISE_TYPE'].forEach(dictType => {
      expect(source).toContain(`useDict('${dictType}')`);
    });
    expect(source).toContain("`${name}（${id}）`");
    expect(source).not.toContain("`${id} · ${name}`");
  });

  it('兼容详情顶层与 lead 内的标签、人员和附件关系', () => {
    expect(source).toMatch(/props\.detail\?\.tagIds\s*\|\|\s*lead\.value\?\.tagIds/);
    expect(source).toMatch(/props\.detail\?\.managerEmpIds\s*\|\|\s*lead\.value\?\.managerEmpIds/);
    expect(source).toMatch(/props\.detail\?\.attachments\s*\|\|\s*lead\.value\?\.attachments/);
  });
});
