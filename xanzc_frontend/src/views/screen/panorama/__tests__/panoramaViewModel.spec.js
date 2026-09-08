import { describe, expect, it } from 'vitest';
import {
  clampProgress,
  defaultTrendMetric,
  sortRankingRows,
  topRankingRows
} from '../panoramaViewModel';

describe('panoramaViewModel', () => {
  const rows = [
    { orgCode: 'a', deposit: 100, increase: -2, average: null },
    { orgCode: 'b', deposit: 90, increase: 5, average: 7 },
    { orgCode: 'c', deposit: 80, increase: null, average: 10 }
  ];

  it('按所选指标稳定排序，负数有效，缺值只保留在明细', () => {
    expect(sortRankingRows(rows, 'increase').map(row => row.orgCode)).toEqual(['b', 'a', 'c']);
    expect(topRankingRows(rows, 'increase').map(row => row.orgCode)).toEqual(['b', 'a']);
  });

  it('有净增值时默认净增，否则兼容余额趋势', () => {
    expect(defaultTrendMetric([{ deposit: 12, depositIncrease: null }])).toBe('deposit');
    expect(defaultTrendMetric([{ deposit: 12, depositIncrease: 0 }])).toBe('depositIncrease');
  });

  it('只限制图形进度，实际超额值交给文字层展示', () => {
    expect(clampProgress(125)).toBe(100);
    expect(clampProgress(-5)).toBe(0);
    expect(clampProgress(0)).toBe(0);
    expect(clampProgress(null)).toBeNull();
  });
});
