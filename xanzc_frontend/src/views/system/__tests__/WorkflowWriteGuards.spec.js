// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const { messageBox } = vi.hoisted(() => ({
  messageBox: { confirm: vi.fn(), alert: vi.fn() }
}));

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: messageBox
}));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('@/api/flowDesign', () => ({
  listFlows: vi.fn().mockResolvedValue([]),
  getFlow: vi.fn(),
  createFlow: vi.fn(),
  saveFlow: vi.fn(),
  publishFlow: vi.fn(),
  deleteFlow: vi.fn(),
  importExistingFlows: vi.fn()
}));
vi.mock('@/api/workflow', () => ({
  transferInitiate: vi.fn(),
  transferCandidates: vi.fn().mockResolvedValue([])
}));
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ user: { mainOrgCode: '1001' }, orgName: '总行营业部' })
}));

import { publishFlow } from '@/api/flowDesign';
import { transferInitiate } from '@/api/workflow';
import FlowList from '../FlowList.vue';
import TransferDialog from '../../../components/TransferDialog.vue';

const passthrough = (name) => ({ name, template: '<div><slot /><slot name="footer" /></div>' });
const empty = (name) => ({ name, template: '<div />' });
const commonStubs = {
  PageTitle: passthrough('PageTitle'),
  'el-button': { name: 'ElButton', props: ['loading', 'disabled'], emits: ['click'], template: '<button :disabled="loading || disabled" @click="$emit(\'click\')"><slot /></button>' },
  'el-table': passthrough('ElTable'),
  'el-table-column': empty('ElTableColumn'),
  'el-tag': passthrough('ElTag'),
  'el-dialog': passthrough('ElDialog'),
  'el-form': passthrough('ElForm'),
  'el-form-item': passthrough('ElFormItem'),
  'el-input': empty('ElInput'),
  'el-select': passthrough('ElSelect'),
  'el-option': empty('ElOption')
};

function deferred() {
  let resolve;
  const promise = new Promise((res) => { resolve = res; });
  return { promise, resolve };
}

let wrapper;

beforeEach(() => {
  vi.clearAllMocks();
  messageBox.confirm.mockResolvedValue('confirm');
});

afterEach(() => wrapper?.unmount());

describe('流程关键写操作防重复提交', () => {
  it('发布同一流程时，确认后的重复触发只提交一次', async () => {
    const request = deferred();
    publishFlow.mockReturnValueOnce(request.promise);
    wrapper = mount(FlowList, { global: { stubs: commonStubs, directives: { loading: { mounted() {}, updated() {} } } } });
    await flushPromises();

    const row = { id: 'FLOW_1', name: '业绩调整流程' };
    const first = wrapper.vm.doPublish(row);
    const second = wrapper.vm.doPublish(row);
    await flushPromises();

    expect(publishFlow).toHaveBeenCalledTimes(1);
    expect(publishFlow).toHaveBeenCalledWith('FLOW_1');

    request.resolve({ ok: true });
    await Promise.all([first, second]);
  });

  it('转交提交尚未完成时，重复触发只发起一次转交请求', async () => {
    const request = deferred();
    transferInitiate.mockReturnValueOnce(request.promise);
    wrapper = mount(TransferDialog, {
      props: {
        modelValue: true,
        task: { taskId: 'TASK_1', nodeName: '分行审批', currentAssignee: 'zhangsan' }
      },
      global: { stubs: { ...commonStubs }, directives: { loading: { mounted() {}, updated() {} } } }
    });
    wrapper.vm.formRef = { validate: vi.fn().mockResolvedValue(true) };
    wrapper.vm.form.toEmpId = 'lisi';
    wrapper.vm.form.reason = '需由客户经理继续跟进';

    const first = wrapper.vm.onSubmit();
    const second = wrapper.vm.onSubmit();
    await flushPromises();

    expect(transferInitiate).toHaveBeenCalledTimes(1);
    expect(transferInitiate).toHaveBeenCalledWith('TASK_1', {
      toEmpId: 'lisi',
      reason: '需由客户经理继续跟进'
    });

    request.resolve({ ok: true });
    await Promise.all([first, second]);
  });
});
