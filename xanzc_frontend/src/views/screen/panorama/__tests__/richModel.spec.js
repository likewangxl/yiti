import { describe, expect, it } from 'vitest';
import demoModel from '../demoModel';

describe('panorama rich demo model contract', () => {
  it('提供两项存款扩展 KPI、含负值的净增趋势和支行排名扩展字段', () => {
    expect(demoModel.kpis).toEqual(expect.arrayContaining([
      expect.objectContaining({ key: 'depositIncrease', label: '存款较上月净增', unit: '亿元' }),
      expect.objectContaining({ key: 'depositAverage', label: '存款月均余额', unit: '亿元' })
    ]));
    expect(demoModel.trend).toEqual(expect.arrayContaining([
      expect.objectContaining({ depositIncrease: expect.any(Number) })
    ]));
    expect(demoModel.trend.some(item => item.depositIncrease < 0)).toBe(true);
    const baseline = 1270.5;
    demoModel.trend.forEach((item, index) => {
      const previous = index === 0 ? baseline : demoModel.trend[index - 1].deposit;
      expect(item.depositIncrease).toBeCloseTo(item.deposit - previous, 2);
    });
    expect(demoModel.kpis.find(item => item.key === 'depositIncrease').value)
      .toBe(demoModel.trend.at(-1).depositIncrease);
    expect(demoModel.kpis.find(item => item.key === 'deposit').change)
      .toBeCloseTo((demoModel.trend.at(-1).deposit - demoModel.trend.at(-2).deposit)
        / demoModel.trend.at(-2).deposit * 100, 1);
    expect(demoModel.rankings).toHaveLength(43);
    const institutionCodes = new Set(demoModel.institutions.map(item => item.orgCode));
    expect(demoModel.institutions).toHaveLength(43);
    expect(new Set(demoModel.institutions.map(item => item.cityCode))).toHaveLength(10);
    expect(demoModel.institutions.filter(item => item.cityCode === '610100')).toHaveLength(12);
    expect(demoModel.institutions.filter(item => !item.located)).toHaveLength(3);
    expect(demoModel.rankings.every(item => institutionCodes.has(item.orgCode))).toBe(true);
    expect(demoModel.rankings.every(item => !item.orgCode.startsWith('DEMO-CITY-'))).toBe(true);
    expect(demoModel.rankings.every(item => Object.prototype.hasOwnProperty.call(item, 'increase'))).toBe(true);
    expect(demoModel.rankings.every(item => Object.prototype.hasOwnProperty.call(item, 'average'))).toBe(true);
  });

  it('经营关注使用明确示例条目，不拼成总量', () => {
    expect(demoModel.attention).toEqual([
      { label: '在途任务', count: 48 },
      { label: '待审批', count: 18 },
      { label: '临近时限', count: 6 },
      { label: '超时任务', count: 3 },
      { label: '目标待跟进', count: 4 }
    ]);
    expect(demoModel.disclaimer).toContain('非业务数据');
  });
});
