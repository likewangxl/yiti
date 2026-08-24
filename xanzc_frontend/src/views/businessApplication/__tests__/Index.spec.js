// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  listLoanApplications: vi.fn(),
  getLoanApplication: vi.fn(),
  listLoanAttachments: vi.fn(),
  deleteLoanApplication: vi.fn(),
  submitLoanApplication: vi.fn()
}));
const workflow = vi.hoisted(() => ({
  listTodoTasks: vi.fn(),
  listDoneTasks: vi.fn(),
  claimTask: vi.fn(),
  approveTask: vi.fn(),
  rejectTask: vi.fn()
}));
const routerPush = vi.hoisted(() => vi.fn());
const routeState = vi.hoisted(() => ({ query: {}, params: {}, path: '/bizexec/loans' }));
const message = vi.hoisted(() => ({ success: vi.fn(), info: vi.fn(), warning: vi.fn(), error: vi.fn() }));

vi.mock('@/api/businessApplication', () => api);
vi.mock('@/api/workflow', () => workflow);
vi.mock('vue-router', () => ({ useRouter: () => ({ push: routerPush }), useRoute: () => routeState }));
vi.mock('element-plus', () => ({ ElMessage: message, ElMessageBox: { confirm: vi.fn().mockResolvedValue('ok'), prompt: vi.fn() } }));
vi.mock('@/composables/useDict', () => ({ useDict: () => ({ options: [], labelOf: value => value, loading: false }) }));

import LoanIndex from '../Index.vue';

const stubs = {
  PageTitle: { props: ['title'], template: '<h1>{{ title }}</h1>' },
  BpAdaptiveRowActions: { template: '<div><slot name="primary" /><slot name="expanded" /><slot name="compact" /></div>' },
  LoanForm: { props: ['modelValue'], template: '<div v-if="modelValue" class="loan-form-stub" />' },
  LoanDetail: { props: ['modelValue'], template: '<div v-if="modelValue" class="loan-detail-stub" />' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-select': { template: '<select />' },
  'el-option': { template: '<option />' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-pagination': { template: '<div />' },
  'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-radio-group': { template: '<div><slot /></div>' },
  'el-radio-button': { template: '<button><slot /></button>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-drawer': { props: ['modelValue'], template: '<div v-if="modelValue"><slot /></div>' },
  'el-dropdown': { template: '<div><slot /><slot name="dropdown" /></div>' },
  'el-dropdown-menu': { template: '<div><slot /></div>' },
  'el-dropdown-item': { template: '<button><slot /></button>' }
};

describe('资产立项列表', () => {
  it('从触达任务入口新建时带入任务和客户', async () => {
    routeState.query = { sourceTouchTaskId: 'T-1', custId: 'C-1', custName: '浦爱科技' };
    api.listLoanApplications.mockResolvedValue({ records: [], total: 0 });
    workflow.listTodoTasks.mockResolvedValue({ records: [], total: 0 });
    const wrapper = mount(LoanIndex, { global: { stubs, directives: { 'bp-overflow-tooltip': {} } } });
    await flushPromises();

    wrapper.vm.openCreate();

    expect(wrapper.vm.editingLoan).toEqual(expect.objectContaining({
      sourceTouchTaskId: 'T-1', custId: 'C-1', custName: '浦爱科技'
    }));
    routeState.query = {};
  });

  it('从工作台已办进入时切换到已办页签后再查询任务', async () => {
    routeState.query = { tab: 'done', loanId: 'L-2', taskId: 'T-2' };
    workflow.listDoneTasks.mockResolvedValue({ records: [], total: 0 });
    api.listLoanApplications.mockResolvedValue({ records: [], total: 0 });

    const wrapper = mount(LoanIndex, { global: { stubs, directives: { 'bp-overflow-tooltip': {} } } });
    await flushPromises();

    expect(wrapper.vm.taskTab).toBe('done');
    expect(workflow.listDoneTasks).toHaveBeenCalledWith(expect.objectContaining({ bizType: 'LOAN' }));
    routeState.query = {};
  });

  it('列表默认加载 LOAN 申请并支持分页筛选', async () => {
    api.listLoanApplications.mockResolvedValue({ records: [{ id: 'L-1', custName: '浦爱科技', status: 'DRAFT' }], total: 1 });
    workflow.listTodoTasks.mockResolvedValue({ records: [], total: 0 });
    workflow.listDoneTasks.mockResolvedValue({ records: [], total: 0 });
    const wrapper = mount(LoanIndex, { global: { stubs, directives: { 'bp-overflow-tooltip': {} } } });
    await flushPromises();

    expect(api.listLoanApplications).toHaveBeenCalledWith(expect.objectContaining({ pageNo: 1, pageSize: 20 }));
    expect(wrapper.vm.rows).toHaveLength(1);
    wrapper.vm.query.keyword = '浦爱';
    wrapper.vm.query.projectType = 'SHOULD_NOT_BE_SENT';
    wrapper.vm.search();
    await flushPromises();
    expect(api.listLoanApplications).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '浦爱', pageNo: 1 }));
    expect(api.listLoanApplications.mock.calls.at(-1)[0]).not.toHaveProperty('projectType');
  });

  it('编辑时并行加载详情与已有附件，交给表单展示', async () => {
    api.listLoanApplications.mockResolvedValue({ records: [], total: 0 });
    workflow.listTodoTasks.mockResolvedValue({ records: [], total: 0 });
    api.getLoanApplication.mockResolvedValue({ id: 'L-1', status: 'DRAFT' });
    api.listLoanAttachments.mockResolvedValue([{ id: 'F-1', fileName: '授信材料.pdf' }]);
    const wrapper = mount(LoanIndex, { global: { stubs, directives: { 'bp-overflow-tooltip': {} } } });
    await flushPromises();

    await wrapper.vm.openEdit({ id: 'L-1', status: 'DRAFT' });
    await flushPromises();

    expect(api.getLoanApplication).toHaveBeenCalledWith('L-1');
    expect(api.listLoanAttachments).toHaveBeenCalledWith('L-1');
    expect(wrapper.vm.editingLoan.attachments).toEqual([{ id: 'F-1', fileName: '授信材料.pdf' }]);
  });

  it('待办页签固定以 bizType=LOAN 查询，驳回文案明确为驳回结束', async () => {
    api.listLoanApplications.mockResolvedValue({ records: [], total: 0 });
    workflow.listTodoTasks.mockResolvedValue({ records: [{ taskId: 'T-1', bizType: 'LOAN', bizId: 'L-1', claimable: true }], total: 1 });
    workflow.listDoneTasks.mockResolvedValue({ records: [], total: 0 });
    const wrapper = mount(LoanIndex, { global: { stubs, directives: { 'bp-overflow-tooltip': {} } } });
    await flushPromises();

    expect(workflow.listTodoTasks).toHaveBeenCalledWith(expect.objectContaining({ bizType: 'LOAN' }));
    expect(wrapper.vm.taskNodeName({ taskName: '公司部审核', nodeName: '错误兼容值' })).toBe('公司部审核');
    expect(wrapper.text()).toContain('驳回结束');
    expect(wrapper.text()).not.toContain('退回发起人');
  });

  it('claimable 未明确为 false 时不能直接审批，审批前先签收', async () => {
    api.listLoanApplications.mockResolvedValue({ records: [], total: 0 });
    workflow.listTodoTasks.mockResolvedValue({ records: [], total: 0 });
    workflow.claimTask.mockResolvedValue({ ok: true });
    workflow.approveTask.mockResolvedValue({ ok: true });
    const wrapper = mount(LoanIndex, { global: { stubs, directives: { 'bp-overflow-tooltip': {} } } });
    await flushPromises();

    await wrapper.vm.approveRow({ taskId: 'T-1', bizType: 'LOAN' });
    expect(workflow.claimTask).toHaveBeenCalledWith('T-1');
    expect(workflow.approveTask).toHaveBeenCalledWith('T-1', expect.any(String));
  });
});
