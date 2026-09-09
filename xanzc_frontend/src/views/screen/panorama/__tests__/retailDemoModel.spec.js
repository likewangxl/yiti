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
  it('新增资产负增跟进与个贷净增目标，详情示例能与经营数据核对', () => {
    const followup = retailDemoModel.attention.find(item => item.label === '资产负增机构跟进');
    const negativeInstitutions = retailDemoModel.rankings.filter(item => item.increase < 0);
    expect(followup).toBeDefined();
    expect(followup.count).toBe(negativeInstitutions.length);
    expect(followup.detail.description).toContain(negativeInstitutions[0].name);
    expect(followup.detail.description).toContain(Math.abs(negativeInstitutions[0].increase).toFixed(2));
    expect(retailDemoModel.attention).toHaveLength(4);
    expect(retailDemoModel.attention.every(item => item.detail.description && item.detail.coordination && item.detail.source)).toBe(true);
    expect(retailDemoModel.targets).toHaveLength(5);
    expect(retailDemoModel.targets.find(item => item.name === '年度个人贷款净增')).toMatchObject({ actual: 18.35, target: 25 });
  });

});
