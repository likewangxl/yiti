import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../ClaimedPool.vue', import.meta.url), 'utf8');

describe('已认领客户池标签展示契约', () => {
  it('不展示客户编号，按 tagNames 展示多个客户标签', () => {
    expect(source).not.toContain('prop="custNo" label="客户编号"');
    expect(source).toContain('客户标签');
    expect(source).toContain('row.tagNames');
    expect(source).toContain('v-for="tagName in row.tagNames"');
    expect(source).toContain("row.tagNames?.length");
  });

  it('按页签、关键词和来源把筛选条件交给后端，不在当前页二次过滤', () => {
    expect(source).toContain('listClaimedCustomers({ tab: tab.value');
    expect(source).toContain('keyword: keyword.value || undefined');
    expect(source).toContain('sourceType: sourceType.value || undefined');
    expect(source).not.toContain('filteredRows');
    expect(source).not.toContain('.filter(r=>tab.value');
    expect(source).toContain('total.value = Number(r?.total || 0)');
  });

  it('按来源、认领时间、触达状态和结果展示关系，并只允许后端允许的操作', () => {
    expect(source).toContain('来源');
    expect(source).toContain('认领时间');
    expect(source).toContain('触达状态');
    expect(source).toContain('取消原因');
    expect(source).toContain('row.claimStatus');
    expect(source).toContain('row.canTouch');
    expect(source).toContain('row.canReTouch');
    expect(source).not.toContain('prop="custNo" label="客户编号"');
  });

  it('来源筛选与后端 distribution_mode 一致，且不把跨机构触达权当成认领关系', () => {
    expect(source).toContain('value="PUBLIC"');
    expect(source).toContain('value="SCOPE"');
    expect(source).toContain('value="OWNER"');
    expect(source).toContain('row.allocationSource || row.sourceType');
    expect(source).not.toContain('value="CROSS_ORG_MARKETING"');
  });
});
