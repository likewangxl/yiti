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
  'el-empty': { props: ['description'], template: '<div class="empty-stub">{{ description }}<slot /></div>' }
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

function recurringGeneralAssignment(overrides = {}) {
  return temporaryAssignment({
    assignmentId: 1005,
    taskId: 5,
    taskTitle: '每月初经营分析填报',
    taskDescription: '请按每月初要求填报经营分析',
    taskNature: 'RECURRING',
    businessType: 'GENERAL',
    cycleType: 'MONTH_START',
    ...overrides
  });
}

const historyTabCases = [
  { tab: 'reviewing', status: 'ORG_PENDING', label: '审核中' },
  { tab: 'passed', status: 'APPROVED', label: '已通过' },
  { tab: 'rejected', status: 'REJECTED_BY_ORG', label: '已驳回' }
];
const taskHistoryColumns = ['审核状态', '得分', '审核意见', '驳回意见'];

function renderedColumnLabels(wrapper) {
  return wrapper.findAll('.column-stub').map((column) => column.attributes('data-label'));
}

function expectTaskHistoryColumnsHidden(wrapper) {
  const labels = renderedColumnLabels(wrapper);
  for (const label of taskHistoryColumns) expect(labels).not.toContain(label);
}

describe('报送员任务处理', () => {
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

  it('改名为任务处理并提供待处理、审核中、已通过、已驳回四个页签', async () => {
    const wrapper = mount(RecordsView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    expect(wrapper.find('.page-title').text()).toBe('任务处理');
    expect(wrapper.text()).not.toContain('上报信息');
    expect(wrapper.text()).toContain('待处理');
    expect(wrapper.text()).toContain('审核中');
    expect(wrapper.text()).toContain('已通过');
    expect(wrapper.text()).toContain('已驳回');
    expect(wrapper.text()).not.toContain('待审核');
    wrapper.unmount();
  });

  it('空态文案同步为暂无任务处理记录', async () => {
    listMyTaskAssignments.mockResolvedValue({ records: [], total: 0 });
    const wrapper = mount(RecordsView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    expect(wrapper.find('.empty-stub').text()).toBe('暂无任务处理记录');
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
      tab: 'PENDING'
    });
    expect(wrapper.vm.records[0]).toMatchObject({
      assignmentId: 1001,
      title: '专项整改任务',
      nature: 'TEMPORARY',
      status: 'pending',
      isFourDimension: false
    });

    wrapper.vm.query.title = '整改';
    wrapper.vm.query.nature = 'TEMPORARY';
    wrapper.vm.query.cycle = 'MONTH_END';
    await wrapper.vm.handleSearch();
    await settle();

    expect(listMyTaskAssignments).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 10,
      tab: 'PENDING',
      title: '整改',
      taskNature: 'TEMPORARY',
      cycleType: 'MONTH_END'
    });
    wrapper.unmount();
  });

  it('普通定时任务的维度显示普通任务，性质和周期仍按定时任务展示', async () => {
    listMyTaskAssignments.mockResolvedValueOnce({
      records: [recurringGeneralAssignment()],
      total: 1
    });
    const wrapper = mount(RecordsView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    const row = wrapper.vm.records[0];
    expect(row).toMatchObject({
      nature: 'RECURRING',
      cycle: 'MONTH_START',
      isPeriodic: true,
      isFourDimension: false
    });
    expect(wrapper.vm.taskNatureLabel(row.nature)).toBe('定时任务');
    expect(wrapper.vm.dimensionLabel(row)).toBe('普通任务');
    expect(wrapper.vm.cycleLabel(row.cycle)).toBe('每月初');
    wrapper.unmount();
  });

  it('临时任务标题在同一 SPA 页签打开填报入口，四维任务保留材料上报跳转意图', async () => {
    const wrapper = mount(RecordsView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    wrapper.vm.openRow(temporaryAssignment());
    expect(routerPush).toHaveBeenCalledWith({
      path: '/redengine/task-entry',
      query: { taskId: 42, assignmentId: 1001, tab: 'pending', status: 'UNREPORTED' }
    });
    expect(routerResolve).not.toHaveBeenCalled();

    wrapper.vm.openRow({ taskId: 99, assignmentId: 1002, businessType: 'FOUR_DIMENSION', isFourDimension: true });
    expect(routerPush).toHaveBeenCalledWith({
      path: '/redengine/report',
      query: { taskId: 99, assignmentId: 1002 }
    });
    wrapper.unmount();
  });

  it('审核中普通任务点击仍在同一 SPA 页签打开并带上当前状态上下文', async () => {
    const wrapper = mount(RecordsView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    wrapper.vm.activeTab = 'reviewing';
    wrapper.vm.openRow(temporaryAssignment({ status: 'ORG_PENDING' }));
    expect(routerPush).toHaveBeenCalledWith({
      path: '/redengine/task-entry',
      query: { taskId: 42, assignmentId: 1001, tab: 'reviewing', status: 'ORG_PENDING' }
    });
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

  it.each(historyTabCases)('$label页签不渲染临时任务的审核和评分列', async ({ tab, status }) => {
    getMySubmits.mockResolvedValue({ records: [], total: 0 });
    listMyTaskAssignments.mockResolvedValue({
      records: [temporaryAssignment({ status, reviewFeedback: '请重新补充附件' })],
      total: 1
    });
    const wrapper = mount(RecordsView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();
    wrapper.vm.activeTab = tab;
    await wrapper.vm.reload();
    await settle();

    expect(wrapper.vm.records[0]).toMatchObject({ status: tab, nature: 'TEMPORARY', isFourDimension: false });
    expectTaskHistoryColumnsHidden(wrapper);
    wrapper.unmount();
  });

  it.each(historyTabCases)('$label页签不渲染定时普通任务的审核和评分列', async ({ tab, status }) => {
    getMySubmits.mockResolvedValue({ records: [], total: 0 });
    listMyTaskAssignments.mockResolvedValue({
      records: [recurringGeneralAssignment({ status })],
      total: 1
    });
    const wrapper = mount(RecordsView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();
    wrapper.vm.activeTab = tab;
    await wrapper.vm.reload();
    await settle();

    expect(wrapper.vm.records[0]).toMatchObject({ status: tab, nature: 'RECURRING', isPeriodic: true, isFourDimension: false });
    expectTaskHistoryColumnsHidden(wrapper);
    wrapper.unmount();
  });

  it('审核中页同时保留原四维上报记录和临时任务记录', async () => {
    listMyTaskAssignments.mockResolvedValue({
      records: [
        temporaryAssignment({ status: 'ORG_PENDING' }),
        {
          source: 'material',
          id: 11,
          dimension: 'dim1',
          itemCode: '1.1',
          itemName: '联建规范度',
          submitterId: 'U1',
          submitDate: '2026-08-21',
          status: 1
        }
      ],
      total: 2
    });
    const wrapper = mount(RecordsView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();
    wrapper.vm.activeTab = 'reviewing';
    await wrapper.vm.reload();
    await settle();

    expect(getMySubmits).not.toHaveBeenCalled();
    expect(wrapper.vm.records.map((item) => item.source)).toEqual(expect.arrayContaining(['material', 'task']));
    expect(wrapper.vm.records.find((item) => item.source === 'material')).toMatchObject({
      dimension: 'dim1',
      status: 'reviewing',
      isFourDimension: true
    });
    expect(wrapper.vm.showMaterialColumns).toBe(true);
    expect(renderedColumnLabels(wrapper)).toEqual(expect.arrayContaining(['审核状态', '得分', '审核意见']));
    expect(renderedColumnLabels(wrapper)).not.toContain('驳回意见');
    wrapper.unmount();
  });

  it('任务或材料列表请求失败时展示错误态而不是误报暂无任务处理记录', async () => {
    listMyTaskAssignments.mockRejectedValueOnce(new Error('服务不可用'));
    const wrapper = mount(RecordsView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    expect(wrapper.vm.loadError).toContain('任务数据加载失败');
    expect(wrapper.find('[role="alert"]').text()).toContain('任务数据加载失败');
    expect(wrapper.find('.empty-stub').exists()).toBe(false);
    wrapper.unmount();
  });

  it('按服务端页签和状态分页，不再另查旧材料列表后在前端合并总数', async () => {
    listMyTaskAssignments.mockResolvedValue({
      records: [temporaryAssignment({ status: 'ORG_PENDING' })],
      total: 9
    });
    getMySubmits.mockResolvedValue({ records: [{ id: 99 }], total: 99 });
    const wrapper = mount(RecordsView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    expect(listMyTaskAssignments).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 10,
      tab: 'PENDING'
    });
    expect(getMySubmits).not.toHaveBeenCalled();
    expect(wrapper.vm.total).toBe(9);
    wrapper.unmount();
  });

  it('四个状态数量使用各页签分页 total，而不是当前页 records 长度', async () => {
    const totals = { PENDING: 7, REVIEWING: 5, PASSED: 3, REJECTED: 2 };
    listMyTaskAssignments.mockImplementation(async (params) => ({
      records: params.tab === 'PENDING' ? [temporaryAssignment()] : [],
      total: totals[params.tab]
    }));
    const wrapper = mount(RecordsView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    expect(wrapper.vm.statusTotals).toEqual({ pending: 7, reviewing: 5, passed: 3, rejected: 2 });
    expect(wrapper.find('.pending-chip strong').text()).toBe('7');
    expect(wrapper.find('.reviewing-chip strong').text()).toBe('5');
    expect(wrapper.find('.passed-chip strong').text()).toBe('3');
    expect(wrapper.find('.rejected-chip strong').text()).toBe('2');
    expect(listMyTaskAssignments).toHaveBeenCalledWith({ pageNo: 1, pageSize: 1, tab: 'REVIEWING' });
    wrapper.unmount();
  });
});
