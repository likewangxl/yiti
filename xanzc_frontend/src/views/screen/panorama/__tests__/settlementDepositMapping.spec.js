// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';

import {
  SETTLEMENT_DEPOSIT_MAPPINGS,
  buildSettlementDepositMetric
} from '../settlementDepositMapping.js';

const [CORP, RETAIL] = SETTLEMENT_DEPOSIT_MAPPINGS;

const dates = {
  current: '2026-09-21',
  day: '2026-09-20',
  month: '2026-08-31',
  year: '2025-12-31'
};

function modelWithFields({ current = {}, history = [], units = {}, trendUnits = units, dataDate = dates.current } = {}) {
  return {
    dataDate,
    blockResults: {
      31: {
        data_date: dataDate,
        ...current,
        unitByField: units
      },
      57: {
        rows: history,
        unitByField: trendUnits
      }
    }
  };
}

function aliasFixture(overrides = {}) {
  const current = {
    [CORP.fieldAlias]: 500000000,
    [RETAIL.fieldAlias]: 303456700
  };
  const history = [
    { data_date: dates.day, [CORP.fieldAlias]: 500000000, [RETAIL.fieldAlias]: 302201700 },
    { data_date: dates.month, [CORP.fieldAlias]: 500000000, [RETAIL.fieldAlias]: 306913400 },
    { data_date: dates.year, [CORP.fieldAlias]: 430000000, [RETAIL.fieldAlias]: 270000000 }
  ];
  const units = { [CORP.fieldAlias]: 'YUAN', [RETAIL.fieldAlias]: 'YUAN' };
  return modelWithFields({ current, history, units, trendUnits: units, ...overrides });
}

describe('settlementDepositMapping', () => {
  it('公开指标 code、宽表物理列、返回别名和语义映射', () => {
    expect(SETTLEMENT_DEPOSIT_MAPPINGS).toEqual([
      {
        metricCode: 'M_0298',
        physicalColumn: 'val_26',
        fieldAlias: '对公结算性存款余额-机构',
        semantic: 'corpSettlementDeposit'
      },
      {
        metricCode: 'M_0331',
        physicalColumn: 'val_38',
        fieldAlias: '零售结算性存款余额-机构',
        semantic: 'retailSettlementDeposit'
      }
    ]);
  });

  it('从当前同日两个中文别名相加，并还原精确日/月/年比较差值', () => {
    const result = buildSettlementDepositMetric(aliasFixture());

    expect(result).toEqual({
      baseValue: 803456700,
      date: dates.current,
      dateValid: true,
      unit: 'YUAN',
      comparisons: {
        day: { baseValue: 1255000, referenceDate: dates.day },
        month: { baseValue: -3456700, referenceDate: dates.month },
        year: { baseValue: 103456700, referenceDate: dates.year }
      }
    });
  });

  it('raw val_N 只在两个 unitByField 都明确时读取，并统一金额单位', () => {
    const model = modelWithFields({
      current: { val_26: 50000, val_38: 30345.67 },
      history: [
        { date: dates.day, val_26: 50000, val_38: 30220.17 },
        { date: dates.month, val_26: 50000, val_38: 30691.34 },
        { date: dates.year, val_26: 43000, val_38: 27000 }
      ],
      units: { val_26: 'TEN_THOUSAND', val_38: 'TEN_THOUSAND' },
      trendUnits: { val_26: 'TEN_THOUSAND', val_38: 'TEN_THOUSAND' }
    });

    expect(buildSettlementDepositMetric(model)).toMatchObject({
      baseValue: 803456700,
      comparisons: {
        day: { baseValue: 1255000 },
        month: { baseValue: -3456700 },
        year: { baseValue: 103456700 }
      }
    });
  });

  it('固定别名优先，别名存在但为空时不回退 raw slot', () => {
    const model = aliasFixture({
      current: {
        [CORP.fieldAlias]: null,
        [RETAIL.fieldAlias]: 303456700,
        val_26: 500000000,
        val_38: 303456700
      },
      units: {
        [CORP.fieldAlias]: 'YUAN',
        [RETAIL.fieldAlias]: 'YUAN',
        val_26: 'YUAN',
        val_38: 'YUAN'
      }
    });

    expect(buildSettlementDepositMetric(model)).toBeNull();
  });

  it('别名记录已有非法 unit 时拒绝该字段，不回退 block 或物理列单位', () => {
    const source = aliasFixture();
    source.blockResults[31] = {
      rows: [{
        data_date: dates.current,
        [CORP.fieldAlias]: 500000000,
        [RETAIL.fieldAlias]: 303456700,
        unitByField: { [CORP.fieldAlias]: '%', [RETAIL.fieldAlias]: 'YUAN' }
      }],
      unitByField: {
        [CORP.fieldAlias]: 'YUAN', [RETAIL.fieldAlias]: 'YUAN',
        [CORP.physicalColumn]: 'YUAN', [RETAIL.physicalColumn]: 'YUAN'
      }
    };

    expect(buildSettlementDepositMetric(source)).toBeNull();
  });

  it.each([
    ['missing corp value', { current: { [RETAIL.fieldAlias]: 303456700 } }],
    ['invalid value', { current: { [CORP.fieldAlias]: 'bad', [RETAIL.fieldAlias]: 303456700 } }],
    ['unknown unit', { units: { [CORP.fieldAlias]: 'UNKNOWN', [RETAIL.fieldAlias]: 'YUAN' } }],
    ['missing unit evidence', { units: {} }]
  ])('%s keeps current metric null', (_name, overrides) => {
    expect(buildSettlementDepositMetric(aliasFixture(overrides))).toBeNull();
  });

  it('零值是合法余额，输入模型和历史 rows 不被修改', () => {
    const source = aliasFixture({
      current: { [CORP.fieldAlias]: 0, [RETAIL.fieldAlias]: 0 },
      history: [
        { data_date: dates.day, [CORP.fieldAlias]: 0, [RETAIL.fieldAlias]: 0 },
        { data_date: dates.month, [CORP.fieldAlias]: 0, [RETAIL.fieldAlias]: 0 },
        { data_date: dates.year, [CORP.fieldAlias]: 0, [RETAIL.fieldAlias]: 0 }
      ]
    });
    const before = structuredClone(source);

    expect(buildSettlementDepositMetric(source)).toMatchObject({
      baseValue: 0,
      date: dates.current,
      dateValid: true,
      comparisons: {
        day: { baseValue: 0 },
        month: { baseValue: 0 },
        year: { baseValue: 0 }
      }
    });
    expect(source).toEqual(before);
  });

  it('历史日期重复、缺列或缺单位证据时只省略对应 comparison', () => {
    const source = aliasFixture({
      history: [
        { data_date: dates.day, [CORP.fieldAlias]: 500000000, [RETAIL.fieldAlias]: 302201700 },
        { data_date: dates.day, [CORP.fieldAlias]: 500000000, [RETAIL.fieldAlias]: 302201700 },
        { data_date: dates.month, [CORP.fieldAlias]: 500000000 },
        { data_date: dates.year, [CORP.fieldAlias]: 430000000, [RETAIL.fieldAlias]: 270000000 }
      ],
      trendUnits: { [CORP.fieldAlias]: 'YUAN', [RETAIL.fieldAlias]: 'YUAN' }
    });

    expect(buildSettlementDepositMetric(source)).toEqual({
      baseValue: 803456700,
      date: dates.current,
      dateValid: true,
      unit: 'YUAN',
      comparisons: {
        year: { baseValue: 103456700, referenceDate: dates.year }
      }
    });
  });

  it('非法 model 日期时不生成 metric', () => {
    expect(buildSettlementDepositMetric(aliasFixture({ dataDate: '2026-02-30' }))).toBeNull();
  });
});
