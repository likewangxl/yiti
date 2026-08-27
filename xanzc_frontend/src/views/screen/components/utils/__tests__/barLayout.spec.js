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

  it('按每根柱的等分步长反算柱间距，使组内和跨类目间距相等', () => {
    for (const { width, categoryCount, seriesCount } of [
      { width: 20, categoryCount: 2, seriesCount: 2 },
      { width: 320, categoryCount: 4, seriesCount: 2 },
      { width: 960, categoryCount: 4, seriesCount: 2 }
    ]) {
      const layout = resolveBarLayout({ width, height: 240, categoryCount, seriesCount });
      const categorySlot = width / categoryCount;
      const step = categorySlot / seriesCount;
      const innerGap = layout.barWidth * Number.parseFloat(layout.barGap) / 100;
      const crossCategoryGap = categorySlot
        - (seriesCount * layout.barWidth + (seriesCount - 1) * innerGap);

      expect(innerGap).toBeGreaterThan(0);
      expect(crossCategoryGap).toBeGreaterThan(0);
      expect(innerGap).toBeCloseTo(crossCategoryGap, 2);
      expect(layout.barWidth + innerGap).toBeCloseTo(step, 2);
    }
  });

  it('绘图区变宽时动态增大组内间隔，并且相同输入结果稳定', () => {
    const narrow = resolveBarLayout({ width: 320, height: 240, categoryCount: 4, seriesCount: 2 });
    const wide = resolveBarLayout({ width: 960, height: 240, categoryCount: 4, seriesCount: 2 });

    expect(Number.parseFloat(wide.barGap)).toBeGreaterThan(Number.parseFloat(narrow.barGap));
    expect(resolveBarLayout({ width: 320, height: 240, categoryCount: 4, seriesCount: 2 }))
      .toEqual(narrow);
    expect(resolveBarLayout({ width: 960, height: 240, categoryCount: 4, seriesCount: 2 }))
      .toEqual(wide);
  });

  it('堆叠模式按一根视觉柱计算，横向模式按绘图区高度均匀分布', () => {
    const cases = [
      {
        options: { width: 960, height: 180, categoryCount: 6, seriesCount: 2, horizontal: true },
        axisLength: 180,
        visualSeriesCount: 2
      },
      {
        options: { width: 640, height: 240, categoryCount: 4, seriesCount: 5, stacked: true },
        axisLength: 640,
        visualSeriesCount: 1
      }
    ];

    for (const { options, axisLength, visualSeriesCount } of cases) {
      const layout = resolveBarLayout(options);
      const categorySlot = axisLength / options.categoryCount;
      const step = categorySlot / visualSeriesCount;
      const innerGap = layout.barWidth * Number.parseFloat(layout.barGap) / 100;
      const crossCategoryGap = categorySlot
        - (visualSeriesCount * layout.barWidth + (visualSeriesCount - 1) * innerGap);

      expect(innerGap).toBeGreaterThan(0);
      expect(crossCategoryGap).toBeGreaterThan(0);
      expect(innerGap).toBeCloseTo(crossCategoryGap, 2);
      expect(layout.barWidth + innerGap).toBeCloseTo(step, 2);
    }
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
    expect(Number.isFinite(Number.parseFloat(dense.barGap))).toBe(true);
    expect(Number.isFinite(Number.parseFloat(dense.barCategoryGap))).toBe(true);
    expect(fallback.barWidth).toBe(BAR_LAYOUT_LIMITS.minBarWidth);
    expect(fallback.barGap).toMatch(/%$/);
    expect(fallback.barCategoryGap).toMatch(/%$/);
  });
});
