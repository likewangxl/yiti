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

  it('页头右侧提供批量导入和录入线索入口', () => {
    expect(source).toMatch(/<header class="page-head">[\s\S]*<div class="page-actions">[\s\S]*>批量导入<[\s\S]*>录入线索<[\s\S]*<\/div>[\s\S]*<\/header>/);
    expect(source).toMatch(/function openImport\(\)[\s\S]*activeTab\.value\s*=\s*'imports'[\s\S]*loadBatches\(\)/);
    expect(source).toMatch(/>录入线索<[\s\S]*@click="openCreate"|@click="openCreate"[\s\S]*>录入线索</);
    expect(source).not.toContain('>新增线索<');
  });

  it('录入抽屉按 V2_DEMO 分为四个信息分节并使用同步字段', () => {
    expect(source).toContain('<el-drawer');
    expect(source).toContain('size="min(1080px, 92vw)"');
    ['01', '基础信息', '02', '经营属性', '03', '分配信息', '04', '补充资料'].forEach(text => {
      expect(source).toContain(text);
    });
    [
      'form.leadType', 'form.custName', 'form.unifiedCreditCode',
      'form.isAccountOpenedSnapshot', 'form.custNo', 'form.industry',
      'form.groupType', 'form.groupName', 'form.customerType', 'form.isKeystone',
      'form.enterpriseType', 'form.tagIds', 'form.distributionMode',
      'form.managerEmpIds', 'form.customerDesc', 'form.creditAmount',
      'form.creditExposureAmount', 'form.attachmentIds'
    ].forEach(field => expect(source).toContain(field));
    expect(source).toMatch(/touchRestricted:\s*1/);
    expect(source).not.toContain('form.contactPerson');
    expect(source).not.toContain('form.registeredAddress');
  });

  it('复用字典、审批通过的启用标签和员工远程搜索', () => {
    ['INDUSTRY', 'GROUP_TYPE', 'CUSTOMER_TYPE', 'ENTERPRISE_TYPE'].forEach(dictType => {
      expect(source).toContain(`useDict('${dictType}')`);
    });
    expect(source).toContain('listMarketingCustomerTags');
    expect(source).toMatch(/status:\s*'ENABLED'[\s\S]*approvalStatus:\s*'APPROVED'[\s\S]*pageNo:\s*1[\s\S]*pageSize:\s*100/);
    expect(source).toContain('searchEmployees');
    expect(source).toContain(':remote-method="searchManagers"');
    expect(source).toMatch(/v-if="ownerCandidate"[\s\S]*value="OWNER"/);
  });

  it('金额以万元展示并在保存时传递附件 ID', () => {
    expect(source).toContain('授信金额（万元）');
    expect(source).toContain('授信敞口金额（万元）');
    expect(source).toContain('uploadMarketingLeadAttachment');
    expect(source).toMatch(/attachmentIds[\s\S]*uploadPendingAttachments/);
  });

  it('编辑时优先使用详情顶层的人员和标签关系', () => {
    expect(source).toMatch(/const managerEmpIds\s*=\s*detail\?\.managerEmpIds\s*\|\|\s*lead\.managerEmpIds/);
    expect(source).toMatch(/const tagIds\s*=\s*detail\?\.tagIds\s*\|\|\s*lead\.tagIds/);
  });

  it('V2_DEMO 标记的经营字段都参与必填校验，数值 0 作为有效默认值', () => {
    ['industry', 'groupType', 'customerType', 'isKeystone', 'enterpriseType', 'isAccountOpenedSnapshot'].forEach(field => {
      expect(source).toMatch(new RegExp(`${field}:\\s*\\[\\{\\s*required:\\s*true`));
    });
    expect(source).toMatch(/isAccountOpenedSnapshot:\s*0/);
    expect(source).toMatch(/isKeystone:\s*0/);
  });
});
