import { describe, expect, it } from 'vitest';
import {
  BINDING_SLOTS,
  BRANCH_SLOT_ORDER,
  ALL_SLOT_ORDER,
  UNIT_VALUES,
  buildCodeComponents,
  buildBinding,
  getDatasourceFieldOptions,
  getCompositionFieldSpecs,
  getCompositionMode,
  normalizeBinding,
  PERIOD_VALUES,
  validateBinding
} from '../bindings';

describe('panorama bindings contract', () => {
  it('公开固定槽位、字段语义和单位白名单', () => {
    expect(BRANCH_SLOT_ORDER).toEqual([
      'deposit', 'depositIncrease', 'depositAverage', 'loan', 'customers', 'revenue', 'rate', 'trend',
      'composition', 'ranking', 'attention', 'branches', 'branchTrend', 'citySummary'
    ]);
    expect(ALL_SLOT_ORDER).toEqual(Object.keys(BINDING_SLOTS));
    expect(ALL_SLOT_ORDER).toContain('retailAum');
    expect(BRANCH_SLOT_ORDER).not.toContain('retailAum');
    expect(UNIT_VALUES).toEqual([
      'YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION', 'COUNT',
      'TEN_THOUSAND_COUNT', 'PERCENT', 'RATIO'
    ]);
    expect(BINDING_SLOTS.branches.required).toEqual(['orgCode']);
    expect(BINDING_SLOTS.attention.required).toEqual(['label', 'count']);
    expect(BINDING_SLOTS.attention.fields).toEqual(expect.arrayContaining([
      expect.objectContaining({ semantic: 'orgCode', label: '机构号', kind: 'dimension', required: false })
    ]));
    expect(BINDING_SLOTS.trend.required).toEqual(['date']);
    expect(BINDING_SLOTS.trend.atLeastOneOf).toEqual(['deposit', 'loan', 'depositIncrease']);
    expect(BINDING_SLOTS.depositIncrease).toMatchObject({
      label: '存款较上月净增',
      innerType: 'METRIC_CARD',
      required: ['value']
    });
    expect(BINDING_SLOTS.depositAverage).toMatchObject({
      label: '存款月均余额',
      innerType: 'METRIC_CARD',
      required: ['value']
    });
    expect(BINDING_SLOTS.ranking.fields).toEqual(expect.arrayContaining([
      expect.objectContaining({ semantic: 'increase', unitKinds: ['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION'] }),
      expect.objectContaining({ semantic: 'average', unitKinds: ['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION'] })
    ]));
    expect(BINDING_SLOTS.citySummary.oneOfRequired).toEqual(['cityCode', 'orgCode']);
    expect(BINDING_SLOTS.trend.fields).toEqual(expect.arrayContaining([
      expect.objectContaining({ semantic: 'deposit' }),
      expect.objectContaining({ semantic: 'loan' }),
      expect.objectContaining({ semantic: 'depositIncrease' })
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
    expect(getDatasourceFieldOptions({
      configJson: JSON.stringify({ fieldMeta: [{ col: 'missing_role' }] })
    })).toEqual([expect.objectContaining({ col: 'missing_role', role: 'UNKNOWN' })]);

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

  it('新存款金额槽位只接受金额单位，且净增趋势可以独立满足趋势条件', () => {
    expect(validateBinding('depositIncrease', {
      dsId: 2, fields: { value: 'increase' }, units: { value: 'YUAN' }
    })).toEqual([]);
    expect(validateBinding('depositAverage', {
      dsId: 2, fields: { value: 'average' }, units: { value: 'HUNDRED_MILLION' }
    })).toEqual([]);
    expect(validateBinding('depositIncrease', {
      dsId: 2, fields: { value: 'increase' }, units: { value: 'COUNT' }
    })).toContain('单位不适用: value');
    expect(validateBinding('trend', {
      dsId: 2, fields: { date: 'date', depositIncrease: 'increase' }, units: { depositIncrease: 'YUAN' }
    })).toEqual([]);
    expect(validateBinding('trend', {
      dsId: 2, fields: { date: 'date', depositIncrease: 'increase' }, units: { depositIncrease: 'COUNT' }
    })).toContain('单位不适用: depositIncrease');
  });

  it('排名可选净增和月均字段仍要求明确金额单位', () => {
    expect(validateBinding('ranking', {
      dsId: 2,
      fields: { orgCode: 'org', name: 'name', value: 'deposit', increase: 'increase', average: 'average' },
      units: { value: 'YUAN', increase: 'TEN_THOUSAND', average: 'HUNDRED_MILLION' }
    })).toEqual([]);
    expect(validateBinding('ranking', {
      dsId: 2,
      fields: { orgCode: 'org', name: 'name', value: 'deposit', increase: 'increase' },
      units: { value: 'YUAN', increase: 'PERCENT' }
    })).toContain('单位不适用: increase');
  });

  it('公开业务构成两种互斥绑定模式及管理页字段 helper', () => {
    expect(getCompositionMode({ fields: {} })).toBe('rows');
    expect(getCompositionMode({ fields: { name: '业务类型', value: '余额' } })).toBe('rows');
    expect(getCompositionMode({ fields: { corporate: '对公', retail: '零售' } })).toBe('columns');
    expect(getCompositionMode({ fields: { corporate: '对公' } })).toBe('columns');
    expect(getCompositionFieldSpecs('rows').map(item => item.semantic)).toEqual(['name', 'value']);
    expect(getCompositionFieldSpecs('columns').map(item => item.semantic)).toEqual(['corporate', 'retail', 'total']);
    expect(getCompositionFieldSpecs('columns')).toEqual(expect.arrayContaining([
      expect.objectContaining({ semantic: 'corporate', kind: 'metric', unitKinds: ['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION', 'PERCENT', 'RATIO'] }),
      expect.objectContaining({ semantic: 'retail', kind: 'metric', unitKinds: ['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION', 'PERCENT', 'RATIO'] }),
      expect.objectContaining({ semantic: 'total', required: false, kind: 'metric', unitKinds: ['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION', 'PERCENT', 'RATIO'] })
    ]));
  });

  it('composition 绑定接受旧行模式和双列模式但拒绝混合/混类单位', () => {
    expect(validateBinding('composition', {
      dsId: 2, fields: { name: '业务类型', value: '余额' }, units: { value: 'YUAN' }
    })).toEqual([]);
    expect(validateBinding('composition', {
      dsId: 2, fields: { corporate: '对公余额', retail: '零售余额' },
      units: { corporate: 'TEN_THOUSAND', retail: 'YUAN' }
    })).toEqual([]);
    expect(validateBinding('composition', {
      dsId: 2, fields: { corporate: '对公余额', retail: '零售余额' },
      units: { corporate: 'PERCENT', retail: 'RATIO' }
    })).toEqual([]);
    expect(validateBinding('composition', {
      dsId: 2, fields: { name: '业务类型', value: '余额', corporate: '对公余额', retail: '零售余额' },
      units: { value: 'YUAN', corporate: 'YUAN', retail: 'YUAN' }
    })).toContain('构成字段模式不能混用');
    expect(validateBinding('composition', {
      dsId: 2, fields: { corporate: '对公余额', retail: '零售余额' },
      units: { corporate: 'PERCENT', retail: 'YUAN' }
    })).toContain('构成单位类型必须一致');
    expect(validateBinding('composition', {
      dsId: 2, fields: { corporate: '对公余额' }, units: { corporate: 'YUAN' }
    })).toContain('缺少字段: retail');
    expect(validateBinding('composition', {
      dsId: 2, fields: { corporate: '对公余额', retail: '零售余额' },
      units: { corporate: 'YUAN', retail: 'YUAN', value: 'YUAN' }
    })).toContain('单位未绑定字段: value');
  });

  it('composition columns 允许可选 total，但要求同类明确单位且不允许 rows 携带 total', () => {
    expect(validateBinding('composition', {
      dsId: 2, fields: { corporate: '对公余额', retail: '零售余额', total: '总余额' },
      units: { corporate: 'TEN_THOUSAND', retail: 'YUAN', total: 'HUNDRED_MILLION' }
    })).toEqual([]);
    expect(validateBinding('composition', {
      dsId: 2, fields: { corporate: '对公占比', retail: '零售占比', total: '总占比' },
      units: { corporate: 'PERCENT', retail: 'RATIO', total: 'PERCENT' }
    })).toEqual([]);
    expect(validateBinding('composition', {
      dsId: 2, fields: { corporate: '对公余额', retail: '零售余额', total: '总占比' },
      units: { corporate: 'YUAN', retail: 'YUAN', total: 'PERCENT' }
    })).toContain('构成单位类型必须一致');
    expect(validateBinding('composition', {
      dsId: 2, fields: { corporate: '对公余额', retail: '零售余额', total: '总余额' },
      units: { corporate: 'YUAN', retail: 'YUAN' }
    })).toContain('缺少单位: total');
    expect(validateBinding('composition', {
      dsId: 2, fields: { name: '业务类型', value: '余额', total: '总余额' },
      units: { value: 'YUAN', total: 'YUAN' }
    })).toContain('字段不受支持: total');
    expect(validateBinding('composition', {
      dsId: 2, fields: { corporate: '对公余额', retail: '零售余额' },
      units: { corporate: 'YUAN', retail: 'YUAN', total: 'YUAN' }
    })).toContain('单位未绑定字段: total');
  });

  it('composition 双列字段和单位随组件快照往返保留', () => {
    const binding = buildBinding(7,
      { corporate: '对公余额', retail: '零售余额' },
      { corporate: 'TEN_THOUSAND', retail: 'YUAN' });
    const components = buildCodeComponents({ composition: binding });
    expect(JSON.parse(components[0].bindJson)).toEqual(binding);
    expect(normalizeBinding(JSON.parse(components[0].bindJson), 'composition')).toEqual(binding);
  });
});
