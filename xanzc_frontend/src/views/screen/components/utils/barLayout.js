// 柱状对比布局计算（无 Vue/ECharts 依赖，便于用确定性输入单测）。
// width/height 是减去 grid 边距后的实际绘图区尺寸；横向模式使用 height 计算柱厚。

export const BAR_LAYOUT_LIMITS = Object.freeze({
  minBarWidth: 3,
  maxBarWidth: 36,
  minGapPx: 1.5,
  maxGapPx: 8,
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

  // 每个类目默认只使用约 72% 的槽位，槽位余量作为类目间隔；系列越多，组内间隔略增，避免相邻柱粘连。
  const gapRatio = clamp(0.16 + Math.min(visualSeriesCount - 1, 8) * 0.01, 0.16, 0.24);
  const targetGroupWidth = categorySlot * 0.72;
  const minimumGaps = Math.max(0, visualSeriesCount - 1) * BAR_LAYOUT_LIMITS.minGapPx;
  const rawBarWidth = categorySlot > 0
    ? (targetGroupWidth - minimumGaps)
      / (visualSeriesCount + Math.max(0, visualSeriesCount - 1) * gapRatio)
    : 0;
  const barWidth = Math.round(clamp(rawBarWidth, BAR_LAYOUT_LIMITS.minBarWidth, BAR_LAYOUT_LIMITS.maxBarWidth));
  const gapPx = clamp(barWidth * gapRatio, BAR_LAYOUT_LIMITS.minGapPx, BAR_LAYOUT_LIMITS.maxGapPx);
  const groupWidth = barWidth * visualSeriesCount + gapPx * Math.max(0, visualSeriesCount - 1);

  // 极密数据下仍给 ECharts 一个正的类目间隔；正常数据使用剩余槽位作为真实间隔。
  const categoryGapPx = categorySlot > groupWidth
    ? categorySlot - groupWidth
    : Math.max(BAR_LAYOUT_LIMITS.minGapPx, categorySlot * 0.04);
  const categoryGapPct = categorySlot > 0
    ? clamp(Math.round((categoryGapPx / categorySlot) * 100), BAR_LAYOUT_LIMITS.minCategoryGapPct,
      BAR_LAYOUT_LIMITS.maxCategoryGapPct)
    : 20;
  const barGapPct = clamp(Math.round((gapPx / barWidth) * 100), 12, 100);

  return {
    barWidth,
    barGap: `${barGapPct}%`,
    barCategoryGap: `${categoryGapPct}%`
  };
}
