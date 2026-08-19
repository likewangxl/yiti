// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('@/api/perf', () => ({
  getAdjustDetail: vi.fn(),
  getAdjustApprovalHistory: vi.fn().mockResolvedValue([]),
  getAllocPreview: vi.fn().mockResolvedValue({ allocList: [] }),
}));
vi.mock('@/api/system', () => ({
  listDictItems: vi.fn().mockResolvedValue([]),
}));

import { getAdjustDetail } from '@/api/perf';
import AllocAdjustViewDialog from '../AllocAdjustViewDialog.vue';

const passthrough = (name) => ({ name, template: '<div><slot /></div>' });
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  'el-dialog': passthrough('ElDialog'),
  'el-form': passthrough('ElForm'),
  'el-form-item': passthrough('ElFormItem'),
  'el-descriptions': passthrough('ElDescriptions'),
  'el-descriptions-item': passthrough('ElDescriptionsItem'),
  'el-row': passthrough('ElRow'),
  'el-col': passthrough('ElCol'),
  'el-select': passthrough('ElSelect'),
  'el-option': empty('ElOption'),
  'el-input': empty('ElInput'),
  'el-input-number': empty('ElInputNumber'),
  'el-autocomplete': empty('ElAutocomplete'),
  'el-table': empty('ElTable'),
  'el-table-column': empty('ElTableColumn'),
  'el-empty': empty('ElEmpty'),
  'el-timeline': passthrough('ElTimeline'),
  'el-timeline-item': passthrough('ElTimelineItem'),
  'el-tag': passthrough('ElTag'),
};

let wrapper;
afterEach(() => {
  wrapper?.unmount();
  vi.clearAllMocks();
});

async function mountDialog(detail) {
  getAdjustDetail.mockResolvedValue(detail);
  wrapper = mount(AllocAdjustViewDialog, {
    props: { modelValue: true, applyId: 'ADJ-1' },
    global: { stubs, directives: { loading: () => {} } },
  });
  await flushPromises();
  await nextTick();
  await flushPromises();
  return wrapper;
}

describe('AllocAdjustViewDialog 当前节点审批员工', () => {
  it('审批中展示当前节点可审批员工的姓名和工号', async () => {
    const view = await mountDialog({
      status: 'IN_APPROVAL',
      currentNodeApprovers: [
        { employeeName: '张三', employeeNo: '10001' },
        { employeeName: '李四', employeeNo: '10002' },
      ],
    });

    const approvers = view.find('.current-node-approvers');
    expect(approvers.exists()).toBe(true);
    expect(approvers.text()).toContain('当前节点可审批员工');
    expect(approvers.text()).toContain('张三（10001）');
    expect(approvers.text()).toContain('李四（10002）');
  });

  it('节点已审核后不展示可审批员工', async () => {
    const view = await mountDialog({
      status: 'APPROVED',
      currentNodeApprovers: [{ employeeName: '张三', employeeNo: '10001' }],
    });

    expect(view.find('.current-node-approvers').exists()).toBe(false);
  });
});
