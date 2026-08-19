// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

const {
  executeSqlProbeMock,
  getSqlWhitelistMock,
  getSqlHistoryMock,
  getSqlHistoryItemMock,
  createSqlExportMock,
  listSqlExportTasksMock,
  downloadSqlExportBlobMock,
  messageMock
} = vi.hoisted(() => ({
  executeSqlProbeMock: vi.fn(),
  getSqlWhitelistMock: vi.fn(),
  getSqlHistoryMock: vi.fn(),
  getSqlHistoryItemMock: vi.fn(),
  createSqlExportMock: vi.fn(),
  listSqlExportTasksMock: vi.fn(),
  downloadSqlExportBlobMock: vi.fn(),
  messageMock: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn()
  }
}));

vi.mock('@/api/report', () => ({
  executeSqlProbe: executeSqlProbeMock,
  getSqlWhitelist: getSqlWhitelistMock,
  getSqlHistory: getSqlHistoryMock,
  getSqlHistoryItem: getSqlHistoryItemMock,
  createSqlExport: createSqlExportMock,
  listSqlExportTasks: listSqlExportTasksMock,
  downloadSqlExportBlob: downloadSqlExportBlobMock
}));
vi.mock('element-plus', () => ({ ElMessage: messageMock }));
vi.mock('@/utils/datetime', () => ({ fmtDateTimeCol: vi.fn() }));
vi.mock('@/utils/sqlCrypto', () => ({ encryptSql: (value) => `encrypted:${value}` }));

import Sql from '../Sql.vue';

const stubs = {
  PageTitle: { name: 'PageTitle', template: '<h1>SQL 探查</h1>' },
  'el-button': {
    name: 'ElButton',
    props: ['loading', 'disabled', 'icon'],
    emits: ['click'],
    template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-input': {
    name: 'ElInput',
    props: ['modelValue', 'type', 'placeholder'],
    emits: ['update:modelValue'],
    template: `
      <textarea v-if="type === 'textarea'" :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" />
      <input v-else :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" />
    `
  },
  'el-input-number': {
    name: 'ElInputNumber',
    props: ['modelValue', 'min', 'max'],
    emits: ['update:modelValue'],
    template: '<input v-bind="$attrs" type="number" :value="modelValue" :min="min" :max="max" @input="$emit(\'update:modelValue\', Number($event.target.value))" />'
  },
  'el-alert': { name: 'ElAlert', template: '<div><slot name="title" /><slot /></div>' },
  'el-table': { name: 'ElTable', props: ['data'], template: '<div><slot /></div>' },
  'el-table-column': { name: 'ElTableColumn', template: '<span />' },
  'el-progress': { name: 'ElProgress', template: '<span />' },
  'el-tag': { name: 'ElTag', template: '<span><slot /></span>' },
  'el-tooltip': { name: 'ElTooltip', template: '<span><slot /></span>' },
  'el-pagination': {
    name: 'ElPagination',
    props: ['currentPage', 'pageSize', 'pageSizes', 'total', 'layout'],
    emits: ['update:currentPage', 'current-change'],
    template: '<div data-testid="pagination-stub" />'
  },
  'el-dialog': {
    name: 'ElDialog',
    props: ['modelValue', 'title'],
    emits: ['update:modelValue'],
    template: '<div v-if="modelValue" data-testid="dialog"><slot /><slot name="footer" /></div>'
  }
};

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

function mountSql() {
  return mount(Sql, {
    global: {
      stubs,
      directives: { 'bp-overflow-tooltip': {}, loading: {} }
    }
  });
}

function buttonByText(wrapper, text) {
  return wrapper.findAll('button').find((button) => button.text().trim() === text);
}

let wrapper;

beforeEach(() => {
  executeSqlProbeMock.mockReset();
  getSqlWhitelistMock.mockReset().mockResolvedValue(['cust_master']);
  getSqlHistoryMock.mockReset().mockResolvedValue({ records: [], total: 0 });
  getSqlHistoryItemMock.mockReset();
  createSqlExportMock.mockReset().mockResolvedValue({ taskId: 'EXP-1' });
  listSqlExportTasksMock.mockReset().mockResolvedValue([]);
  downloadSqlExportBlobMock.mockReset();
  Object.values(messageMock).forEach((mock) => mock.mockReset());
});

afterEach(() => wrapper?.unmount());

describe('Sql.vue SQL 探查历史与异步下载', () => {
  it('历史记录固定每页 10 条，切页仍按 pageSize=10 请求且不提供 page-size 选择', async () => {
    getSqlHistoryMock.mockResolvedValue({
      records: [{ id: 'H1', sqlText: 'SELECT 1', remark: '核对' }],
      total: 11
    });

    wrapper = mountSql();
    await settle();
    await buttonByText(wrapper, '查看历史').trigger('click');
    await settle();

    expect(getSqlHistoryMock).toHaveBeenNthCalledWith(1, { pageNo: 1, pageSize: 10 });
    const pagination = wrapper.findComponent({ name: 'ElPagination' });
    expect(pagination.props('pageSize')).toBe(10);
    expect(pagination.props('pageSizes')).toBeUndefined();
    expect(pagination.props('layout')).toBe('total, prev, pager, next');

    pagination.vm.$emit('update:currentPage', 2);
    pagination.vm.$emit('current-change', 2);
    await settle();
    expect(getSqlHistoryMock).toHaveBeenNthCalledWith(2, { pageNo: 2, pageSize: 10 });
  });

  it('下载任务固定每页 5 条，切页按当前页请求并展示总数', async () => {
    listSqlExportTasksMock.mockResolvedValue({
      records: [{ id: 'EXP-1', status: 'SUCCESS', fileName: 'sql-result.xlsx' }],
      total: 11
    });

    wrapper = mountSql();
    await settle();

    expect(listSqlExportTasksMock).toHaveBeenNthCalledWith(1, { pageNo: 1, pageSize: 5 });
    const pagination = wrapper.findComponent({ name: 'ElPagination' });
    expect(pagination.props('pageSize')).toBe(5);
    expect(pagination.props('total')).toBe(11);
    expect(pagination.props('pageSizes')).toBeUndefined();
    expect(pagination.props('layout')).toBe('total, prev, pager, next');

    pagination.vm.$emit('update:currentPage', 2);
    pagination.vm.$emit('current-change', 2);
    await settle();
    expect(listSqlExportTasksMock).toHaveBeenNthCalledWith(2, { pageNo: 2, pageSize: 5 });
  });

  it('轮询保持当前页，并只根据当前页的处理中任务决定是否继续', async () => {
    vi.useFakeTimers();
    listSqlExportTasksMock
      .mockResolvedValueOnce({ records: [{ id: 'EXP-1', status: 'SUCCESS' }], total: 11 })
      .mockResolvedValueOnce({ records: [{ id: 'EXP-2', status: 'RUNNING' }], total: 11 })
      .mockResolvedValue({ records: [{ id: 'EXP-2', status: 'SUCCESS' }], total: 11 });

    try {
      wrapper = mountSql();
      await settle();
      const pagination = wrapper.findComponent({ name: 'ElPagination' });
      pagination.vm.$emit('update:currentPage', 2);
      pagination.vm.$emit('current-change', 2);
      await settle();
      expect(listSqlExportTasksMock).toHaveBeenNthCalledWith(2, { pageNo: 2, pageSize: 5 });

      vi.advanceTimersByTime(3000);
      await settle();
      expect(listSqlExportTasksMock).toHaveBeenNthCalledWith(3, { pageNo: 2, pageSize: 5 });
    } finally {
      vi.useRealTimers();
    }
  });

  it('创建下载任务后回到第一页刷新任务列表', async () => {
    listSqlExportTasksMock
      .mockResolvedValueOnce({ records: [{ id: 'EXP-1', status: 'SUCCESS' }], total: 11 })
      .mockResolvedValueOnce({ records: [{ id: 'EXP-2', status: 'SUCCESS' }], total: 11 })
      .mockResolvedValueOnce({ records: [{ id: 'EXP-3', status: 'RUNNING' }], total: 12 });

    wrapper = mountSql();
    await settle();
    const pagination = wrapper.findComponent({ name: 'ElPagination' });
    pagination.vm.$emit('update:currentPage', 2);
    pagination.vm.$emit('current-change', 2);
    await settle();
    expect(listSqlExportTasksMock).toHaveBeenNthCalledWith(2, { pageNo: 2, pageSize: 5 });

    wrapper.vm.reason = '导出核对';
    await nextTick();
    await buttonByText(wrapper, '下载').trigger('click');
    await nextTick();
    await buttonByText(wrapper, '确定').trigger('click');
    await settle();

    expect(createSqlExportMock).toHaveBeenCalledWith({
      sql: 'encrypted:SELECT cust_no, cust_name, industry, customer_type\nFROM CUST_MASTER\nORDER BY cust_no\nLIMIT 10',
      remark: '导出核对',
      exportCount: 1000
    });
    expect(listSqlExportTasksMock).toHaveBeenNthCalledWith(3, { pageNo: 1, pageSize: 5 });
  });

  it('点击下载先打开条数选项，默认 1000，取消不提交', async () => {
    wrapper = mountSql();
    await settle();
    wrapper.vm.reason = '导出核对';
    await nextTick();

    await buttonByText(wrapper, '下载').trigger('click');
    await nextTick();

    expect(createSqlExportMock).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="export-count"]').element.value).toBe('1000');
    expect(wrapper.get('[data-testid="export-count"]').attributes('min')).toBe('1');
    expect(wrapper.get('[data-testid="export-count"]').attributes('max')).toBe('50000');

    await buttonByText(wrapper, '取消').trigger('click');
    expect(createSqlExportMock).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="export-count"]').exists()).toBe(false);
  });

  it('确认下载提交 exportCount，超出 50000 时提示并阻止提交', async () => {
    wrapper = mountSql();
    await settle();
    wrapper.vm.reason = '导出核对';
    await nextTick();

    await buttonByText(wrapper, '下载').trigger('click');
    await nextTick();
    await wrapper.get('[data-testid="export-count"]').setValue('2500');
    await buttonByText(wrapper, '确定').trigger('click');
    await settle();

    expect(createSqlExportMock).toHaveBeenCalledWith({
      sql: 'encrypted:SELECT cust_no, cust_name, industry, customer_type\nFROM CUST_MASTER\nORDER BY cust_no\nLIMIT 10',
      remark: '导出核对',
      exportCount: 2500
    });

    await buttonByText(wrapper, '下载').trigger('click');
    await nextTick();
    await wrapper.get('[data-testid="export-count"]').setValue('50001');
    await buttonByText(wrapper, '确定').trigger('click');

    expect(createSqlExportMock).toHaveBeenCalledTimes(1);
    expect(messageMock.warning).toHaveBeenCalledWith(expect.stringContaining('1 至 50000'));
  });

  it('下载任务完成后使用任务 row.fileName 触发文件下载', async () => {
    const originalCreateObjectURL = URL.createObjectURL;
    const originalRevokeObjectURL = URL.revokeObjectURL;
    const originalCreateElement = document.createElement.bind(document);
    const createObjectURLMock = vi.fn(() => 'blob:sql-export');
    const revokeObjectURLMock = vi.fn();
    const createdAnchors = [];
    URL.createObjectURL = createObjectURLMock;
    URL.revokeObjectURL = revokeObjectURLMock;
    vi.spyOn(document, 'createElement').mockImplementation((tagName, options) => {
      const element = originalCreateElement(tagName, options);
      if (tagName === 'a') {
        vi.spyOn(element, 'click').mockImplementation(() => {});
        createdAnchors.push(element);
      }
      return element;
    });
    downloadSqlExportBlobMock.mockResolvedValue(new Blob(['xlsx']));
    listSqlExportTasksMock.mockResolvedValue([{
      id: 'EXP-1',
      status: 'SUCCESS',
      fileName: 'sql-result-01.zip'
    }]);

    try {
      wrapper = mountSql();
      await settle();
      await wrapper.vm.downloadFile({ id: 'EXP-1', fileName: 'sql-result-01.zip' });
      await settle();

      expect(downloadSqlExportBlobMock).toHaveBeenCalledWith('EXP-1');
      expect(createObjectURLMock).toHaveBeenCalled();
      expect(createdAnchors[0].download).toBe('sql-result-01.zip');
    } finally {
      vi.restoreAllMocks();
      URL.createObjectURL = originalCreateObjectURL;
      URL.revokeObjectURL = originalRevokeObjectURL;
    }
  });
});
