// 已发布大屏运行时的 1920×1080 舞台适配。
// 编辑器的鼠标位移、矩形夹取和拖拽缩放逻辑已退役，运行态只保留一次性舞台映射。

export const DESIGN_W = 1920;
export const DESIGN_H = 1080;

// 适配策略联合枚举：服务端发布包的 canvasStyle.adaptor 仍按此白名单解释。
export const ADAPTORS = ['keep', 'keepProportion', 'widthFirst', 'heightFirst'];

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
