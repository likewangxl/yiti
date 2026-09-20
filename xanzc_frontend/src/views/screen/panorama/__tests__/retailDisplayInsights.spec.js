import { describe, expect, it } from 'vitest';
import {
  buildDepositComparison,
  buildInstitutionDepositView,
  buildRetailDisplayInsights,
  buildTrendSummary,
  classifyInstitution
} from '../retailDisplayInsights.js';

describe('retailDisplayInsights', () => {
  it('把时点余额与月日均明确作为口径对照，差额不标成净增', () => {
    expect(buildDepositComparison({ balance: 120, average: 118.5 })).toMatchObject({
      balance: 120,
      average: 118.5,
      difference: -1.5,
      differenceLabel: '月日均 − 时点余额',
      isNetIncrease: false
    });
  });

  it('识别 PRIMARY/SECONDARY_BRANCH 为分行，其余归入待分类', () => {
    expect(classifyInstitution({ orgNature: 'PRIMARY', operatingLevel: 'BRANCH' })).toBe('primary');
    expect(classifyInstitution({ orgNature: 'SECONDARY_BRANCH' })).toBe('primary');
    expect(classifyInstitution({ orgNature: 'NONE', operatingLevel: 'OTHER' })).toBe('other');
  });

  it('空白字符串不被当成零值', () => {
    expect(buildDepositComparison({ balance: '   ', average: 0 })).toMatchObject({ balance: null, average: 0, difference: null });
  });

  it('机构存款视图在有分行时默认只看分行，并保留月均与余额差额', () => {
    const view = buildInstitutionDepositView([
      { orgCode: 'P1', name: '甲分行', deposit: 100, average: 98, orgNature: 'PRIMARY' },
      { orgCode: 'P2', name: '乙分行', deposit: 80, average: 81, operatingLevel: 'SECONDARY_BRANCH' },
      { orgCode: 'O1', name: '待分类机构', deposit: 50, average: 49, orgNature: 'NONE' }
    ]);
    expect(view.defaultFilter).toBe('primary');
    expect(view.filteredRows).toHaveLength(2);
    expect(view.filteredRows[0]).toMatchObject({ deposit: 100, average: 98, difference: -2, institutionType: 'primary' });
    expect(view.mixed).toBe(true);
    expect(view.title).toContain('机构存款对比');
    expect(view.title).not.toContain('排名');
  });

  it('经营观察只计算可核对事实：机构覆盖、月均低于余额、目标达标和数据日期', () => {
    const insights = buildRetailDisplayInsights({
      dataDate: '2026-09-06',
      institutions: [{ orgCode: 'P1' }, { orgCode: 'P2' }, { orgCode: 'P3' }],
      rankings: [
        { orgCode: 'P1', deposit: 100, average: 98, date: '2026-09-06' },
        { orgCode: 'P2', deposit: 80, average: 82, date: '2026-09-06' },
        { orgCode: 'P3', deposit: null, average: null, date: null }
      ],
      targets: [
        { name: '存款', actual: 102, target: 100 },
        { name: 'AUM', actual: 8, target: 10 },
        { name: '未接入', actual: null, target: null }
      ]
    });
    expect(insights.institutionCoverage).toMatchObject({ available: 2, total: 3, percent: 66.67 });
    expect(insights.averageBelowBalanceCount).toBe(1);
    expect(insights.validTargetCount).toBe(2);
    expect(insights.achievedTargetCount).toBe(1);
    expect(insights.dataDateCoverage).toMatchObject({ date: '2026-09-06', available: 2, total: 3 });
  });

  it('趋势摘要使用存款序列标注观察区间、区间变动和峰谷，不叫环比', () => {
    expect(buildTrendSummary([
      { date: '2026-07', deposit: 100, depositAverage: 99 },
      { date: '2026-08', deposit: 103, depositAverage: 102 },
      { date: '2026-09', deposit: 101, depositAverage: 100 }
    ])).toMatchObject({
      startLabel: '2026-07',
      endLabel: '2026-09',
      depositChange: 1,
      observationLabel: '观察区间 2026-07 至 2026-09',
      peak: { date: '2026-08', value: 103 },
      trough: { date: '2026-07', value: 100 }
    });
  });

  it('行级没有日期时沿用排名来源质量日期，并标明日期是来源级覆盖', () => {
    const insights = buildRetailDisplayInsights({
      rankings: [{ deposit: 100, average: 99 }, { deposit: 80, average: 81 }],
      sourceQualities: { retailRanking: { dataDate: '2026-09-19' } }
    });
    expect(insights.dataDateCoverage).toMatchObject({ date: '2026-09-19', available: 2, total: 2, sourceLevel: true });
  });
});
