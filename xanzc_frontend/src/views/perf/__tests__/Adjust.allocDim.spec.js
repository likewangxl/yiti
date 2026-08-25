// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  submitAdjust: vi.fn().mockResolvedValue({ id: 'ADJ-1' }),
  saveDraftAdjust: vi.fn().mockResolvedValue({ id: 'DRAFT-1', status: 'DRAFT' }),
  submitDraftAdjust: vi.fn().mockResolvedValue({ id: 'DRAFT-1', status: 'IN_APPROVAL' }),
  withdrawAdjust: vi.fn().mockResolvedValue({ ok: true }),
  getAdjustDetail: vi.fn().mockResolvedValue({}),
  getAdjustApprovalHistory: vi.fn().mockResolvedValue([]),
  listMyAdjustTodos: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  listMyAdjustApplies: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  listMyAdjustDones: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  getAllocPreview: vi.fn().mockResolvedValue({ allocList: [] }),
  getCustMasterName: vi.fn().mockResolvedValue({ found: false }),
  getCustIndexValues: vi.fn().mockResolvedValue({}),
  suggestEmployees: vi.fn().mockResolvedValue([]),
  suggestOrgs: vi.fn().mockResolvedValue([])
}));
const dict = vi.hoisted(() => ({
  listDictItems: vi.fn((type) => Promise.resolve(type === 'PERF_ALLOC_DIM'
    ? [
        { dictCode: 'RULE', dictLabel: '按规则分配' },
        { dictCode: 'ACCOUNT', dictLabel: '按账户分配' },
        { dictCode: 'NEW', dictLabel: '新开户分配' }
      ]
    : [
        { dictCode: 'CORP_DEPOSIT', dictLabel: '对公存款' },
        { dictCode: 'CORP_LOAN', dictLabel: '对公贷款' }
      ]))
}));
const ui = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn(), error: vi.fn() }));

vi.mock('element-plus', () => ({
  ElMessage: { success: ui.success, warning: ui.warning, error: ui.error },
  ElMessageBox: { prompt: vi.fn(), confirm: vi.fn() }
}));
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }));
vi.mock('@/components/AllocAdjustViewDialog.vue', () => ({ default: { template: '<div />' } }));
vi.mock('@/api/perf', () => api);
vi.mock('@/api/workflow', () => ({
  approveTask: vi.fn(), rejectTask: vi.fn(), claimTask: vi.fn(), getTaskDetail: vi.fn().mockResolvedValue({})
}));
vi.mock('@/api/auth', () => ({ getMyPermissions: vi.fn().mockResolvedValue({ resourceUrls: [] }) }));
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ user: {} }) }));
vi.mock('@/api/system', () => dict);
vi.mock('@/utils/datetime', () => ({ fmtDateTime: (value) => String(value || '-') }));

import Adjust from '../Adjust.vue';

const passthrough = (name) => ({ name, template: '<div><slot /><slot name="footer" /></div>' });
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  PageTitle: passthrough('PageTitle'),
  'el-button': { name: 'ElButton', emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-tabs': passthrough('ElTabs'),
  'el-tab-pane': passthrough('ElTabPane'),
  'el-form': passthrough('ElForm'),
  'el-form-item': passthrough('ElFormItem'),
  'el-input': empty('ElInput'),
  'el-select': passthrough('ElSelect'),
  'el-option': empty('ElOption'),
  'el-date-picker': empty('ElDatePicker'),
  'el-input-number': empty('ElInputNumber'),
  'el-autocomplete': empty('ElAutocomplete'),
  'el-empty': empty('ElEmpty'),
  'el-radio-group': passthrough('ElRadioGroup'),
  'el-radio': passthrough('ElRadio'),
  'el-col': passthrough('ElCol'),
  'el-row': passthrough('ElRow'),
  'el-descriptions': passthrough('ElDescriptions'),
  'el-descriptions-item': passthrough('ElDescriptionsItem'),
  'el-timeline': passthrough('ElTimeline'),
  'el-timeline-item': passthrough('ElTimelineItem'),
  'el-alert': empty('ElAlert'),
  'el-tag': passthrough('ElTag'),
  'el-pagination': empty('ElPagination'),
  'el-dialog': { name: 'ElDialog', props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
  'el-table': passthrough('ElTable'),
  'el-table-column': { name: 'ElTableColumn', template: '<div><slot :row="{ id: \'ADJ-1\', applyNo: \'ADJ-1\', status: \'DRAFT\' }" :$index="0" /></div>' },
  'el-dropdown': { name: 'ElDropdown', template: '<div><slot /><slot name="dropdown" /></div>' },
  'el-dropdown-menu': passthrough('ElDropdownMenu'),
  'el-dropdown-item': { name: 'ElDropdownItem', template: '<button class="dropdown-item"><slot /></button>' },
  BpAdaptiveRowActions: passthrough('BpAdaptiveRowActions')
};

let wrapper;
afterEach(() => {
  wrapper?.unmount();
  wrapper = undefined;
  vi.clearAllMocks();
  api.getAdjustDetail.mockResolvedValue({});
  api.getAllocPreview.mockResolvedValue({ allocList: [] });
  api.saveDraftAdjust.mockResolvedValue({ id: 'DRAFT-1', status: 'DRAFT' });
  dict.listDictItems.mockImplementation((type) => Promise.resolve(type === 'PERF_ALLOC_DIM'
    ? [
        { dictCode: 'RULE', dictLabel: '按规则分配' },
        { dictCode: 'ACCOUNT', dictLabel: '按账户分配' },
        { dictCode: 'NEW', dictLabel: '新开户分配' }
      ]
    : [
        { dictCode: 'CORP_DEPOSIT', dictLabel: '对公存款' },
        { dictCode: 'CORP_LOAN', dictLabel: '对公贷款' }
      ]));
});

async function mountPage() {
  wrapper = mount(Adjust, { global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } } });
  await flushPromises();
  return wrapper;
}

describe('业绩调整分配维度与原分配关系', () => {
  it('从 PERF_ALLOC_DIM 加载 NEW，并使用同一映射生成标签；接口失败保留三项兜底', async () => {
    await mountPage();

    expect(dict.listDictItems).toHaveBeenCalledWith('PERF_ALLOC_DIM');
    expect(wrapper.vm.allocDimOptions).toEqual([
      { value: 'RULE', label: '按规则分配' },
      { value: 'ACCOUNT', label: '按账户分配' },
      { value: 'NEW', label: '新开户分配' }
    ]);
    expect(wrapper.vm.allocDimLabel('NEW')).toBe('新开户分配');

    dict.listDictItems.mockRejectedValueOnce(new Error('dictionary unavailable'));
    await wrapper.vm.loadAllocDimDict();
    expect(wrapper.vm.allocDimOptions).toEqual([
      { value: 'RULE', label: '按规则分配' },
      { value: 'ACCOUNT', label: '按账户分配' },
      { value: 'NEW', label: '新开户分配' }
    ]);
  });

  it('客户类型切换为零售时联动 ACCOUNT，但维度选项仍保留 RULE/ACCOUNT/NEW', async () => {
    await mountPage();
    wrapper.vm.openCreate();
    wrapper.vm.dlg.form.allocDim = 'RULE';
    wrapper.vm.dlg.form.bizKind = ['CORP_LOAN'];

    wrapper.vm.onCustTypeChange('RETAIL');

    expect(wrapper.vm.dlg.form.allocDim).toBe('ACCOUNT');
    expect(wrapper.vm.dlg.form.bizKind).toEqual(['CORP_DEPOSIT']);
    expect(wrapper.vm.allocDimOptions.map((item) => item.value)).toEqual(['RULE', 'ACCOUNT', 'NEW']);
  });

  it('加载已有零售草稿时保留详情中的分配维度，不触发零售默认联动覆盖', async () => {
    api.getAdjustDetail.mockResolvedValueOnce({
      id: 'DRAFT-RETAIL', status: 'DRAFT', custType: 'RETAIL', custId: 'R-001',
      custName: '零售客户', allocDim: 'NEW', bizKind: 'CORP_DEPOSIT', items: []
    });

    await mountPage();
    await wrapper.vm.openEdit({ id: 'DRAFT-RETAIL', status: 'DRAFT', custType: 'RETAIL', allocDim: 'NEW' });
    await flushPromises();

    expect(wrapper.vm.dlg.form.custType).toBe('RETAIL');
    expect(wrapper.vm.dlg.form.allocDim).toBe('NEW');
    expect(api.getAllocPreview).not.toHaveBeenCalled();
  });

  it('原分配员工选择后自动带出 mainOrgCode/mainOrgName，机构仍可继续手工选择', async () => {
    await mountPage();
    const row = { empId: '', empLabel: '', username: '', empChnName: '', orgCode: '', orgName: '', orgLabel: '' };

    wrapper.vm.onOrigEmpSelect(row, {
      username: 'E001', empChnName: '张三', mainOrgCode: 'ORG-01', mainOrgName: '南山支行', label: 'E001（张三）'
    });

    expect(row).toMatchObject({ empId: 'E001', orgCode: 'ORG-01', orgName: '南山支行', orgLabel: 'ORG-01（南山支行）' });
  });

  it('NEW 不展示原分配、不会预览，也不会要求或提交 originalAllocList', async () => {
    await mountPage();
    wrapper.vm.openCreate();
    Object.assign(wrapper.vm.dlg.form, {
      custType: 'CORP', custId: 'C-NEW', custName: '新客户', allocDim: 'NEW',
      bizKind: ['CORP_DEPOSIT'], reason: '新开户调整',
      items: [{ empId: 'E001', pct: 100, remark: '', empLabel: 'E001' }],
      originalItems: [{ empId: 'OLD', orgCode: 'ORG-OLD', ratio: 100 }]
    });

    await wrapper.vm.loadPreview();
    expect(api.getAllocPreview).not.toHaveBeenCalled();
    expect(wrapper.vm.showOriginalAllocation).toBe(false);
    expect(wrapper.vm.buildAdjustPayload().originalAllocList).toEqual([]);

    wrapper.vm.dlgFormRef = { validate: vi.fn().mockResolvedValue(true) };
    api.submitAdjust.mockClear();
    await wrapper.vm.onSubmit();
    expect(api.submitAdjust).toHaveBeenCalledWith(expect.objectContaining({
      allocDim: 'NEW', originalAllocList: []
    }));
  });

  it('编辑草稿时保留详情 ORIGIN 行为可编辑 originalItems，修改后草稿 payload 持久化', async () => {
    api.getAdjustDetail.mockResolvedValueOnce({
      id: 'DRAFT-1', status: 'DRAFT', custType: 'CORP', custId: 'C-001', custName: '客户一',
      allocDim: 'RULE', bizKind: 'CORP_DEPOSIT', reason: '调整', createdTime: '2026-08-24T10:00:00',
      items: [
        { itemKind: 'ORIGIN', acctNo: 'A-1', empId: 'E-OLD', username: 'E-OLD', empChnName: '旧员工', orgCode: 'ORG-OLD', orgName: '旧机构', ratio: 40 },
        { itemKind: 'NEW', empId: 'E-NEW', pct: 100, remark: '' }
      ]
    });
    api.getAllocPreview.mockResolvedValueOnce({
      allocList: [{ acctNo: 'A-PREVIEW', username: 'E-PREVIEW', orgCode: 'ORG-PREVIEW', ratio: 100 }]
    });

    await mountPage();
    await wrapper.vm.openEdit({ id: 'DRAFT-1', status: 'DRAFT', custId: 'C-001', allocDim: 'RULE' });
    await flushPromises();

    expect(wrapper.vm.dlg.form.originalItems).toHaveLength(1);
    expect(wrapper.vm.dlg.form.originalItems[0]).toMatchObject({ empId: 'E-OLD', orgCode: 'ORG-OLD', ratio: 40 });
    expect(wrapper.vm.showOriginalAllocation).toBe(true);
    expect(wrapper.vm.hasOriginalOwners).toBe(false);

    Object.assign(wrapper.vm.dlg.form.originalItems[0], { orgCode: 'ORG-NEW', orgName: '新机构', ratio: 60 });
    await wrapper.vm.onSaveDraft();

    expect(api.saveDraftAdjust).toHaveBeenCalledWith(expect.objectContaining({
      id: 'DRAFT-1',
      originalAllocList: [expect.objectContaining({ empId: 'E-OLD', orgCode: 'ORG-NEW', orgName: '新机构', ratio: 60 })]
    }));
  });
});
