import { describe, expect, it } from 'vitest';
import {
  buildBusinessSourceCandidates,
  createSourceRequestGate,
  filterBusinessSourceCandidates,
  reconcileBindingFields
} from '../sources/businessSourceCandidates';

const wide = (overrides = {}) => ({
  id: 1,
  dsCode: 'DS_ORG_DEPOSIT',
  dsName: '机构存款',
  status: 'ACTIVE',
  sourceKind: 'WIDE_TABLE',
  dsType: 'SINGLE',
  bizLine: 'COMMON',
  configJson: JSON.stringify({
    schemaVersion: 2,
    scopeMode: 'NAMED_GROUP',
    table: 'ORG_INDEX_RESULT',
    subjectCol: 'org_code',
    fieldMeta: [{ col: 'org_code', alias: '机构号', role: 'DIM' },
      { col: 'deposit_balance', alias: '存款余额', role: 'METRIC', unit: 'YUAN' }],
    metrics: [{ metricCode: 'M_DEP', metricName: '存款余额', unit: 'YUAN' }]
  }),
  ...overrides
});

describe('经营大屏业务来源候选', () => {
  it('将宽表和机构KPI按业务类别归一且保留稳定编码身份', () => {
    const kpi = {
      id: 2, dsCode: 'DS_KPI_ORG', dsName: '机构KPI', status: 'ACTIVE',
      sourceKind: 'KPI_DETAIL', dsType: 'SINGLE', bizLine: 'COMMON',
      configJson: JSON.stringify({ schemaVersion: 2, scopeMode: 'NAMED_GROUP', subjectType: 'ORG',
        mode: 'SNAPSHOT', schemeCode: 'KPI_2026', metrics: [{ metricCode: 'M_DEP', metricName: '存款' }] })
    };
    const result = buildBusinessSourceCandidates([wide(), kpi], {
      screenBizLine: 'COMMON', scopeMode: 'NAMED_GROUP', expectedShape: 'SINGLE'
    });
    expect(result.map(item => [item.id, item.category, item.code])).toEqual([
      [1, 'METRIC', 'DS_ORG_DEPOSIT'], [2, 'KPI', 'DS_KPI_ORG']
    ]);
    expect(result.every(item => item.disabled === false)).toBe(true);
  });

  it('命名机构组只允许ORG宽表及schema2 ORG SNAPSHOT KPI组合', () => {
    const invalid = [
      wide({ id: 3, sourceKind: 'KPI_RESULT', dsType: 'TIMESERIES' }),
      wide({ id: 4, sourceKind: 'KPI_DETAIL', configJson: JSON.stringify({ schemaVersion: 2,
        scopeMode: 'NAMED_GROUP', subjectType: 'EMP', mode: 'TREND', schemeCode: 'K1' }) }),
      wide({ id: 5, sourceKind: 'CUSTOM_SQL', configJson: JSON.stringify({ scopeMode: 'NAMED_GROUP' }) })
    ];
    const result = buildBusinessSourceCandidates(invalid, {
      screenBizLine: 'COMMON', scopeMode: 'NAMED_GROUP', expectedShape: 'ANY'
    });
    expect(result.every(item => item.disabled)).toBe(true);
    expect(result.map(item => item.disabledReason).join(' ')).toMatch(/机构组|ORG|自定义SQL/);
  });

  it('明确标记停用、跨条线和结果形状不兼容', () => {
    const result = buildBusinessSourceCandidates([
      wide({ id: 6, status: 'DISABLED' }),
      wide({ id: 7, bizLine: 'RETAIL' }),
      wide({ id: 8, dsType: 'TIMESERIES' })
    ], { screenBizLine: 'CORP', scopeMode: 'LEGACY_CONTEXT', expectedShape: 'SINGLE' });
    expect(result.map(item => item.disabledReason)).toEqual(expect.arrayContaining([
      expect.stringContaining('停用'), expect.stringContaining('业务条线'), expect.stringContaining('结果形状')
    ]));
  });

  it('按名称、编码、指标及类别过滤，无匹配返回空数组', () => {
    const candidates = buildBusinessSourceCandidates([wide()], {
      screenBizLine: 'COMMON', scopeMode: 'NAMED_GROUP', expectedShape: 'SINGLE'
    });
    expect(filterBusinessSourceCandidates(candidates, { keyword: 'M_DEP', category: 'METRIC' })).toHaveLength(1);
    expect(filterBusinessSourceCandidates(candidates, { keyword: '不存在' })).toEqual([]);
  });

  it('换源时清除新来源未声明的字段及其单位', () => {
    const candidate = buildBusinessSourceCandidates([wide()], {
      screenBizLine: 'COMMON', scopeMode: 'NAMED_GROUP', expectedShape: 'SINGLE'
    })[0];
    expect(reconcileBindingFields({ fields: { orgCode: 'org_code', value: 'old_value' },
      units: { value: 'YUAN', orphan: 'COUNT' } }, candidate)).toEqual({
      fields: { orgCode: 'org_code' }, units: {}
    });
  });

  it('请求门只接受最新响应并原样抛出403或网络失败', async () => {
    const gate = createSourceRequestGate();
    let resolveOld;
    const old = gate.run(() => new Promise(resolve => { resolveOld = resolve; }));
    const latest = await gate.run(async () => [wide()]);
    resolveOld([wide({ id: 99 })]);
    expect(latest.stale).toBe(false);
    expect((await old).stale).toBe(true);
    await expect(gate.run(async () => { throw Object.assign(new Error('403'), { status: 403 }); }))
      .rejects.toThrow('403');
  });
});
