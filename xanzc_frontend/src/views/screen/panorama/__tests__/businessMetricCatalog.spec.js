import { describe, expect, it } from 'vitest';
import {
  buildBusinessMetricCatalog,
  filterBusinessMetricCatalog
} from '../businessMetricCatalog';

function source(overrides = {}) {
  return {
    id: 11,
    dsName: '机构经营来源',
    dsCode: 'DS_ORG_BUSINESS',
    status: 'ACTIVE',
    sourceKind: 'WIDE_TABLE',
    dsType: 'SINGLE',
    bizLine: 'COMMON',
    configJson: JSON.stringify({
      schemaVersion: 2,
      scopeMode: 'NAMED_GROUP',
      table: 'ORG_INDEX_RESULT',
      subjectCol: 'org_code',
      aggregation: { groupBy: 'NONE' },
      fieldMeta: [
        { col: 'deposit_balance', alias: '存款余额', role: 'METRIC', semantic: 'depositBalance', unit: 'HUNDRED_MILLION' },
        { col: 'unknown_amount', alias: '待维护金额', role: 'METRIC', amountScale: 'HUNDRED_MILLION_YUAN' },
        { col: 'org_code', alias: '机构号', role: 'DIM' }
      ],
      metrics: [
        { metricCode: 'M_DEP', metricName: 'deposit_balance', semantic: 'depositBalance', unit: 'HUNDRED_MILLION' }
      ]
    }),
    ...overrides
  };
}

function sourceWithConfig(config, overrides = {}) {
  return source({ ...overrides, configJson: JSON.stringify(config) });
}

function metricField(overrides = {}) {
  return {
    col: 'deposit_balance',
    alias: '存款余额',
    role: 'METRIC',
    semantic: 'depositBalance',
    unit: 'YUAN',
    ...overrides
  };
}

function baseConfig(overrides = {}) {
  return {
    schemaVersion: 2,
    scopeMode: 'NAMED_GROUP',
    table: 'ORG_INDEX_RESULT',
    subjectCol: 'org_code',
    aggregation: { groupBy: 'NONE' },
    fieldMeta: [metricField()],
    metrics: [{ metricCode: 'M_DEP', metricName: 'deposit_balance', semantic: 'depositBalance', unit: 'YUAN' }],
    ...overrides
  };
}

function m98Source(profile = 'CORP_REVENUE', overrides = {}) {
  return {
    id: 9801,
    dsName: '固定统计来源',
    status: 'ACTIVE',
    sourceKind: 'M98_STAT',
    dsType: 'SINGLE',
    bizLine: profile.startsWith('CORP_') ? 'CORP' : 'RETAIL',
    configJson: JSON.stringify({ schemaVersion: 2, scopeMode: 'NAMED_GROUP', profile, mode: 'SUMMARY' }),
    ...overrides
  };
}

describe('businessMetricCatalog', () => {
  it('从受控来源与显式 METRIC 元数据生成稳定、可搜索且不泄漏技术配置的目录项', () => {
    const entries = buildBusinessMetricCatalog([source()], {
      screenBizLine: 'COMMON',
      scopeMode: 'NAMED_GROUP',
      expectedShape: 'SINGLE'
    });
    const deposit = entries.find(item => item.field === 'deposit_balance');

    expect(deposit).toMatchObject({
      key: '11:deposit_balance',
      sourceId: 11,
      field: 'deposit_balance',
      metricName: '存款余额',
      sourceDescription: '机构经营来源',
      unit: 'HUNDRED_MILLION',
      rawUnit: 'HUNDRED_MILLION',
      shape: 'SINGLE',
      semantic: 'depositBalance',
      metricCode: 'M_DEP',
      disabled: false,
      disabledReason: ''
    });
    expect(deposit).not.toHaveProperty('configJson');
    expect(deposit).not.toHaveProperty('table');
    expect(deposit).not.toHaveProperty('slot');
    expect(filterBusinessMetricCatalog(entries, 'M_DEP')).toEqual([deposit]);
  });

  it('保留无法认证的 METRIC，但不从 amountScale 猜单位并明确要求技术人员维护', () => {
    const entries = buildBusinessMetricCatalog([source()], {
      screenBizLine: 'COMMON',
      scopeMode: 'NAMED_GROUP',
      expectedShape: 'SINGLE'
    });
    const unknown = entries.find(item => item.field === 'unknown_amount');

    expect(unknown).toMatchObject({
      key: '11:unknown_amount',
      metricName: '待维护金额',
      unit: null,
      rawUnit: null,
      disabled: true
    });
    expect(unknown.disabledReason).toMatch(/单位|技术人员/);
  });

  it('继承当前屏的业务条线与结果形状禁用原因，同时不暴露表名或 SQL', () => {
    const entries = buildBusinessMetricCatalog([source({
      id: 12,
      dsName: '零售明细来源',
      bizLine: 'RETAIL',
      dsType: 'TIMESERIES'
    })], {
      screenBizLine: 'CORP',
      scopeMode: 'LEGACY_CONTEXT',
      expectedShape: 'SINGLE'
    });
    const entry = entries.find(item => item.field === 'deposit_balance');

    expect(entry.disabled).toBe(true);
    expect(entry.disabledReason).toMatch(/业务条线/);
    expect(entry.disabledReason).toMatch(/当前组件需要单值结果/);
    expect(JSON.stringify(entry)).not.toContain('ORG_INDEX_RESULT');
    expect(JSON.stringify(entry)).not.toMatch(/select|from|sql/i);
  });

  it('支持直接传入 screen 上下文，不能因调用方省略扁平范围字段而放宽来源边界', () => {
    const entries = buildBusinessMetricCatalog([source({ id: 13, bizLine: 'RETAIL' })], {
      screen: { bizLine: 'CORP', orgScopeMode: 'LEGACY_CONTEXT' },
      expectedShape: 'SINGLE'
    });
    const entry = entries.find(item => item.field === 'deposit_balance');

    expect(entry.disabled).toBe(true);
    expect(entry.disabledReason).toContain('业务条线');
  });

  it.each([
    ['缺失状态', { status: undefined }],
    ['空状态', { status: '' }],
    ['未知状态', { status: 'PENDING' }]
  ])('对%s fail-close，不能把状态不明来源作为可选指标', (_label, overrides) => {
    const entries = buildBusinessMetricCatalog([source(overrides)], {
      screenBizLine: 'COMMON', scopeMode: 'NAMED_GROUP', expectedShape: 'SINGLE'
    });

    expect(entries.length).toBeGreaterThan(0);
    expect(entries.every(item => item.disabled)).toBe(true);
    expect(entries.map(item => item.disabledReason).join(' ')).toMatch(/状态|停用/);
  });

  it.each([0, -1, 'abc', null])('对非法 sourceId=%s fail-close', id => {
    const entries = buildBusinessMetricCatalog([source({ id })], {
      screenBizLine: 'COMMON', scopeMode: 'NAMED_GROUP', expectedShape: 'SINGLE'
    });

    expect(entries.length).toBeGreaterThan(0);
    expect(entries.every(item => item.disabled)).toBe(true);
    expect(entries.map(item => item.disabledReason).join(' ')).toMatch(/标识|ID|sourceId/);
  });

  it.each(['UNKNOWN', ''])('对未知或空 dsType=%s fail-close', dsType => {
    const entries = buildBusinessMetricCatalog([source({ dsType })], {
      screenBizLine: 'COMMON', scopeMode: 'NAMED_GROUP', expectedShape: 'SINGLE'
    });

    expect(entries.length).toBeGreaterThan(0);
    expect(entries.every(item => item.disabled)).toBe(true);
    expect(entries.map(item => item.disabledReason).join(' ')).toMatch(/结果形状|dsType|类型/);
  });

  it('坏 JSON 即使通过 sourceCandidates 注入字段也只能保留为禁用项', () => {
    const raw = source({ configJson: '{bad-json' });
    const entries = buildBusinessMetricCatalog([raw], {
      sourceCandidates: [{
        id: 11,
        name: '机构经营来源',
        status: 'ACTIVE',
        sourceKind: 'WIDE_TABLE',
        dsType: 'SINGLE',
        bizLine: 'COMMON',
        disabled: false,
        fields: [{ col: 'deposit_balance', label: '存款余额', role: 'METRIC', metricCode: 'M_DEP', unit: 'YUAN' }]
      }],
      screenBizLine: 'COMMON', scopeMode: 'NAMED_GROUP', expectedShape: 'SINGLE'
    });

    expect(entries).toHaveLength(1);
    expect(entries[0].disabled).toBe(true);
    expect(entries[0].disabledReason).toContain('配置');
  });

  it('未来 schemaVersion 不能成为可选指标', () => {
    const entries = buildBusinessMetricCatalog([sourceWithConfig(baseConfig({ schemaVersion: 3 }))], {
      screenBizLine: 'COMMON', scopeMode: 'NAMED_GROUP', expectedShape: 'SINGLE'
    });

    expect(entries).toHaveLength(1);
    expect(entries[0].disabled).toBe(true);
    expect(entries[0].disabledReason).toMatch(/schemaVersion|版本/);
  });

  it('传入的 sourceCandidates 不能绕过原始 source 的状态和身份校验', () => {
    const raw = source({ status: 'UNKNOWN' });
    const entries = buildBusinessMetricCatalog([raw], {
      sourceCandidates: [{
        ...raw,
        status: 'ACTIVE',
        disabled: false,
        disabledReason: '',
        fields: [{ col: 'deposit_balance', label: '存款余额', role: 'METRIC', metricCode: 'M_DEP', unit: 'YUAN' }]
      }],
      screenBizLine: 'COMMON', scopeMode: 'NAMED_GROUP', expectedShape: 'SINGLE'
    });

    const deposit = entries.find(item => item.field === 'deposit_balance');
    expect(deposit).toBeTruthy();
    expect(deposit.disabled).toBe(true);
    expect(deposit.disabledReason).toContain('状态');
  });

  it('同一字段的 fieldMeta 与 metrics 单位不一致时拒绝选择，不最后写赢', () => {
    const entries = buildBusinessMetricCatalog([sourceWithConfig(baseConfig({
      fieldMeta: [metricField({ unit: 'YUAN' })],
      metrics: [{ metricCode: 'M_DEP', metricName: 'deposit_balance', semantic: 'depositBalance', unit: 'PERCENT' }]
    }))], {
      screenBizLine: 'COMMON', scopeMode: 'NAMED_GROUP', expectedShape: 'SINGLE'
    });

    expect(entries[0]).toMatchObject({ disabled: true, unit: null });
    expect(entries[0].disabledReason).toMatch(/单位.*冲突|不一致/);
  });

  it('显式 unit 与 amountScale 并存时拒绝选择并说明冲突', () => {
    const entries = buildBusinessMetricCatalog([sourceWithConfig(baseConfig({
      fieldMeta: [metricField({ unit: 'YUAN', amountScale: 'HUNDRED_MILLION_YUAN' })],
      metrics: []
    }))], {
      screenBizLine: 'COMMON', scopeMode: 'NAMED_GROUP', expectedShape: 'SINGLE'
    });

    expect(entries[0]).toMatchObject({ disabled: true, unit: null });
    expect(entries[0].disabledReason).toMatch(/amountScale|单位.*冲突/);
  });

  it('原始单位缺失时只从同来源同字段且单位一致的已保存绑定回填', () => {
    const entries = buildBusinessMetricCatalog([sourceWithConfig(baseConfig({
      fieldMeta: [metricField({ unit: null, amountScale: 'HUNDRED_MILLION_YUAN' })],
      metrics: [{ metricCode: 'M_DEP', metricName: 'deposit_balance', semantic: 'depositBalance' }]
    }))], {
      savedBindings: [
        { dsId: 11, fields: { value: 'deposit_balance' }, units: { value: 'YUAN' } },
        { dsId: '11', fields: { amount: 'deposit_balance' }, units: { amount: 'YUAN' } }
      ],
      screenBizLine: 'COMMON', scopeMode: 'NAMED_GROUP', expectedShape: 'SINGLE'
    });

    expect(entries[0]).toMatchObject({
      disabled: false,
      unit: 'YUAN',
      rawUnit: 'YUAN',
      unitEvidence: 'SAVED_BINDING',
      unitEvidenceMessage: '沿用已保存绑定单位，请核对口径'
    });
  });

  it('已保存绑定同字段引用单位冲突时拒绝选择', () => {
    const entries = buildBusinessMetricCatalog([sourceWithConfig(baseConfig({
      fieldMeta: [metricField({ unit: null })],
      metrics: [{ metricCode: 'M_DEP', metricName: 'deposit_balance', semantic: 'depositBalance' }]
    }))], {
      savedBindings: [
        { dsId: 11, fields: { value: 'deposit_balance' }, units: { value: 'YUAN' } },
        { dsId: 11, fields: { amount: 'deposit_balance' }, units: { amount: 'COUNT' } }
      ],
      screenBizLine: 'COMMON', scopeMode: 'NAMED_GROUP', expectedShape: 'SINGLE'
    });

    expect(entries[0]).toMatchObject({ disabled: true, unit: null });
    expect(entries[0].disabledReason).toMatch(/已保存绑定.*冲突|单位.*冲突/);
  });

  it('已有明确元数据单位时不被冲突的已保存绑定覆盖', () => {
    const entries = buildBusinessMetricCatalog([source()], {
      savedBindings: [
        { dsId: 11, fields: { value: 'deposit_balance' }, units: { value: 'COUNT' } },
        { dsId: 11, fields: { amount: 'deposit_balance' }, units: { amount: 'YUAN' } }
      ],
      screenBizLine: 'COMMON', scopeMode: 'NAMED_GROUP', expectedShape: 'SINGLE'
    });
    const deposit = entries.find(item => item.field === 'deposit_balance');

    expect(deposit).toMatchObject({ disabled: false, unit: 'HUNDRED_MILLION', unitEvidence: 'METADATA' });
  });

  it('业务目录将命名机构组和结果形状原因转成中文，不泄漏表名、物理列或 SQL', () => {
    const entries = buildBusinessMetricCatalog([source({
      id: 14,
      dsName: '员工明细来源',
      dsType: 'TIMESERIES',
      configJson: JSON.stringify({
        schemaVersion: 2,
        scopeMode: 'NAMED_GROUP',
        table: 'EMP_INDEX_RESULT',
        subjectCol: 'emp_id',
        aggregation: { groupBy: 'NONE' },
        fieldMeta: [metricField({ col: 'deposit_balance', alias: '存款余额', unit: 'YUAN' })],
        metrics: [{ metricCode: 'M_EMP', metricName: 'deposit_balance', semantic: 'depositBalance', unit: 'YUAN' }]
      })
    })], {
      screenBizLine: 'COMMON',
      scopeMode: 'NAMED_GROUP',
      expectedShape: 'SINGLE',
      period: 'LAST_1M'
    });
    const entry = entries.find(item => item.field === 'deposit_balance');

    expect(entry).toMatchObject({ disabled: true, shape: 'TIMESERIES', shapeLabel: '时序' });
    expect(entry.disabledReason).toContain('机构范围未认证');
    expect(entry.disabledReason).toContain('当前组件需要单值结果');
    expect(entry.disabledReason).not.toMatch(/EMP_INDEX_RESULT|emp_id|org_code|SQL|SELECT|FROM/i);
    expect(JSON.stringify(entry)).not.toMatch(/EMP_INDEX_RESULT|emp_id|org_code|SQL|SELECT|FROM/i);
  });

  it('M98 固定 profile 在槽位匹配且有已保存单位证据时可选，单位不由 profile 猜测', () => {
    const entries = buildBusinessMetricCatalog([m98Source()], {
      screen: { bizLine: 'CORP', orgScopeMode: 'NAMED_GROUP' },
      expectedShape: 'SINGLE',
      slot: 'corpRevenue',
      semantic: 'value',
      savedBindings: [{ dsId: 9801, fields: { value: 'amount' }, units: { value: 'YUAN' } }]
    });
    const entry = entries.find(item => item.field === 'amount');

    expect(entry).toMatchObject({
      disabled: false,
      semantic: 'corpRevenue',
      unit: 'YUAN',
      unitEvidence: 'SAVED_BINDING'
    });
  });

  it('M98 错误 profile、增加配置键或跨固定槽位均不能选择', () => {
    const wrongSlot = buildBusinessMetricCatalog([m98Source()], {
      screenBizLine: 'CORP', scopeMode: 'NAMED_GROUP', expectedShape: 'SINGLE', slot: 'corpLoan',
      savedBindings: [{ dsId: 9801, fields: { value: 'amount' }, units: { value: 'YUAN' } }]
    });
    expect(wrongSlot.find(item => item.field === 'amount')).toMatchObject({ disabled: true });
    expect(wrongSlot.find(item => item.field === 'amount').disabledReason).toContain('固定指标不适用于当前组件');

    const extraConfig = buildBusinessMetricCatalog([m98Source('CORP_REVENUE', {
      configJson: JSON.stringify({
        schemaVersion: 2, scopeMode: 'NAMED_GROUP', profile: 'CORP_REVENUE', mode: 'SUMMARY', extra: true
      })
    })], {
      screenBizLine: 'CORP', scopeMode: 'NAMED_GROUP', expectedShape: 'SINGLE', slot: 'corpRevenue',
      savedBindings: [{ dsId: 9801, fields: { value: 'amount' }, units: { value: 'YUAN' } }]
    });
    expect(extraConfig).toEqual([]);

    const wrongProfile = buildBusinessMetricCatalog([m98Source('UNKNOWN')], {
      screenBizLine: 'CORP', scopeMode: 'NAMED_GROUP', expectedShape: 'SINGLE', slot: 'corpRevenue',
      savedBindings: [{ dsId: 9801, fields: { value: 'amount' }, units: { value: 'YUAN' } }]
    });
    expect(wrongProfile).toEqual([]);
  });

  it('FREE_REPORT 仅显示专用模板说明并保持禁用，不生成通用金额指标', () => {
    const entries = buildBusinessMetricCatalog([{
      id: 9901,
      dsName: '测试经营专用数据集',
      status: 'ACTIVE',
      sourceKind: 'FREE_REPORT',
      dsType: 'SINGLE',
      bizLine: 'COMMON',
      configJson: JSON.stringify({
        schemaVersion: 2,
        scopeMode: 'NAMED_GROUP',
        dataClassification: 'TEST',
        profile: 'BRANCH_OVERVIEW',
        batchId: 'TEST_BRANCH_OPERATING_20260921'
      })
    }], { screenBizLine: 'COMMON', scopeMode: 'NAMED_GROUP', expectedShape: 'SINGLE' });

    expect(entries).toEqual([expect.objectContaining({
      kind: 'DATASET_NOTICE',
      disabled: true,
      disabledReason: expect.stringContaining('专用模板')
    })]);
    expect(entries.some(item => item.role === 'METRIC' && !item.disabled)).toBe(false);
  });
});
