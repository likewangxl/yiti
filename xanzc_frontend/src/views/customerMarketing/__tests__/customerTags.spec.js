import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

describe('客户标签页面', () => {
  it('标签管理与标签审核均不录入或展示标签编码', () => {
    const management = readFileSync(new URL('../CustomerTags.vue', import.meta.url), 'utf8');
    const approval = readFileSync(new URL('../TagApproval.vue', import.meta.url), 'utf8');

    expect(management).not.toContain('tagCode');
    expect(management).not.toContain('标签编码');
    expect(approval).not.toContain('tagCode');
    expect(approval).not.toContain('标签编码');
  });

  it('标签审核展示姓名工号、格式化提交时间且待审核不提供详情入口', () => {
    const approval = readFileSync(new URL('../TagApproval.vue', import.meta.url), 'utf8');

    expect(approval).toContain('personLabel(row)');
    expect(approval).toContain('formatTime(row.createdTime)');
    expect(approval).toContain("row.approvalStatus!=='PENDING'");
    expect(approval).toContain("replace('T',' ')");
    expect(approval).toContain('reviewerLabel(selected)');
    expect(approval).toContain('formatTime(selected.reviewedTime)');
  });

  it('标签审核按页签请求后端范围，审核记录不在前端做人员过滤', () => {
    const approval = readFileSync(new URL('../TagApproval.vue', import.meta.url), 'utf8');

    expect(approval).toContain('approvalStatus:tab.value');
    expect(approval).not.toContain('reviewedBy===');
    expect(approval).not.toContain('reviewedBy ===');
  });

  it('标签管理以多选方式批量禁用和删除，行内只保留客户群与两种导入', () => {
    const management = readFileSync(new URL('../CustomerTags.vue', import.meta.url), 'utf8');

    expect(management).toContain('type="selection"');
    expect(management).toContain('批量禁用');
    expect(management).toContain('批量删除');
    expect(management).toContain('>客户群</el-button>');
    expect(management).toContain('>追加导入</el-button>');
    expect(management).toContain('>全量替换</el-button>');
    expect(management).not.toContain('@click="toggle(row)"');
  });

  it('追加和全量替换通过 Excel 文件导入并提供模板下载', () => {
    const management = readFileSync(new URL('../CustomerTags.vue', import.meta.url), 'utf8');

    expect(management).toContain('<el-upload');
    expect(management).toContain('accept=".xlsx,.xls"');
    expect(management).toContain('下载导入模板');
    expect(management).toContain('downloadTagCustomerImportTemplate');
    expect(management).toContain('importTagCustomersFile');
    expect(management).not.toContain('selectedCustIds');
    expect(management).not.toContain('listMarketingCustomers');
  });

  it('客户群使用统一社会信用代码展示和查询，不展示内部ID和客户编号', () => {
    const management = readFileSync(new URL('../CustomerTags.vue', import.meta.url), 'utf8');

    expect(management).toContain('placeholder="客户名称 / 统一社会信用代码"');
    expect(management).toContain('prop="unifiedCreditCode" label="统一社会信用代码"');
    expect(management).toContain("r.unifiedCreditCode||''");
    expect(management).not.toContain('label="客户ID"');
    expect(management).not.toContain('label="客户编号"');
    expect(management).not.toContain('r.custNo');
  });
});
