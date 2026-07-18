import { describe, it, expect } from 'vitest';
import { filterDatasourcesByMeta } from '../dsFilter';

// 数据源行样例（listScreenDatasources 响应字段：dsType / sourceKind）
const DS = [
  { id: 1, dsName: '存款趋势', dsType: 'TIMESERIES', sourceKind: 'WIDE_TABLE' },
  { id: 2, dsName: '存款快照', dsType: 'SINGLE', sourceKind: 'WIDE_TABLE' },
  { id: 3, dsName: 'KPI细项', dsType: 'SINGLE', sourceKind: 'KPI_DETAIL' },
  { id: 4, dsName: 'KPI细项趋势', dsType: 'TIMESERIES', sourceKind: 'KPI_DETAIL' },
  { id: 5, dsName: '自定义SQL', dsType: 'SINGLE', sourceKind: 'CUSTOM_SQL' }
];

describe('chart-widget dsFilter（属性面板数据源下拉按图表元数据过滤）', () => {
  it('needTimeseries → 仅 TIMESERIES', () => {
    const r = filterDatasourcesByMeta(DS, { needTimeseries: true });
    expect(r.map(d => d.id)).toEqual([1, 4]);
  });
  it('needKinds → 仅对应 source_kind', () => {
    const r = filterDatasourcesByMeta(DS, { needKinds: ['KPI_DETAIL'] });
    expect(r.map(d => d.id)).toEqual([3, 4]);
  });
  it('两个条件同时生效（交集）', () => {
    const r = filterDatasourcesByMeta(DS, { needTimeseries: true, needKinds: ['KPI_DETAIL'] });
    expect(r.map(d => d.id)).toEqual([4]);
  });
  it('无约束 / meta 缺失 → 原样返回；list 非数组容错为 []', () => {
    expect(filterDatasourcesByMeta(DS, { needTimeseries: false }).length).toBe(5);
    expect(filterDatasourcesByMeta(DS, null).length).toBe(5);
    expect(filterDatasourcesByMeta(null, { needTimeseries: true })).toEqual([]);
  });
});
