// @vitest-environment happy-dom
// 绩效运营高危写操作的同步 single-flight 回归测试。
// disabled/loading 只影响真实 DOM 事件，直接调用处理函数时仍必须保证同一批写请求只发一次。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import dayjs from 'dayjs';
import { METRIC_RECALC_DATE_MESSAGES } from '@/utils/metricRecalcDate';

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
  triggerMetricLevelRecalc: vi.fn().mockResolvedValue({ accepted: true }),
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
import {
  batchExecuteMetrics,
  calcKpiScore,
  deleteImportBatch,
  executeMetric,
  retryImport,
  triggerMetricLevelRecalc,
  uploadImportFile
} from '@/api/perf';

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

const today = () => dayjs().format('YYYY-MM-DD');
const yesterday = () => dayjs().subtract(1, 'day').format('YYYY-MM-DD');
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
    wrapper.vm.execDlg.dataDate = yesterday();
    wrapper.vm.execDlg.reason = '补跑指标';

    const first = wrapper.vm.confirmExecute();
    const second = wrapper.vm.confirmExecute();

    expect(executeMetric).toHaveBeenCalledTimes(1);
    expect(executeMetric).toHaveBeenCalledWith('M001', {
      dataDate: yesterday(), cascade: true, async: true, reason: '补跑指标'
    });
    gate.resolve({});
    await Promise.all([first, second]);
  });

  it('批量执行极快双调用只提交一次且保留 batch payload', async () => {
    const wrapper = await mountPage(TaskMonitor);
    const gate = deferred();
    batchExecuteMetrics.mockImplementation(() => gate.promise);
    wrapper.vm.batchDlg.metricCodes = ['M001', 'M002'];
    wrapper.vm.batchDlg.dataDate = yesterday();
    wrapper.vm.batchDlg.reason = '批量补跑';

    const first = wrapper.vm.confirmBatch();
    const second = wrapper.vm.confirmBatch();

    expect(batchExecuteMetrics).toHaveBeenCalledTimes(1);
    expect(batchExecuteMetrics).toHaveBeenCalledWith({
      metricCodes: ['M001', 'M002'], dataDate: yesterday(), async: true, reason: '批量补跑'
    });
    gate.resolve({ success: 2, failed: 0, results: [] });
    await Promise.all([first, second]);
  });

  it('按级别重算默认昨日、仅一级展示分配日期，并且极快双调用只提交一次', async () => {
    const wrapper = await mountPage(TaskMonitor);
    wrapper.vm.openLevelTrigger();

    expect(wrapper.vm.levelDlg.level).toBe(1);
    expect(wrapper.vm.levelDlg.dataDate).toBe(yesterday());
    expect(wrapper.vm.levelDlg.allocDate).toBe('');

    const gate = deferred();
    triggerMetricLevelRecalc.mockImplementation(() => gate.promise);
    wrapper.vm.levelDlg.level = 1;
    wrapper.vm.levelDlg.dataDate = yesterday();
    wrapper.vm.levelDlg.allocDate = '2026-08-16';
    wrapper.vm.levelDlg.reason = '补跑一级指标';

    const first = wrapper.vm.confirmLevelTrigger();
    const second = wrapper.vm.confirmLevelTrigger();

    expect(triggerMetricLevelRecalc).toHaveBeenCalledTimes(1);
    expect(triggerMetricLevelRecalc).toHaveBeenCalledWith({
      level: 1,
      dataDate: yesterday(),
      reason: '补跑一级指标',
      allocDate: '2026-08-16'
    });
    gate.resolve({ accepted: true });
    await Promise.all([first, second]);
  });

  it('按级别重算二级不发送 allocDate，并拒绝当天日期与空原因', async () => {
    const wrapper = await mountPage(TaskMonitor);
    wrapper.vm.openLevelTrigger();
    wrapper.vm.levelDlg.level = 2;
    wrapper.vm.levelDlg.dataDate = yesterday();
    wrapper.vm.levelDlg.allocDate = '2026-08-16';
    wrapper.vm.levelDlg.reason = '补跑二级指标';
    triggerMetricLevelRecalc.mockResolvedValueOnce({ accepted: true });

    await wrapper.vm.confirmLevelTrigger();
    expect(triggerMetricLevelRecalc).toHaveBeenCalledWith({
      level: 2,
      dataDate: yesterday(),
      reason: '补跑二级指标'
    });

    triggerMetricLevelRecalc.mockClear();
    wrapper.vm.levelDlg.dataDate = today();
    await wrapper.vm.confirmLevelTrigger();
    expect(triggerMetricLevelRecalc).not.toHaveBeenCalled();

    wrapper.vm.levelDlg.dataDate = yesterday();
    wrapper.vm.levelDlg.reason = '  ';
    await wrapper.vm.confirmLevelTrigger();
    expect(triggerMetricLevelRecalc).not.toHaveBeenCalled();
  });

  it('单项和批量提交均拒绝当天日期，并保留原 single-flight 边界', async () => {
    const wrapper = await mountPage(TaskMonitor);
    const current = today();
    wrapper.vm.execDlg.metricCode = 'M001';
    wrapper.vm.execDlg.dataDate = current;
    wrapper.vm.execDlg.reason = '当天重算';
    wrapper.vm.batchDlg.metricCodes = ['M001', 'M002'];
    wrapper.vm.batchDlg.dataDate = current;
    wrapper.vm.batchDlg.reason = '当天批量重算';
    executeMetric.mockClear();
    batchExecuteMetrics.mockClear();
    mocks.message.warning.mockClear();

    await wrapper.vm.confirmExecute();
    await wrapper.vm.confirmBatch();

    expect(executeMetric).not.toHaveBeenCalled();
    expect(batchExecuteMetrics).not.toHaveBeenCalled();
    expect(mocks.message.warning).toHaveBeenCalledWith(METRIC_RECALC_DATE_MESSAGES.TODAY_OR_FUTURE);
  });

  it('日期控件和提交兜底都拒绝超过20天的非月末日期', async () => {
    const wrapper = await mountPage(TaskMonitor);
    const old = dayjs().subtract(21, 'day');
    const nonMonthEnd = old.isSame(old.endOf('month'), 'day') ? old.subtract(1, 'day') : old;
    const date = nonMonthEnd.format('YYYY-MM-DD');
    expect(wrapper.vm.disabledFuture(nonMonthEnd.toDate())).toBe(true);
    wrapper.vm.execDlg.metricCode = 'M001';
    wrapper.vm.execDlg.dataDate = date;
    wrapper.vm.execDlg.reason = '历史重算';
    executeMetric.mockClear();
    mocks.message.warning.mockClear();

    await wrapper.vm.confirmExecute();

    expect(executeMetric).not.toHaveBeenCalled();
    expect(mocks.message.warning).toHaveBeenCalledWith(METRIC_RECALC_DATE_MESSAGES.OLDER_THAN_20_NON_MONTH_END);
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
