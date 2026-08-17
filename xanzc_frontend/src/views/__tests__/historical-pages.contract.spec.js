import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const root = new URL('..', import.meta.url);

function sourceOf(path) {
  return readFileSync(new URL(path, root), 'utf8');
}

const listPages = [
  'guarantee/Query.vue',
  'guarantee/DataImport.vue',
  'guarantee/Notice.vue',
  'history/PriceApproval.vue',
  'history/PerfAdjustQuery.vue'
];

describe('历史查询页面 scoped CRUD 结构契约', () => {
  it.each(listPages)('%s 使用主区域、页头、筛选卡、数据面板和分页语义', (path) => {
    const source = sourceOf(path);

    expect(source).toMatch(/<main\b(?=[^>]*class="bp-crud\b)[^>]*>/);
    expect(source).toMatch(/<PageTitle\b/);
    expect(source).toMatch(/class="page-h"/);
    expect(source).toMatch(/class="[^"]*filter-bar[^"]*"/);
    expect(source).toMatch(/class="[^"]*data-panel[^"]*"/);
    expect(source).toMatch(/class="pager"/);
    expect(source).toMatch(/aria-busy/);
    expect(source).toMatch(/暂无|没有/);
    expect(source).not.toMatch(/#[0-9a-f]{3,8}\b/i);
  });

  it('定价审批详情使用 bp-crud 页头、数据面板和详情 section', () => {
    const source = sourceOf('history/PriceApprovalDetail.vue');

    expect(source).toMatch(/<main\b(?=[^>]*class="bp-crud\b)[^>]*>/);
    expect(source).toMatch(/<PageTitle\b/);
    expect(source).toMatch(/class="page-h"/);
    expect(source).toMatch(/class="[^"]*data-panel[^"]*"/);
    expect(source).toMatch(/class="[^"]*detail-section[^"]*"/);
    expect(source).toMatch(/aria-busy/);
    expect(source).toMatch(/加载中|暂无|没有/);
    expect(source).not.toMatch(/#[0-9a-f]{3,8}\b/i);
  });

  it('无权限页保留安全恢复语义并使用语义 token', () => {
    const source = sourceOf('NoAccess.vue');

    expect(source).toMatch(/<main\s+class="[^"]*no-access/);
    expect(source).toMatch(/aria-labelledby="no-access-title"/);
    expect(source).toMatch(/<h1\s+id="no-access-title"/);
    expect(source).toMatch(/aria-label="权限恢复操作"/);
    expect(source).toMatch(/重新加载权限/);
    expect(source).toMatch(/退出登录/);
    expect(source).toMatch(/var\(--color-(?:page|surface|border|text-strong|text-muted|brand-700)/);
    expect(source).not.toMatch(/#[0-9a-f]{3,8}\b/i);
  });

  it('业绩调整详情的 loading 指令落在真实元素，不作用于 Teleport 对话框组件', () => {
    const source = sourceOf('history/PerfAdjustQuery.vue');
    const dialog = source.match(/<el-dialog\b[\s\S]*?<\/el-dialog>/)?.[0] || '';

    expect(dialog).not.toMatch(/<el-dialog\b[^>]*\bv-loading=/);
    expect(dialog).toMatch(/<section\b[^>]*\bv-loading="dlg\.loading"/);
  });
});
