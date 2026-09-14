// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import {
  ALL_SLOT_ORDER,
  BINDING_SLOTS,
  normalizeBinding,
  validateBinding
} from '../bindings';
import {
  applyDefaultBindings,
  resolveDefaultBinding
} from '../defaultBindings';
import { analyzeIntegrationReadiness } from '../integrationReadiness';
import {
  createRuntimeDataVerificationService,
  normalizeVerificationBinding,
  OVERALL_STATUS,
  SLOT_STATUS
} from '../runtimeDataVerification';
import { resolveDataStatus, resolveSourcePresentation } from '../sourcePresentation';
import {
  CORPORATE_BINDING_SLOTS,
  CORPORATE_SLOT_ORDER,
  CORPORATE_TEMPLATE,
  isCorporateBindingSlot
} from '../corporateBindings';

const corporateScreen = {
  id: 41,
  screenCode: 'CORP_OVERVIEW',
  screenName: '对公经营总览',
  bizLine: 'CORP',
  orgScopeMode: 'LEGACY_CONTEXT'
};

function source(id, metrics, extra = {}) {
  return {
    id,
    dsName: `对公来源${id}`,
    sourceKind: 'WIDE_TABLE',
    bizLine: 'CORP',
    status: 'ACTIVE',
    configJson: JSON.stringify({
      table: 'ORG_INDEX_RESULT',
      subjectCol: 'org_code',
      scopeMode: 'SUBJECT',
      aggregation: { groupBy: 'NONE', agg: 'SUM' },
      metrics
    }),
    ...extra
  };
}

describe('corporate panorama binding integration', () => {
  it('exposes an independent corporate template and all required slots', () => {
    expect(CORPORATE_TEMPLATE).toBe('corporate-overview-v1');
    expect(CORPORATE_SLOT_ORDER).toEqual([
      'corpDeposit', 'corpDepositAverage', 'corpLoan', 'corpRevenue', 'corpCustomers', 'corpNplRate',
      'corpTrend', 'corpSegments', 'corpRanking', 'corpAttention', 'corpTargets', 'branches'
    ]);
    expect(CORPORATE_SLOT_ORDER.every(slot => isCorporateBindingSlot(slot))).toBe(true);
    expect(CORPORATE_SLOT_ORDER.every(slot => BINDING_SLOTS[slot])).toBe(true);
    expect(ALL_SLOT_ORDER).toEqual(expect.arrayContaining(CORPORATE_SLOT_ORDER));
    expect(CORPORATE_BINDING_SLOTS.branches.fields.map(field => field.semantic)).toEqual([
      'orgCode', 'orgName', 'cityCode', 'cityName', 'ownerOperatingOrgCode', 'parentOrgCode',
      'lng', 'lat', 'coordSys', 'located'
    ]);
  });

  it('normalizes corporate periods and protects dimensions/units', () => {
    expect(normalizeBinding({ dsId: '7', fields: { date: ' data_date ' } }, 'corpTrend')).toEqual({
      dsId: '7', period: 'LAST_6M_EOM', fields: { date: 'data_date' }, units: {}
    });
    expect(normalizeVerificationBinding({ dsId: 7, fields: { date: 'data_date', deposit: 'amount' } }, 'corpTrend'))
      .toMatchObject({ dsId: 7, period: 'LAST_6M_EOM', fields: { date: 'data_date', deposit: 'amount' } });
    expect(validateBinding('corpDeposit', {
      dsId: 7, fields: { value: 'deposit', date: 'data_date' }, units: { value: 'HUNDRED_MILLION' }
    })).toEqual([]);
    expect(validateBinding('corpDeposit', {
      dsId: 7, fields: { value: 'deposit' }, units: { value: 'COUNT' }
    })).toContain('单位不适用: value');
    expect(validateBinding('corpSegments', {
      dsId: 7, fields: { name: 'segment', customers: 'customers', loan: 'loan' },
      units: { customers: 'COUNT', loan: 'HUNDRED_MILLION' }
    })).toEqual([]);
    expect(validateBinding('corpSegments', {
      dsId: 7, fields: { name: 'segment', customers: 'customers', loan: 'loan' },
      units: { customers: 'COUNT', loan: 'PERCENT' }
    })).toContain('单位不适用: loan');
  });

  it('auto binds only explicit CORP semantics and leaves unknown sources unbound', () => {
    const corp = source(701, [
      { metricCode: 'CORP_DEP', metricName: 'corp_deposit', semantic: 'corpDeposit', unit: 'HUNDRED_MILLION' }
    ]);
    const retail = source(702, [
      { metricCode: 'RETAIL_DEP', metricName: 'retail_deposit', semantic: 'retailDeposit', unit: 'HUNDRED_MILLION' }
    ], { bizLine: 'RETAIL' });
    const unknown = source(703, [
      { metricCode: 'MYSTERY', metricName: 'amount', unit: 'HUNDRED_MILLION' }
    ]);
    const result = resolveDefaultBinding({
      template: CORPORATE_TEMPLATE,
      slot: 'corpDeposit',
      screenScope: corporateScreen,
      datasources: [unknown, retail, corp]
    });
    expect(result.status).toBe('applied');
    expect(result.binding).toMatchObject({
      dsId: 701,
      fields: { value: 'corp_deposit' },
      units: { value: 'HUNDRED_MILLION' }
    });
    expect(resolveDefaultBinding({
      template: CORPORATE_TEMPLATE,
      slot: 'corpDeposit',
      screenScope: corporateScreen,
      datasources: [unknown, retail]
    }).binding).toBeNull();
  });

  it('不会把按机构 SUM 的对公比率自动当作全辖不良率', () => {
    const summedRatio = source(707, [
      { metricCode: 'CORP_NPL', metricName: 'corp_npl', semantic: 'corpNplRate', unit: 'RATIO' }
    ]);
    const result = resolveDefaultBinding({
      template: CORPORATE_TEMPLATE,
      slot: 'corpNplRate',
      screenScope: corporateScreen,
      datasources: [summedRatio]
    });
    expect(result.status).toBe('missing');
    expect(result.binding).toBeNull();
    expect(result.gap).toContain('不能按 SUM 聚合');

    const rankingSource = source(708, [
      { metricCode: 'CORP_DEP', metricName: 'corp_deposit', semantic: 'corpDeposit', unit: 'HUNDRED_MILLION' },
      { metricCode: 'CORP_RATE', metricName: 'corp_rate', semantic: 'corpDepositRate', unit: 'PERCENT' }
    ], { configJson: JSON.stringify({
      table: 'ORG_INDEX_RESULT', subjectCol: 'org_code', scopeMode: 'SUBJECT',
      aggregation: { groupBy: 'SUBJECT', agg: 'SUM' },
      metrics: [
        { metricCode: 'CORP_DEP', metricName: 'corp_deposit', semantic: 'corpDeposit', unit: 'HUNDRED_MILLION' },
        { metricCode: 'CORP_RATE', metricName: 'corp_rate', semantic: 'corpDepositRate', unit: 'PERCENT' }
      ]
    }) });
    const ranking = resolveDefaultBinding({
      template: CORPORATE_TEMPLATE,
      slot: 'corpRanking',
      screenScope: corporateScreen,
      datasources: [rankingSource]
    });
    expect(ranking.status).toBe('applied');
    expect(ranking.binding.fields).toEqual({ orgCode: 'org_code', name: 'org_name', deposit: 'corp_deposit' });
  });

  it('uses the corporate slot order for recommendations and preserves existing bindings', () => {
    const corp = source(704, [
      { metricCode: 'CORP_DEP', metricName: 'corp_deposit', semantic: 'corpDeposit', unit: 'HUNDRED_MILLION' },
      { metricCode: 'CORP_AVG', metricName: 'corp_average', semantic: 'corpDepositAverage', unit: 'HUNDRED_MILLION' }
    ]);
    const current = {
      corpDeposit: { dsId: 999, fields: { value: 'manual' }, units: { value: 'YUAN' } }
    };
    const result = applyDefaultBindings({
      template: CORPORATE_TEMPLATE,
      screenScope: corporateScreen,
      datasources: [corp],
      bindings: current,
      slots: CORPORATE_SLOT_ORDER
    });
    expect(result.preserved).toContain('corpDeposit');
    expect(result.bindings.corpDeposit).toMatchObject({ ...current.corpDeposit, period: 'LATEST' });
    expect(result.applied).toContain('corpDepositAverage');
    expect(result.bindings.deposit).toBeUndefined();
  });

  it('requires the CORP screen and CORP source in static readiness', () => {
    const corp = source(705, [
      { metricCode: 'CORP_DEP', metricName: 'corp_deposit', semantic: 'corpDeposit', unit: 'HUNDRED_MILLION' }
    ]);
    const state = {
      corpDeposit: { dsId: 705, fields: { value: 'corp_deposit' }, units: { value: 'HUNDRED_MILLION' } }
    };
    const ready = analyzeIntegrationReadiness({
      screens: [corporateScreen],
      screen: corporateScreen,
      canvas: { screenId: 41 },
      datasources: [corp],
      bindingState: state,
      slotOrder: ['corpDeposit']
    });
    expect(ready.entries[0].status).toBe('STRUCTURALLY_AVAILABLE');

    const wrongLine = analyzeIntegrationReadiness({
      screens: [{ ...corporateScreen, bizLine: 'RETAIL' }],
      screen: { ...corporateScreen, bizLine: 'RETAIL' },
      canvas: { screenId: 41 },
      datasources: [corp],
      bindingState: state,
      slotOrder: ['corpDeposit']
    });
    expect(wrongLine.entries[0].status).toBe('CONFIG_ERROR');
    expect(wrongLine.entries[0].issues.map(item => item.code)).toContain('CORPORATE_SCOPE_REQUIRED');
  });

  it('keeps corporate source labels and field level availability readable', () => {
    const presentation = resolveSourcePresentation({ renderPackage: { canvasStyle: {
      sourceAvailability: {
        corpDeposit: { status: 'AVAILABLE', fields: { value: { status: 'AVAILABLE', message: '对公存款余额' } } },
        corpTrend: { status: 'PARTIAL', fields: { loan: { status: 'NO_VALUES', message: '近期无有效值' } } }
      }
    } } });
    expect(presentation.sourceAvailability.corpDeposit.fields.value.message).toBe('对公存款余额');
    expect(resolveDataStatus(presentation, {}, 'corpTrend', 'loan')).toEqual({
      status: 'NO_VALUES', message: '近期无有效值'
    });
  });

  it('核验已保存对公草稿时按 corp 槽位读取并拒绝单值多行结果', async () => {
    const binding = {
      dsId: 706,
      period: 'LATEST',
      fields: { value: 'corp_deposit' },
      units: { value: 'HUNDRED_MILLION' }
    };
    const component = {
      id: 'corp-deposit', component: 'ChartWidget', blockId: 1706,
      propValue: { bindingKey: 'corpDeposit' }, bindJson: JSON.stringify(binding)
    };
    const canvas = {
      screenId: 41, screenCode: 'CORP_OVERVIEW', canvasVersion: 3,
      canvasDraftJson: JSON.stringify({ schemaVersion: 2, components: [component] })
    };
    const view = {
      screenId: 41, screenCode: 'CORP_OVERVIEW', runtimeSchemaVersion: 2,
      renderPackageJson: JSON.stringify({
        schemaVersion: 2,
        canvasStyle: { presentation: { type: 'CODE', template: CORPORATE_TEMPLATE } },
        components: [component],
        bindSnapshots: { 1706: { bind: binding } }
      }),
      panoramaInstitutions: []
    };
    const service = createRuntimeDataVerificationService({
      getScreenCanvas: vi.fn().mockResolvedValue(canvas),
      getScreenView: vi.fn().mockResolvedValue(view),
      queryScreenData: vi.fn().mockResolvedValue({ columns: ['corp_deposit'], rows: [[1], [2]] })
    });
    const result = await service.verify({
      screen: corporateScreen,
      canvas,
      slotOrder: CORPORATE_SLOT_ORDER,
      bindingState: { corpDeposit: binding }
    });
    const row = result.results.find(item => item.slot === 'corpDeposit');
    expect(result.overallStatus).toBe(OVERALL_STATUS.HAS_GAPS);
    expect(row.status).toBe(SLOT_STATUS.STRUCTURE_ERROR);
    expect(row.issues.map(item => item.code)).toContain('MULTIPLE_ROWS');
  });
});
