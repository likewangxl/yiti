import { describe, expect, it } from 'vitest';
import {
  buildCorporateLeadershipInsights,
  buildSegmentComparisons,
  segmentAverageLoan
} from '../corporateLeadershipInsights.js';

describe('corporateLeadershipInsights 对公经营观察派生', () => {
  it('按亿元/万户计算户均贷款，零客户、负贷款和缺失均不形成均值', () => {
    expect(segmentAverageLoan({ customers: 2, loan: 8 })).toBe(4);
    expect(segmentAverageLoan({ customers: 0, loan: 8 })).toBeNull();
    expect(segmentAverageLoan({ customers: 2, loan: -1 })).toBeNull();
    expect(segmentAverageLoan({ customers: null, loan: 8 })).toBeNull();
  });

  it('保留客户数、贷款和户均贷款，不把可交叉客群计算成总量占比', () => {
    const rows = buildSegmentComparisons([
      { name: '制造业', customers: 2, loan: 8 },
      { name: '批发零售', customers: 8, loan: 2 },
      { name: '缺失', customers: null, loan: 4 },
      { name: '负贷款', customers: 1, loan: -1 },
      { name: '待更新', customers: 1, loan: null }
    ]);
    expect(rows.slice(0, 2)).toEqual([
      expect.objectContaining({ name: '制造业', customers: 2, loan: 8, averageLoan: 4, loanScale: 100 }),
      expect.objectContaining({ name: '批发零售', customers: 8, loan: 2, averageLoan: 0.25, loanScale: 25 })
    ]);
    expect(rows).toHaveLength(5);
    expect(buildSegmentComparisons([{ name: '制造业', customers: 2, loan: 8 }])[0]).not.toHaveProperty('customerShare');
    expect(buildSegmentComparisons([{ name: '制造业', customers: 2, loan: 8 }])[0]).not.toHaveProperty('loanShare');
    expect(buildSegmentComparisons([
      { name: '待更新', customers: 1, loan: null },
      { name: '客户待更新', customers: null, loan: 3 }
    ])).toEqual([
      expect.objectContaining({ name: '待更新', customers: 1, loan: null, averageLoan: null, loanScale: null }),
      expect.objectContaining({ name: '客户待更新', customers: null, loan: 3, averageLoan: null, loanScale: 100 })
    ]);
  });

  it('分别统计负增机构、达标目标、缺失排名指标和目标缺口', () => {
    expect(buildCorporateLeadershipInsights({
      rankings: [
        { orgCode: 'A', deposit: 10, increase: -2, rate: 101, nplRate: 1.2 },
        { orgCode: 'B', deposit: 8, increase: 0, rate: null, nplRate: 0 },
        { orgCode: 'C', deposit: null, increase: null, rate: 80, nplRate: null }
      ],
      targets: [
        { name: '存款', actual: 12, target: 10 },
        { name: '贷款', actual: 8, target: 10 },
        { name: '客户', actual: null, target: 5 },
        { name: '无效', actual: 1, target: 0 }
      ]
    })).toMatchObject({
      negativeGrowthCount: 1,
      achievedTargetCount: 1,
      missingMetricCount: 4,
      validTargetCount: 3,
      targetCount: 4,
      targetGapCount: 1
    });
  });
});
