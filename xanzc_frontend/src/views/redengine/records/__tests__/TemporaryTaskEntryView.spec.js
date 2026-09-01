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
    expect(wrapper.find('.entry-kicker').text()).toBe('临时任务填报');
    expect(wrapper.text()).toContain('任务性质：临时任务');
    wrapper.unmount();
  });

  it('普通定时任务详情按 taskNature 展示定时任务填报和性质', async () => {
    getMyTaskAssignment.mockResolvedValueOnce({
      assignmentId: 1005,
      taskId: 5,
      taskTitle: '每月初经营分析填报',
      taskDescription: '请按每月初要求填报经营分析',
      taskNature: 'RECURRING',
      businessType: 'GENERAL',
      cycleType: 'MONTH_START',
      windowStartAt: '2026-09-01T00:00:00',
      windowEndAt: '2026-09-05T23:59:59',
      requiresFile: false,
      content: '',
      files: []
    });

    const wrapper = mount(TemporaryTaskEntryView, { global: { stubs } });
    await settle();

    expect(wrapper.find('.entry-kicker').text()).toBe('定时任务填报');
    expect(wrapper.text()).toContain('任务性质：定时任务');
    wrapper.unmount();
  });

  it('兼容任务分配接口返回的 windowStartAt/windowEndAt 时间窗', async () => {
    getMyTaskAssignment.mockResolvedValueOnce({
      assignmentId: 1001,
      taskId: 42,
      taskTitle: '窗口字段任务',
      taskDescription: '请按时间窗填报',
      taskNature: 'TEMPORARY',
      windowStartAt: '2026-09-01T09:00:00',
      windowEndAt: '2026-09-03T18:00:00',
      requiresFile: false,
      content: '',
      files: []
    });

    const wrapper = mount(TemporaryTaskEntryView, { global: { stubs } });
    await settle();

    expect(wrapper.text()).toContain('2026-09-01T09:00:00');
    expect(wrapper.text()).toContain('2026-09-03T18:00:00');
    wrapper.unmount();
  });

  it('不要求上传文件时不展示文件输入和附件行', async () => {
    getMyTaskAssignment.mockResolvedValueOnce({
      assignmentId: 1001,
      taskId: 42,
      taskTitle: '无需附件任务',
      taskDescription: '请直接填报文本',
      taskNature: 'TEMPORARY',
      windowStartAt: '2026-09-01T09:00:00',
      windowEndAt: '2026-09-03T18:00:00',
      requiresFile: false,
      content: '',
      files: []
    });

    const wrapper = mount(TemporaryTaskEntryView, { global: { stubs } });
    await settle();

    expect(wrapper.find('input[type="file"]').exists()).toBe(false);
    expect(wrapper.find('.file-row').exists()).toBe(false);
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

  it('任务详情请求失败时展示错误态而不是误报任务不存在', async () => {
    getMyTaskAssignment.mockRejectedValueOnce(new Error('服务不可用'));
    const wrapper = mount(TemporaryTaskEntryView, { global: { stubs } });
    await settle();

    expect(wrapper.vm.loadError).toBe('任务加载失败，请稍后重试');
    expect(wrapper.find('[role="alert"]').text()).toContain('任务加载失败');
    expect(wrapper.find('.empty-stub').exists()).toBe(false);
    wrapper.unmount();
  });
});
