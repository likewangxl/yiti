import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../MarketingLeadEntry.vue', import.meta.url), 'utf8');

describe('营销线索录入页面契约', () => {
  it('沿用我的客户页面的统一页头、说明、筛选卡片和分页视觉契约', () => {
    expect(source).toMatch(/<header class="page-head">[\s\S]*<div>[\s\S]*<PageTitle[^>]*\/>[\s\S]*<span>展示当前登录人的线索录入记录；支持单条录入和批量导入，存量客户会反显客户主档信息。<\/span>[\s\S]*<\/div>[\s\S]*<\/header>/);
    expect(source).toMatch(/\.page-head h1[\s\S]*font-size:\s*18px/);
    expect(source).toMatch(/\.page-head span[\s\S]*font-size:\s*12px[\s\S]*margin-top:\s*4px/);
    expect(source).toContain('<el-card shadow="never" class="filter-card">');
    expect(source).toContain('class="lead-entry-table"');
    expect(source).toContain('background layout="total, sizes, prev, pager, next"');
    expect(source).toContain(':page-sizes="[10, 20, 50, 100]"');
  });

  it('第一 Tab 按每条 MANUAL 线索展示，不聚合且不展示版本字段', () => {
    expect(source).toContain('listManualLeads');
    expect(source).toContain('leadSource');
    expect(source).toContain('线索录入记录');
    expect(source).toContain('leadNo');
    expect(source).not.toContain('versionNo');
    expect(source).not.toContain('版本');
  });

  it('第二 Tab 展示批次、失败优先明细、OBS 下载和待确认动作', () => {
    expect(source).toContain('listLeadImportBatches');
    expect(source).toContain('MarketingLeadImportDetailDrawer');
    expect(source).toContain('downloadLeadImportSourceFile');
    expect(source).toContain('downloadLeadImportErrorFile');
    expect(source).toContain('WAITING_CONFIRM');
    expect(source).toContain('PROCESS_VALID');
    expect(source).toContain('ABANDON_REIMPORT');
    expect(source).toContain('导入文件名');
    expect(source).toContain('导入时间');
  });

  it('写操作失败时不显示成功提示', () => {
    expect(source).toContain('catch (error)');
    expect(source).toContain('ElMessage.error');
  });
});
