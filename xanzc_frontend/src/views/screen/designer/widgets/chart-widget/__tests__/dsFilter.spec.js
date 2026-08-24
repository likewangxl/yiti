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

const SCOPED_DS = [
  { id: 10, dsName: '零售宽表', dsType: 'TIMESERIES', sourceKind: 'WIDE_TABLE', bizLine: 'RETAIL' },
  { id: 11, dsName: '共用宽表', dsType: 'TIMESERIES', sourceKind: 'WIDE_TABLE', bizLine: 'COMMON' },
  { id: 12, dsName: '对公宽表', dsType: 'TIMESERIES', sourceKind: 'WIDE_TABLE', bizLine: 'CORP' },
  { id: 13, dsName: '存量共用宽表', dsType: 'TIMESERIES', sourceKind: 'WIDE_TABLE' }
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

  it('同时按屏幕业务条线兼容矩阵过滤数据源', () => {
    const r = filterDatasourcesByMeta(SCOPED_DS, null, { bizLine: 'RETAIL' });
    expect(r.map(d => d.id)).toEqual([10, 11, 13]);
  });

  it('命名机构组只允许机构宽表 ORG_INDEX_RESULT + org_code，解析失败也拒绝', () => {
    const rows = [
      {
        id: 20, sourceKind: 'WIDE_TABLE', bizLine: 'RETAIL',
        configJson: JSON.stringify({ table: 'ORG_INDEX_RESULT', subjectCol: 'org_code' })
      },
      {
        id: 21, sourceKind: 'WIDE_TABLE', bizLine: 'RETAIL',
        configJson: JSON.stringify({ table: 'EMP_INDEX_RESULT', subjectCol: 'org_code' })
      },
      {
        id: 22, sourceKind: 'WIDE_TABLE', bizLine: 'RETAIL',
        configJson: JSON.stringify({ table: 'ORG_INDEX_RESULT', subjectCol: 'emp_code' })
      },
      {
        id: 23, sourceKind: 'KPI_DETAIL', bizLine: 'RETAIL',
        configJson: JSON.stringify({ table: 'ORG_INDEX_RESULT', subjectCol: 'org_code' })
      },
      { id: 24, sourceKind: 'WIDE_TABLE', bizLine: 'RETAIL', configJson: '{not-json' }
    ];

    const r = filterDatasourcesByMeta(rows, null, {
      bizLine: 'RETAIL', orgScopeMode: 'NAMED_GROUP'
    });
    expect(r.map(d => d.id)).toEqual([20]);
  });
});
