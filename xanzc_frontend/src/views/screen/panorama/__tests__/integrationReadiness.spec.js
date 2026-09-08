import { describe, expect, it } from 'vitest';
import {
  READINESS_STATUS,
  SLOT_COUNT,
  analyzeIntegrationReadiness
} from '../integrationReadiness';

const source = (overrides = {}) => ({
  id: 77,
  dsName: '机构经营宽表',
  dsType: 'TIMESERIES',
  sourceKind: 'WIDE_TABLE',
  status: 'ACTIVE',
  configJson: JSON.stringify({
    table: 'ORG_INDEX_RESULT',
    aggregation: { groupBy: 'DATE' },
    fieldMeta: [
      { col: 'deposit_raw', alias: '存款原值', role: 'METRIC', amountScale: 'HUNDRED_MILLION_YUAN' },
      { col: 'data_date', alias: '数据日期', role: 'DIM' }
    ]
  }),
  ...overrides
});

const screen = { id: 9, screenCode: 'SCR_CODE', screenName: '经营总览' };

const binding = {
  deposit: { dsId: 77, period: 'LATEST', fields: { value: 'deposit_raw' }, units: { value: 'YUAN' } }
};

describe('integrationReadiness', () => {
  it('固定评估 14 个槽位，未配置时不按来源名称自动绑定', () => {
    const result = analyzeIntegrationReadiness({
      screens: [screen],
      canvas: { screenId: 9, canvasVersion: 4 },
      datasources: [source({ dsName: '存款余额' })],
      bindingState: {}
    });

    expect(SLOT_COUNT).toBe(14);
    expect(result.entries).toHaveLength(14);
    expect(result.entries.every(item => item.status === READINESS_STATUS.UNCONFIGURED)).toBe(true);
    expect(result.entries.find(item => item.slot === 'deposit')).toMatchObject({
      datasourceName: null,
      mappedFields: [],
      originalUnits: [],
      period: 'LATEST'
    });
  });

  it('结构完整时显示来源、映射字段、原始单位和周期，并标为待核对', () => {
    const result = analyzeIntegrationReadiness({
      screens: [screen],
      canvas: { screenId: 9, canvasVersion: 4 },
      datasources: [source()],
      bindingState: binding
    });

    const deposit = result.entries.find(item => item.slot === 'deposit');
    expect(deposit).toMatchObject({
      status: READINESS_STATUS.STRUCTURALLY_AVAILABLE,
      datasourceName: '机构经营宽表',
      period: 'LATEST',
      mappedFields: [expect.objectContaining({ semantic: 'value', column: 'deposit_raw', originalUnit: 'YUAN' })],
      originalUnits: [expect.objectContaining({ semantic: 'value', unit: 'YUAN' })]
    });
    expect(deposit.pendingChecks).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'RUNTIME_NOT_VERIFIED' })
    ]));
    expect(result.disclaimer).toContain('静态预检');
    expect(result.disclaimer).toContain('未执行取数');
    expect(result.configuredCount).toBe(1);
    expect(result.structurallyAvailableCount).toBe(1);
  });

  it('数据源没有 ACTIVE 状态时保持结构可读但明确标出来源状态待核验', () => {
    const result = analyzeIntegrationReadiness({
      screens: [screen],
      canvas: { screenId: 9 },
      datasources: [source({ status: undefined })],
      bindingState: binding
    });

    const deposit = result.entries.find(item => item.slot === 'deposit');
    expect(deposit.status).toBe(READINESS_STATUS.STRUCTURALLY_AVAILABLE);
    expect(deposit.pendingChecks).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'DATASOURCE_STATUS_UNVERIFIED' })
    ]));
  });

  it('缺失字段、错误单位或不存在的数据源属于配置错误', () => {
    const result = analyzeIntegrationReadiness({
      screens: [screen],
      canvas: { screenId: 9 },
      datasources: [source()],
      bindingState: {
        deposit: { dsId: 77, period: 'LATEST', fields: { value: 'unknown_col' }, units: { value: 'PERCENT' } },
        loan: { dsId: 999, period: 'LATEST', fields: { value: 'loan_raw' }, units: { value: 'YUAN' } },
        customers: { dsId: 77, period: 'LATEST', fields: {}, units: {} }
      }
    });

    expect(result.entries.find(item => item.slot === 'deposit')).toMatchObject({ status: READINESS_STATUS.CONFIG_ERROR });
    expect(result.entries.find(item => item.slot === 'loan')).toMatchObject({ status: READINESS_STATUS.CONFIG_ERROR });
    expect(result.entries.find(item => item.slot === 'customers')).toMatchObject({ status: READINESS_STATUS.CONFIG_ERROR });
    expect(result.configErrorCount).toBe(3);
  });

  it('报告旧 SQL 的常量、固定机构筛选和时序缺少 DATE 分组，但仍标为待核验', () => {
    const riskySource = source({
      sourceKind: 'CUSTOM_SQL',
      configJson: JSON.stringify({
        sql: "SELECT 3 AS actual, 4 AS target, 'ORG-X' AS org_code WHERE org_code = 'ORG-X'",
        fieldMeta: [
          { col: 'actual', alias: '实际值', role: 'METRIC' },
          { col: 'data_date', alias: '数据日期', role: 'DIM' }
        ]
      })
    });
    const result = analyzeIntegrationReadiness({
      screens: [screen],
      canvas: { screenId: 9 },
      datasources: [riskySource],
      bindingState: {
        trend: { dsId: 77, period: 'LAST_6M_EOM', fields: { date: 'data_date', deposit: 'actual' }, units: { deposit: 'YUAN' } }
      }
    });

    const trend = result.entries.find(item => item.slot === 'trend');
    expect(trend.status).toBe(READINESS_STATUS.STRUCTURALLY_AVAILABLE);
    expect(trend.pendingChecks.map(item => item.code)).toEqual(expect.arrayContaining([
      'CONSTANT_SQL', 'FIXED_ORG_FILTER', 'MISSING_DATE_GROUP_BY', 'RUNTIME_NOT_VERIFIED'
    ]));
    expect(trend.statusLabel).toContain('待核对');
  });

  it('识别 9016 形态的带引号中文别名常量 SQL', () => {
    const result = analyzeIntegrationReadiness({
      screens: [screen],
      canvas: { screenId: 9 },
      datasources: [source({
        sourceKind: 'KPI_RESULT',
        configJson: JSON.stringify({
          sql: "select 86.21 as '一般性存款日均完成率', 91.54 as '结算性存款日均完成率', 57.12 as '对公贷款余额完成率'",
          fieldMeta: [
            { col: '一般性存款日均完成率', role: 'METRIC' },
            { col: '结算性存款日均完成率', role: 'METRIC' },
            { col: '对公贷款余额完成率', role: 'METRIC' }
          ]
        })
      })],
      bindingState: { rate: { dsId: 77, period: 'LATEST', fields: { value: '一般性存款日均完成率' }, units: { value: 'PERCENT' } } }
    });

    expect(result.entries.find(item => item.slot === 'rate').pendingChecks).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'CONSTANT_SQL' })
    ]));
  });

  it('屏业务条线与数据源不兼容时报告配置错误', () => {
    const result = analyzeIntegrationReadiness({
      screens: [{ ...screen, bizLine: 'CORP' }],
      canvas: { screenId: 9 },
      datasources: [source({ bizLine: 'RETAIL' })],
      bindingState: binding
    });

    const deposit = result.entries.find(item => item.slot === 'deposit');
    expect(deposit.status).toBe(READINESS_STATUS.CONFIG_ERROR);
    expect(deposit.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'DATASOURCE_SCOPE_MISMATCH' })
    ]));
  });

  it('命名机构组只接受 ORG_INDEX_RESULT 宽表的 org_code 主体', () => {
    const result = analyzeIntegrationReadiness({
      screens: [{ ...screen, orgScopeMode: 'NAMED_GROUP', orgGroupCode: 'G_A' }],
      canvas: { screenId: 9 },
      datasources: [source({
        sourceKind: 'CUSTOM_SQL',
        configJson: JSON.stringify({
          table: 'ORG_INDEX_RESULT',
          subjectCol: 'org_code',
          aggregation: { groupBy: 'DATE' },
          fieldMeta: [
            { col: 'deposit_raw', alias: '存款原值', role: 'METRIC' },
            { col: 'data_date', alias: '数据日期', role: 'DIM' }
          ]
        })
      })],
      bindingState: binding
    });

    const deposit = result.entries.find(item => item.slot === 'deposit');
    expect(deposit.status).toBe(READINESS_STATUS.CONFIG_ERROR);
    expect(deposit.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'NAMED_GROUP_DATASOURCE_UNSAFE' })
    ]));
  });

  it('业务构成 columns 绑定拒绝后端不支持的来源结构', () => {
    const result = analyzeIntegrationReadiness({
      screens: [screen],
      canvas: { screenId: 9 },
      datasources: [source({
        sourceKind: 'CUSTOM_SQL',
        configJson: JSON.stringify({
          table: 'LEGACY_COMPOSITION',
          fieldMeta: [
            { col: 'corporate_raw', alias: '对公', role: 'METRIC' },
            { col: 'retail_raw', alias: '零售', role: 'METRIC' }
          ]
        })
      })],
      bindingState: {
        composition: {
          dsId: 77,
          period: 'LATEST',
          fields: { corporate: 'corporate_raw', retail: 'retail_raw' },
          units: { corporate: 'YUAN', retail: 'YUAN' }
        }
      }
    });

    const composition = result.entries.find(item => item.slot === 'composition');
    expect(composition.status).toBe(READINESS_STATUS.CONFIG_ERROR);
    expect(composition.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'COMPOSITION_COLUMNS_DATASOURCE_UNSUPPORTED' })
    ]));
  });
});
