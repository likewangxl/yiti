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
  'el-image': { name: 'ElImage', inheritAttrs: false, props: ['src', 'previewSrcList'], template: '<img v-bind="$attrs" :src="src" />' },
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
const originalGeolocation = globalThis.navigator?.geolocation;

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
  if (globalThis.navigator) {
    Object.defineProperty(globalThis.navigator, 'geolocation', {
      configurable: true,
      value: originalGeolocation
    });
  }
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

  it('保存工作日志后刷新为办理中，保留补录表单且允许继续追加日志', async () => {
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
    expect(view.find('.log-form').exists()).toBe(true);
    expect(view.find('.complete-task-button').exists()).toBe(true);
    expect(view.find('.complete-task-button').element.disabled).toBe(false);
    expect(view.text()).toContain('已完成现场沟通');

    view.vm.form.logContent = '第二次沟通记录';
    view.vm.form.photoGroups.keyPerson = ['photo-2.jpg'];
    await view.vm.submitLog();
    expect(api.addTouchLog).toHaveBeenCalledTimes(2);
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

  it('办理中已有工作日志时同时显示补录和独立的完成任务按钮', async () => {
    api.getTouchTask.mockResolvedValue(task('IN_PROGRESS'));
    api.listTouchLogs.mockResolvedValue([log({ workLogId: 'WL-2' })]);

    const view = await mountDialog();

    expect(view.find('.log-form').exists()).toBe(true);
    const completeButton = view.get('.complete-task-button');
    expect(completeButton.text()).toContain('完成任务');
    expect(completeButton.element.disabled).toBe(false);

    await completeButton.trigger('click');
    await flushPromises();
    expect(api.completeTouchTask).toHaveBeenCalledWith('TASK-1');
  });

  it('协同人员可补录但不展示完成或取消任务操作', async () => {
    api.getTouchTask.mockResolvedValue({
      ...task('IN_PROGRESS'), canWriteLog: true, canOperateTask: false
    });
    api.listTouchLogs.mockResolvedValue([log()]);

    const view = await mountDialog();

    expect(view.find('.log-form').exists()).toBe(true);
    expect(view.find('.task-actions').exists()).toBe(false);
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
    messageBox.confirm.mockResolvedValueOnce(true).mockRejectedValueOnce('close');

    const view = await mountDialog();
    await view.get('.complete-task-button').trigger('click');
    await flushPromises();

    expect(messageBox.confirm.mock.calls[0][0]).toBe('确认完成该触达任务？完成后仍可补录历史日志，但不能恢复办理状态。');
    expect(messageBox.confirm.mock.calls[0][0]).not.toMatch(/undefined|null|客户号：\s*（/);
  });

  it('中台支持后续选择取消或跳转失败不影响触达完成', async () => {
    api.getTouchTask.mockResolvedValue(task('IN_PROGRESS'));
    api.listTouchLogs.mockResolvedValue([log()]);
    api.completeTouchTask.mockResolvedValue({});
    messageBox.confirm.mockResolvedValueOnce(true).mockRejectedValueOnce('close');

    const view = await mountDialog();
    await view.get('.complete-task-button').trigger('click');
    await flushPromises();

    expect(api.completeTouchTask).toHaveBeenCalledWith('TASK-1');
    expect(view.vm.task.taskStatus).toBe('IN_PROGRESS');
    expect(routerPush).not.toHaveBeenCalled();
  });

  it('历史日志按服务端返回完整追加展示，不截断为第一条', async () => {
    api.getTouchTask.mockResolvedValue(task('IN_PROGRESS'));
    api.listTouchLogs.mockResolvedValue([
      log({ id: 'WL-1', workLogId: 'WL-1', logContent: '第一次沟通' }),
      log({ id: 'WL-2', workLogId: 'WL-2', logContent: '第二次沟通' })
    ]);

    const view = await mountDialog();

    expect(view.findAllComponents({ name: 'ElTimelineItem' })).toHaveLength(2);
    expect(view.text()).toContain('第一次沟通');
    expect(view.text()).toContain('第二次沟通');
  });

  it.each(['PENDING', 'IN_PROGRESS', 'SUCCESS'])('状态 %s 允许继续补录触达日志', async status => {
    api.getTouchTask.mockResolvedValue(task(status));
    api.listTouchLogs.mockResolvedValue([log()]);

    const view = await mountDialog();

    expect(view.find('.log-form').exists()).toBe(true);
    expect(view.find('.save-log-button').exists()).toBe(true);
  });

  it('取消任务仅可查看历史记录，不能补录或再次办理', async () => {
    api.getTouchTask.mockResolvedValue(task('CANCELLED'));
    api.listTouchLogs.mockResolvedValue([log()]);

    const view = await mountDialog();

    expect(view.find('.log-form').exists()).toBe(false);
    expect(view.find('.task-actions').exists()).toBe(false);
    expect(view.find('.read-only-notice').text()).toContain('不能继续补录');
  });

  it('只有在途且已有日志时才展示完成按钮', async () => {
    api.getTouchTask.mockResolvedValue(task('PENDING'));
    api.listTouchLogs.mockResolvedValue([]);

    const view = await mountDialog();

    expect(view.find('.complete-task-button').exists()).toBe(false);
  });

  it('完成后可明确选择资产立项并带出任务、客户和最新工作日志', async () => {
    api.getTouchTask.mockResolvedValue(task('IN_PROGRESS'));
    api.listTouchLogs.mockResolvedValue([log({ workLogId: 91, id: 91 })]);
    api.completeTouchTask.mockResolvedValue({});
    // 第一次确认完成任务；第二次选择不发起中台支持；第三次选择资产立项。
    messageBox.confirm.mockResolvedValueOnce(true)
      .mockRejectedValueOnce('cancel')
      .mockResolvedValueOnce(true);

    const view = await mountDialog();
    await view.get('.complete-task-button').trigger('click');
    await flushPromises();

    expect(messageBox.confirm.mock.calls[1][2]).toMatchObject({
      distinguishCancelAndClose: true
    });
    expect(routerPush).toHaveBeenCalledWith({
      path: '/marketing/asset-projects/new',
      query: { custId: 'CUST-1', sourceTouchTaskId: 'TASK-1', sourceWorklogId: '91' }
    });
  });

  it('获取当前位置成功时填入易读经纬度，并沿用触达时间提交日志', async () => {
    api.getTouchTask.mockResolvedValue(task('PENDING'));
    api.listTouchLogs.mockResolvedValue([]);
    api.addTouchLog.mockResolvedValue({});
    const getCurrentPosition = vi.fn((success, _failure, options) => {
      expect(options).toMatchObject({ enableHighAccuracy: true, timeout: 10000 });
      success({ coords: { latitude: 34.1234567, longitude: 108.9876543, accuracy: 12 } });
    });
    Object.defineProperty(globalThis.navigator, 'geolocation', {
      configurable: true,
      value: { getCurrentPosition }
    });

    const view = await mountDialog();
    view.vm.form.touchTime = '2026-09-02T09:30:00';
    await view.get('.location-button').trigger('click');
    await flushPromises();

    expect(getCurrentPosition).toHaveBeenCalledTimes(1);
    expect(view.vm.form.operatorLocation).toContain('纬度 34.123457，经度 108.987654');
    expect(view.vm.locationMessage).toContain('2026-09-02 09:30:00');

    view.vm.form.logContent = '现场沟通完成';
    view.vm.form.photoGroups.keyPerson = ['photo-1.jpg'];
    await view.vm.submitLog();
    expect(api.addTouchLog).toHaveBeenCalledWith('TASK-1', expect.objectContaining({
      touchTime: '2026-09-02T09:30:00',
      operatorLocation: expect.stringContaining('纬度 34.123457，经度 108.987654')
    }));
  });

  it('兼容后端 ONSITE 触达方式并统一显示为上门拜访', async () => {
    api.getTouchTask.mockResolvedValue(task('SUCCESS'));
    api.listTouchLogs.mockResolvedValue([log({ touchMethod: 'ONSITE' })]);

    const view = await mountDialog();

    expect(view.text()).toContain('上门拜访');
    expect(view.text()).not.toContain('ONSITE');
  });

  it('历史日志照片兼容裸文件对象ID和平台下载URL，并统一交给预览组件', async () => {
    api.getTouchTask.mockResolvedValue(task('SUCCESS'));
    api.listTouchLogs.mockResolvedValue([log({
      photoGroups: {
        keyPerson: ['FILE-001'],
        doorplate: ['/api/files/FILE-002/download#doorplate.jpg'],
        workplace: []
      }
    })]);

    const view = await mountDialog();
    const images = view.findAllComponents({ name: 'ElImage' });

    expect(images.map(image => image.props('src'))).toEqual([
      '/api/files/FILE-001/download',
      '/api/files/FILE-002/download#doorplate.jpg'
    ]);
  });

  it('获取当前位置失败时给出可见提示并保留手工填写入口', async () => {
    api.getTouchTask.mockResolvedValue(task('PENDING'));
    api.listTouchLogs.mockResolvedValue([]);
    const getCurrentPosition = vi.fn((_success, failure) => failure({ code: 1 }));
    Object.defineProperty(globalThis.navigator, 'geolocation', {
      configurable: true,
      value: { getCurrentPosition }
    });

    const view = await mountDialog();
    await view.get('.location-button').trigger('click');
    await flushPromises();

    expect(view.vm.form.operatorLocation).toBe('');
    expect(view.vm.locationMessage).toContain('未获得定位权限');
    expect(view.find('.location-message').text()).toContain('手工填写地址');
    expect(message.warning).toHaveBeenCalledWith(expect.stringContaining('未获得定位权限'));
  });
});
