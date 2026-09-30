import { describe, expect, it } from 'vitest';
import { comparisonDates, computeExplicitComparison, validateComparisonConfig } from '../explicitComparisons';

describe('explicitComparisons', () => {
  it('按数据日期计算昨日、上月末、上年末，并校验闭合配置', () => {
    expect(comparisonDates('2028-03-02')).toEqual({ day: '2028-03-01', month: '2028-02-29', year: '2027-12-31' });
    expect(validateComparisonConfig({ enabled: true, historyBlockId: 57, valueFields: ['deposit'], dateField: 'data_date', sourceUnit: 'YUAN' }, {
      allowedKeys: ['card'], allowedHistoryBlockIds: [57], componentType: 'METRIC_CARD'
    })).toEqual([]);
    expect(validateComparisonConfig({ enabled: true, historyBlockId: 58, valueFields: ['deposit', 'deposit'], dateField: '', sourceUnit: 'AUTO' }, {
      allowedKeys: ['card'], allowedHistoryBlockIds: [57], componentType: 'METRIC_CARD'
    }).length).toBeGreaterThan(0);
  });

  it('跨单位统一后计算金额差，0和负差保留，当前值不匹配则待接入', () => {
    const rows = [
      { data_date: '2028-03-01', deposit: 100000000, unit: 'YUAN' },
      { data_date: '2028-02-29', deposit: 9000, unit: 'TEN_THOUSAND' }
    ];
    expect(computeExplicitComparison({ currentDate: '2028-03-01', period: 'month', mainValue: 10000, mainUnit: 'TEN_THOUSAND', rows, valueFields: ['deposit'], dateField: 'data_date', sourceUnit: 'YUAN' }))
      .toMatchObject({ state: 'READY', value: 1000, referenceDate: '2028-02-29' });
    expect(computeExplicitComparison({ currentDate: '2028-03-01', period: 'month', mainValue: 999, mainUnit: 'TEN_THOUSAND', rows, valueFields: ['deposit'], dateField: 'data_date', sourceUnit: 'YUAN' }).state)
      .toBe('NO_VALUE');
  });

  it('严格拒绝空白/复合值、日期字段回退和协议中文单位', () => {
    const base = { currentDate: '2028-03-02', period: 'month', mainValue: 100, mainUnit: 'YUAN', rows: [{ date: '2028-03-02', value: 100 }, { date: '2028-02-29', value: 90 }], valueFields: ['value'], dateField: 'data_date', sourceUnit: 'YUAN' };
    expect(computeExplicitComparison({ ...base, rows: [{ data_date: '2028-03-02', value: ' ' }, { data_date: '2028-02-29', value: [] }] }).state).toBe('NO_VALUE');
    expect(computeExplicitComparison(base).state).toBe('NO_VALUE');
    expect(validateComparisonConfig({ enabled: true, historyBlockId: 57, valueFields: [' value ', 'value'], dateField: 'data_date', sourceUnit: '元' }, { allowedHistoryBlockIds: [57], componentType: 'METRIC_CARD' }).length).toBeGreaterThan(0);
    expect(validateComparisonConfig({ enabled: true, historyBlockId: 57, valueFields: ['x'.repeat(101)], dateField: 'd'.repeat(101), sourceUnit: 'YUAN' }, { allowedHistoryBlockIds: [57], componentType: 'METRIC_CARD' }).length).toBeGreaterThan(0);
  });

  it('从结果级 unitByField 读取单位并处理 RATIO 到 PERCENT 的百分点差', () => {
    const ratio = computeExplicitComparison({ currentDate: '2028-03-02', period: 'month', mainValue: 0.9, mainUnit: 'RATIO', rows: [
      { data_date: '2028-03-02', rate: 90 }, { data_date: '2028-02-29', rate: 80 }
    ], unitByField: { rate: 'PERCENT' }, valueFields: ['rate'], dateField: 'data_date', sourceUnit: 'PERCENT' });
    expect(ratio).toMatchObject({ state: 'READY', value: 10, unit: 'PERCENT' });
  });
});
