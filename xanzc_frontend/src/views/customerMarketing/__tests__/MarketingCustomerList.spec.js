import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../MarketingCustomerList.vue', import.meta.url), 'utf8');

describe('营销客户列表页面契约', () => {
  it('使用营销客户主档分页查询并提供详情、资料编辑和主办权管理', () => {
    expect(source).toContain('listMarketingCustomers');
    expect(source).toContain('getMarketingCustomer');
    expect(source).toContain('updateMarketingCustomerProfile');
    expect(source).toContain('transferCustomerOwner');
    expect(source).toContain('restoreCustomerOwnershipAuto');
    expect(source).toContain('MarketingCustomerDetailDrawer');
    expect(source).toContain('MarketingCustomerOwnerDialog');
    expect(source).toContain('transferAction');
    expect(source).toContain('UNASSIGN');
  });

  it('展示客户主档查询字段和企业详细信息，不使用旧客户列表接口', () => {
    expect(source).toContain('统一社会信用代码');
    expect(source).toContain('主办客户经理');
    expect(source).toContain('授信敞口');
    expect(source).toContain('ownershipMaintainMode');
    expect(source).not.toContain("@/api/customerMarketing");
  });

  it('所属行业在查询表单、表格和编辑表单中复用 INDUSTRY 字典中文口径', () => {
    expect(source).toContain("useDict('INDUSTRY')");
    expect(source).toContain('v-model="query.industry"');
    expect(source).toContain('v-for="item in industryOptions"');
    expect(source).toContain('industryLabelOf(row.industry)');
    expect(source).toContain('v-model="editForm.industry"');
    expect(source).toContain('reloadIndustry()');
    expect(source).not.toContain('<el-table-column prop="industry" label="所属行业"');
    expect(source).not.toContain('<el-input v-model="editForm.industry"');
  });

  it('编辑表单支持有效营销客户标签多选并提供加载和筛选体验', () => {
    expect(source).toContain('listEditableMarketingCustomerTags');
    expect(source).toContain('label="客户标签"');
    expect(source).toContain('v-model="editForm.tagIds"');
    expect(source).toContain('multiple');
    expect(source).toContain('filterable');
    expect(source).toContain('collapse-tags');
    expect(source).toContain(':loading="tagLoading"');
    expect(source).toContain("tag.recordStatus !== 'ACTIVE'");
    expect(source).toContain('expiresAt');
  });

  it('编辑回显详情标签并在保存时保留空数组清空标签', () => {
    expect(source).toContain('tagIds: []');
    expect(source).toContain('tagIds: Array.isArray(detail?.tagIds)');
    expect(source).toContain("key === 'tagIds' ? (Array.isArray(editForm.tagIds)");
  });

  it('保存时只提交可维护字段，不夹带详情中的客户号和主办权', () => {
    expect(source).toContain('PROFILE_EDIT_FIELDS');
    expect(source).toContain('Object.fromEntries(PROFILE_EDIT_FIELDS.map');
    expect(source).not.toContain('const payload = {\n    ...editForm,');
  });
});
