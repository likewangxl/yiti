// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';

const queryScreenData = vi.fn();
vi.mock('@/api/screen', () => ({ queryScreenData: (...args) => queryScreenData(...args) }));

import { normalizeBlockResult, usePanoramaData } from '../../panorama/usePanoramaData';
import { buildDisplayMetricsModel } from '../model/displayMetricsModel';
import { buildDisplaySeriesTableModel } from '../model/displaySeriesTableModel';
import { buildCompositionTabsModel } from '../model/compositionTabsModel';
import { buildInstitutionRankingModel } from '../model/institutionRankingModel';

const presentation = {
  type: 'CODE',
  template: 'branch-overview-v1',
  displaySchemaVersion: 1,
  display: {
    components: [
      {
        componentId: 'metric', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 0, visible: true,
        content: { mainField: 'value' },
        dataRefs: [{ blockId: 11, metricCode: 'M_DEPOSIT', metricName: '存款余额', unit: 'HUNDRED_MILLION', dimension: 'ORG' }]
      },
      {
        componentId: 'trend', componentType: 'TREND', layoutRegion: 'CENTER', order: 1, visible: true,
        content: { series: [{ seriesKey: 'deposit', field: 'deposit', label: '存款', unit: 'HUNDRED_MILLION' }] },
        dataRefs: [{ blockId: 12, metricCode: 'M_TREND', metricName: '趋势', unit: 'HUNDRED_MILLION', dimension: 'ORG' }]
      },
      {
        componentId: 'composition', componentType: 'COMPOSITION_TABS', layoutRegion: 'CENTER', order: 2, visible: true,
        content: { tabs: [{ tabKey: 'deposit', label: '存款', corporateField: 'corporate', retailField: 'retail', totalField: 'total', unit: 'HUNDRED_MILLION' }] },
        dataRefs: [{ blockId: 13, metricCode: 'M_COMPOSITION', metricName: '业务构成', unit: 'HUNDRED_MILLION', dimension: 'ORG' }]
      },
      {
        componentId: 'ranking', componentType: 'RANKING', layoutRegion: 'RIGHT', order: 3, visible: true,
        content: { rankingMetrics: [{ metricKey: 'value', field: 'value', label: '存款余额', unit: 'HUNDRED_MILLION', direction: 'DESC' }] },
        dataRefs: [{ blockId: 14, metricCode: 'M_RANKING', metricName: '机构排名', unit: 'HUNDRED_MILLION', dimension: 'ORG' }]
      }
    ]
  }
};

function binding(slot, blockId, fields, units) {
  return { id: `node-${slot}`, component: 'ChartWidget', blockId, innerType: 'METRIC_CARD', propValue: { bindingKey: slot },
    snapshot: { bind: { dsId: 9, period: 'LATEST', fields, units }, componentType: 'METRIC_CARD' } };
}

function view() {
  const components = [
    binding('deposit', 11, { value: 'deposit_raw' }, { value: 'HUNDRED_MILLION' }),
    binding('trend', 12, { date: 'data_date', deposit: 'deposit_raw' }, { deposit: 'HUNDRED_MILLION' }),
    binding('composition', 13, { corporate: 'corp_raw', retail: 'retail_raw', total: 'total_raw' }, { corporate: 'HUNDRED_MILLION', retail: 'HUNDRED_MILLION', total: 'HUNDRED_MILLION' }),
    binding('ranking', 14, { orgCode: 'org_code_raw', name: 'org_name_raw', value: 'value_raw' }, { value: 'HUNDRED_MILLION' })
  ];
  return {
    screenCode: 'SCR_CODE', runtimeSchemaVersion: 2,
    renderPackage: {
      canvasStyle: { presentation },
      components,
      bindSnapshots: Object.fromEntries(components.map(component => [String(component.blockId), component.snapshot]))
    },
    panoramaInstitutions: [{ orgCode: 'A', orgName: '甲' }, { orgCode: 'B', orgName: '乙' }]
  };
}

function configuredComparisonView(configs = {
  metric: { enabled: true, historyBlockId: 12, valueFields: ['deposit_raw'], dateField: 'data_date', sourceUnit: 'YUAN' }
}) {
  const configuredView = structuredClone(view());
  configuredView.renderPackage.canvasStyle.presentation = {
    ...presentation,
    display: { ...presentation.display, comparisons: configs }
  };
  return configuredView;
}

function comparisonBatchQuality(overrides = {}) {
  return {
    batchId: 'comparison-batch-1', dataDate: '2026-09-30', version: 'V1', status: 'COMPLETE',
    dataClassification: 'TEST', selectedComplete: true, ...overrides
  };
}

function comparisonMainResponse(request, quality = null) {
  const dataDate = request?.dateTo || '2026-09-30';
  const responses = {
    11: { columns: ['deposit_raw'], rows: [[400]], dataDate },
    12: { columns: ['data_date', 'deposit_raw'], rows: [[dataDate, 400]], dataDate },
    13: { columns: ['corp_raw', 'retail_raw', 'total_raw'], rows: [[4, 6, 10]], dataDate },
    14: { columns: ['org_code_raw', 'org_name_raw', 'value_raw'], rows: [['A', '甲', 4], ['B', '乙', 2]], dataDate }
  };
  const response = responses[request?.blockId] || { columns: [], rows: [], dataDate };
  return quality ? { ...response, quality } : response;
}

describe('usePanoramaData 到新展示模型的真实 raw 结果链路', () => {
  beforeEach(() => queryScreenData.mockReset());

  it('按发布 blockId 暴露只读 blockResults，四类展示模型都能读取各自配置字段', async () => {
    queryScreenData.mockImplementation(request => ({
      11: { columns: ['deposit_raw'], rows: [[4]] },
      12: { columns: ['data_date', 'deposit_raw'], rows: [['2026-08', 2], ['2026-09', 4]] },
      13: { columns: ['corp_raw', 'retail_raw', 'total_raw'], rows: [[4, 6, 10]] },
      14: { columns: ['org_code_raw', 'org_name_raw', 'value_raw'], rows: [['A', '甲', 4], ['B', '乙', 2]] }
    }[(request || {}).blockId] || { columns: [], rows: [] }));
    const state = usePanoramaData(ref(view()), ref({ screenCode: 'SCR_CODE' }), { autoLoad: false });

    await state.refresh();

    const blocks = state.model.value.blockResults;
    expect(blocks).toBeTruthy();
    expect(blocks['11']).toMatchObject({ value: 4, deposit_raw: 4, columns: ['deposit_raw'], units: { value: 'HUNDRED_MILLION' } });
    expect(blocks['11'].rows).toEqual([{ deposit_raw: 4, value: 4 }]);
    expect(blocks['13']).toMatchObject({ corporate: 4, retail: 6, total: 10, corp_raw: 4, retail_raw: 6, total_raw: 10 });

    const metrics = buildDisplayMetricsModel(presentation, state.model.value);
    expect(metrics.components[0]).toMatchObject({ blockId: 11, rawValue: 4, state: 'READY' });

    const series = buildDisplaySeriesTableModel(presentation, state.model.value);
    expect(series.components.find(item => item.componentId === 'trend')).toMatchObject({
      state: 'READY', rows: [{ date: '2026-08', deposit: 2 }, { date: '2026-09', deposit: 4 }]
    });

    const composition = buildCompositionTabsModel(presentation, state.model.value);
    expect(composition.tabs[0]).toMatchObject({ state: 'READY', corporate: { value: 4 }, retail: { value: 6 }, total: { value: 10 } });
    expect(composition.tabs[0].corporate.share).toBe(40);
    expect(composition.tabs[0].retail.share).toBe(60);

    const ranking = buildInstitutionRankingModel({
      institutions: state.model.value.institutions,
      sourceAuthorized: true,
      rows: state.model.value.blockResults['14'].rows,
      rankingMetrics: presentation.display.components[3].content.rankingMetrics
    });
    expect(ranking.rows.map(item => [item.orgCode, item.value])).toEqual([['A', 4], ['B', 2]]);
  });

  it('失败和403的刷新不保留本轮新 raw blockResults，也不把错误响应伪装成旧成功结果', async () => {
    queryScreenData.mockImplementation(request => (request || {}).blockId === 11
      ? { columns: ['deposit_raw'], rows: [[4]] }
      : { columns: [], rows: [] });
    const viewRef = ref(view());
    const state = usePanoramaData(viewRef, ref({ screenCode: 'SCR_CODE' }), { autoLoad: false });
    await state.refresh();
    expect(state.model.value.blockResults['11'].value).toBe(4);

    queryScreenData.mockRejectedValueOnce(Object.assign(new Error('服务失败'), { status: 500 }));
    await state.refresh();
    expect(state.model.value.blockResults).toEqual({});

    queryScreenData.mockRejectedValueOnce(Object.assign(new Error('无权访问'), { status: 403 }));
    await state.refresh();
    expect(state.model.value.blockResults).toEqual({});
    expect(state.model.value.permissionStatus).toBe(403);
  });

  it('缺列或重复 raw 列只跳过不可信语义别名，并记录不可用原因，不猜测列名', () => {
    const result = normalizeBlockResult(99, {
      fields: { value: 'missing_value', date: 'raw_date', deposit: 'raw_date', rows: 'raw_date' },
      units: { value: 'HUNDRED_MILLION' }
    }, { columns: ['raw_date'], rows: [['2026-09']] });

    expect(result.value).toBeUndefined();
    expect(result.date).toBe('2026-09');
    expect(result.deposit).toBeUndefined();
    expect(result.rows).toEqual([{ raw_date: '2026-09', date: '2026-09' }]);
    expect(result.aliasIssues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'MISSING_ALIAS_COLUMN', semantic: 'value' }),
      expect.objectContaining({ code: 'DUPLICATE_ALIAS_COLUMN', semantic: 'deposit' }),
      expect.objectContaining({ code: 'INVALID_SEMANTIC_ALIAS', semantic: 'rows' })
    ]));
  });

  it('显式比较来源用同一历史 block 的 RANGE 请求，结果单独进入 comparisonResults', async () => {
    const configuredView = view();
    configuredView.renderPackage.canvasStyle.presentation = {
      ...presentation,
      display: {
        ...presentation.display,
        comparisons: { metric: { enabled: true, historyBlockId: 12, valueFields: ['deposit_raw'], dateField: 'data_date', sourceUnit: 'YUAN' } }
      }
    };
    const requests = [];
    queryScreenData.mockImplementation(request => {
      requests.push(request);
      if (request?.blockId === 12 && request?.period === 'RANGE') return {
        columns: ['data_date', 'deposit_raw'], rows: [
          ['2026-09-30', 400], ['2026-08-31', 300], ['2025-12-31', 200], ['2026-09-29', 350]
        ], unitByField: { deposit_raw: 'YUAN' }, dataDate: '2026-09-30'
      };
      return ({
        11: { columns: ['deposit_raw'], rows: [[4]], unitByField: { deposit_raw: 'HUNDRED_MILLION' }, dataDate: '2026-09-30' },
        12: { columns: ['data_date', 'deposit_raw'], rows: [['2026-09-30', 400]], unitByField: { deposit_raw: 'YUAN' }, dataDate: '2026-09-30' },
        13: { columns: ['corp_raw', 'retail_raw', 'total_raw'], rows: [[4, 6, 10]] },
        14: { columns: ['org_code_raw', 'org_name_raw', 'value_raw'], rows: [['A', '甲', 4], ['B', '乙', 2]] }
      }[request?.blockId] || { columns: [], rows: [] });
    });
    const state = usePanoramaData(ref(configuredView), ref({ screenCode: 'SCR_CODE' }), { autoLoad: false });
    await state.refresh();
    expect(requests.filter(request => request.blockId === 12 && request.period === 'RANGE')).toHaveLength(1);
    expect(requests.find(request => request.blockId === 12 && request.period === 'RANGE')).toMatchObject({ dateFrom: '2025-12-31', dateTo: '2026-09-30', screenCode: 'SCR_CODE' });
    expect(state.model.value.comparisonResults['12']).toMatchObject({ rows: expect.any(Array), unitByField: { deposit_raw: 'YUAN' } });
  });

  it.each(['INVALID_DATE', '0001-01-01'])('数据日非法或无法计算年末时不发比较 RANGE 请求（%s）', async invalidDate => {
    const configuredView = configuredComparisonView();
    const requests = [];
    queryScreenData.mockImplementation(request => {
      requests.push(request);
      const response = comparisonMainResponse(request);
      response.dataDate = invalidDate;
      return response;
    });
    const state = usePanoramaData(ref(configuredView), ref({ screenCode: 'SCR_CODE' }), { autoLoad: false });

    await state.refresh();

    expect(requests.filter(request => request.period === 'RANGE')).toHaveLength(0);
    expect(state.model.value.comparisonResults).toEqual({});
  });

  it('比较历史引用必须同时存在可信快照和当前可见 LINE_TREND 区块', async () => {
    const configuredView = configuredComparisonView({
      metric: { enabled: true, historyBlockId: 11, valueFields: ['deposit_raw'], dateField: 'data_date', sourceUnit: 'YUAN' }
    });
    const requests = [];
    queryScreenData.mockImplementation(request => {
      requests.push(request);
      return comparisonMainResponse(request);
    });
    const state = usePanoramaData(ref(configuredView), ref({ screenCode: 'SCR_CODE' }), { autoLoad: false });

    await state.refresh();

    expect(requests.filter(request => request.period === 'RANGE')).toHaveLength(0);
    expect(state.model.value.comparisonResults).toEqual({});
    expect(state.model.value.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'comparison', code: 'HISTORY_SOURCE_NOT_TRUSTED' })
    ]));
  });

  it.each([401, 403])('比较来源 %s 清空主模型并保留权限错误，不复用上一轮结果', async status => {
    const configuredView = configuredComparisonView();
    queryScreenData.mockImplementation(request => {
      if (request?.period === 'RANGE') return Promise.reject(Object.assign(new Error('比较来源无权访问'), { status }));
      return comparisonMainResponse(request);
    });
    const state = usePanoramaData(ref(configuredView), ref({ screenCode: 'SCR_CODE' }), { autoLoad: false });

    await state.refresh();

    expect(state.model.value.kpis).toEqual([]);
    expect(state.model.value.blockResults).toEqual({});
    expect(state.model.value.comparisonResults).toEqual({});
    expect(state.model.value.permissionStatus).toBe(status);
    expect(state.error.value).toContain('比较来源无权访问');
  });

  it('完整批次模式拒绝比较错 batch、缺质量和 partial 响应，主值保留且比较待接入', async () => {
    const configuredView = configuredComparisonView();
    queryScreenData.mockImplementation(request => {
      if (request?.period === 'RANGE') return {
        ...comparisonMainResponse(request),
        quality: comparisonBatchQuality({ batchId: 'comparison-batch-wrong' }),
        unitByField: { deposit_raw: 'YUAN' }
      };
      return comparisonMainResponse(request, comparisonBatchQuality());
    });
    const state = usePanoramaData(ref(configuredView), ref({ screenCode: 'SCR_CODE' }), {
      autoLoad: false, batchRequired: true
    });

    await state.refresh();

    expect(state.model.value.kpis).toEqual(expect.arrayContaining([expect.objectContaining({ key: 'deposit' })]));
    expect(state.model.value.comparisonResults).toEqual({});
    expect(state.model.value.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'comparison', code: 'BATCH_MISMATCH' })
    ]));
  });

  it.each([
    ['缺少 quality', { quality: undefined }, 'QUALITY_MISSING'],
    ['partial quality', { quality: comparisonBatchQuality({ status: 'PARTIAL' }) }, 'QUALITY_NOT_USABLE']
  ])('完整批次模式比较响应%s时保持主值、比较待接入并记录质量问题', async (_label, rangePayload, issueCode) => {
    const configuredView = configuredComparisonView();
    queryScreenData.mockImplementation(request => {
      if (request?.period === 'RANGE') {
        const response = { ...comparisonMainResponse(request), unitByField: { deposit_raw: 'YUAN' } };
        if (rangePayload.quality !== undefined) response.quality = rangePayload.quality;
        return response;
      }
      return comparisonMainResponse(request, comparisonBatchQuality());
    });
    const state = usePanoramaData(ref(configuredView), ref({ screenCode: 'SCR_CODE' }), {
      autoLoad: false, batchRequired: true
    });

    await state.refresh();

    expect(state.model.value.kpis).toEqual(expect.arrayContaining([expect.objectContaining({ key: 'deposit' })]));
    expect(state.model.value.comparisonResults).toEqual({});
    expect(state.model.value.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'comparison', code: issueCode })
    ]));
  });

  it('缺失历史绑定快照时不查询 RANGE，并记录不可信来源', async () => {
    const configuredView = configuredComparisonView();
    delete configuredView.renderPackage.bindSnapshots['12'];
    const requests = [];
    queryScreenData.mockImplementation(request => {
      requests.push(request);
      return comparisonMainResponse(request);
    });
    const state = usePanoramaData(ref(configuredView), ref({ screenCode: 'SCR_CODE' }), { autoLoad: false });

    await state.refresh();

    expect(requests.filter(request => request.period === 'RANGE')).toHaveLength(0);
    expect(state.model.value.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'comparison', code: 'HISTORY_SOURCE_NOT_TRUSTED' })
    ]));
  });

  it('同一历史 block 的多个比较配置只发一次 RANGE，比较来源变化触发重载而标题变化不触发', async () => {
    const configuredView = configuredComparisonView({
      metric: { enabled: true, historyBlockId: 12, valueFields: ['deposit_raw'], dateField: 'data_date', sourceUnit: 'YUAN' },
      composition: { enabled: true, historyBlockId: 12, valueFields: ['deposit_raw'], dateField: 'data_date', sourceUnit: 'YUAN' }
    });
    const requests = [];
    queryScreenData.mockImplementation(request => {
      requests.push(request);
      if (request?.period === 'RANGE') return {
        ...comparisonMainResponse(request),
        unitByField: { deposit_raw: 'YUAN' },
        rows: [['2026-09-30', 400], ['2026-09-29', 350], ['2025-12-31', 200]]
      };
      return comparisonMainResponse(request);
    });
    const viewRef = ref(configuredView);
    const contextRef = ref({ screenCode: 'SCR_CODE' });
    const state = usePanoramaData(viewRef, contextRef, { autoLoad: false });
    await state.refresh();
    expect(requests.filter(request => request.period === 'RANGE')).toHaveLength(1);

    const firstCount = requests.length;
    const titleChanged = structuredClone(configuredView);
    titleChanged.screenName = '标题变化';
    viewRef.value = titleChanged;
    await vi.waitFor(() => expect(requests.length).toBe(firstCount));

    const sourceChanged = structuredClone(titleChanged);
    sourceChanged.renderPackage.canvasStyle.presentation.display.comparisons.metric.valueFields = ['deposit_v2'];
    viewRef.value = sourceChanged;
    await state.refresh();
    expect(requests.filter(request => request.period === 'RANGE')).toHaveLength(2);
  });

  it('比较历史旧代迟到响应不得覆盖新 generation 的结果', async () => {
    const firstView = configuredComparisonView();
    const requests = [];
    const rangeResolvers = [];
    queryScreenData.mockImplementation(request => {
      requests.push(request);
      if (request?.period === 'RANGE') return new Promise(resolve => rangeResolvers.push({ request, resolve }));
      return comparisonMainResponse(request);
    });
    const viewRef = ref(firstView);
    const contextRef = ref({ screenCode: 'SCR_CODE', dateTo: '2026-09-30' });
    const state = usePanoramaData(viewRef, contextRef, { autoLoad: false });
    const first = state.refresh();
    await vi.waitFor(() => expect(rangeResolvers).toHaveLength(1));

    contextRef.value = { ...contextRef.value, dateTo: '2026-10-31' };
    const second = state.refresh();
    await vi.waitFor(() => expect(rangeResolvers).toHaveLength(2));
    rangeResolvers[1].resolve({
      ...comparisonMainResponse(rangeResolvers[1].request),
      unitByField: { deposit_raw: 'YUAN' },
      rows: [['2026-10-31', 400], ['2026-10-30', 390], ['2025-12-31', 222]]
    });
    await second;
    rangeResolvers[0].resolve({
      ...comparisonMainResponse(rangeResolvers[0].request),
      unitByField: { deposit_raw: 'YUAN' },
      rows: [['2026-09-30', 400], ['2026-09-29', 390], ['2025-12-31', 111]]
    });
    await first;

    expect(state.model.value.comparisonResults['12'].rows).toEqual(expect.arrayContaining([
      expect.objectContaining({ deposit_raw: 222 })
    ]));
    expect(state.model.value.comparisonResults['12'].rows).not.toEqual(expect.arrayContaining([
      expect.objectContaining({ deposit_raw: 111 })
    ]));
  });
});
