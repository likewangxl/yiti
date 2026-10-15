import { describe, expect, it } from 'vitest';
import {
  buildSchemaFieldSpecs,
  buildSchemaInitialBinding,
  buildDatasourceFieldOptions,
  resolveInitialCatalogAssignments,
  mergeCompatibleDatasourceRequest,
  normalizeInitialFilters
} from '../visibleMetricBindings';

describe('schema1 可见组件绑定契约', () => {
  it('真实分行组件按组件类型暴露精确字段，不把 change 或其他业务线混入', () => {
    const card = buildSchemaFieldSpecs({
      entry: { kind: 'DISPLAY_COMPONENT' },
      component: { componentType: 'METRIC_CARD', content: { mainField: '完成率' }, dataRefs: [{ unit: 'PERCENT' }] }
    });
    expect(card.map(item => item.semantic)).toEqual(['value']);
    expect(card[0].unitKinds).toEqual(['PERCENT', 'RATIO']);

    const growth = buildSchemaFieldSpecs({ entry: { kind: 'BUSINESS_GROWTH' }, component: { componentType: 'TREND' } });
    expect(growth.filter(item => item.kind !== 'dimension').map(item => item.semantic))
      .toEqual(['retailDeposit', 'retailLoan', 'corpDeposit', 'corpLoan']);
    expect(growth.map(item => item.semantic)).not.toContain('change');

    const ranking = buildSchemaFieldSpecs({
      entry: { kind: 'INSTITUTION_RANKING' },
      component: { componentType: 'RANKING', content: { rankingMetrics: [
        { metricKey: 'value', field: 'deposit' }, { metricKey: 'increase', field: 'increase' }, { metricKey: 'average', field: 'average' }
      ] } }
    });
    expect(ranking.filter(item => item.kind !== 'dimension').map(item => item.semantic))
      .toEqual(['value', 'increase', 'average']);
    expect(ranking.map(item => item.semantic)).not.toContain('change');
  });

  it('从 metrics + 目录 outputColumn 唯一回显字段和单位，不能猜非目录字段', () => {
    const source = {
      sourceKind: 'WIDE_TABLE',
      configJson: JSON.stringify({ metrics: [
        { metricCode: 'M_RATE', metricName: '测试完成率', slot: 331 },
        { metricCode: 'M_AMOUNT', metricName: '测试余额', slot: 320 }
      ] })
    };
    const catalog = [
      { metricCode: 'M_RATE', metricName: '测试完成率', outputColumn: '测试完成率', unit: 'PERCENT', bindable: true },
      { metricCode: 'M_AMOUNT', metricName: '测试余额', outputColumn: '测试余额', unit: 'YUAN', bindable: true }
    ];
    const options = buildDatasourceFieldOptions(source, catalog);
    expect(options.find(item => item.col === '测试完成率')).toMatchObject({ role: 'METRIC', unit: 'PERCENT', metricCode: 'M_RATE' });
    expect(resolveInitialCatalogAssignments({
      fields: { value: '测试完成率' }, units: { value: 'PERCENT' }, fieldSpecs: [{ semantic: 'value', kind: 'metric' }], catalog, datasource: source
    })).toEqual({ value: { metricCode: 'M_RATE', unit: 'PERCENT' } });
    expect(resolveInitialCatalogAssignments({
      fields: { value: '历史手工列' }, units: { value: 'YUAN' }, fieldSpecs: [{ semantic: 'value', kind: 'metric' }], catalog, datasource: source
    })).toEqual({});
  });

  it('克隆宽表时保留原 metrics、fieldMeta、过滤和聚合，不能把旧来源缩成单列', () => {
    const source = {
      id: 9032, sourceKind: 'WIDE_TABLE', bizLine: 'COMMON', dsType: 'TIMESERIES',
      configJson: JSON.stringify({ table: 'ORG_INDEX_RESULT', scopeMode: 'NAMED_GROUP',
        metrics: [{ metricCode: 'M_AMOUNT' }, { metricCode: 'M_OTHER' }],
        fieldMeta: [{ col: '旧余额', role: 'METRIC', unit: 'YUAN' }],
        aggregation: { groupBy: 'DATE', agg: 'SUM', filters: [{ col: 'val_1', op: 'GT', value: '0' }] } }),
      timeParamJson: '["LATEST","LAST_1M"]'
    };
    const merged = mergeCompatibleDatasourceRequest(source, {
      sourceKind: 'WIDE_TABLE', dsType: 'TIMESERIES', bizLine: 'COMMON',
      configJson: JSON.stringify({ table: 'ORG_INDEX_RESULT', scopeMode: 'NAMED_GROUP',
        metrics: [{ metricCode: 'M_RATE' }], fieldMeta: [{ col: '完成率', role: 'METRIC', unit: 'PERCENT' }],
        aggregation: { groupBy: 'DATE', agg: 'SUM', filters: [{ col: 'val_1', op: 'GT', value: '0' }] } })
    });
    expect(merged.ok).toBe(true);
    expect(JSON.parse(merged.configJson).metrics.map(item => item.metricCode).sort())
      .toEqual(['M_AMOUNT', 'M_OTHER', 'M_RATE']);
    expect(JSON.parse(merged.configJson).fieldMeta.map(item => item.col)).toEqual(['旧余额', '完成率']);
    expect(JSON.parse(merged.configJson).aggregation).toEqual({ groupBy: 'DATE', agg: 'SUM', filters: [{ col: 'val_1', op: 'GT', value: '0' }] });
  });

  it('同一旧列单位与新计划冲突时 fail-close，不静默保留旧单位', () => {
    const merged = mergeCompatibleDatasourceRequest({
      sourceKind: 'WIDE_TABLE', configJson: JSON.stringify({ table: 'ORG_INDEX_RESULT', fieldMeta: [{ col: '余额', unit: 'YUAN' }] })
    }, {
      sourceKind: 'WIDE_TABLE', configJson: JSON.stringify({ table: 'ORG_INDEX_RESULT', fieldMeta: [{ col: '余额', unit: 'TEN_THOUSAND' }] })
    });
    expect(merged.ok).toBe(false);
    expect(merged.reason).toContain('单位发生冲突');
  });

  it('分布页签按实际字段回显源单位，不能把存款列单位套到贷款列', () => {
    const result = buildSchemaInitialBinding({
      entry: { kind: 'COMPOSITION_TAB', tabKey: 'loan' },
      component: { componentType: 'COMPOSITION_TABS', content: { tabs: [
        { tabKey: 'deposit', corporateField: '存款对公', retailField: '存款零售', unit: 'YUAN' },
        { tabKey: 'loan', corporateField: '贷款对公', retailField: '贷款零售', unit: 'HUNDRED_MILLION' }
      ] } },
      binding: { fields: { corporate: '存款对公', retail: '存款零售', corporateLoan: '贷款对公', retailLoan: '贷款零售' }, units: { corporate: 'YUAN', retail: 'YUAN', corporateLoan: 'TEN_THOUSAND', retailLoan: 'TEN_THOUSAND' } }
    });
    expect(result.units).toMatchObject({ corporate: 'TEN_THOUSAND', retail: 'TEN_THOUSAND' });
  });

  it('CUSTOM_SQL 同名字段没有目录来源证据时保留手工列，不自动回填 metricCode', () => {
    const assignment = resolveInitialCatalogAssignments({
      datasource: { sourceKind: 'CUSTOM_SQL', configJson: JSON.stringify({ fieldMeta: [{ col: '同名列', role: 'METRIC' }] }) },
      fields: { value: '同名列' }, units: { value: 'YUAN' },
      fieldSpecs: [{ semantic: 'value', kind: 'metric', unitKinds: ['YUAN'] }],
      catalog: [{ metricCode: 'M_SAME', metricName: '目录指标', outputColumn: '同名列', unit: 'YUAN' }]
    });
    expect(assignment).toEqual({});
  });

  it('旧来源过滤列只按可信语义反向映射，未知列保留为只读条件', () => {
    const source = {
      sourceKind: 'WIDE_TABLE',
      configJson: JSON.stringify({ metrics: [{ metricCode: 'M_FILTER' }] })
    };
    const catalog = [{ metricCode: 'M_FILTER', metricName: '过滤指标', outputColumn: '过滤指标', valSlot: 320, unit: 'YUAN' }];
    expect(normalizeInitialFilters([
      { col: 'data_date', op: 'GT', value: '2026-10-01' },
      { col: 'org_code', op: 'EQ', value: '001' },
      { col: 'val_320', op: 'GT', value: '0' },
      { col: 'manual_flag', op: 'EQ', value: 'Y' }
    ], { datasource: source, catalog })).toEqual([
      { col: 'data_date', op: 'GT', value: '2026-10-01', target: 'data_date' },
      { col: 'org_code', op: 'EQ', value: '001', target: 'subject' },
      { col: 'val_320', op: 'GT', value: '0', target: 'metric', metricCode: 'M_FILTER' },
      { col: 'manual_flag', op: 'EQ', value: 'Y', target: 'legacy', readonly: true }
    ]);
  });
});
