// 对齐/分布算法——多选工具条的左/右/上/下对齐、水平/垂直居中、水平/垂直等间距分布。
// 纯函数:输入 [{id, top, left, width, height}](1920×1080 设计态像素),输出 patch 数组
// [{id, top, left}](完整两轴坐标,调用方直接覆写 style,未动的轴原样带回)。
// 对齐基准 = 选区包围盒(对齐 DataEase 组合对齐语义);分布 = 首尾不动、中间均分间隙。

/** 选区包围盒(最小外接矩形) */
function bbox(rects) {
  let top = Infinity, left = Infinity, right = -Infinity, bottom = -Infinity;
  for (const r of rects) {
    top = Math.min(top, r.top);
    left = Math.min(left, r.left);
    right = Math.max(right, r.left + r.width);
    bottom = Math.max(bottom, r.top + r.height);
  }
  return { top, left, right, bottom };
}

/**
 * 对齐:type ∈ left|right|top|bottom|hcenter|vcenter。
 * 不足 2 个或未知类型返回 [](调用方按钮已禁用,这里兜底防御)。
 */
export function alignRects(type, rects) {
  if (!Array.isArray(rects) || rects.length < 2) return [];
  const b = bbox(rects);
  const cx = (b.left + b.right) / 2;
  const cy = (b.top + b.bottom) / 2;
  // 每种类型只改一个轴,另一轴原样带回
  const calc = {
    left: r => ({ top: r.top, left: b.left }),
    right: r => ({ top: r.top, left: b.right - r.width }),
    top: r => ({ top: b.top, left: r.left }),
    bottom: r => ({ top: b.bottom - r.height, left: r.left }),
    hcenter: r => ({ top: r.top, left: cx - r.width / 2 }),
    vcenter: r => ({ top: cy - r.height / 2, left: r.left })
  }[type];
  if (!calc) return [];
  return rects.map(r => {
    const p = calc(r);
    return { id: r.id, top: Math.round(p.top), left: Math.round(p.left) };
  });
}

/**
 * 等间距分布:dir ∈ h|v。按主轴起点排序,首尾不动,中间组件在首尾之间均分间隙
 * (间隙可为负——重叠场景照算,与 DataEase 一致)。不足 3 个返回 []。
 */
export function distributeRects(dir, rects) {
  if (!Array.isArray(rects) || rects.length < 3) return [];
  const isH = dir === 'h';
  if (!isH && dir !== 'v') return [];
  const posKey = isH ? 'left' : 'top';
  const sizeKey = isH ? 'width' : 'height';
  const sorted = [...rects].sort((a, b) => a[posKey] - b[posKey]);
  const first = sorted[0];
  const last = sorted[sorted.length - 1];
  // 可分配区间 = [首缘末端, 尾组件起点];中间组件总尺寸之外的空间均分为 n-1 个间隙
  const start = first[posKey] + first[sizeKey];
  const span = last[posKey] - start;
  const midSize = sorted.slice(1, -1).reduce((s, r) => s + r[sizeKey], 0);
  const gap = (span - midSize) / (sorted.length - 1);
  const patches = [];
  let cursor = start;
  for (let i = 0; i < sorted.length; i++) {
    const r = sorted[i];
    if (i === 0 || i === sorted.length - 1) {
      patches.push({ id: r.id, top: r.top, left: r.left }); // 首尾不动
    } else {
      cursor += gap;
      patches.push({
        id: r.id,
        top: isH ? r.top : Math.round(cursor),
        left: isH ? Math.round(cursor) : r.left
      });
      cursor += r[sizeKey];
    }
  }
  // 按主轴排序顺序回吐(调用方按 id 应用 patch,顺序无关紧要,契约固定为排序序)
  return patches;
}
