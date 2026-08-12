// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

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
  replaceRoleResources
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
});
