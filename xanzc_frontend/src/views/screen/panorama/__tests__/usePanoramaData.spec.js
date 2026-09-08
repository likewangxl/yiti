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
  beforeEach(() => { queryScreenData.mockReset(); });
  afterEach(() => vi.restoreAllMocks());

  it('发布组件只从 blockId -> bindSnapshots 取身份，schema2 不把 dsId 上送且普通槽位不继承 URL 机构上下文', async () => {
    queryScreenData.mockResolvedValue({ columns: ['value'], rows: [[100000000]], columnsMeta: [{ col: 'value', role: 'METRIC', amountScale: 'YUAN' }] });
    const state = usePanoramaData(ref(viewWithSlots({ deposit: 11 })), ref({ screenCode: 'SCR_CODE', orgCode: 'URL_SCOPE', empId: 'URL_EMP' }), { autoLoad: false });
    await state.refresh();
    expect(queryScreenData).toHaveBeenCalledWith(expect.objectContaining({
      schemaVersion: 2, screenCode: 'SCR_CODE', blockId: 11, contextParams: {}
    }));
    expect(queryScreenData.mock.calls[0][0]).not.toHaveProperty('dsId');
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
