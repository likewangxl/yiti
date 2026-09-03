// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}));

const branchRouteState = vi.hoisted(() => ({ query: {} }));
vi.mock('vue-router', () => ({
  useRoute: () => branchRouteState
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
  getReviewPreview,
  getReviewQueue,
  listBranchTaskReviews,
  rejectBranchTask,
  rejectSubmit,
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

function materialRow(overrides = {}) {
  return {
    id: 17,
    dimension: 'dim1',
    itemCode: '1.1',
    itemName: '联建规范度',
    submitterId: 'U1',
    submitDate: '2026-08-30',
    maxScore: 6,
    formData: JSON.stringify({ 本次上报: '已完成' }),
    fileUrls: JSON.stringify([{ fileObjectId: 'legacy-file-1', fileName: '四维材料.pdf' }]),
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

describe('支部任务处理', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    branchRouteState.query = {};
    getReviewQueue.mockResolvedValue({
      records: [materialRow({ formData: null, fileUrls: null })],
      total: 1
    });
    listBranchTaskReviews.mockResolvedValue({ records: [taskRow()], total: 1 });
    getBranchTaskReview.mockResolvedValue(taskRow());
    getReviewPreview.mockResolvedValue({});
    approveSubmit.mockResolvedValue({});
    approveBranchTask.mockResolvedValue({ status: 'BRANCH_APPROVED' });
    submitBranchTaskToOrg.mockResolvedValue({ status: 'ORG_PENDING' });
    rejectBranchTask.mockResolvedValue({ status: 'REJECTED_BY_BRANCH' });
    rejectSubmit.mockResolvedValue({});
    downloadTaskAttachment.mockResolvedValue(new Blob(['file']));
  });

  it('标题改为任务处理并提供四个页签，旧支部审核工作台文案消失', async () => {
    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();

    expect(wrapper.find('.page-title').text()).toBe('任务处理');
    expect(wrapper.text()).not.toContain('支部审核工作台');
    expect(wrapper.text()).toContain('待处理');
    expect(wrapper.text()).toContain('审核中');
    expect(wrapper.text()).toContain('已通过');
    expect(wrapper.text()).toContain('已驳回');
    expect(wrapper.text()).not.toContain('待审核');
    expect(wrapper.vm.items[0]).toMatchObject({ assignmentId: 1001, nature: 'TEMPORARY', status: 'pending' });
    expect(wrapper.find('.review-card').attributes()).toMatchObject({ role: 'button', tabindex: '0' });
    expect(wrapper.find('.review-card .description').exists()).toBe(false);
    expect(wrapper.find('.review-card .summary').exists()).toBe(false);
    expect(wrapper.find('.review-card .card-actions').exists()).toBe(false);
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

    expect(listBranchTaskReviews).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 20,
      title: '整改',
      taskNature: 'TEMPORARY',
      cycleType: 'MONTH_END',
      tab: 'PENDING'
    });
    const item = wrapper.vm.items.find((row) => row.source === 'task');
    await wrapper.vm.selectItem(item);
    await settle();
    expect(wrapper.vm.showDetailDialog).toBe(true);
    expect(wrapper.find('.task-detail-dialog').text()).toContain('整改已完成');
    expect(wrapper.find('.task-detail-dialog').text()).toContain('整改说明.pdf');
    await wrapper.vm.downloadAttachment(item, taskRow().files[0]);
    expect(downloadTaskAttachment).toHaveBeenCalledWith(42, 1001, 'file-1');
    wrapper.unmount();
  });

  it('列表支持回车打开当前页详情弹窗，关闭后不保留弹窗', async () => {
    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();
    const card = wrapper.find('.review-card');

    await card.trigger('keydown.enter');
    await settle();

    expect(wrapper.vm.showDetailDialog).toBe(true);
    expect(wrapper.find('.detail-dialog').exists()).toBe(true);
    wrapper.vm.showDetailDialog = false;
    await nextTick();
    expect(wrapper.find('.detail-dialog').exists()).toBe(false);
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
    expect(wrapper.vm.statusTotals).toMatchObject({ pending: 1, reviewing: 3 });
    wrapper.unmount();
  });

  it('四大维度 task 通过和提交均走任务工作流接口，不能调用旧材料审核接口', async () => {
    const fourDimensionTask = taskRow({
      taskTitle: '四维材料任务',
      itemName: '联建规范度',
      dimension: 'dim1',
      businessType: 'FOUR_DIMENSION',
      isFourDimension: true,
      legacyReviewId: 17
    });
    listBranchTaskReviews.mockResolvedValueOnce({ records: [fourDimensionTask], total: 1 });
    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();
    const item = wrapper.vm.items[0];

    await wrapper.vm.handleApprove(item);
    expect(approveBranchTask).toHaveBeenCalledWith(1001, { feedback: undefined });
    expect(approveSubmit).not.toHaveBeenCalled();
    expect(item).toMatchObject({ status: 'pending', branchApproved: true });
    expect(wrapper.vm.showSubmitToOrg(item)).toBe(true);

    await wrapper.vm.handleSubmitToOrg(item);
    expect(submitBranchTaskToOrg).toHaveBeenCalledWith(1001, { feedback: undefined });
    expect(item).toMatchObject({ status: 'reviewing', branchApproved: false });
    wrapper.unmount();
  });

  it('四大维度 task 驳回走任务工作流接口，不能调用旧材料驳回接口', async () => {
    const fourDimensionTask = taskRow({
      taskTitle: '四维材料任务',
      itemName: '联建规范度',
      dimension: 'dim1',
      businessType: 'FOUR_DIMENSION',
      isFourDimension: true,
      legacyReviewId: 17
    });
    listBranchTaskReviews.mockResolvedValueOnce({ records: [fourDimensionTask], total: 1 });
    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();
    const item = wrapper.vm.items[0];
    wrapper.vm.openReject(item);
    wrapper.vm.rejectReason = '请补充佐证材料';

    await wrapper.vm.handleConfirmReject();
    expect(rejectBranchTask).toHaveBeenCalledWith(1001, { feedback: '请补充佐证材料' });
    expect(rejectSubmit).not.toHaveBeenCalled();
    expect(item.status).toBe('rejected');
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

  it('同页分别展示任务和旧四维材料，各自保留服务端分页且计数相加', async () => {
    getReviewQueue.mockImplementation(async (params) => ({
      records: params.tab === 'PENDING' ? [materialRow()] : [],
      total: params.tab === 'PENDING' ? 3 : 0
    }));
    listBranchTaskReviews.mockResolvedValue({ records: [taskRow()], total: 7 });
    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();

    expect(listBranchTaskReviews).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 20,
      tab: 'PENDING'
    });
    expect(getReviewQueue).toHaveBeenCalledWith({ pageNo: 1, pageSize: 20, tab: 'PENDING' });
    expect(wrapper.vm.total).toBe(7);
    expect(wrapper.vm.legacyTotal).toBe(3);
    expect(wrapper.vm.statusTotals.pending).toBe(10);
    expect(wrapper.find('[data-source="task"] .review-card').exists()).toBe(true);
    expect(wrapper.find('[data-source="material"] .review-card').exists()).toBe(true);
    expect(wrapper.find('[data-source="task"] .pagination-stub').attributes('data-total')).toBe('7');
    expect(wrapper.find('[data-source="material"] .pagination-stub').attributes('data-total')).toBe('3');

    await wrapper.find('[data-source="material"] .review-card').trigger('click');
    await settle();
    expect(getReviewPreview).toHaveBeenCalledWith(17);
    expect(wrapper.find('[data-test="four-dimension-detail"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('首页 submitId 路由进入任务处理后自动打开对应旧四维材料详情', async () => {
    branchRouteState.query = { tab: 'pending', source: 'material', submitId: '17' };
    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();

    expect(wrapper.vm.showDetailDialog).toBe(true);
    expect(wrapper.vm.selectedItem).toMatchObject({ source: 'material', legacyReviewId: 17 });
    expect(getReviewPreview).toHaveBeenCalledWith(17);
    expect(wrapper.find('[data-test="four-dimension-detail"]').exists()).toBe(true);
    wrapper.unmount();
    branchRouteState.query = {};
  });

  it('首页 submitId 不在旧材料当前页时仍直接加载详情并打开弹窗', async () => {
    branchRouteState.query = { tab: 'pending', source: 'material', submitId: '17' };
    getReviewQueue.mockResolvedValue({ records: [], total: 99 });
    getReviewPreview.mockResolvedValue(materialRow({ id: 17, submitId: 17, content: '跨页材料详情' }));
    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();

    expect(wrapper.vm.showDetailDialog).toBe(true);
    expect(wrapper.vm.selectedItem).toMatchObject({ source: 'material', legacyReviewId: '17' });
    expect(wrapper.find('[data-test="four-dimension-detail"]').text()).toContain('跨页材料详情');
    expect(getReviewPreview).toHaveBeenCalledWith('17');
    await wrapper.vm.handleApprove(wrapper.vm.selectedItem);
    expect(approveSubmit).toHaveBeenCalledWith('17', { feedback: undefined });
    expect(wrapper.vm.legacyTotal).toBe(98);
    expect(wrapper.vm.showDetailDialog).toBe(false);
    wrapper.unmount();
    branchRouteState.query = {};
  });

  it('旧四维材料通过后从待处理当前页移除并同步分页总数', async () => {
    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();
    const item = wrapper.vm.legacyItems[0];
    await wrapper.vm.selectItem(item);
    await settle();

    await wrapper.vm.handleApprove(item);
    expect(approveSubmit).toHaveBeenCalledWith(17, { feedback: undefined });
    expect(item.status).toBe('passed');
    expect(wrapper.vm.legacyItems).toHaveLength(0);
    expect(wrapper.vm.legacyTotal).toBe(0);
    expect(wrapper.vm.showDetailDialog).toBe(false);
    wrapper.unmount();
  });

  it('旧四维材料驳回后从待处理当前页移除并同步分页总数', async () => {
    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();
    const item = wrapper.vm.legacyItems[0];
    await wrapper.vm.selectItem(item);
    await settle();

    wrapper.vm.openReject(item);
    wrapper.vm.rejectReason = '请补充佐证材料';
    await wrapper.vm.handleConfirmReject();
    expect(rejectSubmit).toHaveBeenCalledWith(17, { feedback: '请补充佐证材料' });
    expect(item.status).toBe('rejected');
    expect(wrapper.vm.legacyItems).toHaveLength(0);
    expect(wrapper.vm.legacyTotal).toBe(0);
    expect(wrapper.vm.showDetailDialog).toBe(false);
    wrapper.unmount();
  });

  it('四个状态数量使用各页签分页 total，切页不会把其他计数归零', async () => {
    const totals = { PENDING: 7, REVIEWING: 5, PASSED: 3, REJECTED: 2 };
    const legacyTotals = { PENDING: 11, REVIEWING: 0, PASSED: 13, REJECTED: 17 };
    listBranchTaskReviews.mockImplementation(async (params) => ({
      records: params.tab === 'PENDING' ? [taskRow()] : [],
      total: totals[params.tab]
    }));
    getReviewQueue.mockImplementation(async (params) => ({
      records: [],
      total: legacyTotals[params.tab]
    }));
    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();

    expect(wrapper.vm.statusTotals).toEqual({ pending: 18, reviewing: 5, passed: 16, rejected: 19 });
    expect(wrapper.find('.pending-stat .stat-num').text()).toBe('18');
    expect(wrapper.find('.reviewing-stat .stat-num').text()).toBe('5');
    expect(listBranchTaskReviews).toHaveBeenCalledWith({ pageNo: 1, pageSize: 1, tab: 'REVIEWING' });
    expect(getReviewQueue).toHaveBeenCalledWith({ pageNo: 1, pageSize: 1, tab: 'REVIEWING' });
    wrapper.unmount();
  });

  it('支部审核卡片明确区分任务说明与本次填报内容', async () => {
    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();

    const item = wrapper.vm.items.find((row) => row.source === 'task');
    await wrapper.vm.selectItem(item);
    await settle();

    const dialog = wrapper.find('.task-detail-dialog');
    expect(dialog.find('.description .field-label').text()).toContain('任务说明');
    expect(dialog.find('.summary .field-label').text()).toContain('本次填报内容');
    expect(wrapper.find('.review-card .description').exists()).toBe(false);
    expect(wrapper.find('.review-card .summary').exists()).toBe(false);
    wrapper.unmount();
  });

  it('四大维度详情明确显示维度、任务名称、材料明细编码和结构化材料，并保留任务附件', async () => {
    const fourDimensionRow = taskRow({
      taskTitle: '四大维度材料任务',
      businessType: 'FOUR_DIMENSION',
      isFourDimension: true,
      legacyReviewId: 17,
      formData: { 初始材料: '已归档' },
      files: [{ fileId: 'task-file-1', fileName: '上报附件.pdf' }]
    });
    listBranchTaskReviews.mockResolvedValueOnce({ records: [fourDimensionRow], total: 1 });
    getBranchTaskReview.mockResolvedValueOnce({
      ...fourDimensionRow,
      dimensionCode: 'dim2',
      itemCode: '2.1',
      formData: JSON.stringify({ 本次上报: '完成联建' }),
      content: '材料预览内容',
      files: []
    });

    const wrapper = mount(BranchReviewView, { global: { stubs } });
    await settle();
    const item = wrapper.vm.items[0];
    await wrapper.vm.selectItem(item);
    await settle();

    expect(getBranchTaskReview).toHaveBeenCalledWith(1001);
    expect(getReviewPreview).not.toHaveBeenCalled();
    expect(wrapper.find('[data-test="four-dimension-detail"]').exists()).toBe(true);
    const dialogText = wrapper.find('.detail-dialog').text();
    expect(dialogText).toContain('业务提升');
    expect(dialogText).toContain('2.1');
    expect(dialogText).toContain('四大维度材料任务');
    expect(dialogText).toContain('结构化材料');
    expect(dialogText).toContain('本次上报');
    expect(dialogText).toContain('材料预览内容');
    expect(dialogText).toContain('上报附件.pdf');
    expect(item.files).toEqual([{ fileId: 'task-file-1', fileName: '上报附件.pdf' }]);

    await wrapper.vm.downloadAttachment(item, item.files[0]);
    expect(downloadTaskAttachment).toHaveBeenCalledWith(42, 1001, 'task-file-1');
    wrapper.unmount();
  });
});
