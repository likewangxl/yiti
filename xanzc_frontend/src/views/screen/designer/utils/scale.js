// 大屏画布坐标换算:设计态坐标恒 1920×1080 基准(幂等),画布容器 transform: scale;
// 鼠标屏幕位移换算回设计坐标需除以 scale。运行时渲染按适配策略做一次性映射,
// 不采用 DataEase 的增量换算模型(研读避坑 #1:有状态基准易漂移)。

export const DESIGN_W = 1920;
export const DESIGN_H = 1080;

// 适配策略联合枚举:全仓库唯一来源(研读避坑 #2:锁死可选值防文档/常量/实现三方漂移)
export const ADAPTORS = ['keep', 'keepProportion', 'widthFirst', 'heightFirst'];

/** 适应窗口缩放比:取宽/高比的较小值,保证整屏可见(对齐 ScreenView.fit) */
export function fitScale(viewportW, viewportH, designW = DESIGN_W, designH = DESIGN_H) {
  if (viewportW <= 0 || viewportH <= 0) return 1;
  return Math.min(viewportW / designW, viewportH / designH);
}

/** 屏幕像素位移 → 设计态位移(除以 scale;scale<=0 兜底 1) */
export function screenDeltaToDesign(deltaPx, scale) {
  const s = scale > 0 ? scale : 1;
  return deltaPx / s;
}

/** 夹取组件矩形到画布内:禁拖出(top/left>=0 且不越右/下界)+ 极小尺寸下限 */
export function clampRect({ top, left, width, height }, opts = {}) {
  const { designW = DESIGN_W, designH = DESIGN_H, minW = 20, minH = 20 } = opts;
  let w = Math.max(minW, Math.round(width));
  let h = Math.max(minH, Math.round(height));
  w = Math.min(w, designW);
  h = Math.min(h, designH);
  let l = Math.round(left);
  let t = Math.round(top);
  l = Math.min(Math.max(0, l), designW - w);
  t = Math.min(Math.max(0, t), designH - h);
  return { top: t, left: l, width: w, height: h };
}

/** 运行时舞台 CSS:按策略算 scale(一次性映射,幂等) */
export function stageStyle(adaptor, viewportW, viewportH, designW = DESIGN_W, designH = DESIGN_H) {
  const sw = viewportW / designW;
  const sh = viewportH / designH;
  let scale;
  switch (adaptor) {
    case 'keep': scale = 1; break;
    case 'widthFirst': scale = sw; break;
    case 'heightFirst': scale = sh; break;
    case 'keepProportion':
    default: scale = Math.min(sw, sh); break;
  }
  return { width: designW, height: designH, transform: `scale(${scale})`, scale };
}
