// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}));

const routerPush = vi.fn();
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { assignmentId: '1001' }, query: {} }),
  useRouter: () => ({ push: routerPush })
}));

vi.mock('@/api/redengine', () => ({
  getMyTaskAssignment: vi.fn(),
  submitTask: vi.fn(),
  uploadFile: vi.fn()
}));

import { ElMessage } from 'element-plus';
import { getMyTaskAssignment, submitTask, uploadFile } from '@/api/redengine';
import TemporaryTaskEntryView from '../TemporaryTaskEntryView.vue';

const stubs = {
  'el-button': { props: ['loading', 'disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
  'el-empty': { template: '<div class="empty-stub"><slot /></div>' }
};

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

describe('临时任务填报', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getMyTaskAssignment.mockResolvedValue({
      assignmentId: 1001,
      taskId: 42,
      taskTitle: '专项整改任务',
      taskDescription: '请填报整改情况，参考 https://example.com/guide。',
      taskNature: 'TEMPORARY',
      businessType: 'GENERAL',
      temporaryStartTime: '2026-08-25 09:00:00',
      temporaryEndTime: '2026-08-31 18:00:00',
      requiresFile: true,
      fileTypeCodes: ['PDF', 'DOCX'],
      maxFileSizeBytes: 5 * 1024 * 1024,
      content: '',
      files: []
    });
    uploadFile.mockResolvedValue({ fileObjectId: 'file-1' });
    submitTask.mockResolvedValue({ status: 'BRANCH_PENDING' });
  });

  it('加载任务说明、时间窗和后端下发的文件策略', async () => {
    const wrapper = mount(TemporaryTaskEntryView, { global: { stubs } });
    await settle();

    expect(getMyTaskAssignment).toHaveBeenCalledWith(1001);
    expect(wrapper.find('.page-title').text()).toBe('专项整改任务');
    expect(wrapper.text()).toContain('2026-08-25 09:00:00');
    expect(wrapper.text()).toContain('PDF、DOCX');
    expect(wrapper.text()).toContain('5 MB');
    expect(wrapper.find('[data-test="description-link"]').attributes('href')).toBe('https://example.com/guide');
    wrapper.unmount();
  });

  it('提交文本和附件时先上传文件，再以 assignmentId 和幂等号提交并进入审核中', async () => {
    const wrapper = mount(TemporaryTaskEntryView, { global: { stubs } });
    await settle();
    wrapper.vm.form.content = '整改已完成，详见附件';
    wrapper.vm.selectedFiles = [new File(['proof'], '整改说明.pdf', { type: 'application/pdf' })];

    await wrapper.vm.handleSubmit();
    await settle();

    expect(uploadFile).toHaveBeenCalledTimes(1);
    expect(submitTask).toHaveBeenCalledWith(expect.objectContaining({
      assignmentId: 1001,
      content: '整改已完成，详见附件',
      fileObjectIds: ['file-1'],
      clientRequestId: expect.any(String)
    }));
    expect(ElMessage.success).toHaveBeenCalledWith('提交成功，已进入审核中');
    expect(routerPush).toHaveBeenCalledWith('/redengine/records');
    wrapper.unmount();
  });

  it('没有正文时阻止提交，不伪造成功状态', async () => {
    const wrapper = mount(TemporaryTaskEntryView, { global: { stubs } });
    await settle();
    wrapper.vm.form.content = '   ';

    await wrapper.vm.handleSubmit();

    expect(submitTask).not.toHaveBeenCalled();
    expect(ElMessage.warning).toHaveBeenCalledWith('请填写填报内容');
    wrapper.unmount();
  });
});
