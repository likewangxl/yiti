import { describe, expect, it } from 'vitest';
import { retailDemoModel } from '../retailDemoModel.js';

describe('retailDemoModel', () => {
  it('提供固定的陕西零售演示数据，覆盖零值、缺失、负净增、超额和无 cityCode', () => {
    expect(retailDemoModel.demoOnly).toBe(true);
    expect(retailDemoModel.dataDate).toBe('2026-08-31');
    expect(retailDemoModel.kpis.find(item => item.key === 'retailAum')).toMatchObject({ value: 852.6, change: 1.72 });
    expect(retailDemoModel.kpis.find(item => item.key === 'retailDepositAverage')).toMatchObject({ value: 568.42 });
    expect(retailDemoModel.kpis.filter(item => item.key !== 'retailDepositAverage').map(item => item.key)).toEqual([
      'retailAum', 'retailDeposit', 'retailRevenue', 'retailValueCustomers', 'retailLoan', 'retailNplRate'
    ]);
    expect(retailDemoModel.trend.at(-1)).toMatchObject({ date: '2026-08', aum: 852.6, deposit: 572.16 });
    expect(retailDemoModel.rankings.some(item => item.increase < 0)).toBe(true);
    expect(retailDemoModel.rankings.some(item => item.increase === 0)).toBe(true);
    expect(retailDemoModel.rankings.some(item => item.aum === null)).toBe(true);
    expect(retailDemoModel.targets.some(item => item.actual > item.target)).toBe(true);
    expect(retailDemoModel.targets.some(item => item.target <= 0)).toBe(true);
    expect(retailDemoModel.institutions.some(item => !item.cityCode)).toBe(true);
  });
});
