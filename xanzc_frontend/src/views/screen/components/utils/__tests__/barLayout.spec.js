import { describe, expect, it } from 'vitest';
import { BAR_LAYOUT_LIMITS, resolveBarLayout } from '../barLayout';

describe('resolveBarLayout（柱状对比自适应柱宽与间隔）', () => {
  it('相同数据在更宽绘图区使用更宽的柱，且柱宽受上下限约束', () => {
    const narrow = resolveBarLayout({ width: 320, height: 240, categoryCount: 4, seriesCount: 2 });
    const wide = resolveBarLayout({ width: 960, height: 240, categoryCount: 4, seriesCount: 2 });

    expect(wide.barWidth).toBeGreaterThan(narrow.barWidth);
    expect(narrow.barWidth).toBeGreaterThanOrEqual(BAR_LAYOUT_LIMITS.minBarWidth);
    expect(wide.barWidth).toBeLessThanOrEqual(BAR_LAYOUT_LIMITS.maxBarWidth);
    expect(narrow.barGap).toMatch(/%$/);
    expect(narrow.barCategoryGap).toMatch(/%$/);
  });

  it('系列越多单柱越窄，堆叠模式按每个类目一个视觉柱组计算', () => {
    const oneSeries = resolveBarLayout({ width: 640, height: 240, categoryCount: 4, seriesCount: 1 });
    const manySeries = resolveBarLayout({ width: 640, height: 240, categoryCount: 4, seriesCount: 5 });
    const oneStack = resolveBarLayout({ width: 640, height: 240, categoryCount: 4, seriesCount: 1, stacked: true });
    const manyStack = resolveBarLayout({ width: 640, height: 240, categoryCount: 4, seriesCount: 5, stacked: true });

    expect(manySeries.barWidth).toBeLessThan(oneSeries.barWidth);
    expect(manySeries.barGap).toMatch(/%$/);
    expect(manyStack.barWidth).toBe(oneStack.barWidth);
    expect(manyStack.barCategoryGap).toBe(oneStack.barCategoryGap);
  });

  it('横向模式使用绘图区高度决定柱厚，纵向模式不受高度变化影响', () => {
    const short = resolveBarLayout({ width: 960, height: 180, categoryCount: 6, seriesCount: 2, horizontal: true });
    const tall = resolveBarLayout({ width: 960, height: 420, categoryCount: 6, seriesCount: 2, horizontal: true });
    const verticalShort = resolveBarLayout({ width: 960, height: 180, categoryCount: 6, seriesCount: 2 });
    const verticalTall = resolveBarLayout({ width: 960, height: 420, categoryCount: 6, seriesCount: 2 });

    expect(tall.barWidth).toBeGreaterThan(short.barWidth);
    expect(verticalTall.barWidth).toBe(verticalShort.barWidth);
  });

  it('类目和系列较多时仍返回正间隔，异常尺寸安全回退', () => {
    const dense = resolveBarLayout({ width: 720, height: 240, categoryCount: 20, seriesCount: 8 });
    const fallback = resolveBarLayout({ width: 0, height: 0, categoryCount: 0, seriesCount: 0 });

    expect(Number.parseFloat(dense.barGap)).toBeGreaterThan(0);
    expect(Number.parseFloat(dense.barCategoryGap)).toBeGreaterThan(0);
    expect(fallback.barWidth).toBe(BAR_LAYOUT_LIMITS.minBarWidth);
    expect(fallback.barGap).toMatch(/%$/);
    expect(fallback.barCategoryGap).toMatch(/%$/);
  });
});
