import { describe, expect, it } from 'vitest';
import { buildBranchCoreMetrics, buildBranchPerformanceModel } from '../branchPerformanceModel';

function metricCell(score, overrides = {}) {
  return { actual: 100, target: 100, base: 0, completeRate: 100, score, ...overrides };
}

describe('支行核心经营指标模型', () => {
  it('只生成六张固定卡，按明确 key 绑定并保留来源口径', () => {
    const model = {
      kpis: [
        { key: 'corpDeposit', label: '对公存款余额', value: '10.5', date: '2026-09-28', unit: '亿元' },
        { key: 'retailDeposit', label: '储蓄存款余额', value: 0, date: '2026-09-28', unit: '亿元' },
        { key: 'corporateLoan', label: '公司贷款余额', value: 8, date: '2026-09-28', unit: '亿元' },
        { key: 'retailLoan', label: '零售贷款余额', value: 7, date: '2026-09-28', unit: '亿元' },
        { key: 'operatingRevenue', label: '营业收入', value: 6, date: '2026-09-28', unit: '万元' },
        { key: 'intermediaryIncome', label: '中间业务收入', value: 2, date: '2026-09-28', unit: '万元' },
        { key: 'corpRevenue', label: '对公 FTP 收入', value: 999, date: '2026-09-28', unit: '万元' }
      ],
      compositionMetrics: { depositTotal: 1000, corporate: 400, retail: 600 }
    };
    const snapshot = structuredClone(model);

    const result = buildBranchCoreMetrics(model);

    expect(result.map(item => item.key)).toEqual([
      'corpDeposit', 'retailDeposit', 'corpLoan', 'retailLoan', 'revenue', 'intermediaryIncome'
    ]);
    expect(result).toHaveLength(6);
    expect(result[0]).toMatchObject({ value: 10.5, label: '对公存款余额', date: '2026-09-28', unit: '亿元' });
    expect(result[1]).toMatchObject({ value: 0, label: '储蓄存款余额', date: '2026-09-28', unit: '亿元' });
    expect(result.find(item => item.key === 'corpLoan')).toMatchObject({ value: 8, label: '公司贷款余额' });
    expect(result.find(item => item.key === 'revenue')).toMatchObject({ value: 6, label: '营业收入' });
    expect(result.find(item => item.key === 'revenue').value).not.toBe(999);
    expect(result).not.toEqual(expect.arrayContaining([expect.objectContaining({ key: 'corpRevenue' })]));
    expect(model).toEqual(snapshot);
  });

  it('未绑定六项的值为 null 且状态明确，不从总额或构成推导', () => {
    const result = buildBranchCoreMetrics({
      kpis: [{ key: 'depositTotal', label: '存款总额', value: 100 }, { key: 'corpRevenue', value: 9 }],
      compositionMetrics: [{ key: 'corporate', value: 40 }, { key: 'retail', value: 60 }]
    });
    expect(result).toHaveLength(6);
    expect(result.every(item => item.value === null && item.status === '未绑定/暂无数据')).toBe(true);
  });
});

describe('支行绩效结果模型', () => {
  it('机构指标保留全部列，按 actual-base 生成 actual，并保持零目标完成率为空', () => {
    const metrics = [
      { metricCode: 'ORG_A', metricName: '机构指标 A', baseDim: 'ORG' },
      { metricCode: 'ORG_ZERO', metricName: '零目标指标', baseDim: 'ORG' },
      { metricCode: 'ORG_NO_BASE', metricName: '缺基础值指标', baseDim: 'ORG' },
      { metricCode: 'UNKNOWN', metricName: '不在机构方案的未知列', baseDim: 'EMP' }
    ];
    const orgRecords = [{
      subjectId: 'O-1', subjectName: '测试支行', subjectType: 'ORG', schemeCode: 'S1', dataDate: '2026-09-28', totalScore: 80,
      metrics: {
        ORG_A: { actual: 100, target: 100, base: 20, completeRate: 80, score: 8, unit: '万元' },
        ORG_ZERO: { actual: 10, target: 0, base: 0, completeRate: 99, score: 0 },
        ORG_NO_BASE: { actual: 100, target: 100, completeRate: 100, score: 10 }
      }
    }];
    const snapshot = structuredClone({ metrics, orgRecords });

    const result = buildBranchPerformanceModel({ metrics, orgRecords, empRecords: [] });

    expect(result.orgItems).toHaveLength(3);
    expect(result.orgItems[0]).toMatchObject({
      metricCode: 'ORG_A', metricName: '机构指标 A', actual: 80, sourceActual: 100, base: 20,
      target: 100, completionRate: 80, score: 8
    });
    expect(result.orgItems[1]).toMatchObject({ actual: 10, target: 0, completionRate: null, score: 0 });
    expect(result.orgItems[2]).toMatchObject({ actual: null, sourceActual: 100, base: null, target: 100, rate: 100 });
    expect(result.orgItems.some(item => item.metricCode === 'UNKNOWN')).toBe(false);
    expect({ metrics, orgRecords }).toEqual(snapshot);
  });

  it('无 baseDim 时按 ORG/EMP 记录中的指标键分列，未知记录列不伪造指标', () => {
    const metrics = [
      { metricCode: 'SHARED', metricName: '同名指标' },
      { metricCode: 'EMP_ONLY', metricName: '员工指标' },
      { metricCode: 'NOT_IN_ANY_RECORD', metricName: '未知方案列' }
    ];
    const result = buildBranchPerformanceModel({
      metrics,
      orgRecords: [{ subjectId: 'O1', subjectType: 'ORG', metrics: { SHARED: metricCell(10) } }],
      empRecords: [{ subjectId: 'E1', subjectName: '员工1', subjectType: 'EMP', totalScore: 10, metrics: {
        SHARED: metricCell(5), EMP_ONLY: metricCell(5), EXTRA: metricCell(5)
      } }]
    });
    expect(result.orgItems.map(item => item.metricCode)).toEqual(['SHARED']);
    expect(result.ranking[0].metricCount).toBe(2);
    expect(result.ranking[0]).toMatchObject({ subjectId: 'E1' });
    expect(result.ranking[0].missingCount).toBe(0);
    expect(result.metricCount).toBe(2);
  });

  it('基础值缺失或负目标时不造出机构完成率', () => {
    const result = buildBranchPerformanceModel({
      metrics: [{ metricCode: 'ORG_NEG', metricName: '负目标', baseDim: 'ORG' }],
      orgRecords: [{ subjectId: 'O1', subjectType: 'ORG', metrics: {
        ORG_NEG: { actual: 10, target: -5, completeRate: 200, score: 1 }
      } }]
    });
    expect(result.orgItems[0]).toMatchObject({ actual: null, sourceActual: 10, base: null, target: -5, completionRate: null, rate: null });
  });

  it('按同方案同日期保留至少 150 人全量排名，同分采用竞赛排名且零分可参与', () => {
    const metrics = [
      { metricCode: 'M_SCORE', metricName: '考核指标', baseDim: 'EMP' },
      ...Array.from({ length: 20 }, (_, index) => ({ metricCode: `M_${index + 1}`, metricName: `指标${index + 1}`, baseDim: 'EMP' }))
    ];
    const empRecords = Array.from({ length: 150 }, (_, index) => {
      const score = index === 0 || index === 1 ? 100 : index === 2 ? 98 : index === 149 ? 0 : 90 - index / 10;
      return {
        subjectId: `E${String(index + 1).padStart(3, '0')}`,
        subjectName: `员工${index + 1}`,
        subjectType: 'EMP', schemeCode: 'S1', dataDate: '2026-09-28', totalScore: score,
        metrics: Object.fromEntries(metrics.map(metric => [metric.metricCode, metricCell(index === 149 ? 0 : score)]))
      };
    });
    empRecords.push({
      subjectId: 'OTHER-SCHEME', subjectName: '其他方案', subjectType: 'EMP', schemeCode: 'OTHER', dataDate: '2026-09-28', totalScore: 1000,
      metrics: Object.fromEntries(metrics.map(metric => [metric.metricCode, metricCell(1000)]))
    });
    const result = buildBranchPerformanceModel({
      metrics,
      orgRecords: [{ subjectId: 'O1', subjectType: 'ORG', schemeCode: 'S1', dataDate: '2026-09-28', metrics: {} }],
      empRecords
    });

    expect(result.participantCount).toBe(150);
    expect(result.rankedCount).toBe(150);
    expect(result.ranking).toHaveLength(150);
    expect(result.ranking[0]).toMatchObject({ subjectId: 'E001', rank: 1 });
    expect(result.ranking[1]).toMatchObject({ subjectId: 'E002', rank: 1 });
    expect(result.ranking[2]).toMatchObject({ subjectId: 'E003', rank: 3 });
    expect(result.ranking.at(-1)).toMatchObject({ subjectId: 'E150', totalScore: 0, rank: 150 });
    expect(result.ranking.every(row => row.metricCount === 21 && row.missingCount === 0)).toBe(true);
    expect(result.averageScore).toBeGreaterThan(0);
    expect(result.highestScore).toBe(100);
  });

  it('完整性只看每个 EMP 指标的有限 score，部分分数进入 unranked，缺失矩阵可解释', () => {
    const metrics = [
      { metricCode: 'M1', metricName: '指标1', baseDim: 'EMP' },
      { metricCode: 'M2', metricName: '指标2', baseDim: 'EMP' },
      { metricCode: 'M3', metricName: '指标3', baseDim: 'EMP' }
    ];
    const common = { subjectType: 'EMP', schemeCode: 'S1', dataDate: '2026-09-28' };
    const result = buildBranchPerformanceModel({
      metrics,
      orgRecords: [],
      empRecords: [
        { ...common, subjectId: 'COMPLETE', subjectName: '完整员工', totalScore: 30, metrics: {
          M1: metricCell(10), M2: metricCell(20), M3: metricCell(0, { actual: 0, target: 100, completeRate: 0 })
        } },
        { ...common, subjectId: 'PARTIAL', subjectName: '部分员工', totalScore: 20, metrics: {
          M1: metricCell(20), M2: metricCell(' '), M3: metricCell(0)
        } },
        { ...common, subjectId: 'INVALID', subjectName: '非法员工', totalScore: 'NaN', metrics: {
          M1: metricCell(Infinity), M2: metricCell(10), M3: metricCell(false)
        } }
      ]
    });

    expect(result.participantCount).toBe(3);
    expect(result.rankedCount).toBe(1);
    expect(result.ranking).toHaveLength(1);
    expect(result.ranking[0]).toMatchObject({ subjectId: 'COMPLETE', completedCount: 2, incompleteCount: 1, missingCount: 0, metricCount: 3 });
    expect(result.unranked.map(row => row.subjectId)).toEqual(['PARTIAL', 'INVALID']);
    expect(result.unranked[0]).toMatchObject({ missingCount: 1, metricCount: 3 });
    expect(result.unranked[1]).toMatchObject({ missingCount: 2, metricCount: 3 });
  });
});
