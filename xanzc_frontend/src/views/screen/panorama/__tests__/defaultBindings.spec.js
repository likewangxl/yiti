// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import {
  applyDefaultBindings,
  resolveDefaultBinding,
  summarizeBinding
} from '../defaultBindings';

const screenScope = {
  viewLevel: 'PROVINCE',
  bizLine: 'COMMON',
  orgScopeMode: 'LEGACY_CONTEXT'
};

function source(id, config, extra = {}) {
  return {
    id,
    dsName: `来源${id}`,
    sourceKind: 'WIDE_TABLE',
    bizLine: 'COMMON',
    status: 'ACTIVE',
    configJson: JSON.stringify(config),
    ...extra
  };
}

function orgWideSource(id, metrics, aggregation = { groupBy: 'NONE', agg: 'SUM' }, extra = {}, configExtra = {}) {
  return source(id, {
    table: 'ORG_INDEX_RESULT',
    subjectCol: 'org_code',
    scopeMode: 'GLOBAL',
    aggregation,
    metrics,
    ...configExtra
  }, extra);
}

function customSqlInstitutionTable(id, resultShape = 'TABLE', extra = {}, configExtra = {}) {
  return source(id, {
    resultShape,
    scopeMode: 'GLOBAL',
    fields: [
      { col: 'org_code', alias: '机构号', role: 'DIM' },
      { col: 'org_name', alias: '机构名称', role: 'DIM' },
      { col: 'deposit', alias: '存款余额', semantic: 'rankingValue', role: 'METRIC', unit: 'HUNDRED_MILLION' }
    ],
    ...configExtra
  }, { sourceKind: 'CUSTOM_SQL', ...extra });
}

describe('defaultBindings', () => {
  it('只在固定指标编码唯一且语义完整时自动绑定，并按已确认宽表口径设为亿元', () => {
    const result = resolveDefaultBinding({
      slot: 'depositAverage',
      template: 'branch-overview-v1',
      screenScope,
      datasources: [orgWideSource(9014, [{ metricCode: 'M_0265', metricName: '一般性存款月均余额-机构', slot: 12 }])]
    });

    expect(result.status).toBe('applied');
    expect(result.binding).toMatchObject({
      dsId: 9014,
      fields: { value: '一般性存款月均余额-机构' },
      units: { value: 'HUNDRED_MILLION' }
    });
    expect(result.binding.fields.value).not.toBe('M_0265');
  });

  it('按槽位粒度拒绝未声明聚合的一般宽表，避免把原始多行来源当作单值', () => {
    const rawWide = source(9002, {
      table: 'ORG_INDEX_RESULT',
      subjectCol: 'org_code',
      metrics: [{ metricCode: 'M_0265', metricName: '一般性存款月均余额-机构', slot: 12 }]
    });
    const result = resolveDefaultBinding({
      slot: 'depositAverage', template: 'branch-overview-v1', screenScope, datasources: [rawWide]
    });
    expect(result.status).toBe('missing');
    expect(result.gap).toContain('NONE');
  });

  it('趋势只接受 DATE 粒度，机构展示只接受 SUBJECT 粒度', () => {
    const trendSource = orgWideSource(9012, [
      { metricCode: 'M_TREND', metricName: '存款趋势', semantic: 'deposit', unit: 'HUNDRED_MILLION' }
    ], undefined);
    const branchSource = source(9002, {
      table: 'ORG_INDEX_RESULT',
      metrics: [{ metricCode: 'M_BRANCH', metricName: '存款', semantic: 'deposit', unit: 'HUNDRED_MILLION' }]
    });
    const trend = resolveDefaultBinding({ slot: 'trend', template: 'branch-overview-v1', screenScope, datasources: [trendSource] });
    const branches = resolveDefaultBinding({ slot: 'branches', template: 'branch-overview-v1', screenScope, datasources: [branchSource] });
    expect(trend.status).toBe('missing');
    expect(trend.gap).toContain('DATE');
    expect(branches.status).toBe('missing');
    expect(branches.gap).toContain('SUBJECT');
  });

  it('机构展示在有固定内置机构名称时同时绑定机构号和机构名称', () => {
    const result = resolveDefaultBinding({
      slot: 'branches', template: 'branch-overview-v1', screenScope,
      datasources: [orgWideSource(9015, [
        { metricCode: 'M_0265', metricName: '一般性存款月均余额-机构', slot: 12 }
      ], { groupBy: 'SUBJECT', agg: 'SUM' }, {}, { scopeMode: 'SUBJECT' })]
    });
    expect(result.status).toBe('applied');
    expect(result.binding.fields).toEqual({ orgCode: 'org_code', orgName: 'org_name' });
  });

  it('经营关注来源可把机构号作为可选维度候选', () => {
    const result = resolveDefaultBinding({
      slot: 'attention', template: 'branch-overview-v1', screenScope,
      datasources: [source(9018, {
        resultShape: 'TABLE', scopeMode: 'SUBJECT',
        fields: [
          { col: 'org_code', alias: '机构号', semantic: 'orgCode', role: 'DIM' },
          { col: 'TEST_BRANCH_ATTENTION', alias: '关注事项', semantic: 'attentionLabel', role: 'DIM' },
          { col: 'attention_count', alias: '数量', semantic: 'attentionCount', role: 'METRIC', unit: 'COUNT' }
        ]
      }, { sourceKind: 'CUSTOM_SQL' })]
    });
    expect(result.status).toBe('applied');
    expect(result.binding.fields).toEqual({
      label: 'TEST_BRANCH_ATTENTION', count: 'attention_count', orgCode: 'org_code'
    });
    expect(result.binding.units.count).toBe('COUNT');
  });

  it('非宽表来源没有明确结果结构时不因 dsType 或名称自动当作单值', () => {
    const custom = source(9017, {
      semantic: 'depositAverage',
      fields: [{ col: 'average', semantic: 'depositAverage', role: 'METRIC', unit: 'HUNDRED_MILLION' }]
    }, { sourceKind: 'CUSTOM_SQL' });
    const shaped = source(9018, {
      resultShape: 'SINGLE',
      fields: [{ col: 'average', semantic: 'depositAverage', role: 'METRIC', unit: 'HUNDRED_MILLION' }]
    }, { sourceKind: 'CUSTOM_SQL' });
    const missing = resolveDefaultBinding({ slot: 'depositAverage', template: 'branch-overview-v1', screenScope, datasources: [custom] });
    const applied = resolveDefaultBinding({ slot: 'depositAverage', template: 'branch-overview-v1', screenScope, datasources: [shaped] });
    expect(missing.status).toBe('missing');
    expect(missing.gap).toContain('单值');
    expect(applied.status).toBe('applied');
  });

  it('同一语义存在多个可用来源时保留缺口，不按数组首项猜测', () => {
    const result = resolveDefaultBinding({
      slot: 'depositAverage',
      template: 'branch-overview-v1',
      screenScope,
      datasources: [
        orgWideSource(9014, [{ metricCode: 'M_0265', metricName: '一般性存款月均余额-机构', slot: 12 }]),
        orgWideSource(9015, [{ metricCode: 'M_0265', metricName: '一般性存款月均余额-机构', slot: 12 }], {
          groupBy: 'NONE', agg: 'SUM', filters: [{ col: 'org_code', op: 'IN', value: 'A,B' }]
        })
      ]
    });

    expect(result.status).toBe('ambiguous');
    expect(result.binding).toBeNull();
    expect(result.gap).toContain('多个');
    expect(result.candidates).toHaveLength(2);
  });

  it('相同一行结构仅多了无关指标时稳定消除等价候选，范围或过滤不同仍保留歧义', () => {
    const sameShape = [
      orgWideSource(9014, [
        { metricCode: 'M_0265', metricName: '一般性存款月均余额-机构', slot: 12 },
        { metricCode: 'M_0277', metricName: '对公一般性存款余额-机构', slot: 18 },
        { metricCode: 'M_0266', metricName: '一般性存款月均余额较上月-机构', slot: 51 }
      ]),
      orgWideSource(9010, [
        { metricCode: 'M_0265', metricName: '一般性存款月均余额-机构', slot: 12 },
        { metricCode: 'M_0277', metricName: '对公一般性存款余额-机构', slot: 18 }
      ])
    ];
    const collapsed = resolveDefaultBinding({ slot: 'depositAverage', template: 'branch-overview-v1', screenScope, datasources: sameShape });
    expect(collapsed.status).toBe('applied');
    expect(collapsed.binding.dsId).toBe(9010);
    expect(collapsed.candidates).toHaveLength(1);

    const differentFilter = resolveDefaultBinding({
      slot: 'depositAverage', template: 'branch-overview-v1', screenScope,
      datasources: [
        ...sameShape,
        orgWideSource(9016, [{ metricCode: 'M_0265', metricName: '一般性存款月均余额-机构', slot: 12 }],
          { groupBy: 'NONE', agg: 'SUM', filters: [{ col: 'org_code', op: 'IN', value: 'A,B' }] })
      ]
    });
    expect(differentFilter.status).toBe('ambiguous');
    expect(differentFilter.candidates.map(item => item.id)).toContain(9016);
  });

  it('按当前机构范围过滤来源，命名机构组不能套用全局汇总', () => {
    const result = resolveDefaultBinding({
      slot: 'depositAverage',
      template: 'branch-overview-v1',
      screenScope: { ...screenScope, orgScopeMode: 'NAMED_GROUP' },
      datasources: [orgWideSource(9014, [{ metricCode: 'M_0265', metricName: '一般性存款月均余额-机构', slot: 12 }])]
    });

    expect(result.status).toBe('missing');
    expect(result.gap).toContain('范围');
  });

  it('GLOBAL 权限范围下允许明确 TABLE 逐机构 CUSTOM_SQL 保留已有机构排名绑定', () => {
    const existing = {
      dsId: 9020,
      period: 'LATEST',
      fields: { orgCode: 'org_code', name: 'org_name', value: 'deposit' },
      units: { value: 'HUNDRED_MILLION' }
    };
    const result = resolveDefaultBinding({
      slot: 'ranking',
      template: 'branch-overview-v1',
      screenScope,
      existingBinding: existing,
      datasources: [customSqlInstitutionTable(9020)]
    });

    expect(result.status).toBe('preserved');
    expect(result.binding).toEqual(existing);
    expect(result.source.id).toBe(9020);
  });

  it('CUSTOM_SQL 机构列表仍拒绝 SINGLE、缺 org_code 维度和 NAMED_GROUP', () => {
    const single = resolveDefaultBinding({
      slot: 'ranking', template: 'branch-overview-v1', screenScope,
      datasources: [customSqlInstitutionTable(9021, 'SINGLE')]
    });
    const noOrgCode = resolveDefaultBinding({
      slot: 'ranking', template: 'branch-overview-v1', screenScope,
      datasources: [customSqlInstitutionTable(9022, 'TABLE', {}, {
        fields: [{ col: 'org_name', alias: '机构名称', role: 'DIM' },
          { col: 'deposit', alias: '存款余额', semantic: 'rankingValue', role: 'METRIC', unit: 'HUNDRED_MILLION' }]
      })]
    });
    const namedGroup = resolveDefaultBinding({
      slot: 'ranking', template: 'branch-overview-v1',
      screenScope: { ...screenScope, orgScopeMode: 'NAMED_GROUP' },
      datasources: [customSqlInstitutionTable(9023)]
    });

    expect(single.status).toBe('missing');
    expect(single.gap).toContain('TABLE');
    expect(noOrgCode.status).toBe('missing');
    expect(noOrgCode.gap).toContain('org_code');
    expect(namedGroup.status).toBe('missing');
    expect(namedGroup.gap).toContain('机构指标宽表');
  });

  it('固定对公/零售指标成对且一行来源明确时自动选择双列展示配置', () => {
    const result = resolveDefaultBinding({
      slot: 'composition', template: 'branch-overview-v1', screenScope,
      datasources: [orgWideSource(9014, [
        { metricCode: 'M_0277', metricName: '对公一般性存款余额-机构', slot: 18 },
        { metricCode: 'M_0309', metricName: '零售一般性存款余额-机构', slot: 30 }
      ])]
    });
    expect(result.status).toBe('applied');
    expect(result.binding.fields).toEqual({
      corporate: '对公一般性存款余额-机构',
      retail: '零售一般性存款余额-机构'
    });
    expect(result.binding.units).toEqual({ corporate: 'HUNDRED_MILLION', retail: 'HUNDRED_MILLION' });
  });

  it('一键配置只写入空展示内容，已保存/手工配置保持不变', () => {
    const existing = {
      deposit: { dsId: 33, period: 'LATEST', fields: { value: 'manual_deposit' }, units: { value: 'YUAN' } },
      depositAverage: null
    };
    const result = applyDefaultBindings({
      template: 'branch-overview-v1',
      screenScope,
      datasources: [
        orgWideSource(9014, [{ metricCode: 'M_0265', metricName: '一般性存款月均余额-机构', slot: 12 }])
      ],
      bindings: existing
    });

    expect(result.bindings.deposit).toEqual(existing.deposit);
    expect(result.bindings.depositAverage).toMatchObject({ dsId: 9014 });
    expect(result.applied).toContain('depositAverage');
  });

  it('切换模板时零售来源与共用来源隔离', () => {
    const result = resolveDefaultBinding({
      slot: 'retailAum',
      template: 'retail-overview-v1',
      screenScope: { viewLevel: 'PROVINCE', bizLine: 'RETAIL', orgScopeMode: 'NAMED_GROUP' },
      datasources: [
        orgWideSource(1, [{ metricCode: 'RETAIL_AUM', metricName: 'aum', semantic: 'aum', unit: 'HUNDRED_MILLION' }]),
        source(2, {
          table: 'ORG_INDEX_RESULT', subjectCol: 'org_code', scopeMode: 'NAMED_GROUP',
          aggregation: { groupBy: 'NONE', agg: 'SUM' },
          metrics: [{ metricCode: 'RETAIL_AUM', metricName: 'aum', semantic: 'aum', unit: 'HUNDRED_MILLION' }]
        }, { bizLine: 'RETAIL' })
      ]
    });

    expect(result.status).toBe('applied');
    expect(result.binding.dsId).toBe(2);
  });

  it('缺少明确单位、常量目标、对公贷款和城市身份时均不自动绑定', () => {
    const missingUnit = resolveDefaultBinding({
      slot: 'depositAverage', template: 'branch-overview-v1', screenScope,
      datasources: [source(1, {
        table: 'OTHER_TABLE',
        aggregation: { groupBy: 'NONE', agg: 'SUM' },
        metrics: [{ metricCode: 'M_0265', metricName: '一般性存款月均余额-机构', semantic: 'depositAverage' }]
      })]
    });
    const constant = resolveDefaultBinding({
      slot: 'rate', template: 'branch-overview-v1', screenScope,
      datasources: [source(2, {
        sourceType: 'CONSTANT',
        fields: [{ col: 'rate', semantic: 'completionRate', role: 'METRIC', unit: 'PERCENT' }]
      })]
    });
    const corporateLoan = resolveDefaultBinding({
      slot: 'loan', template: 'branch-overview-v1', screenScope,
      datasources: [orgWideSource(3, [{ metricCode: 'M_0347', metricName: '对公一般性贷款余额-机构', semantic: 'loan', unit: 'HUNDRED_MILLION' }])]
    });
    const city = resolveDefaultBinding({
      slot: 'citySummary', template: 'branch-overview-v1', screenScope,
      datasources: [orgWideSource(4, [{ metricCode: 'M_0277', metricName: '对公一般性存款余额-机构', semantic: 'deposit', unit: 'HUNDRED_MILLION' }], { groupBy: 'SUBJECT', agg: 'SUM' })]
    });

    expect(missingUnit.status).toBe('missing');
    expect(missingUnit.gap).toContain('单位');
    expect(constant.status).toBe('blocked');
    expect(constant.gap).toContain('常量');
    expect(corporateLoan.status).toBe('blocked');
    expect(corporateLoan.gap).toContain('对公');
    expect(city.status).toBe('missing');
    expect(city.gap).toContain('城市');
  });

  it('维度元数据即使错误携带单位也只按身份字段处理，不抛异常', () => {
    const result = resolveDefaultBinding({
      slot: 'citySummary', template: 'branch-overview-v1', screenScope,
      datasources: [orgWideSource(7, [{ metricCode: 'M_0277', metricName: '对公一般性存款余额-机构', semantic: 'deposit', unit: 'HUNDRED_MILLION' }],
        { groupBy: 'SUBJECT', agg: 'SUM' }, {}, {
          fieldMeta: [{ col: 'city_code', semantic: 'cityCode', role: 'DIM', unit: 'YUAN' }]
        })]
    });
    expect(result.status).toBe('applied');
    expect(result.binding.fields.cityCode).toBe('city_code');
    expect(result.binding.units.cityCode).toBeUndefined();
  });

  it('摘要使用业务语言并显示来源、字段、单位', () => {
    expect(summarizeBinding('存款月均余额', {
      dsId: 9014,
      fields: { value: '一般性存款月均余额-机构' },
      units: { value: 'HUNDRED_MILLION' }
    }, [{ id: 9014, dsName: '机构指标汇总' }])).toContain('机构指标汇总 → 一般性存款月均余额-机构（亿元）');
    expect(summarizeBinding('支行机构', {
      dsId: 9015,
      fields: { orgCode: 'org_code' },
      units: {}
    }, [{ id: 9015, dsName: '机构指标按机构统计' }])).not.toContain('单位待确认');
  });
});
