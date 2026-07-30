// @vitest-environment happy-dom
// Task 17d 联调发现的逻辑缺陷回归测试：
// ReviewView.vue 的 `watch(selectedItem, ...)` 在任意 selectedItem 引用变化时都无条件把
// finalScore 重置为 0。但 handleApprove/handleConfirmReject 成功后会执行
// `selectedItem.value = { ...item }`（同一条目，仅更新 status 字段）来触发视图刷新，
// 这个"同条目重赋值"同样会命中该 watch，把刚提交的评分显示重置为 0——与下方
// "已通过，计 X 分" 结果横幅同时出现在页面上，视觉自相矛盾（Playwright 联调截图复现）。
// 更隐蔽的影响：selectItem() 里 getReviewPreview 异步返回后也会重赋值 selectedItem
// （同 id），若用户在预览加载期间已经开始手填分数，预览返回时会把用户刚输入的分值静默清零。
// 正确语义应为：仅当 selectedItem 变为"不同 id 的条目"时才重置评分，同条目重赋值不应清零。
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}));

vi.mock('@/api/redengine', () => ({
  getReviewQueue: vi.fn(),
  getReviewPreview: vi.fn(),
  approveSubmit: vi.fn(),
  rejectSubmit: vi.fn()
}));

import { getReviewQueue, getReviewPreview, approveSubmit } from '@/api/redengine';
import ReviewView from '../review/ReviewView.vue';

// 桩使用手写可交互的 el-input-number/el-input，避免依赖 Element Plus 全量挂载
// （支持 v-model），避免测试环境需要真实安装 ElementPlus 插件。
const stubs = {
  'el-input-number': {
    name: 'ElInputNumber',
    props: ['modelValue'],
    template:
      '<input class="score-stub" type="number" :value="modelValue" ' +
      '@input="$emit(\'update:modelValue\', Number($event.target.value))" />'
  },
  'el-input': {
    name: 'ElInput',
    props: ['modelValue'],
    template:
      '<textarea class="comment-stub" :value="modelValue" ' +
      '@input="$emit(\'update:modelValue\', $event.target.value)"></textarea>'
  },
  'el-button': { name: 'ElButton', emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-dialog': { name: 'ElDialog', props: ['modelValue'], template: '<div><slot /><slot name="footer" /></div>' },
  'el-tag': { template: '<span><slot /></span>' }
};

let wrapper;
beforeEach(() => {
  vi.clearAllMocks();
});
afterEach(() => {
  wrapper?.unmount();
});

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

describe('沉浸式审核工作台(ReviewView) - 审核通过后评分展示', () => {
  it('审核通过后评分输入框应保留已提交分值，不应被重置为 0', async () => {
    const queueRow = {
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
    getReviewQueue.mockResolvedValue({ records: [queueRow], total: 1 });
    getReviewPreview.mockResolvedValue(queueRow);
    approveSubmit.mockResolvedValue({});

    wrapper = mount(ReviewView, { global: { stubs } });
    await settle();

    await wrapper.find('.queue-item').trigger('click');
    await settle();

    const scoreInput = wrapper.find('.score-stub');
    await scoreInput.setValue(5);
    await settle();
    expect(Number(scoreInput.element.value)).toBe(5);

    const approveBtn = wrapper.findAll('button').find((b) => b.text().includes('通过并计'));
    await approveBtn.trigger('click');
    await settle();

    expect(approveSubmit).toHaveBeenCalledWith(17, { score: 5, feedback: undefined });
    // 核心断言：审核通过后评分输入框应仍显示刚提交的分值，而非被 watch 误重置为 0
    expect(Number(wrapper.find('.score-stub').element.value)).toBe(5);
  });
});
