// @vitest-environment happy-dom
// 审批流监控「转交/指派」按钮开关回归（2026-07-20）：
// 未签收的候选组任务（currentAssignee 为空，如机构负责人会签）此前被 canTransfer 卡死点不动，
// 导致秘书岗无法分派；现已放开为「指派」，按钮可用且文案区分。
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}));

vi.mock('@/api/workflow', () => ({
  monitorProcesses: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  getProcessInfo: vi.fn().mockResolvedValue({}),
  getProcessHistory: vi.fn().mockResolvedValue([]),
  getProcessNodes: vi.fn().mockResolvedValue([]),
  processTransferHistory: vi.fn().mockResolvedValue([])
}));

vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }));

import WorkflowMonitor from '../WorkflowMonitor.vue';

const passthrough = (name) => ({ name, template: '<div><slot /><slot name="header" /><slot name="footer" /></div>' });
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  PageTitle: passthrough('PageTitle'),
  TransferDialog: empty('TransferDialog'),
  'el-button': { name: 'ElButton', emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-card': passthrough('ElCard'),
  'el-drawer': { name: 'ElDrawer', props: ['modelValue'], template: '<div v-if="modelValue"><slot /></div>' },
  'el-form': passthrough('ElForm'),
  'el-form-item': passthrough('ElFormItem'),
  'el-input': empty('ElInput'),
  'el-select': empty('ElSelect'),
  'el-option': empty('ElOption'),
  'el-date-picker': empty('ElDatePicker'),
  'el-table': { name: 'ElTable', props: ['data'], template: '<div><slot /></div>' },
  'el-table-column': empty('ElTableColumn'),
  'el-pagination': empty('ElPagination'),
  'el-tag': passthrough('ElTag'),
  'el-descriptions': passthrough('ElDescriptions'),
  'el-descriptions-item': passthrough('ElDescriptionsItem'),
  'el-timeline': passthrough('ElTimeline'),
  'el-timeline-item': passthrough('ElTimelineItem'),
  'el-empty': empty('ElEmpty'),
  'el-image': empty('ElImage'),
  'el-tabs': passthrough('ElTabs'),
  'el-tab-pane': passthrough('ElTabPane')
};

function mountPage() {
  return mount(WorkflowMonitor, { global: { stubs } });
}

/** 未签收的候选组任务行（机构负责人会签，尚无人认领） */
const unclaimedRow = {
  processInstanceId: 'PID_1',
  businessKey: 'ALLOC_ADJUST:abc',
  processStatus: 'RUNNING',
  currentTaskId: 'TASK_1',
  currentAssignee: null
};

/** 已签收行（有明确办理人） */
const claimedRow = { ...unclaimedRow, currentAssignee: 'sunbq' };

beforeEach(() => vi.clearAllMocks());

describe('WorkflowMonitor 转交/指派按钮', () => {
  it('未签收的候选组任务：按钮可用（此前被禁用，秘书岗无法分派）', async () => {
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.vm.canTransfer(unclaimedRow)).toBe(true);
  });

  it('未签收时按钮文案为「指派」，已签收为「转交」', async () => {
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.vm.transferLabel(unclaimedRow)).toBe('指派');
    expect(wrapper.vm.transferLabel(claimedRow)).toBe('转交');
  });

  it('流程已结束或无活跃任务：仍不可操作', async () => {
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.vm.canTransfer({ ...unclaimedRow, processStatus: 'COMPLETED' })).toBe(false);
    expect(wrapper.vm.canTransfer({ ...unclaimedRow, currentTaskId: null })).toBe(false);
  });

  it('打开弹窗时透传 currentAssignee，供弹窗区分转交/指派文案', async () => {
    const wrapper = mountPage();
    await flushPromises();

    wrapper.vm.openTransfer(unclaimedRow);
    expect(wrapper.vm.transferDlg.task).toMatchObject({
      taskId: 'TASK_1',
      businessKey: 'ALLOC_ADJUST:abc',
      currentAssignee: null
    });
    expect(wrapper.vm.transferDlg.show).toBe(true);
  });
});
