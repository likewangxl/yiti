// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick, ref } from 'vue';

const queryScreenData = vi.fn();
vi.mock('@/api/screen', () => ({ queryScreenData: (...args) => queryScreenData(...args) }));

import { usePanoramaData } from '../usePanoramaData';

function viewWithSlots(bindings = {}) {
  const components = Object.entries(bindings).map(([slot, blockId]) => ({
    id: `node-${slot}`, component: 'ChartWidget', blockId,
    innerType: 'METRIC_CARD', propValue: { bindingKey: slot }
  }));
  return {
    screenCode: 'SCR_CODE', runtimeSchemaVersion: 2,
    renderPackage: {
      canvasStyle: { presentation: { type: 'CODE', template: 'branch-overview-v1' } },
      components,
      bindSnapshots: Object.fromEntries(Object.entries(bindings).map(([slot, blockId]) => [String(blockId), {
        bind: { dsId: 9, period: 'LATEST', fields: { value: `${slot}_col` }, units: { value: 'YUAN' } },
        componentType: 'METRIC_CARD'
      }]))
    },
    panoramaInstitutions: []
  };
}

function retailViewWithSlots(bindings = {}) {
  const fields = {
    retailAum: { value: 'aum' },
    retailTrend: { date: 'date', aum: 'aum' },
    branches: { orgCode: 'org_code' },
    deposit: { value: 'deposit' },
    branchTrend: { date: 'date', deposit: 'deposit' }
  };
  const units = {
    retailAum: { value: 'YUAN' },
    retailTrend: { aum: 'YUAN' },
    branches: {},
    deposit: { value: 'YUAN' },
    branchTrend: { deposit: 'YUAN' }
  };
  const components = Object.entries(bindings).map(([slot, blockId]) => ({
    id: `node-${slot}`, component: 'ChartWidget', blockId,
    innerType: 'METRIC_CARD', propValue: { bindingKey: slot }
  }));
  return {
    screenCode: 'RETAIL_CODE', runtimeSchemaVersion: 2,
    renderPackage: {
      canvasStyle: { presentation: { type: 'CODE', template: 'retail-overview-v1' } },
      components,
      bindSnapshots: Object.fromEntries(Object.entries(bindings).map(([slot, blockId]) => [String(blockId), {
        bind: { dsId: 9, period: 'LATEST', fields: fields[slot] || { value: `${slot}_col` }, units: units[slot] || { value: 'YUAN' } },
        componentType: 'METRIC_CARD'
      }]))
    },
    panoramaInstitutions: []
  };
}

describe('usePanoramaData', () => {
  it('对公发布仅查询对公槽，保留零值，403清空并禁止全行趋势串用', async () => {
    const view = retailViewWithSlots({ corpDeposit: 51, retailAum: 52, deposit: 53, branchTrend: 54 });
    view.renderPackage.canvasStyle.presentation.template = 'corporate-overview-v1';
    view.renderPackage.bindSnapshots['51'].bind = { dsId: 9, period: 'LATEST', fields: { value: 'value' }, units: { value: 'YUAN' } };
    queryScreenData.mockResolvedValue({ columns: ['value'], rows: [[0]] });
    const state = usePanoramaData(ref(view), ref({}), { autoLoad: false });
    await state.refresh();
    expect(queryScreenData).toHaveBeenCalledTimes(1);
    expect(queryScreenData.mock.calls[0][0].blockId).toBe(51);
    expect(state.model.value.kpis.find(item => item.key === 'corpDeposit')?.value).toBe(0);
    await state.selectBranch('A');
    expect(queryScreenData).toHaveBeenCalledTimes(1);
    queryScreenData.mockRejectedValue({ status: 403, message: '无权访问' });
    await state.refresh();
    expect(state.error.value).toBe('无权访问');
    expect(state.model.value.kpis.every(item => item.value == null)).toBe(true);
    state.dispose();
  });

  beforeEach(() => { queryScreenData.mockReset(); });
  afterEach(() => vi.restoreAllMocks());

  const batchQuality = (overrides = {}) => ({
    batchId: 'opaque-batch-1', dataDate: '2026-09-10', version: 'V7', status: 'COMPLETE',
    dataClassification: 'TEST',
    calculatedAt: '2026-09-11T01:02:03Z', expected: 14, received: 14,
    expectedSubjects: 4, receivedSubjects: 4, sourceAsOf: { financial: '2026-09-10' },
    missingSubjects: [], missing: [], mixedPeriod: false, selectedComplete: true,
    newerIncomplete: [], ...overrides
  });

  it('发布组件只从 blockId -> bindSnapshots 取身份，schema2 不把 dsId 上送且普通槽位不继承 URL 机构上下文', async () => {
    queryScreenData.mockResolvedValue({ columns: ['value'], rows: [[100000000]], columnsMeta: [{ col: 'value', role: 'METRIC', amountScale: 'YUAN' }] });
    const state = usePanoramaData(ref(viewWithSlots({ deposit: 11 })), ref({ screenCode: 'SCR_CODE', orgCode: 'URL_SCOPE', empId: 'URL_EMP' }), { autoLoad: false });
    await state.refresh();
    expect(queryScreenData).toHaveBeenCalledWith(expect.objectContaining({
      schemaVersion: 2, screenCode: 'SCR_CODE', blockId: 11, contextParams: {}
    }));
    expect(queryScreenData.mock.calls[0][0]).not.toHaveProperty('dsId');
  });

  it('历史14槽发布包不因可选 loanRate 自动增加请求', async () => {
    queryScreenData.mockResolvedValue({ columns: ['value'], rows: [[100000000]] });
    const state = usePanoramaData(ref(viewWithSlots({ deposit: 11 })), ref({ screenCode: 'SCR_CODE' }), { autoLoad: false });
    await state.refresh();
    expect(queryScreenData.mock.calls.map(([request]) => request.blockId)).toEqual([11]);
  });

  it('发布包明确配置有效 loanRate 组件时才请求该独立槽位', async () => {
    queryScreenData.mockResolvedValue({ columns: ['value'], rows: [[42]] });
    const state = usePanoramaData(ref(viewWithSlots({ loanRate: 15 })), ref({ screenCode: 'SCR_CODE' }), { autoLoad: false });
    await state.refresh();
    expect(queryScreenData.mock.calls.map(([request]) => request.blockId)).toEqual([15]);
  });

  it('loanRate 组件缺少有效数据源时不发请求', async () => {
    queryScreenData.mockResolvedValue({ columns: ['value'], rows: [[42]] });
    const view = viewWithSlots({ loanRate: 16 });
    view.renderPackage.bindSnapshots['16'].bind.dsId = null;
    const state = usePanoramaData(ref(view), ref({ screenCode: 'SCR_CODE' }), { autoLoad: false });
    await state.refresh();
    expect(queryScreenData).not.toHaveBeenCalled();
  });

  it('相同请求去重且并发不超过 3 个', async () => {
    let running = 0; let maxRunning = 0;
    queryScreenData.mockImplementation(() => new Promise(resolve => {
      running += 1; maxRunning = Math.max(maxRunning, running);
      setTimeout(() => { running -= 1; resolve({ columns: ['value'], rows: [[0]], columnsMeta: [{ col: 'value', amountScale: 'YUAN' }] }); }, 5);
    }));
    const state = usePanoramaData(ref(viewWithSlots({ deposit: 11, loan: 12, customers: 13, revenue: 14 })), ref({ screenCode: 'SCR_CODE' }), { autoLoad: false });
    await Promise.all([state.refresh(), state.refresh()]);
    expect(maxRunning).toBeLessThanOrEqual(3);
    expect(queryScreenData).toHaveBeenCalledTimes(4);
  });

  it('初屏预取授权目录与 branches 交集的支行趋势，最多20家且并发不超过3', async () => {
    let active = 0; let maxActive = 0;
    queryScreenData.mockImplementation(request => new Promise(resolve => {
      active += 1; maxActive = Math.max(maxActive, active);
      setTimeout(() => {
        active -= 1;
        if (request.blockId === 21) {
          resolve({ columns: ['org_code'], rows: [['A'], ['B'], ['C'], ['D'], ['X']] });
        } else {
          resolve({ columns: ['date', 'deposit'], rows: Array.from({ length: 19 }, (_, index) => [`2026-${String(index + 1).padStart(2, '0')}`, 1]) });
        }
      }, 1);
    }));
    const view = viewWithSlots({ branches: 21, branchTrend: 22 });
    view.panoramaInstitutions = ['A', 'B', 'C', 'D', 'X', 'Y'].map(orgCode => ({ orgCode }));
    view.renderPackage.bindSnapshots['21'].bind.fields = { orgCode: 'org_code' };
    view.renderPackage.bindSnapshots['21'].bind.units = {};
    view.renderPackage.bindSnapshots['22'].bind.fields = { date: 'date', deposit: 'deposit' };
    view.renderPackage.bindSnapshots['22'].bind.units = { deposit: 'HUNDRED_MILLION' };
    const state = usePanoramaData(ref(view), ref({ screenCode: 'SCR_CODE' }), { autoLoad: false });
    await state.refresh();
    expect(maxActive).toBeLessThanOrEqual(3);
    expect(queryScreenData.mock.calls.filter(([request]) => request.blockId === 22)).toHaveLength(5);
    expect(state.model.value.institutions.filter(item => ['A', 'B', 'C', 'D', 'X'].includes(item.orgCode)).every(item => item.trend.length === 19)).toBe(true);
    expect(state.model.value.institutions.find(item => item.orgCode === 'Y')?.trend || []).toHaveLength(0);
  });

  it('刷新期间旧代预取迟到不得覆盖新代模型', async () => {
    const trendResolvers = [];
    queryScreenData.mockImplementation(request => {
      if (request.blockId === 21) return Promise.resolve({ columns: ['org_code'], rows: [['A']] });
      return new Promise(resolve => trendResolvers.push(resolve));
    });
    const view = viewWithSlots({ branches: 21, branchTrend: 22 });
    view.panoramaInstitutions = [{ orgCode: 'A' }];
    view.renderPackage.bindSnapshots['21'].bind.fields = { orgCode: 'org_code' };
    view.renderPackage.bindSnapshots['21'].bind.units = {};
    view.renderPackage.bindSnapshots['22'].bind.fields = { date: 'date', deposit: 'deposit' };
    view.renderPackage.bindSnapshots['22'].bind.units = { deposit: 'HUNDRED_MILLION' };
    const context = ref({ screenCode: 'SCR_CODE' });
    const state = usePanoramaData(ref(view), context, { autoLoad: false });
    const first = state.refresh();
    await vi.waitFor(() => expect(trendResolvers).toHaveLength(1));
    context.value = { ...context.value, dateTo: '2026-09-10' };
    const second = state.refresh();
    await vi.waitFor(() => expect(trendResolvers).toHaveLength(2));
    trendResolvers[1]({ columns: ['date', 'deposit'], rows: [['NEW', 2]] });
    await second;
    trendResolvers[0]({ columns: ['date', 'deposit'], rows: [['OLD', 1]] });
    await first;
    expect(state.model.value.institutions.find(item => item.orgCode === 'A')?.trend).toEqual([{ date: 'NEW', deposit: 2, loan: null }]);
  });

  it('严格批次模式先锁定首个 LATEST 的不透明 batchId，后续槽位和趋势预取透传同一批次', async () => {
    const pending = [];
    queryScreenData.mockImplementation(request => new Promise((resolve, reject) => {
      pending.push({ request, resolve, reject });
    }));
    const view = viewWithSlots({ deposit: 11, loan: 12 });
    const state = usePanoramaData(ref(view), ref({ screenCode: 'SCR_CODE' }), {
      autoLoad: false, batchRequired: true
    });
    const loading = state.refresh();
    await nextTick();
    expect(pending).toHaveLength(1);
    expect(pending[0].request).not.toHaveProperty('batchId');
    pending[0].resolve({
      quality: batchQuality(), columns: ['deposit_col'], rows: [[100000000]],
      columnsMeta: [{ col: 'deposit_col', role: 'METRIC', unit: 'YUAN', decimals: 2 }]
    });
    await vi.waitFor(() => expect(pending).toHaveLength(2));
    expect(pending[1].request.batchId).toBe('opaque-batch-1');
    pending[1].resolve({
      quality: batchQuality(), columns: ['loan_col'], rows: [[200000000]],
      columnsMeta: [{ col: 'loan_col', role: 'METRIC', unit: 'YUAN', decimals: 2 }]
    });
    await loading;
    expect(state.model.value.quality).toMatchObject({ batchId: 'opaque-batch-1', status: 'COMPLETE' });
    expect(state.model.value.kpis).toEqual(expect.arrayContaining([
      expect.objectContaining({ key: 'deposit', value: 1 }),
      expect.objectContaining({ key: 'loan', value: 2 })
    ]));
  });

  it('严格批次趋势预取只取当前前四家机构，并为每个请求透传锁定批次', async () => {
    queryScreenData.mockImplementation(request => {
      if (request.blockId === 11) {
        return Promise.resolve({ quality: batchQuality(), columns: ['value'], rows: [[100000000]] });
      }
      if (request.blockId === 21) {
        return Promise.resolve({ quality: batchQuality(), columns: ['org_code'], rows: [['A'], ['B'], ['C'], ['D'], ['E']] });
      }
      return Promise.resolve({ quality: batchQuality(), columns: ['date', 'deposit'], rows: [['2026-09-10', 100000000]] });
    });
    const view = viewWithSlots({ deposit: 11, branches: 21, branchTrend: 22 });
    view.panoramaInstitutions = ['A', 'B', 'C', 'D', 'E'].map(orgCode => ({ orgCode }));
    view.renderPackage.bindSnapshots['21'].bind.fields = { orgCode: 'org_code' };
    view.renderPackage.bindSnapshots['21'].bind.units = {};
    view.renderPackage.bindSnapshots['22'].bind.fields = { date: 'date', deposit: 'deposit' };
    view.renderPackage.bindSnapshots['22'].bind.units = { deposit: 'YUAN' };
    const state = usePanoramaData(ref(view), ref({ screenCode: 'SCR_CODE' }), {
      autoLoad: false, batchRequired: true
    });

    await state.refresh();

    const trendCalls = queryScreenData.mock.calls
      .map(([request]) => request)
      .filter(request => request.blockId === 22);
    expect(trendCalls).toHaveLength(4);
    expect(trendCalls.every(request => request.batchId === 'opaque-batch-1')).toBe(true);
    expect(state.model.value.quality).toMatchObject({ batchId: 'opaque-batch-1', status: 'COMPLETE' });
  });

  it('严格批次首响应无完整批次时不再请求其余槽位，并清空可见模型', async () => {
    queryScreenData.mockResolvedValue({
      quality: {
        batchId: null, dataDate: null, version: null, status: 'NO_COMPLETE_BATCH',
        selectedComplete: false, message: '没有完整批次'
      }, columns: [], rows: []
    });
    const state = usePanoramaData(ref(viewWithSlots({ deposit: 11, loan: 12 })), ref({ screenCode: 'SCR_CODE' }), {
      autoLoad: false, batchRequired: true
    });
    await state.refresh();
    expect(queryScreenData).toHaveBeenCalledTimes(1);
    expect(state.model.value.kpis).toEqual([]);
    expect(state.model.value.quality).toMatchObject({ status: 'NO_COMPLETE_BATCH', batchId: null });
  });

  it('严格批次后续响应缺少或混入其他 batchId 时弃用整轮模型，不沿用首槽或旧模型', async () => {
    const pending = [];
    queryScreenData.mockImplementation(request => new Promise((resolve, reject) => {
      pending.push({ request, resolve, reject });
    }));
    const state = usePanoramaData(ref(viewWithSlots({ deposit: 11, loan: 12 })), ref({ screenCode: 'SCR_CODE' }), {
      autoLoad: false, batchRequired: true
    });
    const loading = state.refresh();
    await nextTick();
    pending[0].resolve({ quality: batchQuality(), columns: ['deposit_col'], rows: [[100000000]] });
    await vi.waitFor(() => expect(pending).toHaveLength(2));
    pending[1].resolve({ columns: ['loan_col'], rows: [[200000000]] });
    await loading;
    expect(state.model.value.kpis).toEqual([]);
    expect(state.model.value.qualityGuard).toMatchObject({ code: 'QUALITY_MISSING' });
  });

  it('严格批次后续响应混入不同 dataClassification 时 fail-close，不发布混分类模型', async () => {
    const pending = [];
    queryScreenData.mockImplementation(request => new Promise((resolve, reject) => {
      pending.push({ request, resolve, reject });
    }));
    const state = usePanoramaData(ref(viewWithSlots({ deposit: 11, loan: 12 })), ref({ screenCode: 'SCR_CODE' }), {
      autoLoad: false, batchRequired: true
    });
    const loading = state.refresh();
    await nextTick();
    pending[0].resolve({ quality: batchQuality(), columns: ['deposit_col'], rows: [[100000000]] });
    await vi.waitFor(() => expect(pending).toHaveLength(2));
    pending[1].resolve({ quality: batchQuality({ dataClassification: 'PROD' }), columns: ['loan_col'], rows: [[200000000]] });
    await loading;
    expect(state.model.value.kpis).toEqual([]);
    expect(state.model.value.qualityGuard).toMatchObject({ code: 'DATA_CLASSIFICATION_MISMATCH' });
  });

  it('严格批次首响应分类不是 TEST/PROD 时 fail-close，不发布未知分类模型', async () => {
    queryScreenData.mockResolvedValue({
      quality: batchQuality({ dataClassification: 'UNKNOWN' }),
      columns: ['deposit_col'], rows: [[100000000]],
      columnsMeta: [{ col: 'deposit_col', role: 'METRIC', unit: 'YUAN', decimals: 2 }]
    });
    const state = usePanoramaData(ref(viewWithSlots({ deposit: 11, loan: 12 })), ref({ screenCode: 'SCR_CODE' }), {
      autoLoad: false, batchRequired: true
    });
    await state.refresh();
    expect(state.model.value.kpis).toEqual([]);
    expect(state.model.value.qualityGuard).toMatchObject({ code: 'QUALITY_NOT_USABLE' });
  });

  it('严格批次机构下钻携带已锁定 batchId，迟到/混批响应清空旧整屏模型', async () => {
    const pending = [];
    queryScreenData.mockImplementation(request => new Promise((resolve, reject) => {
      pending.push({ request, resolve, reject });
    }));
    const view = viewWithSlots({ branches: 21, branchTrend: 22 });
    view.panoramaInstitutions = [{ orgCode: 'A' }];
    view.renderPackage.bindSnapshots['21'].bind.fields = { orgCode: 'org_code' };
    view.renderPackage.bindSnapshots['21'].bind.units = {};
    view.renderPackage.bindSnapshots['22'].bind.fields = { date: 'date', deposit: 'deposit' };
    view.renderPackage.bindSnapshots['22'].bind.units = { deposit: 'YUAN' };
    const state = usePanoramaData(ref(view), ref({ screenCode: 'SCR_CODE' }), {
      autoLoad: false, batchRequired: true
    });
    const initial = state.refresh();
    await nextTick();
    expect(pending[0].request.blockId).toBe(21);
    pending[0].resolve({ quality: batchQuality(), columns: ['org_code'], rows: [['A']] });
    await vi.waitFor(() => expect(pending.some(item => item.request.blockId === 22)).toBe(true));
    const prefetch = pending.find(item => item.request.blockId === 22);
    expect(prefetch.request.batchId).toBe('opaque-batch-1');
    prefetch.resolve({ quality: batchQuality(), columns: ['date', 'deposit'], rows: [['2026-09', 100000000]] });
    await initial;
    expect(state.model.value.quality?.batchId).toBe('opaque-batch-1');

    const drill = state.selectBranch('A');
    await vi.waitFor(() => expect(pending.filter(item => item.request.blockId === 22).length).toBeGreaterThan(1));
    const drillRequest = pending.filter(item => item.request.blockId === 22).at(-1);
    expect(drillRequest.request.batchId).toBe('opaque-batch-1');
    drillRequest.resolve({ quality: batchQuality({ batchId: 'opaque-batch-2' }), columns: ['date', 'deposit'], rows: [['2026-09', 200000000]] });
    await drill;
    expect(state.model.value.kpis).toEqual([]);
    expect(state.model.value.qualityGuard).toMatchObject({ code: 'BATCH_MISMATCH' });
  });

  it('预取任一机构返回403时 fail-close，不发布主体模型', async () => {
    queryScreenData.mockImplementation(request => {
      if (request.blockId === 21) return Promise.resolve({ columns: ['org_code'], rows: [['A'], ['B']] });
      if (request.contextParams?.orgCode === 'B') return Promise.reject(Object.assign(new Error('没有权限（403）'), { status: 403, response: { status: 403 } }));
      return Promise.resolve({ columns: ['date', 'deposit'], rows: [['2026-09', 1]] });
    });
    const view = viewWithSlots({ branches: 21, branchTrend: 22 });
    view.panoramaInstitutions = [{ orgCode: 'A' }, { orgCode: 'B' }];
    view.renderPackage.bindSnapshots['21'].bind.fields = { orgCode: 'org_code' };
    view.renderPackage.bindSnapshots['21'].bind.units = {};
    view.renderPackage.bindSnapshots['22'].bind.fields = { date: 'date', deposit: 'deposit' };
    view.renderPackage.bindSnapshots['22'].bind.units = { deposit: 'HUNDRED_MILLION' };
    const state = usePanoramaData(ref(view), ref({ screenCode: 'SCR_CODE' }), { autoLoad: false });
    await state.refresh();
    expect(state.model.value.kpis).toEqual([]);
    expect(state.model.value.institutions).toEqual([]);
    expect(state.error.value).toContain('403');
  });

  it('预取普通500只标记对应机构趋势错误并保留主体模型', async () => {
    queryScreenData.mockImplementation(request => {
      if (request.blockId === 21) return Promise.resolve({ columns: ['org_code'], rows: [['A'], ['B']] });
      if (request.contextParams?.orgCode === 'B') return Promise.reject(Object.assign(new Error('趋势服务暂不可用'), { status: 500, response: { status: 500 } }));
      return Promise.resolve({ columns: ['date', 'deposit'], rows: [['2026-09', 1]] });
    });
    const view = viewWithSlots({ branches: 21, branchTrend: 22 });
    view.panoramaInstitutions = [{ orgCode: 'A' }, { orgCode: 'B' }];
    view.renderPackage.bindSnapshots['21'].bind.fields = { orgCode: 'org_code' };
    view.renderPackage.bindSnapshots['21'].bind.units = {};
    view.renderPackage.bindSnapshots['22'].bind.fields = { date: 'date', deposit: 'deposit' };
    view.renderPackage.bindSnapshots['22'].bind.units = { deposit: 'HUNDRED_MILLION' };
    const state = usePanoramaData(ref(view), ref({ screenCode: 'SCR_CODE' }), { autoLoad: false });
    await state.refresh();
    expect(state.model.value.institutions).toHaveLength(2);
    expect(state.model.value.institutions.find(item => item.orgCode === 'B')?.trendIssue).toContain('趋势服务暂不可用');
    expect(state.error.value).toBe('');
  });

  it('切换机构后旧 branchTrend 迟到响应不能污染新机构', async () => {
    const pending = [];
    queryScreenData.mockImplementation(request => new Promise(resolve => pending.push({ request, resolve })));
    const view = ref(viewWithSlots({ branches: 21, branchTrend: 22 }));
    const context = ref({ screenCode: 'SCR_CODE', orgCode: 'A' });
    const state = usePanoramaData(view, context, { autoLoad: false });
    const initialLoad = state.refresh();
    await nextTick();
    expect(pending.find(item => item.request.blockId === 21)?.request.contextParams).toEqual({});
    pending.filter(item => item.request.blockId === 21)
      .forEach(item => item.resolve({ columns: ['value'], rows: [[0]], columnsMeta: [{ col: 'value', amountScale: 'YUAN' }] }));
    await initialLoad;
    const oldLoad = state.selectBranch('A');
    await nextTick();
    const newLoad = state.selectBranch('B');
    await nextTick();
    pending.filter(item => item.request.blockId === 22 && item.request.contextParams?.orgCode === 'A')
      .forEach(item => item.resolve({ columns: ['date', 'value'], rows: [['OLD', 1]] }));
    pending.filter(item => item.request.blockId === 22 && item.request.contextParams?.orgCode === 'B')
      .forEach(item => item.resolve({ columns: ['date', 'value'], rows: [['NEW', 2]] }));
    pending.filter(item => item.request.blockId === 21).forEach(item => item.resolve({ columns: ['value'], rows: [[0]], columnsMeta: [{ col: 'value', amountScale: 'YUAN' }] }));
    await Promise.all([oldLoad, newLoad]);
    expect(state.model.value.institutions.find(item => item.orgCode === 'A')?.trend || []).not.toContainEqual(expect.objectContaining({ date: 'OLD' }));
  });

  it('401/403 会清空整屏模型并保留错误，null 响应按 slot 记录错误', async () => {
    queryScreenData.mockRejectedValueOnce(Object.assign(new Error('禁止访问'), { response: { status: 403 }, status: 403 }));
    const state = usePanoramaData(ref(viewWithSlots({ deposit: 11 })), ref({ screenCode: 'SCR_CODE' }), { autoLoad: false });
    await state.refresh();
    expect(state.error.value).toContain('禁止访问');
    expect(state.model.value.kpis).toEqual([]);
    queryScreenData.mockResolvedValueOnce(null);
    await state.refresh();
    expect(state.model.value.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'deposit', code: 'NULL_RESPONSE' })
    ]));
  });

  it('支行 A 趋势失败后切换到 B，B 成功会清理 A 的 slot 错误', async () => {
    const pending = [];
    queryScreenData.mockImplementation(request => new Promise((resolve, reject) => {
      pending.push({ request, resolve, reject });
    }));
    const view = ref(viewWithSlots({ branches: 21, branchTrend: 22 }));
    view.value.renderPackage.bindSnapshots['21'].bind.fields = { orgCode: 'org_code' };
    view.value.renderPackage.bindSnapshots['21'].bind.units = {};
    view.value.renderPackage.bindSnapshots['22'].bind.fields = { date: 'date', deposit: 'deposit' };
    view.value.renderPackage.bindSnapshots['22'].bind.units = { deposit: 'YUAN' };
    const state = usePanoramaData(view, ref({ screenCode: 'SCR_CODE' }), { autoLoad: false });
    const initialLoad = state.refresh();
    await nextTick();
    pending.find(item => item.request.blockId === 21).resolve({
      columns: ['org_code'], rows: [['A'], ['B']]
    });
    await initialLoad;

    const failedA = state.selectBranch('A');
    await nextTick();
    pending.find(item => item.request.blockId === 22 && item.request.contextParams?.orgCode === 'A')
      .reject(new Error('A 趋势失败'));
    await failedA;
    expect(state.model.value.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'branchTrend', code: 'REQUEST_FAILED' })
    ]));

    const successB = state.selectBranch('B');
    await nextTick();
    pending.find(item => item.request.blockId === 22 && item.request.contextParams?.orgCode === 'B')
      .resolve({ columns: ['date', 'deposit'], rows: [['2026-09', 100000000]] });
    await successB;
    expect(state.model.value.issues.some(item => item.slot === 'branchTrend')).toBe(false);
    expect(state.model.value.institutions.find(item => item.orgCode === 'B')?.trend)
      .toEqual([{ date: '2026-09', deposit: 1, loan: null }]);
  });

  it('按发布 template 动态选择零售槽位，跨模板槽位不请求且零售不启用 branchTrend drill', async () => {
    queryScreenData.mockImplementation(request => {
      if (request.blockId === 31) return Promise.resolve({ columns: ['aum'], rows: [[100000000]] });
      if (request.blockId === 34) return Promise.resolve({ columns: ['org_code'], rows: [['A']] });
      return Promise.resolve({ columns: ['value'], rows: [[100000000]] });
    });
    const state = usePanoramaData(ref(retailViewWithSlots({ retailAum: 31, deposit: 32, branchTrend: 33, branches: 34 })),
      ref({ screenCode: 'RETAIL_CODE' }), { autoLoad: false });
    await state.refresh();
    expect(queryScreenData.mock.calls.map(([request]) => request.blockId)).toEqual(expect.arrayContaining([31, 34]));
    expect(queryScreenData.mock.calls.map(([request]) => request.blockId)).not.toEqual(expect.arrayContaining([32, 33]));
    expect(state.model.value.kpis).toEqual([{ key: 'retailAum', label: '零售AUM', value: 1, unit: '亿元', change: null }]);
    const count = queryScreenData.mock.calls.length;
    await state.selectBranch('A');
    expect(queryScreenData).toHaveBeenCalledTimes(count);
  });

  it('零售 403 清空零售模型并保留错误，切屏迟到结果不能污染新模型', async () => {
    let resolveOld;
    queryScreenData.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; }));
    const view = ref(retailViewWithSlots({ retailAum: 41 }));
    const state = usePanoramaData(view, ref({ screenCode: 'RETAIL_CODE' }), { autoLoad: false });
    const oldLoad = state.refresh();
    await nextTick();
    view.value = viewWithSlots({ deposit: 42 });
    queryScreenData.mockResolvedValueOnce({ columns: ['deposit_col'], rows: [[200000000]] });
    const newLoad = state.refresh();
    await newLoad;
    resolveOld({ columns: ['aum'], rows: [[100000000]] });
    await oldLoad;
    expect(state.model.value.kpis).toEqual([{ key: 'deposit', label: '存款余额', value: 2, unit: '亿元', change: null }]);

    queryScreenData.mockRejectedValueOnce(Object.assign(new Error('零售禁止'), { response: { status: 403 }, status: 403 }));
    await state.refresh();
    expect(state.error.value).toContain('零售禁止');
    expect(state.model.value.kpis).toEqual([]);
  });
});
