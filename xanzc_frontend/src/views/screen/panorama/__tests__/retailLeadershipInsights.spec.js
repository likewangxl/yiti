import { describe, expect, it } from 'vitest';
import {
  buildRetailLeadershipInsights,
  buildSegmentComparisons,
  segmentAverageAum
} from '../retailLeadershipInsights.js';

describe('retailLeadershipInsights 零售经营观察派生', () => {
  it('按亿元/万户计算户均AUM，只接受客户大于0且AUM非负的分层', () => {
    expect(segmentAverageAum({ customers: 2, aum: 8 })).toBe(4);
    expect(segmentAverageAum({ customers: 0, aum: 8 })).toBeNull();
    expect(segmentAverageAum({ customers: 2, aum: -1 })).toBeNull();
    expect(segmentAverageAum({ customers: null, aum: 8 })).toBeNull();
  });

  it('并排计算已绑定分层内的客户占比与资产占比，不把分层冒充全客群', () => {
    const rows = buildSegmentComparisons([
      { name: '私行', customers: 2, aum: 8 },
      { name: '大众', customers: 8, aum: 2 },
      { name: '缺失', customers: null, aum: 4 },
      { name: '负资产', customers: 1, aum: -1 }
    ]);
    expect(rows).toEqual([
      expect.objectContaining({ name: '私行', customerShare: 20, aumShare: 80, averageAum: 4 }),
      expect.objectContaining({ name: '大众', customerShare: 80, aumShare: 20, averageAum: 0.25 })
    ]);
    expect(rows).toHaveLength(2);
  });

  it('经营观察分别统计负增机构、目标达标、排名缺指标和有效目标缺口', () => {
    expect(buildRetailLeadershipInsights({
      rankings: [
        { orgCode: 'A', aum: 10, increase: -2, rate: 101, nplRate: 1.2 },
        { orgCode: 'B', aum: 8, increase: 0, rate: null, nplRate: 0 },
        { orgCode: 'C', aum: null, increase: null, rate: 80, nplRate: null }
      ],
      targets: [
        { name: 'AUM', actual: 12, target: 10 },
        { name: '存款', actual: 8, target: 10 },
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

  it('存款排名缺失量按已绑定字段统计，未绑定 rate/npl 不算业务异常', () => {
    expect(buildRetailLeadershipInsights({
      rankings: [
        { orgCode: 'A', deposit: 10, average: 9, increase: -1 },
        { orgCode: 'B', deposit: 8, average: null, increase: null }
      ],
      rankingBoundFields: ['orgCode', 'name', 'deposit', 'average', 'increase']
    })).toMatchObject({
      negativeGrowthCount: 1,
      growthComparableCount: 1,
      growthSampleCount: 2,
      missingMetricCount: 2
    });
    expect(buildRetailLeadershipInsights({
      rankings: [
        { orgCode: 'A', deposit: 10, average: 9, increase: -1, rate: null, nplRate: null },
        { orgCode: 'B', deposit: 8, average: null, increase: null, rate: null, nplRate: null }
      ]
    }).missingMetricCount).toBe(2);
  });
});
