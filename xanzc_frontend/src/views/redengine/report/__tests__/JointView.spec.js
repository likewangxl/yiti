// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}));

const routeState = {
  query: {
    taskId: '42',
    assignmentId: '1001',
    periodKey: '2026-Q3',
    detailItemCode: '1.1'
  }
};
vi.mock('vue-router', () => ({
  useRoute: () => routeState
}));

vi.mock('@/api/redengine', () => ({
  createSubmit: vi.fn(),
  getMyTaskAssignment: vi.fn(),
  uploadFile: vi.fn()
}));

import { createSubmit, getMyTaskAssignment, uploadFile } from '@/api/redengine';
import { ElMessage } from 'element-plus';
import JointView from '../JointView.vue';

const stubs = {
  'el-input': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
  'el-input-number': { props: ['modelValue'], template: '<input :value="modelValue" />' },
  'el-select': { props: ['modelValue'], emits: ['update:modelValue'], template: '<select><slot /></select>' },
  'el-option': { template: '<option><slot /></option>' },
  'el-date-picker': { props: ['modelValue'], template: '<input :value="modelValue" />' },
  'el-upload': { template: '<div><slot /></div>' },
  'el-button': { props: ['loading'], emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-tag': { template: '<span><slot /></span>' }
};

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

describe('四大维度任务上下文', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getMyTaskAssignment.mockResolvedValue({
      taskId: 42,
      assignmentId: 1001,
      taskInstanceId: 2001,
      periodKey: '2026-Q3',
      detailItemCode: '1.1',
      uploadCount: 2,
      completed: true,
      lastUploadedAt: '2026-08-31 10:20:00'
    });
    uploadFile.mockResolvedValue({ id: 'file-1' });
    createSubmit.mockResolvedValue(3001);
  });

  it('读取路由任务上下文并展示服务端四维上传进度', async () => {
    const wrapper = mount(JointView, { global: { stubs } });
    await settle();

    expect(getMyTaskAssignment).toHaveBeenCalledWith(1001);
    expect(wrapper.vm.taskContext).toMatchObject({
      taskId: '42',
      assignmentId: '1001',
      periodKey: '2026-Q3',
      detailItemCode: '1.1'
    });
    expect(wrapper.vm.progress).toMatchObject({
      uploadCount: 2,
      completed: true,
      lastUploadedAt: '2026-08-31 10:20:00'
    });
    expect(wrapper.text()).toContain('已上传 2 次');
    expect(wrapper.text()).toContain('本季度已完成');
    wrapper.unmount();
  });

  it('任务上下文提交旧材料时传回任务关联字段和 itemCode，不发送不受支持的 detailItemCode', async () => {
    const wrapper = mount(JointView, { global: { stubs } });
    await settle();
    wrapper.vm.forms['1.1'].unit = '测试单位';
    wrapper.vm.forms['1.1'].conclusion = '重复上传校验';
    await wrapper.vm.submitRecord('1.1');

    expect(createSubmit).toHaveBeenCalledWith(expect.objectContaining({
      taskId: 42,
      taskInstanceId: 2001,
      taskAssignmentId: 1001,
      periodKey: '2026-Q3',
      itemCode: '1.1'
    }));
    expect(createSubmit.mock.calls[0][0]).not.toHaveProperty('detailItemCode');
    expect(wrapper.vm.progress.uploadCount).toBe(3);
    expect(wrapper.vm.progress.completed).toBe(true);
    wrapper.unmount();
  });

  it('没有任务上下文时保留旧四维材料提交契约，不伪造任务关联字段', async () => {
    const previousQuery = routeState.query;
    routeState.query = {};
    let wrapper;
    try {
      wrapper = mount(JointView, { global: { stubs } });
      await settle();
      expect(wrapper.vm.hasTaskContext).toBe(false);
      expect(getMyTaskAssignment).not.toHaveBeenCalled();

      wrapper.vm.forms['1.1'].unit = '测试单位';
      wrapper.vm.forms['1.1'].conclusion = '兼容旧材料上报';
      await wrapper.vm.submitRecord('1.1');

      const payload = createSubmit.mock.calls[0][0];
      expect(payload).toMatchObject({
        dimension: 'dim1',
        itemCode: '1.1',
        itemName: '联建规范度'
      });
      expect(payload).not.toHaveProperty('taskId');
      expect(payload).not.toHaveProperty('taskInstanceId');
      expect(payload).not.toHaveProperty('taskAssignmentId');
      expect(payload).not.toHaveProperty('detailItemCode');
      expect(ElMessage.success).toHaveBeenCalledWith(expect.stringContaining('支部任务处理'));
    } finally {
      wrapper?.unmount();
      routeState.query = previousQuery;
    }
  });
});
