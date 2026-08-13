import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const pageFiles = [
  'Dynamic.vue',
  'Presets.vue',
  'FreeReport.vue',
  'FreeReportDetail.vue',
  'Sql.vue',
  'AmasApprovals.vue',
  'AmasApprovalDetail.vue'
];

function source(name) {
  return readFileSync(fileURLToPath(new URL(`../${name}`, import.meta.url)), 'utf8');
}

describe('报表查询页桌面后台结构契约', () => {
  it.each(pageFiles)('%s 使用语义化 bp-crud 主区域和可读 busy 状态', (name) => {
    const content = source(name);
    expect(content).toMatch(/<main\b(?=[^>]*class="bp-crud\b[^"]*")[^>]*>/);
    expect(content).toMatch(/aria-labelledby=/);
    expect(content).toMatch(/:aria-busy=/);
  });

  it('预置报表卡片使用键盘可达的按钮，而不是可点击 div 和 emoji 图标', () => {
    const content = source('Presets.vue');
    expect(content).toMatch(/<button[\s\S]*v-for="card in CARDS"/);
    expect(content).not.toMatch(/class="ico">[📈👥📞]/);
  });

  it('自由报表的导入与危险操作在请求期间禁用，删除保留明确的不可恢复确认', () => {
    const content = source('FreeReport.vue');
    expect(content).toMatch(/:loading="importDlg\.uploading"/);
    expect(content).toMatch(/:disabled="importDlg\.uploading"/);
    expect(content).toMatch(/数据将不可恢复/);
  });

  it('SQL 探查显式标示高风险操作和执行中防重复', () => {
    const content = source('Sql.vue');
    expect(content).toMatch(/高风险/);
    expect(content).toMatch(/:disabled="!valid \|\| running"/);
    expect(content).toMatch(/class="bp-crud-dialog"/);
  });
});
