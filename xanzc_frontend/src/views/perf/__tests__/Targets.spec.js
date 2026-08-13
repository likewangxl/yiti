// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn() })
}));

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}));

vi.mock('@/api/perf', () => ({
  listTargets: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  createTargetPlan: vi.fn(),
  updateTargetPlan: vi.fn(),
  deleteTargetPlan: vi.fn(),
  listKpiRules: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  getTargetAdjust: vi.fn().mockResolvedValue({}),
  listTargetAdjusts: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  getTargetAdjustApprovalHistory: vi.fn().mockResolvedValue([]),
  listTargetValues: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  listMetrics: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  calcKpiScore: vi.fn(),
  uploadImportFile: vi.fn()
}));

vi.mock('@/api/workflow', () => ({
  listTodoTasks: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  listDoneTasks: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  claimTask: vi.fn(),
  approveTask: vi.fn(),
  rejectTask: vi.fn()
}));

vi.mock('@/api/auth', () => ({
  getMyPermissions: vi.fn().mockResolvedValue({ isSystemAdmin: true })
}));

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ user: { empId: 'E100', roles: [{ roleId: '238' }] } })
}));

import { approveTask, claimTask } from '@/api/workflow';
import Targets from '../Targets.vue';

const passthrough = (name) => ({ name, template: '<div><slot /><slot name="header" /><slot name="footer" /></div>' });
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  PageTitle: {
    name: 'PageTitle',
    inheritAttrs: false,
    template: '<h1 class="page-title" v-bind="$attrs">目标管理<slot /></h1>'
  },
  'el-button': {
    name: 'ElButton',
    props: ['disabled', 'loading'],
    emits: ['click'],
    template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-tabs': passthrough('ElTabs'),
  'el-tab-pane': passthrough('ElTabPane'),
  'el-form': {
    name: 'ElForm',
    inheritAttrs: false,
    methods: { validate: () => Promise.resolve(true) },
    template: '<form v-bind="$attrs"><slot /></form>'
  },
  'el-form-item': passthrough('ElFormItem'),
  'el-input': { name: 'ElInput', props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
  'el-select': passthrough('ElSelect'),
  'el-option': empty('ElOption'),
  'el-table': { name: 'ElTable', props: ['data'], emits: ['selection-change'], template: '<div><slot /></div>' },
  'el-table-column': empty('ElTableColumn'),
  'el-pagination': empty('ElPagination'),
  'el-tag': passthrough('ElTag'),
  'el-alert': empty('ElAlert'),
  'el-dialog': passthrough('ElDialog'),
  'el-date-picker': empty('ElDatePicker')
};

let wrapper;

afterEach(() => {
  wrapper?.unmount();
  wrapper = undefined;
  vi.clearAllMocks();
});

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

function deferred() {
  let resolve;
  const promise = new Promise((res) => { resolve = res; });
  return { promise, resolve };
}

describe('Targets.vue 目标管理工作区', () => {
  it('以 bp-crud 页面骨架呈现三类工作区、筛选和可读的列表状态', async () => {
    wrapper = mount(Targets, { global: { stubs, directives: { loading: { mounted() {}, updated() {} } } } });
    await settle();

    expect(wrapper.find('main.bp-crud.targets-page[aria-labelledby="targets-page-title"]').exists()).toBe(true);
    expect(wrapper.get('h1#targets-page-title').text()).toContain('目标管理');
    expect(wrapper.find('section[aria-label="目标方案筛选"]').exists()).toBe(true);
    expect(wrapper.find('section[aria-label="目标方案列表"][aria-describedby="targets-plans-state"]').exists()).toBe(true);
    expect(wrapper.get('#targets-plans-state').text()).toContain('暂无目标方案数据');
    expect(wrapper.find('[aria-label="目标管理工作区"]').exists()).toBe(true);
    expect(wrapper.html()).toContain('待我审批');
    expect(wrapper.html()).toContain('已审批');
  });

  it('同一待办在认领与审批未完成时只会发送一次请求，并保留审批参数', async () => {
    const claimPending = deferred();
    claimTask.mockReturnValueOnce(claimPending.promise);
    approveTask.mockResolvedValueOnce({ ok: true });
    wrapper = mount(Targets, { global: { stubs, directives: { loading: { mounted() {}, updated() {} } } } });
    await settle();

    wrapper.vm.reviewDlg.row = { taskId: 'TASK-1', claimable: true };
    wrapper.vm.reviewDlg.opinion = '同意本次修正';
    const first = wrapper.vm.submitReview('APPROVE');
    const second = wrapper.vm.submitReview('APPROVE');

    await nextTick();
    expect(claimTask).toHaveBeenCalledTimes(1);
    claimPending.resolve({ ok: true });
    await Promise.all([first, second]);
    await settle();

    expect(approveTask).toHaveBeenCalledTimes(1);
    expect(approveTask).toHaveBeenCalledWith('TASK-1', '同意本次修正');
  });
});
