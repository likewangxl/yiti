// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  createLead: vi.fn(),
  deleteLead: vi.fn(),
  getLead: vi.fn(),
  listEnabledTags: vi.fn(),
  listLeads: vi.fn(),
  lookupLeadMainManager: vi.fn(),
  submitLead: vi.fn(),
  updateLead: vi.fn(),
  uploadLeadAttachment: vi.fn(),
  previewLeadImport: vi.fn(),
  executeLeadImport: vi.fn()
}));
const employees = vi.hoisted(() => ({ searchEmployees: vi.fn() }));
const message = vi.hoisted(() => ({ success: vi.fn(), info: vi.fn(), warning: vi.fn(), error: vi.fn() }));

vi.mock('@/api/customerMarketing', () => api);
vi.mock('@/api/employees', () => employees);
vi.mock('@/composables/useDict', () => ({ useDict: () => ({ options: [] }) }));
vi.mock('element-plus', () => ({ ElMessage: message, ElMessageBox: { confirm: vi.fn() } }));

import LeadEntry from '../LeadEntry.vue';

const passthrough = (name, template = '<div><slot /></div>') => ({ name, inheritAttrs: false, template });
const stubs = {
  PageTitle: { template: '<h1>线索录入</h1>' },
  LeadDetailDrawer: passthrough('LeadDetailDrawer'),
  'el-alert': { props: ['title'], template: '<div class="alert">{{ title }}</div>' },
  'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-card': passthrough('ElCard'),
  'el-dialog': { props: ['modelValue'], template: '<div v-if="modelValue" class="dialog"><slot /><slot name="footer" /></div>' },
  'el-descriptions': passthrough('ElDescriptions'),
  'el-descriptions-item': passthrough('ElDescriptionsItem'),
  'el-form': passthrough('ElForm', '<form><slot /></form>'),
  'el-form-item': passthrough('ElFormItem'),
  'el-input': passthrough('ElInput'),
  'el-input-number': passthrough('ElInputNumber'),
  'el-option': passthrough('ElOption'),
  'el-radio': passthrough('ElRadio'),
  'el-radio-button': passthrough('ElRadioButton'),
  'el-radio-group': passthrough('ElRadioGroup'),
  'el-select': passthrough('ElSelect'),
  'el-table': passthrough('ElTable'),
  'el-table-column': { template: '<div />' },
  'el-tag': passthrough('ElTag'),
  'el-pagination': passthrough('ElPagination'),
  'el-upload': passthrough('ElUpload')
};

describe('LeadEntry CCRM 临时关闭态', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.listLeads.mockResolvedValue({ records: [], total: 0 });
    api.listEnabledTags.mockResolvedValue([]);
  });

  it('打开新建弹窗后隐藏存量客户主办查询和主办专属，并阻止 CCRM 查询', async () => {
    const wrapper = mount(LeadEntry, {
      global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } }
    });
    await flushPromises();

    await wrapper.vm.openCreate();
    await flushPromises();

    expect(wrapper.text()).not.toContain('存量客户与主办权查询');
    expect(wrapper.text()).not.toContain('查询主办权');
    expect(wrapper.text()).not.toContain('主办专属');
    expect(wrapper.text()).toContain('全行公开认领');
    expect(wrapper.text()).toContain('指定客户经理范围');

    wrapper.vm.form.distributionMode = 'OWNER';
    await flushPromises();
    expect(wrapper.text()).not.toContain('审批通过后为存量客户主办人建立认领关系并自动生成首次触达任务。');

    wrapper.vm.form.custName = '测试客户';
    wrapper.vm.form.unifiedCreditCode = '91310000MA00000000';
    await wrapper.vm.lookupOwnership();
    expect(api.lookupLeadMainManager).not.toHaveBeenCalled();

    wrapper.unmount();
  });

  it('新建表单默认开启触达限制，保存 payload 携带 touchRestricted', async () => {
    const wrapper = mount(LeadEntry, {
      global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } }
    });
    await flushPromises();

    await wrapper.vm.openCreate();
    expect(wrapper.vm.form.touchRestricted).toBe(1);
    expect(wrapper.vm.rules.touchRestricted.some(rule => rule.required)).toBe(true);

    wrapper.vm.form.touchRestricted = 0;
    expect(wrapper.vm.payload([]).touchRestricted).toBe(0);

    wrapper.unmount();
  });

  it('批量导入入口明确要求每行填写是否触达限制是或否', async () => {
    const wrapper = mount(LeadEntry, {
      global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } }
    });
    await flushPromises();

    wrapper.vm.openImport();
    await flushPromises();

    expect(wrapper.text()).toContain('批量导入线索');
    expect(wrapper.text()).toContain('是否触达限制');
    expect(wrapper.text()).toContain('每行只能填写“是”或“否”');
    expect(wrapper.text()).not.toContain('统一触达限制开关');

    wrapper.unmount();
  });

  it('实际保存时新建和编辑 payload 都携带触达限制值', async () => {
    api.createLead.mockResolvedValue('LEAD-NEW');
    api.updateLead.mockResolvedValue({});
    api.getLead.mockResolvedValue({ id: 'LEAD-1', touchRestricted: 0 });
    const wrapper = mount(LeadEntry, {
      global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } }
    });
    await flushPromises();

    await wrapper.vm.openCreate();
    wrapper.vm.formRef = { validate: vi.fn().mockResolvedValue(true) };
    await wrapper.vm.save(false);
    expect(api.createLead).toHaveBeenCalledWith(expect.objectContaining({ touchRestricted: 1 }));

    await wrapper.vm.openEdit({ id: 'LEAD-1' });
    wrapper.vm.formRef = { validate: vi.fn().mockResolvedValue(true) };
    await wrapper.vm.save(false);
    expect(api.updateLead).toHaveBeenCalledWith('LEAD-1', expect.objectContaining({ touchRestricted: 0 }));

    wrapper.unmount();
  });

  it('预览存在错误行时禁止执行导入', async () => {
    const file = new File(['客户名称,是否触达限制\n华夏科技,其他'], '线索导入.xlsx');
    api.previewLeadImport.mockResolvedValue({ batchId: 'BATCH-1', totalRows: 1, errorRows: 1, failCount: 1 });
    const wrapper = mount(LeadEntry, {
      global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } }
    });
    await flushPromises();

    wrapper.vm.openImport();
    wrapper.vm.onImportFileChange({ raw: file }, [{ raw: file, name: file.name }]);
    await wrapper.vm.previewImport();

    expect(api.previewLeadImport).toHaveBeenCalledWith(file);
    expect(wrapper.vm.canExecuteImport).toBe(false);
    expect(await wrapper.vm.executeImport()).toBe(false);
    expect(api.executeLeadImport).not.toHaveBeenCalled();

    wrapper.unmount();
  });
});
