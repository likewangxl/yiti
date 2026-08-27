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

  it('第一 Tab 按每条 MANUAL 线索展示，不聚合且不向用户展示线索编号和版本', () => {
    expect(source).toContain('listManualLeads');
    expect(source).toContain('leadSource');
    expect(source).toContain('线索录入记录');
    expect(source).not.toContain('prop="leadNo"');
    expect(source).not.toContain('row.leadNo');
    expect(source).not.toContain('线索编号 / 企业名称');
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
      'form.creditExposureAmount', 'form.touchRestricted', 'form.attachmentIds'
    ].forEach(field => expect(source).toContain(field));
    expect(source).toMatch(/label="是否触达限制"[^>]*prop="touchRestricted"[\s\S]*v-model="form\.touchRestricted"/);
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

  it('经营属性均为非必填，新开户企业不预设是否基石客户', () => {
    ['industry', 'groupType', 'customerType', 'isKeystone', 'enterpriseType'].forEach(field => {
      expect(source).not.toMatch(new RegExp(`${field}:\\s*\\[\\{\\s*required:\\s*true`));
    });
    expect(source).toMatch(/isAccountOpenedSnapshot:\s*0/);
    expect(source).toMatch(/isKeystone:\s*null/);
    expect(source).toContain('均为选填，新开户企业可暂不填写');
  });

  it('客户名称失焦后精确反查，信用代码反查保持兼容', () => {
    expect(source).toMatch(/v-model="form\.custName"[^>]*@blur="lookupCustomerByName"/);
    expect(source).toMatch(/v-model="form\.unifiedCreditCode"[^>]*@blur="lookupCustomerByCreditCode"/);
    expect(source).toMatch(/lookupMarketingCustomer\(\{customerName/);
    expect(source).toMatch(/lookupMarketingCustomer\(\{unifiedCreditCode/);
    expect(source).toMatch(/form\.unifiedCreditCode\s*=\s*customer\.unifiedCreditCode/);
  });

  it('编辑仅以当前主档主办权决定 OWNER，并按姓名（工号）展示', () => {
    expect(source).toMatch(/value="PUBLIC"[^>]*:disabled="Boolean\(ownerCandidate\)"/);
    expect(source).toMatch(/value="SCOPE"[^>]*:disabled="Boolean\(ownerCandidate\)"/);
    expect(source).toContain('{{ ownerCandidate.name }}（{{ ownerCandidate.id }}）');
    expect(source).not.toContain('{{ ownerCandidate.id }} · {{ ownerCandidate.name }}');
    expect(source).toMatch(/if\(customer\.mainManagerId\)[\s\S]*form\.distributionMode='OWNER'/);
    expect(source).toMatch(/matchedCustomer\.value=detail\?\.currentCustomer\|\|null;[\s\S]*if\(matchedCustomer\.value\?\.mainManagerId\)[\s\S]*form\.distributionMode='OWNER'[\s\S]*else if\(form\.distributionMode==='OWNER'\)[\s\S]*form\.distributionMode='PUBLIC'/);
  });

  it('查看详情复用与录入表单一致的只读业务字段组件', () => {
    expect(source).toContain("import MarketingLeadReadonlyDetail from '@/components/MarketingLeadReadonlyDetail.vue'");
    expect(source).toContain('<MarketingLeadReadonlyDetail :detail="leadDetail" />');
    expect(source).not.toContain('<el-descriptions v-if="leadDetail?.lead"');
  });

  it('抽屉支持保存草稿和提交审批，送审失败时保留已保存草稿', () => {
    expect(source).toMatch(/@click="saveLead\(false\)"[^>]*>保存草稿</);
    expect(source).toContain('@click="saveLead(true)"');
    expect(source).toContain("'提交审批'");
    expect(source).toMatch(/async function saveLead\(andSubmit=false\)/);
    expect(source).toMatch(/await persistLeadDraft\([\s\S]*await submitMarketingLead\(savedId\)/);
    expect(source).toContain('草稿已保存，但提交审批失败');
  });

  it('四张状态卡使用 pageSize=1 的 total，并与下拉状态互斥同步', () => {
    expect(source).toContain('class="lead-stat-grid"');
    ['草稿', '审批中', '已通过', '已退回'].forEach(label => expect(source).toContain(label));
    expect(source).toMatch(/listManualLeads\(\{status,pageNo:1,pageSize:1\}\)/);
    expect(source).toMatch(/leadQuery\.status===status\?'':status/);
    expect(source).toContain('@change="filterByDropdown"');
    expect(source).not.toContain("REJECTED:'已驳回'");
  });

  it('筛选栏提供重置按钮并清空关键词和唯一状态筛选', () => {
    expect(source).toMatch(/@click="resetLeadFilters"[^>]*>重置</);
    expect(source).toMatch(/function resetLeadFilters\(\)\{[\s\S]*leadQuery\.keyword=''[\s\S]*leadQuery\.status=''[\s\S]*leadQuery\.pageNo=1[\s\S]*loadLeads\(\)/);
  });

  it('线索录入筛选条件、查询和重置固定在同一行，并将按钮放入操作项', () => {
    expect(source).toContain('<el-form inline class="filter-form" @submit.prevent>');
    expect(source).toContain('<el-form-item class="filter-actions">');
    expect(source).toMatch(/\.filter-form\s*\{[^}]*display:\s*flex[^}]*flex-wrap:\s*nowrap/);
    expect(source).toMatch(/\.filter-actions\s*\{[^}]*flex:\s*0\s+0\s+auto/);
  });

  it('已退回线索可复用编辑抽屉修改并重新提交审批', () => {
    expect(source).toMatch(/v-if="row\.leadStatus === 'REJECTED'"[^>]*@click="editLead\(row\)"[^>]*>重新编辑</);
    expect(source).toMatch(/const editingLeadStatus = ref\(''\)/);
    expect(source).toMatch(/editingLeadStatus\.value=lead\.leadStatus\|\|row\.leadStatus\|\|''/);
    expect(source).toContain("editingLeadStatus === 'REJECTED' ? '重新提交审批' : '提交审批'");
    expect(source).toContain('已退回线索修改后可重新提交审批');
    expect(source).toMatch(/await updateMarketingLead\(form\.id,payload\)[\s\S]*await submitMarketingLead\(savedId\)/);
  });

  it('已匹配客户改名或名称查询未命中时清理旧主档快照，不影响未匹配时的手工代码', () => {
    expect(source).toMatch(/function clearPreviousMatchedSnapshot\(preserveCreditCode=false\)\{[\s\S]*if\(!matchedCustomer\.value\)return false/);
    ['unifiedCreditCode', 'custNo', 'mainManagerId', 'mainOrgId', 'managerEmpIds'].forEach(field => {
      expect(source).toMatch(new RegExp(`${field}:[^,}]*`));
    });
    expect(source).toMatch(/leadType:'NEW_ACCOUNT'/);
    expect(source).toMatch(/isAccountOpenedSnapshot:0/);
    expect(source).toMatch(/isKeystone:null/);
    expect(source).toMatch(/matchedCustomer\.value\?\.custName!==customerName[\s\S]*clearPreviousMatchedSnapshot\(\)/);
    expect(source).toMatch(/if\(!customer\)[\s\S]*clearPreviousMatchedSnapshot\(\)/);
  });

  it('已匹配客户修改信用代码时清理旧快照并保留当前新代码', () => {
    expect(source).toMatch(/function clearPreviousMatchedSnapshot\(preserveCreditCode=false\)/);
    expect(source).toMatch(/unifiedCreditCode:preserveCreditCode\?form\.unifiedCreditCode:''/);
    expect(source).toMatch(/matchedCustomer\.value\?\.unifiedCreditCode!==unifiedCreditCode[\s\S]*clearPreviousMatchedSnapshot\(true\)[\s\S]*unifiedCreditCode\.length!==18/);
    expect(source).toMatch(/lookupMarketingCustomer\(\{unifiedCreditCode\}\)[\s\S]*if\(!customer\)[\s\S]*clearPreviousMatchedSnapshot\(true\)/);
  });

  it('抽屉提示客户名称和信用代码都可自动反显', () => {
    expect(source).toContain('按客户名称或统一社会信用代码自动反显');
    expect(source).not.toContain('按统一社会信用代码自动反显');
  });
});
