import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const pages = [
  ['资源管理', '../Resources.vue', 'resources-page', 'resources-page-title'],
  ['权限配置', '../Permission.vue', 'permission-page', 'permission-page-title'],
  ['字典管理', '../Dict.vue', 'dict-page', 'dict-page-title'],
  ['工作日历', '../Calendar.vue', 'calendar-page', 'calendar-page-title'],
  ['任务调度', '../Jobs.vue', 'jobs-page', 'jobs-page-title'],
  ['审计日志', '../Audit.vue', 'audit-page', 'audit-page-title'],
  ['通知中心', '../Notifications.vue', 'notifications-page', 'notifications-page-title'],
  ['文件管理', '../Files.vue', 'files-page', 'files-page-title']
];

function sourceOf(relativePath) {
  return readFileSync(new URL(relativePath, import.meta.url), 'utf8');
}

describe('系统治理与 RBAC 页面桌面 CRUD 契约', () => {
  it.each(pages)('%s 使用 bp-crud 页面边界、可访问页头与稳定数据面板', (_name, path, pageClass, titleId) => {
    const source = sourceOf(path);

    expect(source).toMatch(new RegExp(`<main\\s+class="bp-crud ${pageClass}"[^>]*aria-labelledby="${titleId}"`));
    expect(source).toContain('<header class="page-h">');
    expect(source).toContain(`id="${titleId}"`);
    expect(source).toContain('filter-bar');
    expect(source).toContain('data-panel');
    expect(source).toContain('toolbar');
    expect(source).toContain('aria-busy');
    expect(source).toContain('table-state');
  });

  it.each(pages)('%s 的弹窗或高风险操作保持可审计的确认与状态边界', (_name, path) => {
    const source = sourceOf(path);

    expect(source).toMatch(/(?:bp-crud-dialog|ElMessageBox\.confirm|el-popconfirm)/);
    expect(source).toMatch(/(?:loading|saving|pending|importing|initing|markingAll)/);
  });
});
