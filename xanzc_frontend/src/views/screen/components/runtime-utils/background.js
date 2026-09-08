// 已发布大屏运行时的背景纯函数——全局背景三选一(纯色/线性渐变/图片 URL)
// 与组件级背景(透明/纯色/渐变)共用，兼容历史 canvas_style_json。

/** 深色主题缺省双色(与 --scr-bg / 主题深蓝渐变一致) */
const DEFAULT_FROM = '#050e2b';
const DEFAULT_TO = '#0a1f4e';
const DEFAULT_ANGLE = 135;

/**
 * 线性渐变 CSS 生成:双色 + 角度。
 * 角度非法(NaN/非数值)回退 135°,颜色缺省回退深色主题双色——保证任何输入都产出合法 CSS。
 */
export function gradientCss(from, to, angle) {
  const a = Number.isFinite(Number(angle)) && angle !== null && String(angle).trim() !== ''
    ? Number(angle) : DEFAULT_ANGLE;
  return `linear-gradient(${a}deg, ${from || DEFAULT_FROM} 0%, ${to || DEFAULT_TO} 100%)`;
}

/**
 * canvas_style_json 读时兼容:补 backgroundType('solid'|'gradient'|'image')/bgGradient/bgImage 默认。
 * 不 mutate 输入(返回新对象);已含新字段的数据幂等透传;null/undefined 兜底全默认。
 */
export function normalizeCanvasStyle(cs) {
  const src = cs && typeof cs === 'object' ? cs : {};
  const g = src.bgGradient && typeof src.bgGradient === 'object' ? src.bgGradient : {};
  return {
    ...src,
    background: src.background || DEFAULT_FROM,
    backgroundType: ['solid', 'gradient', 'image'].includes(src.backgroundType) ? src.backgroundType : 'solid',
    bgGradient: {
      from: g.from || DEFAULT_FROM,
      to: g.to || DEFAULT_TO,
      angle: Number.isFinite(Number(g.angle)) && g.angle !== undefined && g.angle !== null ? Number(g.angle) : DEFAULT_ANGLE
    },
    bgImage: typeof src.bgImage === 'string' ? src.bgImage : ''
  };
}

/**
 * 画布全局背景 → CSS 样式对象。
 * @param cs 原始 canvasStyle(可为旧格式,内部自动 normalize)
 * @param fallbackColor solid 无色值时的兜底色——运行时传 'transparent'
 *        (保持 ScreenRenderer "canvasStyle.background 缺省时舞台透明" 的既有契约)
 */
export function canvasBackgroundStyle(cs, fallbackColor = DEFAULT_FROM) {
  const src = cs && typeof cs === 'object' ? cs : {};
  const n = normalizeCanvasStyle(src);
  if (n.backgroundType === 'gradient') {
    return { background: gradientCss(n.bgGradient.from, n.bgGradient.to, n.bgGradient.angle) };
  }
  if (n.backgroundType === 'image' && n.bgImage) {
    // 纯色垫底:图片加载失败/透明图时不露白
    return {
      backgroundColor: src.background || fallbackColor,
      backgroundImage: `url(${n.bgImage})`,
      backgroundSize: 'cover',
      backgroundPosition: 'center center'
    };
  }
  // solid(含 image 但 URL 为空的回退):不用 normalize 后的默认色,以便运行时透传 fallback
  return { background: src.background || fallbackColor };
}

/**
 * 组件级背景样式:style.bgType ∈ 'none'|'solid'|'gradient'。
 * 缺省/none/solid 未选色 → 空对象(透明,与存量组件行为完全一致,读时零迁移)。
 */
export function componentBackgroundStyle(style) {
  const s = style && typeof style === 'object' ? style : {};
  if (s.bgType === 'solid' && s.bgColor) return { background: s.bgColor };
  if (s.bgType === 'gradient') return { background: gradientCss(s.bgFrom, s.bgTo, s.bgAngle) };
  return {};
}
