// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}));

const routerPush = vi.fn();
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { taskId: '42' } }),
  useRouter: () => ({ push: routerPush })
}));

vi.mock('@/api/redengine', () => ({
  createTaskExport: vi.fn(),
  downloadTaskAttachment: vi.fn(),
  downloadTaskExport: vi.fn(),
  getTaskDetail: vi.fn(),
  getTaskExportStatus: vi.fn(),
  listMaterialDetailItems: vi.fn(),
  listTaskAssignments: vi.fn()
}));

import {
  createTaskExport,
  downloadTaskAttachment,
  downloadTaskExport,
  getTaskDetail,
  getTaskExportStatus,
  listMaterialDetailItems,
  listTaskAssignments
} from '@/api/redengine';
import TaskDetailView from '../TaskDetailView.vue';

const stubs = {
  'el-icon': { template: '<span><slot /></span>' },
  'el-card': { template: '<section><slot name="header" /><slot /></section>' },
  'el-descriptions': { template: '<div><slot /></div>' },
  'el-descriptions-item': { props: ['label'], template: '<span><b>{{ label }}</b><slot /></span>' },
  'el-table': { props: ['data'], template: '<div class="table-stub"><slot /></div>' },
  'el-table-column': { props: ['label', 'prop'], template: '<div class="column-stub" :data-label="label" />' },
  'el-input': {
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue'],
    template: '<input :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-button': { props: ['loading', 'disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
  'el-dialog': { props: ['modelValue', 'title'], template: '<div v-if="modelValue" class="dialog-stub"><slot /><slot name="footer" /></div>' },
  'el-checkbox-group': { props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot /></div>' },
  'el-checkbox': { props: ['label'], template: '<label><input type="checkbox" :value="label" /><slot /></label>' },
  'el-pagination': { props: ['total'], template: '<div class="pagination-stub" :data-total="total" />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-empty': { template: '<div class="empty-stub"><slot /></div>' }
};

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

describe('任务详情', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getTaskDetail.mockResolvedValue({
      taskId: 42,
      title: '临时整改',
      description: '请填报情况 https://example.com/guide',
      taskNature: 'TEMPORARY',
      businessType: 'GENERAL',
      typeName: '整改反馈',
      audienceType: 'ALL_BRANCH',
      temporaryStartTime: '2026-08-20 09:00:00',
      temporaryEndTime: '2026-08-31 18:00:00',
      publishTime: '2026-08-19 10:00:00',
      requiresFile: true,
      fileTypeCodes: ['PDF']
    });
    listTaskAssignments.mockResolvedValue({
      records: [{
        assignmentId: 1001,
        branchId: 11,
        branchName: '第一党支部',
        stage: 'UNREPORTED',
        submitterName: null,
        submittedAt: null,
        content: null,
        files: []
      }],
      total: 1
    });
    listMaterialDetailItems.mockResolvedValue([]);
    createTaskExport.mockResolvedValue({ exportId: 'export-1' });
    getTaskExportStatus.mockResolvedValue({ exportId: 'export-1', status: 'SUCCESS', totalRows: 1, sheetCount: 1 });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('加载任务配置和支部实例，未上报字段展示 --', async () => {
    const wrapper = mount(TaskDetailView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    expect(getTaskDetail).toHaveBeenCalledWith(42);
    expect(listTaskAssignments).toHaveBeenCalledWith(42, { pageNo: 1, pageSize: 10 });
    expect(wrapper.find('.page-title').text()).toBe('临时整改');
    expect(wrapper.vm.assignments[0].submitterName).toBe('--');
    expect(wrapper.vm.assignments[0].submittedAt).toBe('--');
    expect(wrapper.find('[data-test="description-link"]').attributes('href')).toBe('https://example.com/guide');
    wrapper.unmount();
  });

  it('发起导出并轮询到完成状态', async () => {
    const wrapper = mount(TaskDetailView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    await wrapper.vm.handleExport();
    await settle();

    expect(createTaskExport).toHaveBeenCalledWith(42, { itemCodes: [] });
    expect(getTaskExportStatus).toHaveBeenCalledWith('export-1');
    expect(wrapper.vm.exportState.status).toBe('SUCCEEDED');
    wrapper.unmount();
  });

  it('组件卸载后不重新启动导出状态轮询', async () => {
    vi.useFakeTimers();
    let resolveStatus;
    getTaskExportStatus.mockImplementationOnce(() => new Promise((resolve) => {
      resolveStatus = resolve;
    }));
    const wrapper = mount(TaskDetailView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    const exportPromise = wrapper.vm.handleExport();
    await flushPromises();
    expect(getTaskExportStatus).toHaveBeenCalledTimes(1);

    wrapper.unmount();
    resolveStatus({ exportId: 'export-1', status: 'RUNNING' });
    await exportPromise;
    await flushPromises();
    await vi.advanceTimersByTimeAsync(1100);

    expect(getTaskExportStatus).toHaveBeenCalledTimes(1);
  });

  it('导出已完成但不可下载时不展示或触发下载', async () => {
    getTaskExportStatus.mockResolvedValueOnce({
      exportId: 'export-1',
      status: 'SUCCEEDED',
      downloadable: false,
      totalRows: 1,
      sheetCount: 1
    });
    const wrapper = mount(TaskDetailView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    await wrapper.vm.handleExport();
    await settle();
    await wrapper.vm.downloadExport();

    expect(wrapper.vm.exportState.downloadable).toBe(false);
    expect(downloadTaskExport).not.toHaveBeenCalled();
    expect(wrapper.findAll('button').some((button) => button.text().includes('下载 ZIP'))).toBe(false);
    wrapper.unmount();
  });

  it('任务详情请求失败时展示错误态而不是误报暂无支部填报数据', async () => {
    getTaskDetail.mockRejectedValueOnce(new Error('服务不可用'));
    const wrapper = mount(TaskDetailView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    expect(wrapper.vm.loadError).toBe('任务详情加载失败，请稍后重试');
    expect(wrapper.find('[role="alert"]').text()).toContain('任务详情加载失败');
    expect(wrapper.find('.empty-stub').exists()).toBe(false);
    wrapper.unmount();
  });

  it('附件下载使用任务实例和文件 ID', async () => {
    listTaskAssignments.mockResolvedValueOnce({
      records: [{
        assignmentId: 1001,
        branchName: '第一党支部',
        stage: 'SUBMITTED',
        submitterName: '张伟',
        submittedAt: '2026-08-27 10:00:00',
        files: [{ fileId: 'file-1', fileName: '整改说明.pdf' }]
      }],
      total: 1
    });
    const wrapper = mount(TaskDetailView, {
      global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
    });
    await settle();

    await wrapper.vm.downloadAttachment({ assignmentId: 1001, taskId: 42 }, { fileId: 'file-1', fileName: '整改说明.pdf' });
    expect(downloadTaskAttachment).toHaveBeenCalledWith(42, 1001, 'file-1');
    wrapper.unmount();
  });
});
