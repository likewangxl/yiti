// @vitest-environment happy-dom
// 绩效运营高危写操作的同步 single-flight 回归测试。
// disabled/loading 只影响真实 DOM 事件，直接调用处理函数时仍必须保证同一批写请求只发一次。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

const mocks = vi.hoisted(() => ({
  listComputeBatches: vi.fn().mockResolvedValue([]),
  triggerCompute: vi.fn().mockResolvedValue({}),
  getComputeBatch: vi.fn().mockResolvedValue({}),
  getKpiScoreStats: vi.fn().mockResolvedValue({}),
  listKpiCalcLogs: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  listKpiRules: vi.fn().mockResolvedValue([]),
  calcKpiScore: vi.fn().mockResolvedValue('OK'),
  getLatestKpiCalcLogDate: vi.fn().mockResolvedValue(''),
  listMetricSummary: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  batchExecuteMetrics: vi.fn().mockResolvedValue({ success: 0, failed: 0, results: [] }),
  executeMetric: vi.fn().mockResolvedValue({}),
  listMetrics: vi.fn().mockResolvedValue([]),
  listRunTasks: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  listImports: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  uploadImportFile: vi.fn().mockResolvedValue({ batchId: 'B-DEFAULT', errorRows: 0 }),
  refreshImportStatus: vi.fn().mockResolvedValue({}),
  retryImport: vi.fn().mockResolvedValue({}),
  deleteImportBatch: vi.fn().mockResolvedValue({}),
  downloadImportErrors: vi.fn().mockResolvedValue({}),
  downloadImportSourceFile: vi.fn().mockResolvedValue({}),
  listDictItems: vi.fn().mockResolvedValue([]),
  message: {
    success: vi.fn(), warning: vi.fn(), error: vi.fn()
  }
}));

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), mocks.message)
}));
vi.mock('@/api/perf', () => mocks);
vi.mock('@/api/system', () => ({ listDictItems: mocks.listDictItems }));
vi.mock('@/utils/datetime', () => ({ fmtDateTimeCol: vi.fn() }));

import Compute from '../Compute.vue';
import TaskMonitor from '../TaskMonitor.vue';
import Import from '../Import.vue';
import { batchExecuteMetrics, calcKpiScore, deleteImportBatch, executeMetric, retryImport, uploadImportFile } from '@/api/perf';

const passthrough = (name) => ({ name, template: '<div><slot /><slot name="tip" /><slot name="reference" /></div>' });
const empty = (name) => ({ name, template: '<div />' });
const buttonStub = {
  name: 'ElButton',
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>'
};
const stubs = {
  PageTitle: passthrough('PageTitle'),
  'el-button': buttonStub,
  'el-dialog': passthrough('ElDialog'),
  'el-drawer': empty('ElDrawer'),
  'el-alert': empty('ElAlert'),
  'el-form': passthrough('ElForm'),
  'el-form-item': passthrough('ElFormItem'),
  'el-input': {
    name: 'ElInput',
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-select': empty('ElSelect'),
  'el-option': empty('ElOption'),
  'el-radio-group': passthrough('ElRadioGroup'),
  'el-radio': passthrough('ElRadio'),
  'el-tag': passthrough('ElTag'),
  'el-tooltip': passthrough('ElTooltip'),
  'el-table': { name: 'ElTable', template: '<div><slot /></div>' },
  'el-table-column': empty('ElTableColumn'),
  'el-date-picker': empty('ElDatePicker'),
  'el-pagination': empty('ElPagination'),
  'el-upload': {
    name: 'ElUpload',
    template: '<div><slot /><slot name="tip" /></div>',
    methods: { clearFiles() {} }
  },
  'el-popconfirm': passthrough('ElPopconfirm'),
  'el-icon': passthrough('ElIcon'),
  'upload-filled': empty('UploadFilled')
};

const today = () => new Date().toISOString().slice(0, 10);
const deferred = () => {
  let resolve;
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve };
};
const settle = async () => {
  await flushPromises();
  await nextTick();
  await flushPromises();
};

let wrappers = [];
beforeEach(() => {
  vi.clearAllMocks();
  wrappers = [];
});
afterEach(() => {
  wrappers.forEach((wrapper) => wrapper.unmount());
});

async function mountPage(component) {
  const wrapper = mount(component, { global: { stubs } });
  wrappers.push(wrapper);
  await settle();
  return wrapper;
}

describe('绩效计算与任务执行同步防重', () => {
  it('触发计算极快双调用只提交一次且保留单方案 payload', async () => {
    const wrapper = await mountPage(Compute);
    const gate = deferred();
    calcKpiScore.mockImplementation(() => gate.promise);
    wrapper.vm.trgDlg.schemes = ['S1'];
    wrapper.vm.trgDlg.form.dataDate = today();
    wrapper.vm.trgDlg.form.reason = '批量补算';

    const first = wrapper.vm.onConfirmTrigger();
    const second = wrapper.vm.onConfirmTrigger();

    expect(calcKpiScore).toHaveBeenCalledTimes(1);
    expect(calcKpiScore).toHaveBeenCalledWith({ dataDate: today(), schemeCode: 'S1', reason: '批量补算' });
    gate.resolve('OK');
    await Promise.all([first, second]);
  });

  it('失败批次重试极快双调用只提交一次且保留 retry payload', async () => {
    const wrapper = await mountPage(Compute);
    const gate = deferred();
    mocks.triggerCompute.mockImplementation(() => gate.promise);
    const row = { batch: 'B-RETRY', rawId: 'TASK-1' };

    const first = wrapper.vm.onRetry(row);
    const second = wrapper.vm.onRetry(row);

    expect(mocks.triggerCompute).toHaveBeenCalledTimes(1);
    expect(mocks.triggerCompute).toHaveBeenCalledWith({ batch: 'B-RETRY', retry: true });
    gate.resolve({});
    await Promise.all([first, second]);
  });

  it('单项执行极快双调用只提交一次且保留 execute payload', async () => {
    const wrapper = await mountPage(TaskMonitor);
    const gate = deferred();
    executeMetric.mockImplementation(() => gate.promise);
    wrapper.vm.execDlg.metricCode = 'M001';
    wrapper.vm.execDlg.dataDate = today();
    wrapper.vm.execDlg.reason = '补跑指标';

    const first = wrapper.vm.confirmExecute();
    const second = wrapper.vm.confirmExecute();

    expect(executeMetric).toHaveBeenCalledTimes(1);
    expect(executeMetric).toHaveBeenCalledWith('M001', {
      dataDate: today(), cascade: true, async: true, reason: '补跑指标'
    });
    gate.resolve({});
    await Promise.all([first, second]);
  });

  it('批量执行极快双调用只提交一次且保留 batch payload', async () => {
    const wrapper = await mountPage(TaskMonitor);
    const gate = deferred();
    batchExecuteMetrics.mockImplementation(() => gate.promise);
    wrapper.vm.batchDlg.metricCodes = ['M001', 'M002'];
    wrapper.vm.batchDlg.dataDate = today();
    wrapper.vm.batchDlg.reason = '批量补跑';

    const first = wrapper.vm.confirmBatch();
    const second = wrapper.vm.confirmBatch();

    expect(batchExecuteMetrics).toHaveBeenCalledTimes(1);
    expect(batchExecuteMetrics).toHaveBeenCalledWith({
      metricCodes: ['M001', 'M002'], dataDate: today(), async: true, reason: '批量补跑'
    });
    gate.resolve({ success: 2, failed: 0, results: [] });
    await Promise.all([first, second]);
  });
});

describe('绩效导入写操作同步防重', () => {
  it('上传极快双调用只提交一次且保留 upload payload', async () => {
    const wrapper = await mountPage(Import);
    const gate = deferred();
    uploadImportFile.mockImplementation(() => gate.promise);
    const file = { name: 'metric.xlsx', size: 1024 };
    const dataDate = today();
    wrapper.vm.picked = file;
    wrapper.vm.kind = 'METRIC_RESULT';
    wrapper.vm.date = dataDate;
    wrapper.vm.plan = '';
    const timeout = vi.spyOn(globalThis, 'setTimeout').mockImplementation(() => 0);

    const first = wrapper.vm.onUpload(false);
    const second = wrapper.vm.onUpload(false);

    expect(uploadImportFile).toHaveBeenCalledTimes(1);
    expect(uploadImportFile).toHaveBeenCalledWith('METRIC_RESULT', file, dataDate, {
      uploader: '当前用户', schemeCode: '', archiveSource: false
    });
    gate.resolve({ batchId: 'B-UPLOAD', errorRows: 0 });
    await Promise.all([first, second]);
    timeout.mockRestore();
  });

  it('导入失败批次重试极快双调用只提交一次', async () => {
    const wrapper = await mountPage(Import);
    const gate = deferred();
    retryImport.mockImplementation(() => gate.promise);
    const row = { batchId: 'B-RETRY' };

    const first = wrapper.vm.onRetry(row);
    const second = wrapper.vm.onRetry(row);

    expect(retryImport).toHaveBeenCalledTimes(1);
    expect(retryImport).toHaveBeenCalledWith('B-RETRY');
    gate.resolve({});
    await Promise.all([first, second]);
  });

  it('导入批次删除极快双调用只提交一次且保留删除原因', async () => {
    const wrapper = await mountPage(Import);
    const gate = deferred();
    deleteImportBatch.mockImplementation(() => gate.promise);
    const row = { batchId: 'B-DELETE' };

    const first = wrapper.vm.onDelete(row);
    const second = wrapper.vm.onDelete(row);

    expect(deleteImportBatch).toHaveBeenCalledTimes(1);
    expect(deleteImportBatch).toHaveBeenCalledWith('B-DELETE', '前端列表删除');
    gate.resolve({});
    await Promise.all([first, second]);
  });
});
