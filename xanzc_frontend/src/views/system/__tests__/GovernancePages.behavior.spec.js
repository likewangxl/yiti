// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import dayjs from 'dayjs';
import { METRIC_RECALC_DATE_MESSAGES } from '@/utils/metricRecalcDate';
import { ElMessage } from 'element-plus';

const { messageBox } = vi.hoisted(() => ({
  messageBox: { confirm: vi.fn(), prompt: vi.fn() }
}));

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: messageBox
}));
vi.mock('@/mock', () => ({
  sysRoles: [],
  sysResources: [],
  sysScopeMatrix: { bizTypes: [], matrix: {} }
}));
vi.mock('@/api/system', () => ({
  listAllRoles: vi.fn(),
  listResources: vi.fn(),
  getScopeMatrix: vi.fn(),
  getRoleResourceIds: vi.fn(),
  replaceRoleResources: vi.fn(),
  saveBizScope: vi.fn(),
  getCalendar: vi.fn(),
  setCalendarDay: vi.fn(),
  initCalendarYear: vi.fn(),
  importCalendar: vi.fn(),
  listJobs: vi.fn(),
  pauseJob: vi.fn(),
  resumeJob: vi.fn(),
  triggerJob: vi.fn(),
  listJobLogs: vi.fn()
}));

import {
  getCalendar,
  getRoleResourceIds,
  getScopeMatrix,
  initCalendarYear,
  listAllRoles,
  listJobs,
  listResources,
  pauseJob,
  replaceRoleResources,
  triggerJob
} from '@/api/system';
import Permission from '../Permission.vue';
import Calendar from '../Calendar.vue';
import Jobs from '../Jobs.vue';

const passthrough = (name, template = '<div><slot /><slot name="footer" /><slot name="default" /></div>') => ({ name, template });
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  PageTitle: { name: 'PageTitle', inheritAttrs: false, template: '<h1 v-bind="$attrs"><slot /></h1>' },
  'el-button': {
    name: 'ElButton', inheritAttrs: false, props: { disabled: Boolean, loading: Boolean }, emits: ['click'],
    template: '<button v-bind="$attrs" :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-form': passthrough('ElForm', '<form><slot /></form>'),
  'el-form-item': passthrough('ElFormItem'),
  'el-input': {
    name: 'ElInput', inheritAttrs: false, props: { modelValue: [String, Number] }, emits: ['update:modelValue'],
    template: '<input v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-select': passthrough('ElSelect'),
  'el-option': empty('ElOption'),
  'el-checkbox': { name: 'ElCheckbox', props: ['modelValue'], emits: ['change'], template: '<input type="checkbox" :checked="modelValue" @change="$emit(\'change\', $event.target.checked)" />' },
  'el-table': { name: 'ElTable', props: { data: Array }, template: '<div><slot /></div>' },
  'el-table-column': empty('ElTableColumn'),
  'el-tag': passthrough('ElTag'),
  'el-dialog': passthrough('ElDialog'),
  'el-alert': passthrough('ElAlert'),
  'el-upload': passthrough('ElUpload'),
  'el-pagination': empty('ElPagination'),
  'el-date-picker': empty('ElDatePicker')
};

function deferred() {
  let resolve;
  const promise = new Promise((res) => { resolve = res; });
  return { promise, resolve };
}

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

function mountPage(component) {
  return mount(component, {
    global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
  });
}

let wrapper;

beforeEach(() => {
  vi.clearAllMocks();
  listAllRoles.mockResolvedValue([{ roleId: 'role-1', roleChName: '运营管理员', roleCode: 'OPS_ADMIN', recordStatus: 0 }]);
  listResources.mockResolvedValue([{ resourceId: 'P_1', menuName: '查询资源', resourceUrl: '/api/admin/resources', resourceMethod: 'GET' }]);
  getRoleResourceIds.mockResolvedValue(['P_1']);
  getScopeMatrix.mockResolvedValue({ bizTypes: [], matrix: { 'role-1': {} } });
  getCalendar.mockResolvedValue([]);
  listJobs.mockResolvedValue({ records: [], total: 0 });
  pauseJob.mockResolvedValue({ ok: true });
});

afterEach(() => wrapper?.unmount());

describe('系统治理与 RBAC 写操作保护', () => {
  it('资源加载完成后默认收起每个资源大类，逐类展开互不影响', async () => {
    listResources.mockResolvedValueOnce([
      { resourceId: 'P_WORKSPACE', menuName: '工作台查询', resourceUrl: '/api/portal/home', resourceMethod: 'GET' },
      { resourceId: 'P_CUSTOMER', menuName: '客户查询', resourceUrl: '/api/customers', resourceMethod: 'GET' }
    ]);

    wrapper = mountPage(Permission);
    await settle();

    expect(wrapper.vm.groupedRes.map(group => group.name)).toEqual(['工作台', '客户营销']);
    expect([...wrapper.vm.collapsedKeys]).toEqual(['工作台', '客户营销']);

    const toggles = wrapper.findAll('button.collapse-toggle');
    expect(toggles).toHaveLength(2);
    expect(toggles[0].attributes('aria-expanded')).toBe('false');
    expect(toggles[1].attributes('aria-expanded')).toBe('false');

    await toggles[0].trigger('click');

    expect(wrapper.vm.collapsedKeys.has('工作台')).toBe(false);
    expect(wrapper.vm.collapsedKeys.has('客户营销')).toBe(true);
    expect(wrapper.findAll('button.collapse-toggle')[0].attributes('aria-expanded')).toBe('true');
    expect(wrapper.findAll('button.collapse-toggle')[1].attributes('aria-expanded')).toBe('false');
  });

  it('权限资源保存：取消确认不请求，确认后同一提交只发送一次且保留完整负载', async () => {
    wrapper = mountPage(Permission);
    await settle();
    wrapper.vm.toggleOne('P_2', true);

    messageBox.confirm.mockRejectedValueOnce(new Error('cancel'));
    await wrapper.vm.onSaveResources();
    expect(replaceRoleResources).not.toHaveBeenCalled();

    const request = deferred();
    messageBox.confirm.mockResolvedValueOnce('confirm');
    messageBox.prompt.mockResolvedValueOnce({ value: '权限调整已复核' });
    replaceRoleResources.mockReturnValueOnce(request.promise);

    const first = wrapper.vm.onSaveResources();
    const second = wrapper.vm.onSaveResources();
    await settle();

    expect(replaceRoleResources).toHaveBeenCalledTimes(1);
    expect(replaceRoleResources).toHaveBeenCalledWith('role-1', ['P_1', 'P_2'], '权限调整已复核');

    request.resolve({ ok: true });
    await Promise.all([first, second]);
  });

  it('权限资源读取失败：保留已知绑定并冻结勾选、还原和保存', async () => {
    wrapper = mountPage(Permission);
    await settle();

    getRoleResourceIds.mockRejectedValueOnce(new Error('资源绑定读取失败'));
    await wrapper.vm.loadRoleChecked('role-1');
    await settle();

    expect(wrapper.vm.loadError).toContain('角色资源加载失败');
    expect([...wrapper.vm.checkedIds]).toEqual(['P_1']);

    wrapper.vm.toggleOne('P_2', true);
    wrapper.vm.resetChecked();
    expect([...wrapper.vm.checkedIds]).toEqual(['P_1']);

    await wrapper.vm.onSaveResources();
    expect(replaceRoleResources).not.toHaveBeenCalled();
  });

  it('工作日历初始化：取消确认时绝不调用初始化接口', async () => {
    wrapper = mountPage(Calendar);
    await settle();
    messageBox.confirm.mockRejectedValueOnce(new Error('cancel'));

    await wrapper.vm.onInit();

    expect(initCalendarYear).not.toHaveBeenCalled();
  });

  it('任务暂停：同一任务请求未完成时不重复发送暂停接口', async () => {
    wrapper = mountPage(Jobs);
    await settle();
    const request = deferred();
    pauseJob.mockReturnValueOnce(request.promise);

    const first = wrapper.vm.onPause({ id: 9, jobKey: 'PERF_CALC' });
    const second = wrapper.vm.onPause({ id: 9, jobKey: 'PERF_CALC' });
    await nextTick();

    expect(pauseJob).toHaveBeenCalledTimes(1);
    expect(pauseJob).toHaveBeenCalledWith(9);

    request.resolve({ ok: true });
    await Promise.all([first, second]);
  });

  it('指标类任务手动触发默认昨日，并拒绝当天和超期非月末日期', async () => {
    wrapper = mountPage(Jobs);
    await settle();
    wrapper.vm.onTrigger({ id: 11, jobKey: 'LEVEL1_METRIC_CALC' });
    expect(wrapper.vm.trgDlg.dataDate).toBe(dayjs().subtract(1, 'day').format('YYYY-MM-DD'));
    expect(wrapper.vm.trgDlg.disabledDate(dayjs().toDate())).toBe(true);

    wrapper.vm.trgDlg.dataDate = dayjs().format('YYYY-MM-DD');
    wrapper.vm.trgDlg.reason = '当天重算';
    triggerJob.mockClear();
    ElMessage.warning.mockClear();
    await wrapper.vm.confirmTrigger();

    expect(triggerJob).not.toHaveBeenCalled();
    expect(ElMessage.warning).toHaveBeenCalledWith(METRIC_RECALC_DATE_MESSAGES.TODAY_OR_FUTURE);
  });

  it('非指标系统任务保持今天默认和原有未来日期规则', async () => {
    wrapper = mountPage(Jobs);
    await settle();
    wrapper.vm.onTrigger({ id: 12, jobKey: 'SYS_SESSION_CLEAN' });
    expect(wrapper.vm.trgDlg.dataDate).toBe(dayjs().format('YYYY-MM-DD'));
    expect(wrapper.vm.trgDlg.disabledDate(dayjs().toDate())).toBe(false);
    wrapper.vm.trgDlg.reason = '手动维护';
    triggerJob.mockClear();
    await wrapper.vm.confirmTrigger();

    expect(triggerJob).toHaveBeenCalledWith(12, '手动维护', dayjs().format('YYYY-MM-DD'), undefined);

    wrapper.vm.trgDlg.dataDate = dayjs().add(1, 'day').format('YYYY-MM-DD');
    triggerJob.mockClear();
    ElMessage.warning.mockClear();
    await wrapper.vm.confirmTrigger();
    expect(triggerJob).not.toHaveBeenCalled();
    expect(ElMessage.warning).toHaveBeenCalledWith(`数据日期不能大于今天（${dayjs().format('YYYY-MM-DD')}）`);
  });

  it('指标类任务超过20天的非月末日期给出专用提示且不提交', async () => {
    wrapper = mountPage(Jobs);
    await settle();
    wrapper.vm.onTrigger({ id: 13, jobKey: 'PERF_METRIC_DAILY' });
    const old = dayjs().subtract(21, 'day');
    const nonMonthEnd = old.isSame(old.endOf('month'), 'day') ? old.subtract(1, 'day') : old;
    wrapper.vm.trgDlg.dataDate = nonMonthEnd.format('YYYY-MM-DD');
    wrapper.vm.trgDlg.reason = '历史重算';
    triggerJob.mockClear();
    ElMessage.warning.mockClear();
    await wrapper.vm.confirmTrigger();

    expect(triggerJob).not.toHaveBeenCalled();
    expect(ElMessage.warning).toHaveBeenCalledWith(METRIC_RECALC_DATE_MESSAGES.OLDER_THAN_20_NON_MONTH_END);
  });

  it('指标调度的业绩分配日期只受未来限制，不套用重算日期规则', async () => {
    wrapper = mountPage(Jobs);
    await settle();
    wrapper.vm.onTrigger({ id: 14, jobKey: 'LEVEL1_METRIC_CALC' });
    const old = dayjs().subtract(21, 'day');
    const nonMonthEnd = old.isSame(old.endOf('month'), 'day') ? old.subtract(1, 'day') : old;
    const allocDate = nonMonthEnd.format('YYYY-MM-DD');
    wrapper.vm.trgDlg.allocDate = allocDate;
    wrapper.vm.trgDlg.reason = '补跑分配';
    triggerJob.mockClear();
    ElMessage.warning.mockClear();

    await wrapper.vm.confirmTrigger();

    expect(wrapper.vm.trgDlg.disabledAllocDate(nonMonthEnd.toDate())).toBe(false);
    expect(triggerJob).toHaveBeenCalledWith(14, '补跑分配', dayjs().subtract(1, 'day').format('YYYY-MM-DD'), allocDate);
  });
});
