// 框选纯函数——画布空白拖拽出半透明选框:两点归一化矩形、矩形求交、命中组件集合。
// 坐标全部为 1920×1080 设计态像素(调用方 CanvasCore 已把鼠标坐标除以 scale 换算)。

/** 两点(任意拖拽方向)归一化为 {top,left,width,height} */
export function normalizeRect(p1, p2) {
  return {
    top: Math.min(p1.y, p2.y),
    left: Math.min(p1.x, p2.x),
    width: Math.abs(p1.x - p2.x),
    height: Math.abs(p1.y - p2.y)
  };
}

/** 矩形求交:交集面积 >0 才算命中(仅边缘贴合不算) */
export function rectsIntersect(a, b) {
  return a.left < b.left + b.width && b.left < a.left + a.width
    && a.top < b.top + b.height && b.top < a.top + a.height;
}

/**
 * 框选命中:与组件矩形求交,跳过锁定(isLock)与隐藏(isShow===false)组件。
 * 返回命中组件 id 数组(保持 components 数组顺序=图层顺序)。
 */
export function hitComponents(rect, components) {
  return (components || [])
    .filter(c => !c.isLock && c.isShow !== false && rectsIntersect(rect, c.style))
    .map(c => c.id);
}
