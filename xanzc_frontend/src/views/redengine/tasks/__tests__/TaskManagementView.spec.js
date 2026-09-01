// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}));

vi.mock('@/api/redengine', () => ({
  listTasks: vi.fn()
}));

import { listTasks } from '@/api/redengine';
import TaskManagementView from '../TaskManagementView.vue';

const source = readFileSync(resolve(process.cwd(), 'src/views/redengine/tasks/TaskManagementView.vue'), 'utf8');

const stubs = {
  'el-icon': { template: '<span><slot /></span>' },
  'el-card': { template: '<section><slot name="header" /><slot /></section>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { props: ['label'], template: '<label><span>{{ label }}</span><slot /></label>' },
  'el-input': {
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue', 'keyup'],
    template: '<input :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-select': {
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue'],
    template: '<select :data-placeholder="placeholder" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>'
  },
  'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  'el-button': { props: ['loading'], emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-table': { props: ['data'], template: '<div class="table-stub"><slot /></div>' },
  'el-table-column': { props: ['label', 'prop'], template: '<div class="column-stub" :data-label="label" />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-pagination': {
    props: ['currentPage', 'pageSize', 'total'],
    emits: ['current-change', 'size-change'],
    template: '<div class="pagination-stub"><button class="next" @click="$emit(\'current-change\', Number(currentPage) + 1)">下一页</button></div>'
  },
  'el-empty': { template: '<div class="empty-stub"><slot /></div>' }
};

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

describe('任务管理列表', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    listTasks.mockResolvedValue({
      records: [{
        taskId: 42,
        title: '季度材料上报',
        nature: 'SCHEDULED',
        typeName: '四大维度材料上报',
        cycle: 'QUARTER_END',
        startAt: '2026-09-26',
        endAt: '2026-09-30',
        publishedAt: '2026-09-01 09:00:00',
        targetCount: 8,
        submittedCount: 6,
        status: 'PUBLISHED'
      }],
      total: 21
    });
  });

  it('查询条件采用响应式网格并让查询操作始终完整可见', () => {
    expect(source).toMatch(/class="filter-form"/);
    expect(source).toMatch(/class="filter-control"/);
    expect(source).toMatch(/class="filter-actions"/);
    expect(source).toMatch(/\.filter-form\s*\{[^}]*display:\s*grid/);
    expect(source).toMatch(/grid-template-columns:\s*repeat\(auto-fit,\s*minmax\(/);
    expect(source).toMatch(/\.filter-control\s*\{[^}]*width:\s*100%\s*!important/);
    expect(source).toMatch(/\.filter-actions\s*\{[^}]*display:\s*flex/);
    expect(source).not.toMatch(/min-width:\s*(?:\d{4,}|\d{3,}px)/);
  });

  it('首次加载分页列表并展示任务管理标题、任务摘要字段', async () => {
    const wrapper = mount(TaskManagementView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    expect(wrapper.find('.page-title').text()).toBe('任务管理');
    expect(listTasks).toHaveBeenCalledWith({ pageNo: 1, pageSize: 10, status: 'PUBLISHED' });
    expect(wrapper.vm.rows[0]).toMatchObject({
      taskId: 42,
      title: '季度材料上报',
      targetCount: 8,
      submittedCount: 6
    });
    expect(wrapper.find('.pagination-stub').exists()).toBe(true);
    wrapper.unmount();
  });

  it('任务类型查询使用后端业务类型枚举，而不是自由文本', async () => {
    const wrapper = mount(TaskManagementView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    const typeSelect = wrapper.findAll('select')
      .find((select) => select.text().includes('四大维度材料上报'));
    expect(typeSelect).toBeDefined();
    expect(typeSelect.text()).toContain('普通任务');
    wrapper.unmount();
  });

  it('点击查询重置到第一页并携带任务标题和性质', async () => {
    const wrapper = mount(TaskManagementView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    wrapper.vm.query.title = '整改';
    wrapper.vm.query.nature = 'TEMPORARY';
    await wrapper.vm.handleSearch();
    await settle();

    expect(listTasks).toHaveBeenLastCalledWith({
      pageNo: 1,
      pageSize: 10,
      title: '整改',
      taskNature: 'TEMPORARY',
      status: 'PUBLISHED'
    });
    wrapper.unmount();
  });

  it('翻页沿用已提交的查询条件', async () => {
    const wrapper = mount(TaskManagementView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();
    wrapper.vm.query.title = '季度';
    wrapper.vm.query.nature = 'SCHEDULED';
    await wrapper.vm.handleSearch();
    await settle();

    await wrapper.find('.next').trigger('click');
    await settle();

    expect(listTasks).toHaveBeenLastCalledWith({
      pageNo: 2,
      pageSize: 10,
      title: '季度',
      taskNature: 'PERIODIC',
      status: 'PUBLISHED'
    });
    wrapper.unmount();
  });

  it('列表请求失败时展示错误态而不是误报暂无任务', async () => {
    listTasks.mockRejectedValueOnce(new Error('服务不可用'));
    const wrapper = mount(TaskManagementView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    expect(wrapper.vm.loadError).toBe('任务列表加载失败，请稍后重试');
    expect(wrapper.find('[role="alert"]').text()).toContain('任务列表加载失败');
    expect(wrapper.find('.empty-stub').exists()).toBe(false);
    wrapper.unmount();
  });
});
