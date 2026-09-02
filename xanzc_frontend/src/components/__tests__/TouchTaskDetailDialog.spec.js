// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
    addTouchLog: vi.fn(),
  cancelTouchTask: vi.fn(),
  completeTouchTask: vi.fn(),
  getMarketingCustomer: vi.fn(),
  getTouchTask: vi.fn(),
  listTouchLogs: vi.fn(),
  uploadTouchPhoto: vi.fn(),
}));
const message = vi.hoisted(() => ({
  error: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
}));
const messageBox = vi.hoisted(() => ({ confirm: vi.fn(), prompt: vi.fn() }));
const routerPush = vi.hoisted(() => vi.fn());

vi.mock('@/api/customerMarketing', () => api);
vi.mock('element-plus', () => ({ ElMessage: message, ElMessageBox: messageBox }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: routerPush }) }));

import TouchTaskDetailDialog from '../TouchTaskDetailDialog.vue';

const passthrough = (name, template = '<div><slot /></div>') => ({ name, inheritAttrs: false, template });
const stubs = {
  'el-button': {
    name: 'ElButton',
    inheritAttrs: false,
    props: ['disabled', 'loading'],
    emits: ['click'],
    template: '<button v-bind="$attrs" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
  },
  'el-card': passthrough('ElCard'),
  'el-date-picker': passthrough('ElDatePicker'),
  'el-descriptions': passthrough('ElDescriptions'),
  'el-descriptions-item': passthrough('ElDescriptionsItem'),
  'el-dialog': {
    name: 'ElDialog',
    props: ['modelValue', 'closeOnClickModal'],
    template: '<div v-if="modelValue" class="dialog"><slot /></div>',
  },
  'el-empty': passthrough('ElEmpty'),
  'el-form': passthrough('ElForm', '<form><slot /></form>'),
  'el-form-item': passthrough('ElFormItem'),
  'el-image': passthrough('ElImage'),
  'el-input': {
    name: 'ElInput',
    inheritAttrs: false,
    props: ['maxlength', 'modelValue'],
    template: '<textarea v-bind="$attrs" :maxlength="maxlength" :value="modelValue" />',
  },
  'el-option': passthrough('ElOption'),
  'el-select': passthrough('ElSelect'),
  'el-tag': passthrough('ElTag'),
  'el-timeline': passthrough('ElTimeline'),
  'el-timeline-item': passthrough('ElTimelineItem'),
  'el-upload': passthrough('ElUpload'),
};

let wrapper;

function task(status = 'PENDING') {
  return {
    taskNo: 'TASK-1',
    custId: 'CUST-1',
    taskStatus: status,
    assigneeEmpId: 'E-1',
    orgId: 'ORG-1',
  };
}

function log(overrides = {}) {
  return {
    workLogId: 'WL-1',
    id: 'WL-1',
    logTime: '2026-08-24T10:00:00',
    touchMethod: 'VISIT',
    logContent: '已完成现场沟通',
    createdBy: 'E-1',
    ...overrides,
  };
}

async function mountDialog() {
  wrapper = mount(TouchTaskDetailDialog, {
    props: { modelValue: false, taskId: 'TASK-1', allowWrite: true },
    global: { stubs, directives: { loading: () => {} } },
  });
  await wrapper.setProps({ modelValue: true });
  await flushPromises();
  return wrapper;
}

afterEach(() => {
  wrapper?.unmount();
});

beforeEach(() => {
  vi.clearAllMocks();
  api.getMarketingCustomer.mockResolvedValue({ custName: '测试客户' });
  api.completeTouchTask.mockResolvedValue({});
  api.cancelTouchTask.mockResolvedValue({});
  messageBox.confirm.mockResolvedValue(true);
  messageBox.prompt.mockResolvedValue({ value: '客户暂不配合' });
  routerPush.mockReset();
});

describe('TouchTaskDetailDialog 一任务一工作日志', () => {
  it('不允许点击遮罩关闭并丢失未保存的办理内容', async () => {
    api.getTouchTask.mockResolvedValue(task('PENDING'));
    api.listTouchLogs.mockResolvedValue([]);

    const view = await mountDialog();

    expect(view.getComponent({ name: 'ElDialog' }).props('closeOnClickModal')).toBe(false);
  });

  it('仅待办理且没有工作日志时展示新增表单，并限制触达小结为 200 字', async () => {
    api.getTouchTask.mockResolvedValue(task('PENDING'));
    api.listTouchLogs.mockResolvedValue([]);

    const view = await mountDialog();

    expect(view.find('.log-form').exists()).toBe(true);
    expect(view.find('textarea[maxlength="200"]').exists()).toBe(true);
  });

  it('保存工作日志后刷新为办理中，隐藏新增表单且不允许再新增第二条', async () => {
    api.getTouchTask
      .mockResolvedValueOnce(task('PENDING'))
      .mockResolvedValueOnce(task('IN_PROGRESS'));
    api.listTouchLogs
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([log()]);
    api.addTouchLog.mockResolvedValue({ workLogId: 'WL-1' });

    const view = await mountDialog();
    view.vm.form.logContent = '已完成现场沟通';
    view.vm.form.photoGroups.keyPerson = ['photo-1.jpg'];

    await view.get('.save-log-button').trigger('click');
    await flushPromises();

    expect(api.addTouchLog).toHaveBeenCalledTimes(1);
    expect(view.vm.task.taskStatus).toBe('IN_PROGRESS');
    expect(view.find('.log-form').exists()).toBe(false);
    expect(view.find('.complete-task-button').exists()).toBe(true);
    expect(view.find('.complete-task-button').element.disabled).toBe(false);
    expect(view.text()).toContain('已完成现场沟通');

    await view.vm.submitLog();
    expect(api.addTouchLog).toHaveBeenCalledTimes(1);
  });

  it('保存日志后以服务端最新任务状态为准', async () => {
    api.getTouchTask
      .mockResolvedValueOnce(task('PENDING'))
      .mockResolvedValueOnce(task('CANCELLED'));
    api.listTouchLogs
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([log()]);
    api.addTouchLog.mockResolvedValue({ workLogId: 'WL-concurrent' });

    const view = await mountDialog();
    view.vm.form.logContent = '已完成现场沟通';
    view.vm.form.photoGroups.keyPerson = ['photo-1.jpg'];

    await view.get('.save-log-button').trigger('click');
    await flushPromises();

    expect(view.vm.task.taskStatus).toBe('CANCELLED');
    expect(view.find('.complete-task-button').exists()).toBe(false);
  });

  it('办理中已有工作日志时仍显示独立的完成任务按钮', async () => {
    api.getTouchTask.mockResolvedValue(task('IN_PROGRESS'));
    api.listTouchLogs.mockResolvedValue([log({ workLogId: 'WL-2' })]);

    const view = await mountDialog();

    expect(view.find('.log-form').exists()).toBe(false);
    const completeButton = view.get('.complete-task-button');
    expect(completeButton.text()).toContain('完成任务');
    expect(completeButton.element.disabled).toBe(false);

    await completeButton.trigger('click');
    await flushPromises();
    expect(api.completeTouchTask).toHaveBeenCalledWith('TASK-1');
  });

  it('办理中任务可由执行人填写原因后取消，且只调用一次取消接口', async () => {
    api.getTouchTask
      .mockResolvedValueOnce(task('IN_PROGRESS'))
      .mockResolvedValueOnce(task('CANCELLED'));
    api.listTouchLogs
      .mockResolvedValueOnce([log()])
      .mockResolvedValueOnce([log()]);

    const view = await mountDialog();

    const cancelButton = view.get('.cancel-task-button');
    await cancelButton.trigger('click');
    await flushPromises();

    expect(messageBox.prompt).toHaveBeenCalled();
    expect(api.cancelTouchTask).toHaveBeenCalledTimes(1);
    expect(api.cancelTouchTask).toHaveBeenCalledWith('TASK-1', '客户暂不配合');
    expect(view.vm.task.taskStatus).toBe('CANCELLED');
  });

  it('触达成功后询问是否需要中台支持，选择是带出客户和来源任务', async () => {
    api.getTouchTask.mockResolvedValue(task('IN_PROGRESS'));
    api.listTouchLogs.mockResolvedValue([log()]);
    api.getMarketingCustomer.mockResolvedValue({ custName: '测试客户', custNo: 'KH-001' });
    api.completeTouchTask.mockResolvedValue({});
    messageBox.confirm.mockResolvedValueOnce(true).mockResolvedValueOnce(true);

    const view = await mountDialog();
    await view.get('.complete-task-button').trigger('click');
    await flushPromises();

    expect(messageBox.confirm.mock.calls[0][0]).toContain('测试客户');
    expect(messageBox.confirm.mock.calls[0][0]).toContain('KH-001');
    expect(messageBox.confirm.mock.calls[1][0]).toContain('测试客户');
    expect(messageBox.confirm.mock.calls[1][0]).toContain('KH-001');
    expect(messageBox.confirm).toHaveBeenCalledTimes(2);
    expect(routerPush).toHaveBeenCalledWith({
      path: '/bizexec/supports/new',
      query: { custId: 'CUST-1', sourceTouchTaskId: 'TASK-1' }
    });
  });

  it('客户名称和客户号都缺失时完成确认仍保持自然文案', async () => {
    api.getTouchTask.mockResolvedValue({ ...task('IN_PROGRESS'), custName: undefined, custNo: undefined });
    api.getMarketingCustomer.mockResolvedValue({});
    api.listTouchLogs.mockResolvedValue([log()]);
    api.completeTouchTask.mockResolvedValue({});
    messageBox.confirm.mockResolvedValueOnce(true).mockRejectedValueOnce(new Error('cancel'));

    const view = await mountDialog();
    await view.get('.complete-task-button').trigger('click');
    await flushPromises();

    expect(messageBox.confirm.mock.calls[0][0]).toBe('确认完成该触达任务？完成后不可继续补录。');
    expect(messageBox.confirm.mock.calls[0][0]).not.toMatch(/undefined|null|客户号：\s*（/);
  });

  it('中台支持后续选择取消或跳转失败不影响触达完成', async () => {
    api.getTouchTask.mockResolvedValue(task('IN_PROGRESS'));
    api.listTouchLogs.mockResolvedValue([log()]);
    api.completeTouchTask.mockResolvedValue({});
    messageBox.confirm.mockResolvedValueOnce(true).mockRejectedValueOnce(new Error('cancel'));

    const view = await mountDialog();
    await view.get('.complete-task-button').trigger('click');
    await flushPromises();

    expect(api.completeTouchTask).toHaveBeenCalledWith('TASK-1');
    expect(view.vm.task.taskStatus).toBe('IN_PROGRESS');
    expect(routerPush).not.toHaveBeenCalled();
  });
});
