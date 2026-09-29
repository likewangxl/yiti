// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';

import { supplementBranchDemoModel } from '../branchDemoSupplement.js';
import { buildDisplayMetricsModel } from '../../presentation/model/displayMetricsModel.js';

const FIELDS = Object.freeze({
  retailDeposit: '测试_直营零售存款',
  retailDepositRate: '测试_零售存款目标完成率',
  retailLoan: '测试_直营零售贷款',
  retailLoanRate: '测试_零售贷款目标完成率',
  corpDeposit: '测试_直营对公存款',
  corpDepositRate: '测试_对公存款目标完成率',
  corpLoan: '测试_直营对公贷款',
  corpLoanRate: '测试_对公贷款目标完成率',
  revenue: '测试_直营营业收入',
  fee: '测试_直营中间业务收入',
  incomeCorporate: '测试_直营对公营业收入',
  incomeRetail: '测试_直营零售营业收入'
});

const HEADER_UNITS = Object.freeze({
  [FIELDS.retailDeposit]: 'YUAN',
  [FIELDS.retailDepositRate]: 'PERCENT',
  [FIELDS.retailLoan]: 'YUAN',
  [FIELDS.retailLoanRate]: 'PERCENT',
  [FIELDS.corpDeposit]: 'YUAN',
  [FIELDS.corpDepositRate]: 'PERCENT',
  [FIELDS.corpLoan]: 'YUAN',
  [FIELDS.corpLoanRate]: 'PERCENT',
  [FIELDS.revenue]: 'YUAN',
  [FIELDS.fee]: 'YUAN'
});

function headerComponents(fields = Object.keys(HEADER_UNITS)) {
  const componentIds = [
    'business-retail-deposit-balance', 'business-retail-deposit-rate',
    'business-retail-loan-balance', 'business-retail-loan-rate',
    'business-corp-deposit-balance', 'business-corp-deposit-rate',
    'business-corp-loan-balance', 'business-corp-loan-rate',
    'business-revenue-operating', 'business-revenue-fee'
  ];
  return fields.map((field, index) => ({
    componentId: componentIds[index] || `business-${index}`,
    componentType: 'METRIC_CARD',
    layoutRegion: 'HEADER',
    visible: true,
    content: { mainField: field },
    dataRefs: [{ blockId: 31, role: 'PRIMARY', unit: HEADER_UNITS[field] }]
  }));
}

function presentation(overrides = {}) {
  return {
    type: 'CODE',
    template: 'branch-overview-v1',
    displaySchemaVersion: 1,
    dataClassification: 'TEST',
    state: 'draft',
    display: {
      components: [
        ...headerComponents(),
        {
          componentId: 'legacy-trend-57',
          componentType: 'TREND',
          layoutRegion: 'CENTER',
          visible: true,
          content: { series: [] },
          dataRefs: [{ blockId: 57, role: 'PRIMARY', unit: 'YUAN' }]
        },
        {
          componentId: 'legacy-composition-64',
          componentType: 'COMPOSITION_TABS',
          layoutRegion: 'LEFT',
          visible: true,
          content: {
            tabs: [{
              tabKey: 'income',
              label: '收入',
              corporateField: FIELDS.incomeCorporate,
              retailField: FIELDS.incomeRetail,
              totalField: FIELDS.revenue,
              unit: 'YUAN'
            }]
          },
          dataRefs: [{ blockId: 64, role: 'PRIMARY', unit: 'YUAN' }]
        }
      ]
    },
    ...overrides
  };
}

const current = {
  data_date: '2026-09-21',
  [FIELDS.retailDeposit]: 1068201504.93,
  [FIELDS.retailDepositRate]: 90.635193,
  [FIELDS.retailLoan]: 762221976,
  [FIELDS.retailLoanRate]: 86.43982,
  [FIELDS.corpDeposit]: 1194750925.07,
  [FIELDS.corpDepositRate]: null,
  [FIELDS.corpLoan]: 1120758514,
  [FIELDS.corpLoanRate]: null,
  [FIELDS.revenue]: 16972143.28,
  [FIELDS.fee]: null
};

function historyRow(date, values = {}) {
  return {
    data_date: date,
    [FIELDS.retailDeposit]: null,
    [FIELDS.retailLoan]: null,
    [FIELDS.corpDeposit]: null,
    [FIELDS.corpLoan]: null,
    [FIELDS.retailDepositRate]: null,
    [FIELDS.retailLoanRate]: null,
    [FIELDS.corpDepositRate]: null,
    [FIELDS.corpLoanRate]: null,
    [FIELDS.revenue]: null,
    [FIELDS.fee]: null,
    ...values
  };
}

function model() {
  return {
    dataDate: '2026-09-21',
    quality: { status: 'STALE', dataClassification: 'TEST', batchId: 'B-1' },
    issues: [{ slot: 'unrelated', code: 'NO_DATA' }],
    identity: { screenCode: 'SCR_PROVINCE', orgCode: '', cityCode: '' },
    blockResults: {
      31: {
        blockId: 31,
        ...current,
        unitByField: {
          [FIELDS.retailDeposit]: 'YUAN',
          [FIELDS.retailDepositRate]: 'PERCENT',
          [FIELDS.retailLoan]: 'YUAN',
          [FIELDS.retailLoanRate]: 'PERCENT',
          [FIELDS.corpDeposit]: 'YUAN',
          [FIELDS.corpLoan]: 'YUAN',
          [FIELDS.revenue]: 'YUAN'
        },
        rows: [{ ...current }]
      },
      64: {
        blockId: 64,
        [FIELDS.revenue]: current[FIELDS.revenue],
        [FIELDS.incomeCorporate]: null,
        [FIELDS.incomeRetail]: null,
        unitByField: { [FIELDS.revenue]: 'YUAN' },
        rows: [{
          data_date: '2026-09-21',
          [FIELDS.revenue]: current[FIELDS.revenue],
          [FIELDS.incomeCorporate]: null,
          [FIELDS.incomeRetail]: null
        }]
      },
      57: {
        blockId: 57,
        unitByField: {
          [FIELDS.retailDeposit]: 'YUAN',
          [FIELDS.retailLoan]: 'YUAN',
          [FIELDS.corpDeposit]: 'YUAN',
          [FIELDS.corpLoan]: 'YUAN'
        },
        rows: [
          historyRow('2026-08-28'),
          historyRow('2026-08-31'),
          historyRow('2026-09-21')
        ]
      }
    }
  };
}

describe('supplementBranchDemoModel', () => {
  it('为允许的全辖 TEST 草稿补齐结算性存款 KPI、UTC 基期日期和固定比较差值', () => {
    const source = model();
    const before = structuredClone(source);
    const result = supplementBranchDemoModel(source, presentation());
    const settlement = result.model.kpis.find(item => item.key === 'settlementDeposit');

    expect(source).toEqual(before);
    expect(result.model).not.toBe(source);
    expect(result.fields).toContain('settlementDeposit');
    expect(settlement).toEqual({
      key: 'settlementDeposit',
      label: '结算性存款',
      status: '演示数据',
      isDemo: true,
      value: 803456700,
      unit: 'YUAN',
      dataDate: '2026-09-21',
      comparisons: {
        day: { value: 1255000, unit: 'YUAN', referenceDate: '2026-09-20' },
        month: { value: -3456700, unit: 'YUAN', referenceDate: '2026-08-31' },
        year: { value: 103456700, unit: 'YUAN', referenceDate: '2025-12-31' }
      }
    });
  });

  it('使用 UTC 日期计算闰年昨日、上月末和上年末基期', () => {
    const source = model();
    source.dataDate = '2024-03-01';
    const result = supplementBranchDemoModel(source, presentation());
    const settlement = result.model.kpis.find(item => item.key === 'settlementDeposit');

    expect(settlement).toMatchObject({
      dataDate: '2024-03-01',
      comparisons: {
        day: { referenceDate: '2024-02-29' },
        month: { referenceDate: '2024-02-29' },
        year: { referenceDate: '2023-12-31' }
      }
    });
  });

  it.each([
    ['zero', { value: 0, unit: 'YUAN' }],
    ['null', { value: null, unit: 'YUAN' }],
    ['illegal fields', { value: 'invalid', unit: '%', extra: 'keep-me' }]
  ])('已有 settlementDeposit (%s) 完整保留且不补 comparisons', (_name, existing) => {
    const source = model();
    source.kpis = [{ key: 'settlementDeposit', label: '已有结算指标', ...existing }];
    const before = structuredClone(source);
    const result = supplementBranchDemoModel(source, presentation());

    expect(source).toEqual(before);
    expect(result.model.kpis).toEqual(before.kpis);
    expect(result.model.kpis).toHaveLength(1);
    expect(result.model.kpis[0]).not.toHaveProperty('comparisons');
    expect(result.fields).not.toContain('settlementDeposit');
  });

  it('无效日期、缺少 blockResults 或非允许上下文时不补结算 KPI', () => {
    const invalidDate = model();
    invalidDate.dataDate = '2026-02-30';
    const invalidDateResult = supplementBranchDemoModel(invalidDate, presentation());
    expect(Boolean(invalidDateResult.model.kpis?.some(item => item.key === 'settlementDeposit'))).toBe(false);

    const missingBlocks = model();
    delete missingBlocks.blockResults;
    const missingBlocksResult = supplementBranchDemoModel(missingBlocks, presentation());
    expect(missingBlocksResult).toEqual({ model: missingBlocks, fields: [] });

    const scopedResult = supplementBranchDemoModel(model(), presentation({ orgCode: 'ORG-1' }));
    expect(Boolean(scopedResult.model.kpis?.some(item => item.key === 'settlementDeposit'))).toBe(false);
  });

  it('重复执行不增加第二个结算 KPI，也不覆盖第一次结果', () => {
    const first = supplementBranchDemoModel(model(), presentation());
    const beforeSecond = structuredClone(first.model);
    const second = supplementBranchDemoModel(first.model, presentation());

    expect(second).toEqual({ model: first.model, fields: [] });
    expect(second.model).toEqual(beforeSecond);
    expect(second.model.kpis.filter(item => item.key === 'settlementDeposit')).toHaveLength(1);
  });

  it('只在分行 TEST 草稿全辖上下文补齐配置字段、结构和历史，并保持单位与月比较可用', () => {
    const source = model();
    const before = structuredClone(source);
    const result = supplementBranchDemoModel(source, presentation());
    const blocks = result.model.blockResults;

    expect(result.model).not.toBe(source);
    expect(source).toEqual(before);
    expect(result.fields).toEqual(expect.arrayContaining([
      FIELDS.corpDepositRate, FIELDS.corpLoanRate, FIELDS.fee,
      FIELDS.incomeCorporate, FIELDS.incomeRetail,
      FIELDS.retailDeposit, FIELDS.retailLoan, FIELDS.corpDeposit, FIELDS.corpLoan
    ]));
    expect(blocks[31]).toMatchObject({
      [FIELDS.corpDepositRate]: 93.6,
      [FIELDS.corpLoanRate]: 88.2,
      [FIELDS.fee]: current[FIELDS.revenue] * 0.27
    });
    expect(blocks[31].unitByField).toMatchObject({
      [FIELDS.corpDepositRate]: 'PERCENT',
      [FIELDS.corpLoanRate]: 'PERCENT',
      [FIELDS.fee]: 'YUAN'
    });
    expect(blocks[31].rows[0]).toMatchObject({
      [FIELDS.corpDepositRate]: 93.6,
      [FIELDS.corpLoanRate]: 88.2,
      [FIELDS.fee]: current[FIELDS.revenue] * 0.27
    });

    expect(blocks[64][FIELDS.incomeCorporate] + blocks[64][FIELDS.incomeRetail])
      .toBe(blocks[64][FIELDS.revenue]);
    expect(blocks[64]).toMatchObject({
      [FIELDS.incomeCorporate]: blocks[64][FIELDS.revenue] * 0.6,
      [FIELDS.incomeRetail]: blocks[64][FIELDS.revenue] - blocks[64][FIELDS.revenue] * 0.6
    });
    expect(blocks[64].unitByField).toMatchObject({
      [FIELDS.incomeCorporate]: 'YUAN',
      [FIELDS.incomeRetail]: 'YUAN'
    });

    const latest = blocks[57].rows.find(row => row.data_date === '2026-09-21');
    const previous = blocks[57].rows.find(row => row.data_date === '2026-08-31');
    for (const field of [FIELDS.retailDeposit, FIELDS.retailLoan, FIELDS.corpDeposit, FIELDS.corpLoan]) {
      expect(latest[field]).toBe(current[field]);
      expect(Number.isFinite(previous[field])).toBe(true);
    }
    expect(blocks[57].unitByField).toMatchObject({
      [FIELDS.retailDeposit]: 'YUAN',
      [FIELDS.retailLoan]: 'YUAN',
      [FIELDS.corpDeposit]: 'YUAN',
      [FIELDS.corpLoan]: 'YUAN',
      [FIELDS.corpDepositRate]: 'PERCENT',
      [FIELDS.corpLoanRate]: 'PERCENT',
      [FIELDS.revenue]: 'YUAN',
      [FIELDS.fee]: 'YUAN'
    });

    const display = buildDisplayMetricsModel(presentation(), result.model);
    const header = display.components.filter(item => item.layoutRegion === 'HEADER');
    expect(header).toHaveLength(10);
    expect(header.every(item => item.monthDelta.state === 'READY')).toBe(true);
  });

  it('保留已有0、非法非空字符串和其他元信息，不覆盖已有构成值', () => {
    const source = model();
    source.blockResults[31][FIELDS.corpDepositRate] = 0;
    source.blockResults[31].rows[0][FIELDS.corpDepositRate] = 0;
    source.blockResults[31][FIELDS.fee] = 'invalid-fee';
    source.blockResults[31].rows[0][FIELDS.fee] = 'invalid-fee';
    source.blockResults[64][FIELDS.incomeCorporate] = 0;
    source.blockResults[64].rows[0][FIELDS.incomeCorporate] = 0;
    source.blockResults[64][FIELDS.incomeRetail] = 100;
    source.blockResults[64].rows[0][FIELDS.incomeRetail] = 100;

    const result = supplementBranchDemoModel(source, presentation());
    expect(result.model.blockResults[31][FIELDS.corpDepositRate]).toBe(0);
    expect(result.model.blockResults[31][FIELDS.fee]).toBe('invalid-fee');
    expect(result.model.blockResults[64][FIELDS.incomeCorporate]).toBe(0);
    expect(result.model.blockResults[64][FIELDS.incomeRetail]).toBe(100);
    expect(result.model.quality).toEqual(source.quality);
    expect(result.model.issues).toEqual(source.issues);
    expect(result.model.identity).toEqual(source.identity);
  });

  it('已有中间业务收入作为锚点补齐空行，不用派生值覆盖它', () => {
    const source = model();
    source.blockResults[31][FIELDS.fee] = 123456;
    source.blockResults[31].rows[0][FIELDS.fee] = 123456;
    source.blockResults[31].rows.push({ ...current, data_date: '2026-09-20', [FIELDS.fee]: null });

    const result = supplementBranchDemoModel(source, presentation());
    expect(result.model.blockResults[31][FIELDS.fee]).toBe(123456);
    expect(result.model.blockResults[31].rows.map(row => row[FIELDS.fee])).toEqual([123456, 123456]);
  });

  it.each([
    ['unknown-template', { template: 'retail-overview-v1' }, {}],
    ['published', { state: 'published' }, {}],
    ['live', { dataClassification: 'LIVE' }, {}],
    ['org-scoped', { orgCode: 'O-1' }, {}]
  ])('%s 不处理并返回原模型', (_name, presentationOverrides) => {
    const source = model();
    const result = supplementBranchDemoModel(source, presentation(presentationOverrides));
    expect(result).toEqual({ model: source, fields: [] });
  });

  it.each([
    ['loading', { loading: true }],
    ['error', { error: 'request failed' }],
    ['permission', { permissionStatus: 'FORBIDDEN' }]
  ])('%s 不处理并返回原模型', (_name, modelOverrides) => {
    const source = { ...model(), ...modelOverrides };
    const result = supplementBranchDemoModel(source, presentation());
    expect(result).toEqual({ model: source, fields: [] });
  });
});
