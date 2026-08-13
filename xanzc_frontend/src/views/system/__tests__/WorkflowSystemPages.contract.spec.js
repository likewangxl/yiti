import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

function sourceOf(relativePath) {
  return readFileSync(new URL(relativePath, import.meta.url), 'utf8');
}

const crudPages = [
  ['流程配置', '../Config.vue', 'workflow-config-page', 'workflow-config-page-title'],
  ['超时规则', '../TimeoutRules.vue', 'timeout-rules-page', 'timeout-rules-page-title'],
  ['审批流程', '../FlowList.vue', 'flow-list-page', 'flow-list-page-title'],
  ['审批流监控', '../WorkflowMonitor.vue', 'workflow-monitor-page', 'workflow-monitor-page-title'],
  ['人员标签', '../PersonTags.vue', 'person-tags-page', 'person-tags-page-title']
];

describe('流程与高级系统页面桌面信息架构契约', () => {
  it.each(crudPages)('%s 使用统一 CRUD 边界、可访问页头与稳定数据反馈', (_name, path, pageClass, titleId) => {
    const source = sourceOf(path);

    expect(source).toMatch(new RegExp(`<main\\b(?=[^>]*class="bp-crud ${pageClass}"[^>]*)(?=[^>]*aria-labelledby="${titleId}")[^>]*>`));
    expect(source).toContain('<header class="page-h">');
    expect(source).toContain(`id="${titleId}"`);
    expect(source).toContain('filter-bar');
    expect(source).toContain('data-panel');
    expect(source).toContain('toolbar');
    expect(source).toContain('table-state');
    expect(source).toContain('aria-busy');
  });

  it('流程设计器保留画布编辑逻辑，同时提供可访问的加载、只读和选中状态边界', () => {
    const source = sourceOf('../FlowEdit.vue');

    expect(source).toMatch(/<main\b(?=[^>]*class="bp-crud flow-edit-page"[^>]*)(?=[^>]*aria-labelledby="flow-edit-page-title")[^>]*>/);
    expect(source).toContain('<header class="page-h">');
    expect(source).toContain('id="flow-edit-page-title"');
    expect(source).toContain('designer-panel');
    expect(source).toContain('designer-state');
    expect(source).toContain('aria-busy');
  });

  it('转交弹窗和流程画布使用语义 token、可读状态与键盘操作提示', () => {
    const dialog = sourceOf('../../../components/TransferDialog.vue');
    const canvas = sourceOf('../flow/FlowCanvas.vue');

    expect(dialog).toContain('class="bp-crud-dialog transfer-dialog"');
    expect(dialog).toContain('role="status"');
    expect(dialog).toContain(':disabled="submitting"');
    expect(canvas).toContain('tabindex="0"');
    expect(canvas).toContain('aria-label="流程设计画布"');
    expect(canvas).toContain('var(--color-border)');
    expect(canvas).toContain('var(--color-focus)');
  });
});
