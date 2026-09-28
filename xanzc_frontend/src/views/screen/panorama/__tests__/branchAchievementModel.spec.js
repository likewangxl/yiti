import { describe, expect, it } from 'vitest';
import { buildBranchAchievementModel } from '../branchAchievementModel';

describe('支行目标完成模型', () => {
  it('按指标方向计算完成率和缺口，保留超额、零值、负值与来源字段', () => {
    const targets = [
      { key: 'up-over', label: '正向超额', actual: 120, target: 100, unit: '万元', date: '2026-09-28' },
      { key: 'up-short', label: '正向未达', actual: 80, target: 100, unit: '户' },
      { key: 'down-over', label: '反向达标', actual: 80, target: 100, direction: 'DOWN' },
      { key: 'down-zero', label: '反向零值', actual: 0, target: 10, direction: 'DOWN' },
      { key: 'down-negative', label: '反向负值', actual: -2, target: 10, direction: 'DOWN' },
      { key: 'zero-target', label: '零目标', actual: 0, target: 0 },
      { key: 'missing-actual', label: '缺少实际', target: 10 }
    ];
    const snapshot = structuredClone(targets);

    const result = buildBranchAchievementModel(targets);

    expect(result.items).toHaveLength(7);
    expect(result.items.map(item => item.id)).toEqual([
      'up-over-0', 'up-short-1', 'down-over-2', 'down-zero-3', 'down-negative-4', 'zero-target-5', 'missing-actual-6'
    ]);
    expect(result.items[0]).toMatchObject({ rate: 120, gap: -20, state: 'completed', direction: 'UP', missingReason: null });
    expect(result.items[0]).toMatchObject({ label: '正向超额', unit: '万元', date: '2026-09-28' });
    expect(result.items[1]).toMatchObject({ rate: 80, gap: 20, state: 'incomplete', direction: 'UP' });
    expect(result.items[2]).toMatchObject({ rate: 125, gap: -20, state: 'completed', direction: 'DOWN' });
    expect(result.items[3]).toMatchObject({ rate: null, gap: -10, state: 'completed', direction: 'DOWN' });
    expect(result.items[4]).toMatchObject({ rate: -500, gap: -12, state: 'completed', direction: 'DOWN' });
    expect(result.items[5]).toMatchObject({ rate: null, gap: null, state: 'missing', direction: 'UP' });
    expect(result.items[5].missingReason).toMatch(/目标/);
    expect(result.items[6]).toMatchObject({ rate: null, gap: null, state: 'missing', direction: 'UP' });
    expect(result.items[6].missingReason).toMatch(/实际/);
    expect(result).toMatchObject({ total: 7, completed: 4, incomplete: 1, missing: 2 });
    expect(result.attainmentRate).toBeCloseTo(80, 10);
    expect(targets).toEqual(snapshot);
  });

  it('只有显式 rateOnly 才保留百分数来源，实际值存在时拒绝伪造 rate', () => {
    const result = buildBranchAchievementModel([
      { key: 'source-rate', label: '来源完成率', rate: '88.5', rateOnly: true },
      { key: 'source-rate-null-fields', label: '来源完成率空字段', actual: null, target: null, rate: 77, rateOnly: true },
      { key: 'computed', label: '实际计算', actual: 50, target: 100, rate: 999, rateOnly: true },
      { key: 'unapproved-rate', label: '未声明来源完成率', rate: 77 },
      { key: 'invalid', label: '非法数值', actual: ' ', target: 'Infinity' },
      { key: 'boolean', label: '布尔数值', actual: true, target: 1 }
    ]);

    expect(result.items[0]).toMatchObject({ rate: 88.5, state: 'missing', gap: null });
    expect(result.items[1]).toMatchObject({ rate: 77, state: 'missing', gap: null });
    expect(result.items[2]).toMatchObject({ rate: 50, state: 'incomplete', gap: 50 });
    expect(result.items[3]).toMatchObject({ rate: null, state: 'missing' });
    expect(result.items[4]).toMatchObject({ rate: null, state: 'missing' });
    expect(result.items[5]).toMatchObject({ rate: null, state: 'missing' });
    expect(result.attainmentRate).toBe(0);
  });

  it('空输入与全缺失输入不把缺失误算为未完成', () => {
    expect(buildBranchAchievementModel()).toEqual({
      items: [], total: 0, completed: 0, incomplete: 0, missing: 0, attainmentRate: null
    });
    const result = buildBranchAchievementModel([
      { key: 'a', actual: 1, target: 0 },
      { key: 'b', actual: null, target: 10 },
      { key: 'c', actual: 1, target: null },
      { key: 'd', actual: 1, target: -1 }
    ]);
    expect(result).toMatchObject({ total: 4, completed: 0, incomplete: 0, missing: 4, attainmentRate: null });
  });
});
