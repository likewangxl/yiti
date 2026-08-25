import { describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';

vi.mock('../../../api/http', () => ({
  API_BASE: '/api',
  call: vi.fn().mockResolvedValue(new Blob(['template']))
}));

import { call } from '../../../api/http';
import { downloadViolationImportTemplate } from '../../../api/yundun';

const source = readFileSync(new URL('../ViolationManagement.vue', import.meta.url), 'utf8');

describe('云盾违规信息导入弹窗', () => {
  it('点击导入打开弹窗，并在弹窗内完成模板下载、文件选择和导入原因填写', () => {
    const actions = source.match(/<div class="filter-actions">([\s\S]*?)<\/div>/)?.[1] || '';
    const dialog = source.match(/<el-dialog v-model="importDialog\.show"([\s\S]*?)<\/el-dialog>/)?.[0] || '';

    expect(actions).toContain('@click="openImport"');
    expect(actions).not.toContain('<el-upload');
    expect(dialog).toContain('下载导入模板');
    expect(dialog).toContain('accept=".xlsx,.xls"');
    expect(dialog).toContain('importDialog.reason');
    expect(dialog).toContain('开始导入');
    expect(source).toContain('function openImport()');
    expect(source).toContain('function onImportFileChange');
  });

  it('导入弹窗提交前要求文件和导入原因，提交成功关闭并刷新列表', () => {
    const submit = source.match(/async function submitImport\(\) \{([\s\S]*?)\n\}/)?.[1] || '';

    expect(source).toContain(':disabled="!importDialog.file || !importDialog.reason.trim() || importDialog.submitting"');
    expect(submit).toContain('if (!importDialog.file || !importDialog.reason.trim())');
    expect(submit).toContain('importDialog.show = false');
    expect(submit).toContain('load()');
    expect(submit).toContain('成功导入');
  });

  it('模板下载使用当前违规业务类型的 import-template 端点', async () => {
    call.mockClear();

    await downloadViolationImportTemplate('credit');

    expect(call).toHaveBeenCalledWith('get', '/yundun/credit-violations/import-template', {
      responseType: 'blob'
    }, null);
  });
});
