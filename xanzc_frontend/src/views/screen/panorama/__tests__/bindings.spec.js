import { describe, expect, it } from 'vitest';
import {
  BINDING_SLOTS,
  UNIT_VALUES,
  buildCodeComponents,
  buildBinding,
  getDatasourceFieldOptions,
  normalizeBinding,
  PERIOD_VALUES,
  validateBinding
} from '../bindings';

describe('panorama bindings contract', () => {
  it('公开固定槽位、字段语义和单位白名单', () => {
    expect(Object.keys(BINDING_SLOTS)).toEqual([
      'deposit', 'loan', 'customers', 'revenue', 'rate', 'trend',
      'composition', 'ranking', 'attention', 'branches', 'branchTrend', 'citySummary'
    ]);
    expect(UNIT_VALUES).toEqual([
      'YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION', 'COUNT',
      'TEN_THOUSAND_COUNT', 'PERCENT', 'RATIO'
    ]);
    expect(BINDING_SLOTS.branches.required).toEqual(['orgCode']);
    expect(BINDING_SLOTS.trend.required).toEqual(['date']);
    expect(BINDING_SLOTS.trend.atLeastOneOf).toEqual(['deposit', 'loan']);
    expect(BINDING_SLOTS.citySummary.oneOfRequired).toEqual(['cityCode', 'orgCode']);
    expect(BINDING_SLOTS.trend.fields).toEqual(expect.arrayContaining([
      expect.objectContaining({ semantic: 'deposit' }),
      expect.objectContaining({ semantic: 'loan' })
    ]));
  });

  it('只把语义元数据提供为候选，不执行列探测或名称猜测', () => {
    const options = getDatasourceFieldOptions({
      configJson: JSON.stringify({
        fieldMeta: [
          { col: 'org_code', alias: '机构号', role: 'DIM' },
          { col: 'amount', alias: '存款余额', role: 'METRIC', amountScale: 'HUNDRED_MILLION_YUAN' }
        ],
        metrics: [{ metricName: 'legacy_metric' }]
      })
    });
    expect(options).toEqual(expect.arrayContaining([
      expect.objectContaining({ col: 'org_code', label: '机构号', role: 'DIM' }),
      expect.objectContaining({ col: 'amount', label: '存款余额', role: 'METRIC', amountScale: 'HUNDRED_MILLION_YUAN' }),
      expect.objectContaining({ col: 'legacy_metric', role: 'METRIC' })
    ]));

    const wideOptions = getDatasourceFieldOptions({
      sourceKind: 'WIDE_TABLE',
      configJson: JSON.stringify({ table: 'ORG_INDEX_RESULT', subjectCol: 'org_code', metrics: [{ metricName: 'deposit_raw', slot: 3 }], aggregation: { groupBy: 'SUBJECT' } })
    });
    expect(wideOptions.map(item => item.col)).toEqual(expect.arrayContaining(['org_code', 'org_name', 'deposit_raw']));
    expect(wideOptions.find(item => item.col === 'org_code')).toMatchObject({ role: 'DIM', builtin: true });
    const wideTrendOptions = getDatasourceFieldOptions({
      sourceKind: 'WIDE_TABLE',
      configJson: JSON.stringify({ table: 'ORG_INDEX_RESULT', metrics: [{ metricName: 'deposit_raw', slot: 3 }] })
    });
    expect(wideTrendOptions.map(item => item.col)).toContain('data_date');
    expect(normalizeBinding({ dsId: 1, fields: { date: 'data_date' } }, 'trend').period).toBe('LAST_6M_EOM');
    expect(PERIOD_VALUES).toEqual(['LATEST', 'LAST_10D', 'LAST_1M', 'LAST_6M_EOM']);
  });

  it('保存绑定时保留已存在 blockId，未有身份的节点交给 save 生成', () => {
    const bindings = {
      deposit: buildBinding(7, { value: 'deposit_raw' }, { value: 'YUAN' }),
      trend: buildBinding(8, { date: 'month', deposit: 'deposit_raw' }, { deposit: 'YUAN' })
    };
    const components = buildCodeComponents(bindings, [
      { id: 'old-deposit', component: 'ChartWidget', blockId: 41,
        propValue: { bindingKey: 'deposit' }, innerType: 'METRIC_CARD' },
      { id: 'old-title', component: 'TextLabel', style: { top: 4 } }
    ]);
    expect(components).toHaveLength(2);
    expect(components[0]).toMatchObject({ component: 'ChartWidget', blockId: 41,
      propValue: { bindingKey: 'deposit' }, style: { top: 0, left: 0, width: 320, height: 180 } });
    expect(JSON.parse(components[0].bindJson)).toEqual(bindings.deposit);
    expect(components[1]).toMatchObject({ component: 'ChartWidget', blockId: null,
      propValue: { bindingKey: 'trend' }, innerType: 'LINE_TREND' });
    expect(components.some(item => item.component === 'TextLabel')).toBe(false);
  });

  it('拒绝未知槽位、非法数据源和缺少必填语义字段', () => {
    expect(validateBinding('not-a-slot', { dsId: 1, fields: { value: 'x' } })).toContain('槽位不受支持');
    expect(validateBinding('deposit', { dsId: 0, fields: { value: 'x' } })).toContain('数据源无效');
    expect(validateBinding('branches', { dsId: 2, fields: {} })).toContain('缺少字段: orgCode');
    expect(validateBinding('rate', { dsId: 2, fields: { value: 'ratio' }, units: { value: 'UNKNOWN' } }))
      .toContain('单位无效: value');
    expect(validateBinding('rate', { dsId: 2, fields: { value: 'ratio' } }))
      .toContain('缺少单位: value');
    expect(validateBinding('attention', { dsId: 2, fields: { label: 'label', count: 'count' } }))
      .toContain('缺少单位: count');
    expect(validateBinding('citySummary', { dsId: 2, fields: { deposit: 'deposit' }, units: { deposit: 'YUAN' } }))
      .toContain('至少选择一个身份字段: cityCode、orgCode');
  });
});
