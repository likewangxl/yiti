// 吸附对齐计算——参照 DataEase MarkLine.vue 的 showLine 改写为纯函数。
// 与原实现差异:①去旋转(不做 rotate 分支);②改写成纯函数返回 {top,left,lines},
// 不直接 mutate store、不走 eventBus;③坐标全部为 1920×1080 设计态像素
// (调用方 Shape.vue 已把鼠标位移除以 scale 换算,故此处与 scale 无关)。
// 6 基准线:xt/xc/xb(横,对 top/中/bottom)、yl/yc/yr(竖,对 left/中/right),阈值 3px。

/**
 * @param {{top,left,width,height}} cur 当前拖拽组件设计态矩形
 * @param {Array<{top,left,width,height}>} others 其余组件矩形
 * @param {number} diff 吸附阈值(默认 3px)
 * @returns {{top:number,left:number,lines:Object}} 吸附后 top/left + 命中线(key→设计态像素位置)
 */
export function computeSnap(cur, others, diff = 3) {
  const curRight = cur.left + cur.width;
  const curBottom = cur.top + cur.height;
  const curHCenter = cur.left + cur.width / 2;
  const curVCenter = cur.top + cur.height / 2;

  let snapLeft = cur.left;
  let snapTop = cur.top;
  const lines = {}; // 命中线 → 像素位置
  let bestX = diff + 1;
  let bestY = diff + 1;

  for (const o of others) {
    const oRight = o.left + o.width;
    const oBottom = o.top + o.height;
    const oHCenter = o.left + o.width / 2;
    const oVCenter = o.top + o.height / 2;

    // 竖向(yl 左/右→左, yc 中→中, yr 左/右→右);pos = 对齐线的 x 像素
    const xs = [
      { d: Math.abs(cur.left - o.left),      left: o.left,               line: 'yl', pos: o.left },
      { d: Math.abs(curRight - o.left),      left: o.left - cur.width,   line: 'yl', pos: o.left },
      { d: Math.abs(curHCenter - oHCenter),  left: oHCenter - cur.width / 2, line: 'yc', pos: oHCenter },
      { d: Math.abs(cur.left - oRight),      left: oRight,               line: 'yr', pos: oRight },
      { d: Math.abs(curRight - oRight),      left: oRight - cur.width,   line: 'yr', pos: oRight }
    ];
    for (const c of xs) {
      if (c.d <= diff && c.d < bestX) {
        bestX = c.d;
        snapLeft = Math.round(c.left);
        delete lines.yl; delete lines.yc; delete lines.yr;
        lines[c.line] = Math.round(c.pos);
      }
    }

    // 横向(xt 顶/底→顶, xc 中→中, xb 顶/底→底);pos = 对齐线的 y 像素
    const ys = [
      { d: Math.abs(cur.top - o.top),        top: o.top,                 line: 'xt', pos: o.top },
      { d: Math.abs(curBottom - o.top),      top: o.top - cur.height,    line: 'xt', pos: o.top },
      { d: Math.abs(curVCenter - oVCenter),  top: oVCenter - cur.height / 2, line: 'xc', pos: oVCenter },
      { d: Math.abs(cur.top - oBottom),      top: oBottom,               line: 'xb', pos: oBottom },
      { d: Math.abs(curBottom - oBottom),    top: oBottom - cur.height,  line: 'xb', pos: oBottom }
    ];
    for (const c of ys) {
      if (c.d <= diff && c.d < bestY) {
        bestY = c.d;
        snapTop = Math.round(c.top);
        delete lines.xt; delete lines.xc; delete lines.xb;
        lines[c.line] = Math.round(c.pos);
      }
    }
  }
  return { top: snapTop, left: snapLeft, lines };
}
