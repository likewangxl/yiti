import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const source = readFileSync(new URL('../Dict.vue', import.meta.url), 'utf8');

function styleBlock(selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = source.match(new RegExp(`${escaped}\\s*\\{([^}]*)\\}`));
  expect(match, `Dict.vue 应声明 ${selector} 样式规则`).toBeTruthy();
  return match[1];
}

describe('字典管理主从工作区布局契约', () => {
  it('在视口内锁定工作区，并让类型列表与字典项面板各自滚动', () => {
    const workspace = styleBlock('.dict-workspace');
    const types = styleBlock('.dict-types');
    const items = styleBlock('.dict-items');
    const typeList = styleBlock('.type-list');

    expect(workspace).toMatch(/height:\s*(?:min|clamp)\(/);
    expect(workspace).toMatch(/min-height:\s*0/);
    expect(types).toMatch(/min-height:\s*0/);
    expect(types).toMatch(/overflow:\s*hidden/);
    expect(items).toMatch(/min-height:\s*0/);
    expect(items).toMatch(/overflow:\s*hidden/);
    expect(typeList).toMatch(/min-height:\s*0/);
    expect(typeList).toMatch(/overflow:\s*auto/);
    expect(source).toMatch(/\.dict-items\s+:deep\([^)]*el-table[^)]*\)[^{]*\{[^}]*overflow:\s*auto/);
  });

  it('保留工作区外的类型搜索，并在窄屏回退为可用的单列布局', () => {
    expect(source).toMatch(/<el-form-item[^>]*class="dict-type-filter"[^>]*label="字典类型"/);
    expect(source).toMatch(/class="dict-type-search"/);
    expect(source).toMatch(/@media\s*\(max-width:\s*[^)]+\)[\s\S]*\.dict-workspace[\s\S]*grid-template-columns:\s*1fr[\s\S]*height:\s*auto/);
  });
});
