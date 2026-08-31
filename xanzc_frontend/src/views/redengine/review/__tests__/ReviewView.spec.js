// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}));

vi.mock('@/api/redengine', () => ({
  approveOrgTask: vi.fn(),
  approveSubmit: vi.fn(),
  downloadTaskAttachment: vi.fn(),
  getOrgTaskReview: vi.fn(),
  getReviewPreview: vi.fn(),
  getReviewQueue: vi.fn(),
  listOrgTaskReviews: vi.fn(),
  rejectOrgTask: vi.fn(),
  rejectSubmit: vi.fn()
}));

import { ElMessage } from 'element-plus';
import {
  approveOrgTask,
  approveSubmit,
  downloadTaskAttachment,
  getOrgTaskReview,
  getReviewPreview,
  getReviewQueue,
  listOrgTaskReviews,
  rejectOrgTask
} from '@/api/redengine';
import ReviewView from '../ReviewView.vue';

const stubs = {
  'el-input-number': {
    name: 'ElInputNumber',
    props: ['modelValue'],
    template: '<input class="score-stub" type="number" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />'
  },
  'el-input': {
    name: 'ElInput',
    props: ['modelValue', 'placeholder'],
    template: '<textarea class="comment-stub" :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)"></textarea>'
  },
  'el-select': {
    name: 'ElSelect',
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue'],
    template: '<select class="nature-filter" :value="modelValue" :data-placeholder="placeholder" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>'
  },
  'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  'el-radio-group': { props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot /></div>' },
  'el-radio-button': { props: ['label'], template: '<button class="tab-button">{{ label }}<slot /></button>' },
  'el-button': { name: 'ElButton', props: ['loading'], emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-dialog': { name: 'ElDialog', props: ['modelValue'], template: '<div v-if="modelValue" class="dialog-stub"><slot /><slot name="footer" /></div>' },
  'el-tag': { template: '<span><slot /></span>' }
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
    status: 'ORG_PENDING',
    branchName: '第一党支部',
    submitterName: '张伟',
    submittedAt: '2026-08-31 10:00:00',
    content: '整改已完成',
    files: [{ fileId: 'file-1', fileName: '整改说明.pdf' }],
    ...overrides
  };
}

describe('组织审核工作台', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getReviewQueue.mockResolvedValue({ records: [], total: 0 });
    getReviewPreview.mockResolvedValue(null);
    listOrgTaskReviews.mockResolvedValue({ records: [taskRow()], total: 1 });
    getOrgTaskReview.mockResolvedValue(taskRow());
    approveSubmit.mockResolvedValue({});
    approveOrgTask.mockResolvedValue({ status: 'APPROVED' });
    rejectOrgTask.mockResolvedValue({ status: 'REJECTED_BY_ORG' });
    downloadTaskAttachment.mockResolvedValue(new Blob(['file']));
  });

  it('标题为工作台并提供待处理、审核中、已通过、已驳回四个页签', async () => {
    const wrapper = mount(ReviewView, { global: { stubs } });
    await settle();

    expect(wrapper.find('.page-title').text()).toBe('工作台');
    expect(wrapper.text()).toContain('待处理');
    expect(wrapper.text()).toContain('审核中');
    expect(wrapper.text()).toContain('已通过');
    expect(wrapper.text()).toContain('已驳回');
    expect(wrapper.text()).toContain('四大维度材料上报');
    expect(wrapper.text()).toContain('临时任务');
    wrapper.unmount();
  });

  it('左侧类型筛选和查询条件传给组织任务队列', async () => {
    const wrapper = mount(ReviewView, { global: { stubs } });
    await settle();

    wrapper.vm.query.title = '整改';
    wrapper.vm.query.nature = 'TEMPORARY';
    await wrapper.vm.handleSearch();
    await settle();

    expect(listOrgTaskReviews).toHaveBeenLastCalledWith({
      pageNo: 1,
      pageSize: 50,
      title: '整改',
      taskNature: 'TEMPORARY',
      assignmentStatus: 'ORG_PENDING'
    });
    wrapper.unmount();
  });

  it('临时任务展示填报内容和可下载附件，不展示评分区', async () => {
    const wrapper = mount(ReviewView, { global: { stubs } });
    await settle();
    const item = wrapper.vm.reviewItems.find((row) => row.source === 'task');

    await wrapper.vm.selectItem(item);
    await settle();

    expect(wrapper.text()).toContain('整改已完成');
    expect(wrapper.text()).toContain('整改说明.pdf');
    expect(wrapper.find('.scoring-panel').exists()).toBe(false);
    await wrapper.vm.downloadAttachment(item, item.files[0]);
    expect(downloadTaskAttachment).toHaveBeenCalledWith(42, 1001, 'file-1');
    wrapper.unmount();
  });

  it('临时任务驳回意见必填，通过和驳回使用组织任务接口', async () => {
    const wrapper = mount(ReviewView, { global: { stubs } });
    await settle();
    const item = wrapper.vm.reviewItems.find((row) => row.source === 'task');
    await wrapper.vm.selectItem(item);
    await settle();

    await wrapper.vm.handleApprove();
    expect(approveOrgTask).toHaveBeenCalledWith(1001, { feedback: undefined });
    expect(item.status).toBe('passed');

    item.status = 'pending';
    await wrapper.vm.handleRejectClick();
    await wrapper.vm.handleConfirmReject();
    expect(rejectOrgTask).not.toHaveBeenCalled();
    expect(ElMessage.warning).toHaveBeenCalledWith('请填写驳回意见');

    wrapper.vm.rejectReason = '请补充附件';
    await wrapper.vm.handleConfirmReject();
    expect(rejectOrgTask).toHaveBeenCalledWith(1001, { feedback: '请补充附件' });
    expect(item.status).toBe('rejected');
    wrapper.unmount();
  });

  it('四大维度材料仍保留评分区和原审核接口', async () => {
    const material = {
      id: 17,
      orgId: 1,
      dimension: 'dim1',
      itemCode: '1.1',
      itemName: '联建规范度',
      submitterId: 'admin',
      submitDate: '2026-07-19',
      maxScore: 6,
      formData: null,
      fileUrls: null
    };
    getReviewQueue.mockResolvedValue({ records: [material], total: 1 });
    getReviewPreview.mockResolvedValue(material);

    const wrapper = mount(ReviewView, { global: { stubs } });
    await settle();
    const item = wrapper.vm.reviewItems.find((row) => row.source === 'material');
    await wrapper.vm.selectItem(item);
    await settle();

    expect(wrapper.find('.scoring-panel').exists()).toBe(true);
    wrapper.vm.finalScore = 5;
    await wrapper.vm.handleApprove();
    expect(approveSubmit).toHaveBeenCalledWith(17, { score: 5, feedback: undefined });
    wrapper.unmount();
  });

  it('任务列表中的四大维度任务仍按原评分链路处理', async () => {
    const materialTask = taskRow({
      businessType: 'FOUR_DIMENSION',
      taskNature: 'PERIODIC',
      taskTitle: '季度材料上报',
      reviewId: 17
    });
    listOrgTaskReviews.mockResolvedValue({ records: [materialTask], total: 1 });
    getOrgTaskReview.mockResolvedValue(materialTask);

    const wrapper = mount(ReviewView, { global: { stubs } });
    await settle();
    const item = wrapper.vm.reviewItems.find((row) => row.source === 'task');
    await wrapper.vm.selectItem(item);
    await settle();

    expect(item.isFourDimension).toBe(true);
    expect(wrapper.find('.scoring-panel').exists()).toBe(true);
    wrapper.vm.finalScore = 4;
    await wrapper.vm.handleApprove();
    expect(approveSubmit).toHaveBeenCalledWith(17, { score: 4, feedback: undefined });
    wrapper.unmount();
  });
});
