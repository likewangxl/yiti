import { describe, expect, it } from 'vitest';
import { corporateDemoModel } from '../corporateDemoModel.js';

describe('corporateDemoModel', () => {
  it('明确标记为对公本地演示且提供完整展示形态', () => {
    expect(corporateDemoModel.title).toBe('对公经营总览');
    expect(corporateDemoModel.kpis).toHaveLength(6);
    expect(corporateDemoModel.trend.length).toBeGreaterThan(0);
    expect(corporateDemoModel.rankings.length).toBeGreaterThan(0);
    expect(corporateDemoModel.attention.length).toBeGreaterThan(0);
    expect(corporateDemoModel.targets.length).toBeGreaterThan(0);
    expect(corporateDemoModel.demo).toBe(true);
    expect(corporateDemoModel.kpis.find(item => item.key === 'corpCustomers')).toMatchObject({ value: 6.112, unit: '万户' });
    expect(corporateDemoModel.kpis.find(item => item.key === 'corpDeposit')).toMatchObject({ change: -0.14 });
    expect(corporateDemoModel.kpis.find(item => item.key === 'corpLoan')).toMatchObject({ change: 0.83 });
    expect(corporateDemoModel.targets.every(item => item.actual === null || Number.isFinite(item.actual))).toBe(true);
  });
});
