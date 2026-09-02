import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../NameListManagement.vue', import.meta.url), 'utf8');

describe('标签客户名单管理页面契约', () => {
  it('展示查询字段、名单信息和分页，不展示内部ID与创建人工号', () => {
    expect(source).toContain('企业名称');
    expect(source).toContain('统一社会信用代码');
    expect(source).toContain('客户标签');
    expect(source).toContain('创建日期');
    expect(source).toContain('公司地址');
    expect(source).toContain('联系人');
    expect(source).toContain('联系电话');
    expect(source).toContain('备注');
    expect(source).toContain('el-pagination');
    expect(source).not.toContain('nameListId');
    expect(source).not.toContain('createBy');
  });

  it('提供模板下载、导入、导出并限制 Excel 扩展名', () => {
    expect(source).toContain('下载模板');
    expect(source).toContain('导入');
    expect(source).toContain('导出');
    expect(source).toContain('accept=".xlsx,.xls"');
    expect(source).toContain('downloadNameListTemplate');
    expect(source).toContain('importNameList');
    expect(source).toContain('exportNameList');
    expect(source).toContain('URL.createObjectURL');
  });

  it('将导入错误按行号展示，不能只提示成功', () => {
    expect(source).toContain('errors.length');
    expect(source).toContain('rowNumber');
    expect(source).toContain('导入错误');
    expect(source).toContain('importResult.errors');
  });
});
