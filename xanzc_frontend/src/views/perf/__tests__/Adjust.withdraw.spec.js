// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  submitAdjust: vi.fn(), saveDraftAdjust: vi.fn(), submitDraftAdjust: vi.fn(),
  withdrawAdjust: vi.fn().mockResolvedValue({ ok: true }), getAdjustDetail: vi.fn().mockResolvedValue({}),
  getAdjustApprovalHistory: vi.fn().mockResolvedValue([]),
  listMyAdjustTodos: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  listMyAdjustApplies: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  listMyAdjustDones: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  getAllocPreview: vi.fn().mockResolvedValue({}), getCustMasterName: vi.fn().mockResolvedValue({}),
  getCustIndexValues: vi.fn().mockResolvedValue({}), suggestEmployees: vi.fn().mockResolvedValue([]),
  suggestOrgs: vi.fn().mockResolvedValue([])
}));
const ui = vi.hoisted(() => ({
  success: vi.fn(), warning: vi.fn(), error: vi.fn(), prompt: vi.fn()
}));

vi.mock('element-plus', () => ({
  ElMessage: { success: ui.success, warning: ui.warning, error: ui.error },
  ElMessageBox: { prompt: ui.prompt, confirm: vi.fn() }
}));
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }));
vi.mock('@/components/AllocAdjustViewDialog.vue', () => ({ default: { template: '<div />' } }));
vi.mock('@/api/perf', () => api);
vi.mock('@/api/workflow', () => ({
  approveTask: vi.fn(), rejectTask: vi.fn(), claimTask: vi.fn(), getTaskDetail: vi.fn().mockResolvedValue({})
}));
vi.mock('@/api/auth', () => ({ getMyPermissions: vi.fn().mockResolvedValue({ resourceUrls: [] }) }));
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ user: {} }) }));
vi.mock('@/api/system', () => ({ listDictItems: vi.fn().mockResolvedValue([]) }));
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
  'el-table-column': {
    name: 'ElTableColumn',
    template: '<div><slot :row="{ id: \'ADJ-1\', applyNo: \'ADJ-1\', status: \'IN_APPROVAL\' }" :$index="0" /></div>'
  },
  'el-dropdown': { name: 'ElDropdown', template: '<div class="dropdown"><slot /><slot name="dropdown" /></div>' },
  'el-dropdown-menu': passthrough('ElDropdownMenu'),
  'el-dropdown-item': { name: 'ElDropdownItem', template: '<button class="dropdown-item"><slot /></button>' },
  'el-popconfirm': {
    name: 'ElPopconfirm', emits: ['confirm'],
    template: '<div class="withdraw-confirm"><slot name="reference" /><button class="withdraw-confirm-accept" @click="$emit(\'confirm\')">确认撤回</button></div>'
  }
};

let wrapper;

afterEach(() => {
  wrapper?.unmount();
  wrapper = undefined;
  vi.clearAllMocks();
  api.withdrawAdjust.mockResolvedValue({ ok: true });
});

describe('Adjust 撤回二阶段确认', () => {
  it('未触发 Popconfirm 的确认事件时不收集原因也不发撤回；确认后才进入原因输入和写请求', async () => {
    ui.prompt.mockResolvedValue({ value: '提交信息有误' });
    wrapper = mount(Adjust, { global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } } });
    await flushPromises();

    expect(wrapper.find('.withdraw-confirm').exists()).toBe(true);
    expect(ui.prompt).not.toHaveBeenCalled();
    expect(api.withdrawAdjust).not.toHaveBeenCalled();

    await wrapper.get('.withdraw-confirm-accept').trigger('click');
    await flushPromises();

    expect(ui.prompt).toHaveBeenCalledWith('请填写撤回原因', '撤回申请', expect.objectContaining({ type: 'warning' }));
    expect(api.withdrawAdjust).toHaveBeenCalledWith('ADJ-1', '提交信息有误');
  });
});
