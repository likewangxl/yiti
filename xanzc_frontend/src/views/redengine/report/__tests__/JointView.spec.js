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

  it('任务上下文提交旧材料时传回 taskId、assignment、期间和明细编码，并保留重复上传', async () => {
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
      detailItemCode: '1.1',
      itemCode: '1.1'
    }));
    expect(wrapper.vm.progress.uploadCount).toBe(3);
    expect(wrapper.vm.progress.completed).toBe(true);
    wrapper.unmount();
  });
});
