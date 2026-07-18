// 明细表格（TABLE_LIST）自动滚动轮播的分页/窗口纯函数——自研，零新增依赖。
// 轮播模型：行数超出可视区时，每个节拍把起始行环形 +1，取一个环形窗口渲染（大屏常见跑表效果）。

/** 可视行数 = (容器高 - 表头高) / 行高 向下取整，至少 1 行兜底（容器过矮不至于除出 0/负数） */
export function visibleCount(containerH, headerH, rowH) {
  if (!rowH || rowH <= 0) return 1;
  return Math.max(1, Math.floor(((containerH || 0) - (headerH || 0)) / rowH));
}

/** 是否需要轮播：开关开 且 总行数超出可视行数 */
export function shouldCarousel(total, visible, enabled) {
  return !!enabled && total > visible;
}

/** 起始行环形推进；total<=0 兜底 0 */
export function nextStart(start, total) {
  if (!total || total <= 0) return 0;
  return (start + 1) % total;
}

/**
 * 环形窗口取行下标：总行数不超窗口时全量顺序返回（不轮播场景），
 * 否则从 start 起环形取 count 个（跨尾回绕到 0）。
 */
export function windowIndices(start, count, total) {
  if (!total || total <= 0) return [];
  if (total <= count) return Array.from({ length: total }, (_, i) => i);
  const out = [];
  for (let i = 0; i < count; i++) out.push((start + i) % total);
  return out;
}
