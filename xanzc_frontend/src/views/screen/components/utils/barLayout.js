// 柱状对比布局计算（无 Vue/ECharts 依赖，便于用确定性输入单测）。
// width/height 是减去 grid 边距后的实际绘图区尺寸；横向模式使用 height 计算柱厚。

export const BAR_LAYOUT_LIMITS = Object.freeze({
  minBarWidth: 3,
  maxBarWidth: 36,
  minGapPx: 1.5,
  minCategoryGapPct: 4,
  maxCategoryGapPct: 60
});

function positiveInteger(value, fallback = 1) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? Math.max(1, Math.floor(number)) : fallback;
}

function finiteNumber(value, fallback = 0) {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function percentString(value, fallback = 20) {
  const number = Number(value);
  if (!Number.isFinite(number) || number <= 0) return `${fallback}%`;
  // 保留两位小数，避免舍入后破坏等间距关系；超大尺寸仍返回有限值。
  const rounded = number >= 1e12 ? Math.floor(number) : Math.round(number * 100) / 100;
  return `${rounded}%`;
}

/**
 * 根据绘图区尺寸、类目数和指标系列数计算 ECharts 柱状布局。
 *
 * @param {object} options 布局入参。
 * @param {number} options.width 纵向模式的实际绘图区宽度。
 * @param {number} options.height 横向模式的实际绘图区高度。
 * @param {number} options.categoryCount 类目数。
 * @param {number} options.seriesCount 指标系列数。
 * @param {boolean} [options.horizontal=false] 是否横向条形图。
 * @param {boolean} [options.stacked=false] 是否堆叠模式；堆叠每类只占一个视觉柱组。
 * @returns {{barWidth:number, barGap:string, barCategoryGap:string}} ECharts series 可直接使用的布局字段。
 */
export function resolveBarLayout({
  width = 0,
  height = 0,
  categoryCount = 0,
  seriesCount = 0,
  horizontal = false,
  stacked = false
} = {}) {
  const axisLength = Math.max(0, finiteNumber(horizontal ? height : width));
  const categories = positiveInteger(categoryCount);
  const visualSeriesCount = stacked ? 1 : positiveInteger(seriesCount);
  const categorySlot = axisLength / categories;

  // 让同一类目内的柱间距和相邻类目间距相等：
  //   step = categorySlot / visualSeriesCount
  //   gapPx = step - barWidth
  // 显式 barWidth 时 ECharts 的组内间距为 barWidth * barGap%，因此 barGap
  // 必须由实际像素间距反算，不能封顶在 100%。
  const requestedStep = categorySlot > 0 ? categorySlot / visualSeriesCount : 0;
  // 槽位小于最小柱宽时无法同时满足最小柱宽和正间隔，使用最小可布局步长，
  // 保证密集数据仍输出正的柱宽、间距和有限百分比。
  const step = Math.max(requestedStep, BAR_LAYOUT_LIMITS.minBarWidth + BAR_LAYOUT_LIMITS.minGapPx);
  const rawBarWidth = step * 0.72;
  // 先限制到 step - minGapPx，再向下取整，避免四舍五入后柱宽挤掉最小间隔。
  const maxBarWidthForGap = Math.min(
    BAR_LAYOUT_LIMITS.maxBarWidth,
    step - BAR_LAYOUT_LIMITS.minGapPx
  );
  const roundedBarWidth = Math.round(clamp(
    rawBarWidth,
    BAR_LAYOUT_LIMITS.minBarWidth,
    maxBarWidthForGap
  ));
  const barWidth = Math.max(
    BAR_LAYOUT_LIMITS.minBarWidth,
    Math.min(roundedBarWidth, Math.floor(maxBarWidthForGap))
  );
  const gapPx = step - barWidth;
  const barGapPct = (gapPx / barWidth) * 100;

  // barCategoryGap 在显式 barWidth 下不参与最终类目 offset，但保留与真实类目
  // 间距一致的值，供 ECharts 和后续主题配置使用。
  const categoryGapPct = categorySlot > 0
    ? clamp(Math.round((gapPx / categorySlot) * 100), BAR_LAYOUT_LIMITS.minCategoryGapPct,
      BAR_LAYOUT_LIMITS.maxCategoryGapPct)
    : 20;

  return {
    barWidth,
    barGap: percentString(barGapPct),
    barCategoryGap: `${categoryGapPct}%`
  };
}
