// @vitest-environment happy-dom
import { describe, it, expect, vi } from 'vitest';
import { ref } from 'vue';
const query = vi.hoisted(() => vi.fn());
vi.mock('@/api/screen', () => ({ queryScreenData: (...args) => query(...args) }));
import { usePanoramaData } from '../usePanoramaData';

function fixture() {
  const metrics = [{ metricCode: 'BR_DIRECT_DEP', metricName: 'total' }, { metricCode: 'BR_DIRECT_RETAIL_DEP', metricName: 'retail' }];
  const config = { table: 'ORG_INDEX_RESULT', scopeMode: 'NAMED_GROUP', aggregation: { agg: 'SUM', groupBy: 'SUBJECT' }, metrics };
  const view = { screenCode: 'SCR_PROVINCE', orgScopeMode: 'NAMED_GROUP', runtimeSchemaVersion: 2,
    panoramaInstitutions: [{ orgCode: 'A', cityCode: '610300', cityName: '宝鸡市' }, { orgCode: 'B', cityCode: '610100', cityName: '西安市' }],
    renderPackage: { canvasStyle: { periodComparisons: { 'overview-deposit': Object.fromEntries(['day', 'month', 'year'].map(key => [key, { mode: 'OFFSET', outputColumn: 'total', unit: 'YUAN' }])) }, presentation: { type: 'CODE', template: 'branch-overview-v1', displaySchemaVersion: 1, display: { components: [
      { componentId: 'business-retail-deposit-balance', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', dataRefs: [{ blockId: 31, unit: 'YUAN' }], content: { mainField: 'retail' } }
    ] } } }, components: [
      { id: 'deposit', component: 'ChartWidget', blockId: 31, propValue: { bindingKey: 'deposit' } },
      { id: 'city', component: 'ChartWidget', blockId: 60, propValue: { bindingKey: 'citySummary' } }
    ], bindSnapshots: {
      '31': { bind: { dsId: 1, fields: { value: 'total', date: 'data_date' }, units: { value: 'YUAN' } }, sourceDefinition: { config: { ...config, aggregation: { agg: 'SUM', groupBy: 'NONE' } } } },
      '60': { bind: { dsId: 2, fields: { orgCode: 'org_code', deposit: 'total' }, units: { deposit: 'YUAN' } }, sourceDefinition: { config } }
    } } };
  const quality = { batchId: 'batch-1', dataDate: '2026-11-09', subjectValueMode: 'EXCLUSIVE', status: 'COMPLETE', selectedComplete: true, version: 'V1' };
  query.mockImplementation(async req => req.blockId === 31
    ? { columns: ['total', 'data_date'], rows: [[1000, '2026-11-09']], quality }
    : { columns: ['org_code', 'total', 'retail'], columnsMeta: ['total', 'retail'].map(col => ({ col, role: 'METRIC', unit: 'YUAN' })), rows: [['A', 100, 40], ['B', 900, 360]], quality });
  return view;
}

describe('city operating data integration', () => {
  it.each([401, 403])('城市历史请求 %s 时清空当前展示，不能继续展示旧城市值', async status => {
    const view = fixture();
    const base = query.getMockImplementation();
    query.mockImplementation(req => req.blockId === 60 && req.period === 'RANGE' ? Promise.reject({ status }) : base(req));
    const state = usePanoramaData(ref(view), ref({}), { autoLoad: false });
    await state.refresh();
    expect(state.model.value.permissionStatus).toBe(status);
    expect(state.model.value.citySummaries).toEqual({});
    state.dispose();
  });
  it('比较期返回另一批次时保留当前指标但不混入该批次历史', async () => {
    const view = fixture();
    const base = query.getMockImplementation();
    query.mockImplementation(async req => {
      const result = await base(req);
      return req.blockId === 60 && req.period === 'RANGE' ? { ...result, quality: { ...result.quality, batchId: 'other' } } : result;
    });
    const state = usePanoramaData(ref(view), ref({}), { autoLoad: false });
    await state.refresh();
    expect(state.model.value.citySummaries['610300'].blockResults['31'].retail).toBe(40);
    expect(state.model.value.citySummaries['610300'].blockResults['city-history:60']).toBeUndefined();
    expect(state.model.value.issues.some(i => i.code === 'HISTORY_BATCH_MISMATCH')).toBe(true);
    state.dispose();
  });
  it('将已发布的机构独占指标映射给城市页，基期逐日查询并锁定同批次', async () => {
    query.mockReset();
    const state = usePanoramaData(ref(fixture()), ref({}), { autoLoad: false });
    await state.refresh();
    expect(state.model.value.citySummaries['610300'].kpis).toContainEqual(expect.objectContaining({ key: 'retailDeposit', value: 40 }));
    expect(state.model.value.citySummaries['610300'].blockResults['31'].rows[0].retail).toBe(40);
    const history = query.mock.calls.map(([r]) => r).filter(r => r.blockId === 60 && r.period === 'RANGE');
    expect(history.map(r => r.dateFrom).sort()).toEqual(['2025-12-31', '2026-10-31', '2026-11-08']);
    expect(history.every(r => r.dateFrom === r.dateTo && r.batchId === 'batch-1')).toBe(true);
    expect(state.model.value.citySummaries['610300'].blockResults['city-history:60'].rows.map(row => row.data_date).sort())
      .toEqual(['2025-12-31', '2026-10-31', '2026-11-08', '2026-11-09']);
    state.dispose();
  });
});
