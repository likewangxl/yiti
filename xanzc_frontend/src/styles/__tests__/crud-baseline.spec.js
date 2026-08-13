import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const source = readFileSync(new URL('../index.scss', import.meta.url), 'utf8');
const designSystem = readFileSync(new URL('../../../design-system/MASTER.md', import.meta.url), 'utf8');
const marker = '/* Scoped CRUD page baseline */';
const endMarker = '/* End scoped CRUD page baseline */';

function scopedBaseline() {
  const start = source.indexOf(marker);
  const end = source.indexOf(endMarker);

  expect(start, '应声明可审计的 CRUD 基线边界').toBeGreaterThanOrEqual(0);
  expect(end, 'CRUD 基线应有明确结束边界').toBeGreaterThan(start);
  return source.slice(start, end + endMarker.length);
}

describe('共享 CRUD 页面基线样式', () => {
  it('以显式 bp-crud 命名空间和受控选择器覆盖页面结构', () => {
    const scoped = scopedBaseline();

    expect(scoped).toMatch(/\.bp-crud\s*\{/);
    expect(scoped).toMatch(/\.bp-crud\s+:where\(\.page-h\)/);
    expect(scoped).toMatch(/\.bp-crud\s+:where\(\.card-section\)/);
    expect(scoped).toMatch(/\.bp-crud\s+:where\(\.filter-bar,\s*\.filter-form\)/);
    expect(scoped).toMatch(/\.bp-crud\s+:where\(\.toolbar\)/);
    expect(scoped).toMatch(/\.bp-crud\s+:where\(\.pager\)/);
    expect(scoped).toMatch(/\.bp-crud\s+:where\(\.hint\)/);
    expect(scoped).toMatch(/\.bp-crud\s+:where\(\.table-state\)/);
    expect(scoped).toMatch(/\.bp-crud\s+:where\(\.data-panel\)/);
    expect(scoped).toMatch(/\.bp-crud\s+:where\(\.action-group\)/);
  });

  it('仅消费既有语义 token，且不向独立子系统或裸组件泄漏', () => {
    const scoped = scopedBaseline();

    for (const token of [
      '--color-surface', '--color-border', '--color-text-muted', '--space-4', '--shadow-surface'
    ]) {
      expect(scoped).toContain(`var(${token})`);
    }

    expect(scoped).not.toMatch(/#[0-9a-f]{3,8}\b/i);
    expect(scoped).not.toMatch(/\.(?:re-|redengine|screen|scr-)[\w-]*/i);
    expect(scoped).not.toMatch(/(?:^|\})\s*\.el-(?:table|dialog)\b/m);
    expect(scoped).not.toMatch(/(?:^|[,{]\s*)body\b/m);
  });

  it('落实桌面高密度 CRUD 的筛选、表格、状态和操作尺度', () => {
    const scoped = scopedBaseline();

    expect(scoped).toMatch(/\.bp-crud\s+:where\(\.filter-form\)\s*\{[\s\S]*display:\s*grid;/);
    expect(scoped).toMatch(/\.bp-crud\s+:where\(\.filter-form\s+\.el-form-item:last-child\)\s*\{[\s\S]*justify-self:\s*end;/);
    expect(scoped).toMatch(/\.bp-crud\s+:where\(\.el-input__wrapper,\s*\.el-select__wrapper,\s*\.el-button\)\s*\{[\s\S]*min-height:\s*32px;/);
    expect(scoped).toMatch(/\.bp-crud\s+\.el-table\s+th\.el-table__cell,\s*\.bp-crud\s+\.el-table\s+td\.el-table__cell\s*\{[\s\S]*height:\s*40px;/);
    expect(scoped).toMatch(/\.bp-crud\s+:where\(\.el-table\s+\.el-tag\)\s*\{[\s\S]*min-height:\s*24px;/);
    // Element Plus 的 .el-table .cell 为 0,2,0；:where() 的内部选择器不贡献特异度。
    // 这里必须使用足以覆盖组件默认值的主平台作用域，不能仅靠 selector 文本存在。
    expect(scoped).toMatch(/\.bp-crud\s+\.el-table\s+\.cell\s*\{[\s\S]*overflow:\s*hidden;[\s\S]*text-overflow:\s*ellipsis;[\s\S]*white-space:\s*nowrap;/);
    expect(scoped).toMatch(/\.bp-crud\s+\.el-table\s+td\.operation-cell\s+\.cell[\s\S]*\{[\s\S]*overflow:\s*visible;[\s\S]*text-overflow:\s*clip;[\s\S]*white-space:\s*nowrap;/);
    expect(scoped).toMatch(/\.el-table-fixed-column--right\.operation-cell\s+\.cell/);
    expect(scoped).not.toMatch(/\.el-table-fixed-column--right\s+\.cell\s*\{/);
    expect(scoped).toMatch(/\.bp-crud-menu\s+\.danger-item/);
  });

  it('约束网格固有尺寸，宽表只能在 Element Plus 表格内部滚动', () => {
    const scoped = scopedBaseline();

    expect(scoped).toMatch(/\.bp-crud\s*\{[\s\S]*grid-template-columns:\s*minmax\(0,\s*1fr\);[\s\S]*min-width:\s*0;[\s\S]*max-width:\s*100%;/);
    expect(scoped).toMatch(/\.bp-crud\s*>\s*\*\s*\{[\s\S]*box-sizing:\s*border-box;[\s\S]*min-width:\s*0;[\s\S]*max-width:\s*100%;/);
    expect(scoped).toMatch(/\.bp-crud\s+\.el-table\s*\{[\s\S]*min-width:\s*0;[\s\S]*max-width:\s*100%;[\s\S]*width:\s*100%;/);
    expect(scoped).toMatch(/\.bp-crud\s+\.el-descriptions__table\s*\{[\s\S]*table-layout:\s*fixed;[\s\S]*width:\s*100%;/);
    expect(scoped).not.toMatch(/\.bp-crud[\s\S]*overflow-x:\s*hidden/);
  });

  it('用可覆盖 Element Plus 的特异度落实 40px 表头和数据行', () => {
    const scoped = scopedBaseline();

    expect(scoped).toMatch(/\.bp-crud\s+\.el-table\s+th\.el-table__cell,\s*\.bp-crud\s+\.el-table\s+td\.el-table__cell\s*\{[\s\S]*height:\s*40px;[\s\S]*padding:\s*0;/);
    expect(scoped).toMatch(/\.bp-crud\s+\.el-table\s+tr\s*\{[\s\S]*height:\s*40px;/);
    expect(scoped).toMatch(/td\.compact-clamp-cell\s+\.cell[\s\S]*display:\s*-webkit-box;[\s\S]*-webkit-line-clamp:\s*2;[\s\S]*line-height:\s*18px;[\s\S]*max-height:\s*36px;/);
    expect(scoped).toMatch(/td\.compact-stack-cell\s+\.cell[\s\S]*display:\s*grid;[\s\S]*grid-auto-rows:\s*18px;[\s\S]*height:\s*40px;/);
    expect(scoped).toMatch(/td\.compact-status-cell\s+\.cell[\s\S]*grid-template-rows:\s*24px\s+16px;/);
  });

  it('设计规范明确 CLI 可执行的横滚动、行高与真实悬停验收门禁', () => {
    expect(designSystem).toMatch(/document、`\.content` 和 main 均不得出现非预期横向滚动/);
    expect(designSystem).toMatch(/实载表格的表头和数据行计算高度为 40px/);
    expect(designSystem).toMatch(/`compact-clamp-cell`[\s\S]*最多两行[\s\S]*真实溢出时由受控 mouseover/);
    expect(designSystem).toMatch(/必须在真实 mouseover 后校验 title/);
  });
});
