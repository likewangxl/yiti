// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}));

vi.mock('@/api/redengine', () => ({
  approveBranchTask: vi.fn(),
  approveSubmit: vi.fn(),
  downloadTaskAttachment: vi.fn(),
  getBranchTaskReview: vi.fn(),
  getReviewQueue: vi.fn(),
  getReviewPreview: vi.fn(),
  listBranchTaskReviews: vi.fn(),
  rejectBranchTask: vi.fn(),
  rejectSubmit: vi.fn(),
  submitBranchTaskToOrg: vi.fn()
}));

import { ElMessage } from 'element-plus';
import {
  approveBranchTask,
  approveSubmit,
  downloadTaskAttachment,
  getBranchTaskReview,
  getReviewQueue,
  listBranchTaskReviews,
  rejectBranchTask,
  submitBranchTaskToOrg
} from '@/api/redengine';
import BranchReviewView from '../BranchReviewView.vue';

const stubs = {
  'el-radio-group': { props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot /></div>' },
  'el-radio-button': { props: ['label'], template: '<button class="tab-button">{{ label }}<slot /></button>' },
  'el-input': {
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue'],
    template: '<textarea :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-button': { props: ['loading', 'disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
  'el-dialog': { props: ['modelValue'], template: '<div v-if="modelValue" class="dialog-stub"><slot /><slot name="footer" /></div>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-pagination': { props: ['total'], template: '<div class="pagination-stub" :data-total="total" />' }
};

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

function taskRow(overrides = {}) {
  return {
    taskId: 42,
    assignmentId: 1001,
    taskTitle: '专项整改任务',
    taskDescription: '请提交整改情况',
    taskNature: 'TEMPORARY',
    businessType: 'GENERAL',
    status: 'BRANCH_PENDING',
    branchName: '第一党支部',
    submitterName: '张伟',
    submittedAt: '2026-08-31 10:00:00',
    content: '整改已完成',
    files: [{ fileId: 'file-1', fileName: '整改说明.pdf' }],
    ...overrides
  };
}

function recurringGeneralTaskRow(overrides = {}) {
  return taskRow({
    taskId: 5,
    assignmentId: 5,
    taskTitle: '每月初经营分析填报',
    taskDescription: '请按每月初要求填报经营分析',
    taskNature: 'RECURRING',
    businessType: 'GENERAL',
    cycleType: 'MONTH_START',
    ...overrides
  });
}

describe('支部审核工作台', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getReviewQueue.mockResolvedValue({
      records: [{
        id: 17,
        dimension: 'dim1',
        itemCode: '1.1',
        itemName: '联建规范度',
        submitterId: 'U1',
        submitDate: '2026-08-30',
        maxScore: 6,
        formData: null,
        fileUrls: null
      }],
      total: 1
    });
    listBranchTaskReviews.mockResolvedValue({ records: [taskRow()], total: 1 });
    getBranchTaskReview.mockResolvedValue(taskRow());
    approveSubmit.mockResolvedValue({});
    approveBranchTask.mockResolvedValue({ status: 'BRANCH_APPROVED' });
    submitBranchTaskToOrg.mockResolvedValue({ status: 'ORG_PENDING' });
    rejectBranchTask.mockResolvedValue({ status: 'REJECTED_BY_BRANCH' });
    downloadTaskAttachment.mockResolvedValue(new Blob(['file']));
  });

  it('标题改为支部审核工作台并提供四个页签，旧待审核文案消失', async () => {
    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();

    expect(wrapper.find('.page-title').text()).toBe('支部审核工作台');
    expect(wrapper.text()).toContain('待处理');
    expect(wrapper.text()).toContain('审核中');
    expect(wrapper.text()).toContain('已通过');
    expect(wrapper.text()).toContain('已驳回');
    expect(wrapper.text()).not.toContain('待审核');
    expect(wrapper.vm.items[0]).toMatchObject({ assignmentId: 1001, nature: 'TEMPORARY', status: 'pending' });
    expect(wrapper.find('.review-card').attributes()).toMatchObject({ role: 'button', tabindex: '0' });
    wrapper.unmount();
  });

  it('查询任务标题、性质和周期，并显示临时任务正文与附件下载入口', async () => {
    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();

    wrapper.vm.query.title = '整改';
    wrapper.vm.query.nature = 'TEMPORARY';
    wrapper.vm.query.cycle = 'MONTH_END';
    await wrapper.vm.handleSearch();
    await settle();

    expect(listBranchTaskReviews).toHaveBeenLastCalledWith({
      pageNo: 1,
      pageSize: 20,
      title: '整改',
      taskNature: 'TEMPORARY',
      cycleType: 'MONTH_END',
      tab: 'PENDING'
    });
    expect(wrapper.text()).toContain('整改已完成');
    expect(wrapper.text()).toContain('整改说明.pdf');
    await wrapper.vm.downloadAttachment(wrapper.vm.items.find((item) => item.source === 'task'), taskRow().files[0]);
    expect(downloadTaskAttachment).toHaveBeenCalledWith(42, 1001, 'file-1');
    wrapper.unmount();
  });

  it('普通定时任务卡片显示定时任务和每月初，不把 GENERAL 当成临时任务', async () => {
    const recurringTask = recurringGeneralTaskRow();
    listBranchTaskReviews.mockResolvedValueOnce({ records: [recurringTask], total: 1 });

    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();

    const item = wrapper.vm.items[0];
    expect(item).toMatchObject({
      nature: 'RECURRING',
      cycle: 'MONTH_START',
      isPeriodic: true,
      isTemporary: false,
      isTask: true,
      dim: '定时任务'
    });
    expect(wrapper.find('.dim-badge').text()).toBe('定时任务');
    expect(wrapper.text()).toContain('每月初');
    wrapper.unmount();
  });

  it('支部通过后仍留在待处理并显示提交至组织审核，单独提交后进入审核中', async () => {
    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();
    const item = wrapper.vm.items.find((row) => row.source === 'task');
    wrapper.vm.selectItem(item);
    await settle();

    await wrapper.vm.handleApprove(item);
    expect(approveBranchTask).toHaveBeenCalledWith(1001, { feedback: undefined });
    expect(item.status).toBe('pending');
    expect(item.branchApproved).toBe(true);
    expect(wrapper.vm.showSubmitToOrg(item)).toBe(true);

    await wrapper.vm.handleSubmitToOrg(item);
    expect(submitBranchTaskToOrg).toHaveBeenCalledWith(1001, { feedback: undefined });
    expect(item.status).toBe('reviewing');
    expect(item.branchApproved).toBe(false);
    wrapper.unmount();
  });

  it('后端返回 BRANCH_PENDING assignment 和 BRANCH_APPROVED submission 时仍归入待处理', async () => {
    listBranchTaskReviews.mockResolvedValue({
      records: [taskRow({ status: 'BRANCH_PENDING', submissionStatus: 'BRANCH_APPROVED' })],
      total: 1
    });
    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();

    const item = wrapper.vm.items.find((row) => row.source === 'task');
    expect(item).toMatchObject({ status: 'pending', branchApproved: true });
    expect(wrapper.vm.showSubmitToOrg(item)).toBe(true);
    wrapper.unmount();
  });

  it('支部驳回意见必填，提交后任务进入已驳回', async () => {
    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();
    const item = wrapper.vm.items.find((row) => row.source === 'task');
    wrapper.vm.openReject(item);
    await wrapper.vm.handleConfirmReject();
    expect(rejectBranchTask).not.toHaveBeenCalled();
    expect(ElMessage.warning).toHaveBeenCalledWith('请填写驳回意见');

    wrapper.vm.rejectReason = '请补充附件';
    await wrapper.vm.handleConfirmReject();
    expect(rejectBranchTask).toHaveBeenCalledWith(1001, { feedback: '请补充附件' });
    expect(item.status).toBe('rejected');
    wrapper.unmount();
  });

  it('队列请求失败时展示错误态而不是误报暂无记录', async () => {
    listBranchTaskReviews.mockRejectedValueOnce(new Error('服务不可用'));
    getReviewQueue.mockRejectedValueOnce(new Error('服务不可用'));
    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();

    expect(wrapper.vm.loadError).toContain('任务审核队列加载失败');
    expect(wrapper.find('[role="alert"]').text()).toContain('任务审核队列加载失败');
    expect(wrapper.find('.empty-state').exists()).toBe(false);
    wrapper.unmount();
  });

  it('审核队列由服务端页签和状态分页，不再合并旧材料队列', async () => {
    getReviewQueue.mockResolvedValue({ records: [{ id: 17 }], total: 99 });
    listBranchTaskReviews.mockResolvedValue({ records: [taskRow()], total: 7 });
    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();

    expect(listBranchTaskReviews).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 20,
      tab: 'PENDING'
    });
    expect(getReviewQueue).not.toHaveBeenCalled();
    expect(wrapper.vm.total).toBe(7);
    wrapper.unmount();
  });
});
