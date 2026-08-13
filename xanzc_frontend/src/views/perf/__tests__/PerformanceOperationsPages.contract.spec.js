import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(process.cwd(), 'src/views/perf');
const read = (file) => readFileSync(resolve(root, file), 'utf8');

const pages = [
  ['Import.vue', 'perf-import-page', 'perf-import-page-title', 'perf-import-table-state'],
  ['Compute.vue', 'perf-compute-page', 'perf-compute-page-title', 'perf-compute-log-state'],
  ['TaskMonitor.vue', 'perf-task-monitor-page', 'perf-task-monitor-page-title', 'perf-task-monitor-table-state'],
  ['KpiScoreDetail.vue', 'perf-kpi-score-detail-page', 'perf-kpi-score-detail-page-title', 'perf-kpi-score-detail-table-state'],
  ['Adjust.vue', 'perf-adjust-page', 'perf-adjust-page-title', 'perf-adjust-mine-state']
];

describe('绩效运营页面 bp-crud 结构契约', () => {
  it.each(pages)('%s 使用主平台浅色工作区语义、加载态和恢复态', (file, pageClass, titleId, stateId) => {
    const source = read(file);
    expect(source).toMatch(new RegExp(`<main\\s+class="bp-crud ${pageClass}"[^>]*aria-labelledby="${titleId}"`));
    expect(source).toMatch(new RegExp(`aria-describedby="${stateId}"`));
    expect(source).toMatch(/aria-live="polite"/);
    expect(source).toMatch(/:aria-busy=/);
    expect(source).toMatch(/role="alert"/);
    expect(source).toMatch(/重新加载|重试/);
    expect(source).toMatch(/:empty-text=/);
    expect(source).toMatch(/加载失败/);
  });

  it('绩效运营页面不使用 emoji 作为结构图标，并遵循语义 token', () => {
    for (const [file] of pages) {
      const source = read(file);
      expect(source, file).not.toMatch(/[📥▶⚠]/u);
      expect(source, file).toMatch(/var\(--color-|var\(--space-/);
    }
  });

  it('高危/异步操作具有 loading 与 disabled 的重复提交保护', () => {
    expect(read('Import.vue')).toMatch(/:loading="uploading"[^>]*@click="onUpload/);
    expect(read('Compute.vue')).toMatch(/:loading="trgDlg\.saving"/);
    expect(read('TaskMonitor.vue')).toMatch(/:loading="execDlg\.submitting"/);
    expect(read('Adjust.vue')).toMatch(/:disabled="[^"']*(saving|loading)/);
  });
});

describe('AllocAdjustViewDialog 只读弹窗契约', () => {
  it('有可访问的对话框名称、加载状态和错误恢复路径', () => {
    const source = readFileSync(resolve(root, '../../components/AllocAdjustViewDialog.vue'), 'utf8');
    expect(source).toMatch(/class="bp-crud-dialog"/);
    expect(source).toMatch(/aria-label="查看调整申请"/);
    expect(source).toMatch(/:aria-busy=/);
    expect(source).toMatch(/role="alert"/);
    expect(source).toMatch(/重新加载|重试/);
  });
});
