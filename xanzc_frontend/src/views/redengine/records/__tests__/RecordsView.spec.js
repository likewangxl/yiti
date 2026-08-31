// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}));

const routerPush = vi.fn();
const routerResolve = vi.fn((location) => ({
  href: `#/redengine/task-entry?assignmentId=${location.query.assignmentId}`
}));
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush, resolve: routerResolve })
}));

vi.mock('@/api/redengine', () => ({
  getMySubmits: vi.fn(),
  listMyTaskAssignments: vi.fn()
}));

import { getMySubmits, listMyTaskAssignments } from '@/api/redengine';
import RecordsView from '../RecordsView.vue';

const stubs = {
  'el-radio-group': { props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot /></div>' },
  'el-radio-button': { props: ['label'], template: '<button class="tab-button"><slot />{{ label }}</button>' },
  'el-input': {
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue', 'keyup'],
    template: '<input :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-select': {
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue'],
    template: '<select :value="modelValue" :data-placeholder="placeholder" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>'
  },
  'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  'el-button': { props: ['loading', 'disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
  'el-table': { props: ['data'], template: '<div class="table-stub"><slot /></div>' },
  'el-table-column': { props: ['label', 'prop'], template: '<div class="column-stub" :data-label="label" />' },
  'el-pagination': {
    props: ['currentPage', 'pageSize', 'total'],
    emits: ['current-change', 'size-change'],
    template: '<div class="pagination-stub" :data-total="total" />'
  },
  'el-empty': { template: '<div class="empty-stub"><slot /></div>' }
};

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

function temporaryAssignment(overrides = {}) {
  return {
    assignmentId: 1001,
    taskId: 42,
    taskTitle: '专项整改任务',
    taskDescription: '请填报整改情况 https://example.com/guide',
    taskNature: 'TEMPORARY',
    businessType: 'GENERAL',
    cycleType: null,
    status: 'UNREPORTED',
    requiresFile: true,
    fileTypeCodes: ['PDF'],
    maxFileSizeBytes: 5 * 1024 * 1024,
    ...overrides
  };
}

describe('报送员上报信息', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getMySubmits.mockResolvedValue({
      records: [
        { id: 11, dimension: 'dim1', itemCode: 'JC-1', itemName: '联建规范度', submitterId: 'U1', submitDate: '2026-08-21', status: 1 },
        { id: 12, dimension: 'dim2', itemCode: 'HZ-1', itemName: '合作契约化', submitterId: 'U1', submitDate: '2026-08-20', status: 3, reviewFeedback: '请补充佐证材料' }
      ],
      total: 2
    });
    listMyTaskAssignments.mockResolvedValue({ records: [temporaryAssignment()], total: 1 });
  });

  it('改名为上报信息并提供待处理、审核中、已通过、已驳回四个页签', async () => {
    const wrapper = mount(RecordsView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    expect(wrapper.find('.page-title').text()).toBe('上报信息');
    expect(wrapper.text()).toContain('待处理');
    expect(wrapper.text()).toContain('审核中');
    expect(wrapper.text()).toContain('已通过');
    expect(wrapper.text()).toContain('已驳回');
    expect(wrapper.text()).not.toContain('待审核');
    wrapper.unmount();
  });

  it('待处理列表携带任务标题、性质和周期查询条件，并将未上报任务归入待处理', async () => {
    const wrapper = mount(RecordsView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    expect(listMyTaskAssignments).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 10,
      status: 'UNREPORTED'
    });
    expect(wrapper.vm.records[0]).toMatchObject({
      assignmentId: 1001,
      title: '专项整改任务',
      nature: 'TEMPORARY',
      status: 'pending',
      isTemporary: true
    });

    wrapper.vm.query.title = '整改';
    wrapper.vm.query.nature = 'TEMPORARY';
    wrapper.vm.query.cycle = 'MONTH_END';
    await wrapper.vm.handleSearch();
    await settle();

    expect(listMyTaskAssignments).toHaveBeenLastCalledWith({
      pageNo: 1,
      pageSize: 10,
      status: 'UNREPORTED',
      title: '整改',
      taskNature: 'TEMPORARY',
      cycleType: 'MONTH_END'
    });
    wrapper.unmount();
  });

  it('临时任务标题在新窗口打开填报入口，四维任务保留材料上报跳转意图', async () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
    const wrapper = mount(RecordsView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    wrapper.vm.openRow(temporaryAssignment());
    expect(routerResolve).toHaveBeenCalledWith({
      path: '/redengine/task-entry',
      query: { taskId: 42, assignmentId: 1001 }
    });
    expect(openSpy).toHaveBeenCalledWith('#/redengine/task-entry?assignmentId=1001', '_blank', 'noopener,noreferrer');

    wrapper.vm.openRow({ taskId: 99, assignmentId: 1002, businessType: 'FOUR_DIMENSION', isFourDimension: true });
    expect(routerPush).toHaveBeenCalledWith({
      path: '/redengine/report',
      query: { taskId: 99, assignmentId: 1002 }
    });
    openSpy.mockRestore();
    wrapper.unmount();
  });

  it('说明中的网页链接使用新窗口和 noopener 安全属性', () => {
    const wrapper = mount(RecordsView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    const parts = wrapper.vm.descriptionParts('说明 https://example.com/a?x=1。');
    expect(parts).toContainEqual({
      type: 'link',
      value: 'https://example.com/a?x=1',
      href: 'https://example.com/a?x=1',
      target: '_blank',
      rel: 'noreferrer noopener'
    });
    wrapper.unmount();
  });

  it('临时任务历史隐藏审核状态、审核意见和得分，但驳回意见仍可读', async () => {
    getMySubmits.mockResolvedValue({ records: [], total: 0 });
    listMyTaskAssignments.mockResolvedValue({
      records: [temporaryAssignment({ status: 'REJECTED_BY_ORG', reviewFeedback: '请重新补充附件' })],
      total: 1
    });
    const wrapper = mount(RecordsView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();
    wrapper.vm.activeTab = 'rejected';
    await wrapper.vm.reload();
    await settle();

    expect(wrapper.vm.records[0]).toMatchObject({ status: 'rejected', feedback: '请重新补充附件', isTemporary: true });
    expect(wrapper.vm.showMaterialColumns).toBe(false);
    expect(wrapper.vm.showTemporaryFeedback).toBe(true);
    wrapper.unmount();
  });

  it('审核中页同时保留原四维上报记录和临时任务记录', async () => {
    listMyTaskAssignments.mockResolvedValue({
      records: [temporaryAssignment({ status: 'ORG_PENDING' })],
      total: 1
    });
    const wrapper = mount(RecordsView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();
    wrapper.vm.activeTab = 'reviewing';
    await wrapper.vm.reload();
    await settle();

    expect(getMySubmits).toHaveBeenCalledWith(1, 10);
    expect(wrapper.vm.records.map((item) => item.source)).toEqual(expect.arrayContaining(['material', 'task']));
    expect(wrapper.vm.records.find((item) => item.source === 'material')).toMatchObject({
      dimension: 'dim1',
      status: 'reviewing',
      isTemporary: false
    });
    wrapper.unmount();
  });
});
