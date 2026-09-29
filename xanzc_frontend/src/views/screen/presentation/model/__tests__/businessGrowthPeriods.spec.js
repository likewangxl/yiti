import { describe, expect, it } from 'vitest';

import {
  BUSINESS_GROWTH_PERIODS,
  buildBusinessGrowthPeriodModel,
  buildBusinessGrowthTimeAxis,
  selectBusinessGrowthRows
} from '../businessGrowthPeriods.js';

describe('businessGrowthPeriods', () => {
  it('以锚点包含当前日选取近七日和近一月，严格日期排序且不补空日期', () => {
    const rows = [
      { date: '2026-08-31', deposit: 1, loan: 2 },
      { date: '2026-09-01', deposit: 10, loan: 20 },
      { date: '2026-09-05', deposit: 50, loan: 60 },
      { date: '2026-09-25', deposit: 250, loan: 260 },
      { date: '2026-09-28', deposit: 280, loan: 290 },
      { date: '2026-09-30', deposit: 300, loan: 310 },
      { date: '2026-09-31', deposit: 999, loan: 999 },
      { date: '2026-10-01', deposit: 1000, loan: 1001 }
    ];

    expect(selectBusinessGrowthRows(rows, 'WEEK', { anchorDate: '2026-09-30' }))
      .toEqual([
        { date: '2026-09-25', deposit: 250, loan: 260 },
        { date: '2026-09-28', deposit: 280, loan: 290 },
        { date: '2026-09-30', deposit: 300, loan: 310 }
      ]);
    expect(selectBusinessGrowthRows(rows, 'MONTH', { anchorDate: '2026-09-30' })
      .map(row => row.date)).toEqual(['2026-09-01', '2026-09-05', '2026-09-25', '2026-09-28', '2026-09-30']);
  });

  it('无有效 dataDate 时使用最大有效源日期，非法锚点和非法源日期都不进入结果', () => {
    const rows = [
      { date: '2026-09-27', deposit: 27, loan: 270 },
      { date: '2026-09-28', deposit: 28, loan: 280 },
      { date: '2026-02-30', deposit: 30, loan: 300 },
      { date: 'garbage', deposit: 31, loan: 310 }
    ];

    expect(selectBusinessGrowthRows(rows, 'WEEK', { anchorDate: 'invalid' }))
      .toEqual([
        { date: '2026-09-27', deposit: 27, loan: 270 },
        { date: '2026-09-28', deposit: 28, loan: 280 }
      ]);
  });

  it('近一年按当前月和前十一月逐月取最后有效余额，不求和、不平均、不拼伪点', () => {
    const rows = [
      { date: '2025-10-31', deposit: 1031, loan: 2031 },
      { date: '2025-11-15', deposit: 1115, loan: 2115 },
      { date: '2025-12-01', deposit: 1201, loan: 2201 },
      { date: '2025-12-31', deposit: 1231, loan: 2231 },
      { date: '2026-01-30', deposit: 1300, loan: 2300 },
      { date: '2026-03-31', deposit: 1500, loan: 2500 },
      { date: '2026-04-20', deposit: 1620, loan: 2620 },
      { date: '2026-05-31', deposit: 1731, loan: 2731 },
      { date: '2026-06-30', deposit: 1830, loan: 2830 },
      { date: '2026-07-31', deposit: 1931, loan: 2931 },
      { date: '2026-08-15', deposit: 2015, loan: 3015 },
      { date: '2026-09-01', deposit: 2090, loan: 3090 },
      { date: '2026-09-29', deposit: 2129, loan: 3129 },
      { date: '2026-10-01', deposit: 2200, loan: 3200 }
    ];

    const selected = selectBusinessGrowthRows(rows, 'YEAR', { anchorDate: '2026-09-30' });
    expect(selected.map(row => row.date)).toEqual([
      '2025-10-31', '2025-11-15', '2025-12-31', '2026-01-30',
      '2026-03-31', '2026-04-20', '2026-05-31', '2026-06-30',
      '2026-07-31', '2026-08-15', '2026-09-29'
    ]);
    expect(selected.at(-1)).toEqual({ date: '2026-09-29', deposit: 2129, loan: 3129 });
    expect(selected.some(row => row.date === '2026-02-28')).toBe(false);
    expect(selected.some(row => row.date === '2026-10-01')).toBe(false);
  });

  it('构建周期模型时只复制 groups 和 rows，不改源模型，并默认声明三种周期', () => {
    const source = {
      enabled: true,
      title: '业务增长曲线',
      dataDate: '2026-09-30',
      groups: [{ status: 'READY', businessLine: 'RETAIL', rows: [
        { date: '2026-09-28', deposit: 1, loan: 2 },
        { date: '2026-09-30', deposit: 3, loan: 4 }
      ] }]
    };
    const sourceSnapshot = JSON.parse(JSON.stringify(source));

    expect(BUSINESS_GROWTH_PERIODS.map(item => item.key)).toEqual(['WEEK', 'MONTH', 'YEAR']);
    const result = buildBusinessGrowthPeriodModel(source, 'WEEK');

    expect(result).toMatchObject({ period: 'WEEK', anchorDate: '2026-09-30' });
    expect(result.groups[0].rows).toEqual(source.groups[0].rows);
    expect(result).not.toBe(source);
    expect(source).toEqual(sourceSnapshot);
  });

  it('构建近七日、近一月的连续日轴和近一年的连续月轴', () => {
    expect(buildBusinessGrowthTimeAxis('WEEK', { anchorDate: '2024-02-29' })).toEqual({
      granularity: 'DAY',
      categories: [
        '2024-02-23', '2024-02-24', '2024-02-25', '2024-02-26',
        '2024-02-27', '2024-02-28', '2024-02-29'
      ]
    });
    expect(buildBusinessGrowthTimeAxis('MONTH', { dataDate: '2026-01-02' }).categories).toHaveLength(30);
    expect(buildBusinessGrowthTimeAxis('MONTH', { dataDate: '2026-01-02' }).categories).toEqual([
      '2025-12-04', '2025-12-05', '2025-12-06', '2025-12-07', '2025-12-08', '2025-12-09',
      '2025-12-10', '2025-12-11', '2025-12-12', '2025-12-13', '2025-12-14', '2025-12-15',
      '2025-12-16', '2025-12-17', '2025-12-18', '2025-12-19', '2025-12-20', '2025-12-21',
      '2025-12-22', '2025-12-23', '2025-12-24', '2025-12-25', '2025-12-26', '2025-12-27',
      '2025-12-28', '2025-12-29', '2025-12-30', '2025-12-31', '2026-01-01', '2026-01-02'
    ]);
    expect(buildBusinessGrowthTimeAxis('YEAR', { anchorDate: '2026-01-15' })).toEqual({
      granularity: 'MONTH',
      categories: ['2025-02', '2025-03', '2025-04', '2025-05', '2025-06', '2025-07', '2025-08', '2025-09', '2025-10', '2025-11', '2025-12', '2026-01']
    });
  });

  it('无效锚点返回空轴，不用本机时间制造类别', () => {
    expect(buildBusinessGrowthTimeAxis('WEEK', { anchorDate: '2024-02-30' })).toEqual({
      granularity: 'DAY', categories: []
    });
    expect(buildBusinessGrowthTimeAxis('YEAR', { dataDate: '' })).toEqual({
      granularity: 'MONTH', categories: []
    });
  });
});
