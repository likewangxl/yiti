import { describe, expect, it } from 'vitest';
import {
  buildCityInsights,
  buildProvinceInsights,
  deriveTrendObservation,
  resolveDepositTarget,
  summarizeCityTargetStatus,
  summarizeDepositMovement,
  summarizeProvinceTargetStatus,
  summarizeTargetDistance
} from '../leadershipInsights.js';

const branches = [
  {
    orgCode: 'A',
    orgName: '甲支行',
    cityCode: '610100',
    metrics: { deposit: 100, loan: 80, customers: 10, target: 110, rate: 98 },
    trend: [
      { date: '2026-07', deposit: 105 },
      { date: '2026-08', deposit: 101 },
      { date: '2026-09', deposit: 100 }
    ]
  },
  {
    orgCode: 'B',
    orgName: '乙支行',
    cityCode: '610100',
    metrics: { deposit: 120, loan: 90, customers: null, target: null, rate: 102 },
    trend: [{ date: '2026-09', deposit: 120 }]
  },
  {
    orgCode: 'C',
    orgName: '丙支行',
    cityCode: '610100',
    metrics: { deposit: null, loan: 0, customers: 0, target: null, rate: null },
    trend: []
  },
  {
    orgCode: 'D',
    orgName: '丁支行',
    cityCode: '610800',
    metrics: { deposit: 60, loan: 45, customers: 6, target: 65, rate: 80 },
    trend: [{ date: '2026-09', deposit: 60 }]
  }
];

describe('leadershipInsights 经营诊断纯计算', () => {
  it('摘要文案区分净增/净减/持平/暂无数据，并区分目标差、达标和超目标', () => {
    expect(summarizeDepositMovement(-1.88)).toMatchObject({ text: '存款较上月净减1.88亿元', state: 'down' });
    expect(summarizeDepositMovement(2.4)).toMatchObject({ text: '存款较上月净增2.4亿元', state: 'up' });
    expect(summarizeDepositMovement(0)).toMatchObject({ text: '存款较上月持平', state: 'flat' });
    expect(summarizeDepositMovement(null)).toMatchObject({ text: '存款较上月暂无数据', state: 'unknown' });
    expect(summarizeTargetDistance(86.5)).toMatchObject({ text: '距目标还差13.5个百分点', state: 'below' });
    expect(summarizeTargetDistance(100)).toMatchObject({ text: '已达到目标', state: 'achieved' });
    expect(summarizeTargetDistance(125)).toMatchObject({ text: '超目标25个百分点', state: 'above' });
    expect(summarizeTargetDistance(null)).toMatchObject({ text: '目标完成率暂无数据', state: 'unknown' });
  });

  it('只在实际余额和金额目标都可证明时计算目标差，不把完成率当目标金额', () => {
    expect(resolveDepositTarget(branches[0])).toBe(110);
    expect(resolveDepositTarget({ metrics: { deposit: 100, target: 98, rate: 98 } })).toBe(98);
    expect(resolveDepositTarget({ metrics: { deposit: 100, target: 110, targetUnit: '%' } })).toBeNull();
    expect(resolveDepositTarget({ demoOnly: true, metrics: { deposit: 100, target: 110 } })).toBeNull();
  });

  it('省级诊断保留样本口径，分别统计负增长、达标/未达标/未知和覆盖率', () => {
    const insights = buildProvinceInsights({
      kpis: [
        { key: 'depositIncrease', value: -2.5, unit: '亿元' },
        { key: 'rate', value: 86.5, unit: '%' }
      ],
      institutions: branches,
      rankings: branches.map(item => ({ orgCode: item.orgCode, deposit: item.metrics.deposit }))
    });
    expect(insights.sampleSize).toBe(4);
    expect(insights.diagnostics.depositIncrease).toBe(-2.5);
    expect(insights.diagnostics.targetGapPoints).toBe(-13.5);
    expect(insights.diagnostics.decliningCount).toBe(1);
    expect(insights.statusCounts).toEqual({ achieved: 1, below: 2, unknown: 1 });
    expect(insights.coverage.rate).toEqual({ available: 3, total: 4 });
    expect(insights.coverage.deposit).toEqual({ available: 3, total: 4 });
    expect(insights.decliningCoverage).toEqual({ available: 1, total: 4 });
    expect(insights.rows.find(row => row.orgCode === 'A')).toMatchObject({ targetGap: -10, trendState: '连续下降' });
  });

  it('省级目标摘要只统计完成率低于100%的机构，未提供完成率不冒充未完成', () => {
    const insights = buildProvinceInsights({ institutions: branches });
    expect(summarizeProvinceTargetStatus(insights)).toEqual({
      hasData: true,
      headline: '未完成目标机构2家',
      detail: '完成率低于100%，已提供3/4家'
    });
    expect(summarizeProvinceTargetStatus(buildProvinceInsights({
      institutions: [{ orgCode: 'UNKNOWN', metrics: { deposit: 1, rate: null } }]
    }))).toMatchObject({ hasData: false, headline: '目标机构暂无数据' });
  });

  it('没有净增或可比较趋势时下降数量保持未知，并显示可判断样本为 0', () => {
    const insights = buildProvinceInsights({
      institutions: [{ orgCode: 'NO-TREND', metrics: { deposit: 10, rate: null }, trend: [] }]
    });
    expect(insights.diagnostics.decliningCount).toBeNull();
    expect(insights.decliningCoverage).toEqual({ available: 0, total: 1 });
  });

  it('市级只取指定 cityCode，并能给出同城排名、中位值差和无数据态', () => {
    const insights = buildCityInsights({
      cityCode: '610100',
      citySummary: { kpis: [{ key: 'rate', value: 91 }] },
      institutions: branches
    });
    expect(insights.sampleSize).toBe(3);
    expect(insights.statusCounts).toEqual({ achieved: 1, below: 1, unknown: 1 });
    expect(insights.selected('A')).toMatchObject({
      rank: 2,
      total: 2,
      medianDifference: -10,
      targetGap: -10
    });
    expect(insights.selected('C')).toMatchObject({ rank: null, total: 2, medianDifference: null });
    expect(insights.diagnostics.targetGapPoints).toBe(-9);
  });

  it('市级目标摘要给出已完成/未完成/未提供三分列，不受选中支行影响', () => {
    const insights = buildCityInsights({ cityCode: '610100', citySummary: { kpis: [{ key: 'rate', value: 91 }] }, institutions: branches });
    expect(summarizeCityTargetStatus(insights)).toEqual({
      hasData: true,
      achievedText: '已完成目标1家',
      belowText: '未完成目标1家',
      unknownText: '未提供1家'
    });
    expect(summarizeTargetDistance(insights.diagnostics.targetRate).text).toBe('距目标还差9个百分点');
  });

  it('接近100%的显示舍入不能把未达标或超额误判为刚好达标', () => {
    expect(summarizeTargetDistance(99.99)).toMatchObject({ state: 'below', text: '距目标还差不到0.1个百分点' });
    expect(summarizeTargetDistance(100.01)).toMatchObject({ state: 'above', text: '超目标不到0.1个百分点' });
  });

  it('趋势只有一个有效点时不伪造首末变化；连续下降需要三个不同日期', () => {
    expect(deriveTrendObservation([{ date: '2026-09', deposit: 10 }])).toMatchObject({
      first: 10,
      last: 10,
      change: null,
      state: '样本不足'
    });
    expect(deriveTrendObservation([
      { date: '2026-07', deposit: 10 },
      { date: '2026-08', deposit: 12 },
      { date: '2026-09', deposit: 11 }
    ])).toMatchObject({ first: 10, last: 11, change: 1, state: '最新回落' });
    expect(deriveTrendObservation([
      { date: '2026-07', deposit: 12 },
      { date: '2026-08', deposit: 11 },
    ])).toMatchObject({ change: -1, state: '最新回落' });
    expect(deriveTrendObservation([
      { date: '2026-07', deposit: 12 },
      { date: '2026-08', deposit: 11 },
      { date: '2026-09', deposit: 10 }
    ])).toMatchObject({ change: -2, state: '连续下降' });
    expect(deriveTrendObservation([
      { date: '', deposit: 12 },
      { date: '2026-08', deposit: 11 },
      { date: '2026-09', deposit: 10 }
    ])).toMatchObject({ change: null, state: '日期不可比' });
  });

  it('空白、布尔和对象值保持未绑定，不会被 Number 转成零；同值存款并列排名', () => {
    expect(resolveDepositTarget({ metrics: { target: ' ', deposit: false } })).toBeNull();
    expect(buildCityInsights({
      cityCode: '610100',
      institutions: [
        { orgCode: 'X', cityCode: '610100', metrics: { deposit: 100, rate: 100 } },
        { orgCode: 'Y', cityCode: '610100', metrics: { deposit: 100, rate: 100 } }
      ]
    }).selected('Y')).toMatchObject({ rank: 1, total: 2 });
  });

  it('金额类派生值保留微小差额，不提前舍入为 -0；百分点仍按一位', () => {
    const tinyGap = buildProvinceInsights({ institutions: [{
      orgCode: 'TINY', metrics: { deposit: 100.123455, target: 100.123456, rate: 99.9 }, trend: [
        { date: '2026-08', deposit: 100.123455 },
        { date: '2026-09', deposit: 100.123456 }
      ]
    }] });
    expect(tinyGap.rows[0].targetGap).toBe(-0.000001);
    expect(Object.is(tinyGap.rows[0].targetGap, -0)).toBe(false);
    expect(tinyGap.rows[0].trend.change).toBe(0.000001);

    const city = buildCityInsights({ cityCode: '610100', institutions: [
      { orgCode: 'A', cityCode: '610100', metrics: { deposit: 100.123455, rate: 90 } },
      { orgCode: 'B', cityCode: '610100', metrics: { deposit: 100.123457, rate: 90 } }
    ] });
    expect(city.selected('A').medianDifference).toBe(-0.000001);
    expect(summarizeTargetDistance(99.94).value).toBe(-0.1);
  });
});
