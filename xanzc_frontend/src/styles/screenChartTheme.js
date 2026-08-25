// 经营大屏 echarts 图表主题常量。
// echarts option 渲染到 canvas，无法直接读取 CSS 自定义属性（--scr-*），
// 故在此单独维护一份与 src/styles/_screen-theme.scss 同源的色值，供折线/饼图/钻取/地图等图表组件共用，
// 避免颜色字面量散落在各组件里各写一份、逐渐漂移。两处如需改主题色，需同步修改。
export const SCR_COLOR = {
  cyan: '#00e5ff',
  blue: '#3d7eff',
  gold: '#ffd76a',
  up: '#00e676',
  down: '#ff5252',
  orange: '#ff8a65',
  text: '#d5e6ff',
  textDim: '#7d9bc9',
  bgDeep: '#050e2b'
};

// 折线图默认色板（对应 --scr-cyan/num/blue/up + 一个暖色点缀）
export const SCR_PALETTE = [SCR_COLOR.cyan, SCR_COLOR.gold, SCR_COLOR.blue, SCR_COLOR.up, SCR_COLOR.orange];

// 饼图等类目较多的场景用扩展色板（前 5 与折线图色板一致，保持视觉连贯）
export const SCR_PALETTE_WIDE = [
  SCR_COLOR.cyan, SCR_COLOR.blue, SCR_COLOR.gold, SCR_COLOR.up, SCR_COLOR.orange,
  '#ba68c8', '#4dd0e1', '#fff176', '#90caf9', '#a5d6a7'
];

// 图表视觉预设：ECharts 运行在 canvas 中，不能读取设计器的 CSS 变量，
// 因此把 primitive 色板和语义 token 一起收敛在这里，组件只消费 resolveChartTheme 的结果。
const preset = (name, palette, tokens) => {
  const tokenSet = Object.freeze({ ...tokens });
  return Object.freeze({
    name,
    palette: Object.freeze([...palette]),
    tokens: tokenSet,
    token: tokenSet
  });
};

export const SCR_CHART_PRESETS = Object.freeze({
  aurora: preset('aurora', ['#62d2c6', '#86a9ff', '#e8c979', '#7fd39d', '#e59a91', '#b7a0e8', '#d07ba8', '#87c8d8', '#c7e37a', '#f1a46d'], {
    accent: '#62d2c6', accentStrong: '#88e4d8', text: '#e8eef8', textDim: '#93a7c4',
    number: '#f0d58d', bgDeep: '#091426', border: 'rgba(147,167,196,.26)',
    grid: 'rgba(147,167,196,.16)', tooltipBg: 'rgba(9,20,38,.94)', up: '#7fd39d', down: '#e59a91'
  }),
  graphite: preset('graphite', ['#9bc1bc', '#a9b6ca', '#d6b477', '#7da99f', '#b5a2d8', '#d79999', '#78909c', '#c3a6d8', '#d7a37c', '#92b7c6'], {
    accent: '#9bc1bc', accentStrong: '#c4e1dc', text: '#edf1f5', textDim: '#9aaabd',
    number: '#e2c487', bgDeep: '#10161d', border: 'rgba(154,170,189,.28)',
    grid: 'rgba(154,170,189,.17)', tooltipBg: 'rgba(16,22,29,.96)', up: '#8bc9a8', down: '#d99494'
  }),
  vivid: preset('vivid', ['#39c7ee', '#718eff', '#f5c451', '#52d98a', '#ff8377', '#c18cff', '#ff5fb0', '#7be2c1', '#ff9f43', '#9b8cff'], {
    accent: '#39c7ee', accentStrong: '#79e1ff', text: '#f2f7ff', textDim: '#9bb1d4',
    number: '#ffd86f', bgDeep: '#08152b', border: 'rgba(118,163,220,.3)',
    grid: 'rgba(118,163,220,.18)', tooltipBg: 'rgba(8,21,43,.95)', up: '#52d98a', down: '#ff8377'
  })
});

// 命名别名供配置器/外部主题选择器使用，保留 SCR_* 常量的既有导出风格。
export const SCR_THEME_PRESETS = SCR_CHART_PRESETS;
export const SCR_DEFAULT_PRESET = 'aurora';

export function normalizeChartPreset(value) {
  const key = String(value || '').trim().toLowerCase();
  return Object.prototype.hasOwnProperty.call(SCR_CHART_PRESETS, key) ? key : SCR_DEFAULT_PRESET;
}

/** 接受 preset 名称或 styleCfg，未知值一律回退 aurora。 */
export function resolveChartTheme(input) {
  if (input && typeof input === 'object' && input.tokens && Array.isArray(input.palette)) return input;
  const value = typeof input === 'string'
    ? input
    : input?.visualPreset || input?.themePreset || input?.preset;
  return SCR_CHART_PRESETS[normalizeChartPreset(value)];
}

// 语义别名便于非 Vue 的图表配置函数消费，和 resolveChartTheme 保持同一回退口径。
export const getChartTheme = resolveChartTheme;

/**
 * 十六进制主题色 → rgba（渐变柱/面积填充等需要同色不同透明度时用，避免各组件手写 rgba 字面量漂移）。
 * 仅支持 #rrggbb；其他格式原样返回（调用方给的已是 rgba 时不破坏）。
 */
export function scrWithAlpha(hex, alpha) {
  if (typeof hex !== 'string' || !/^#[0-9a-fA-F]{6}$/.test(hex)) return hex;
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r},${g},${b},${alpha})`;
}

export function scrTooltipStyle(theme) {
  if (theme) {
    const t = resolveChartTheme(theme).tokens;
    return {
      backgroundColor: t.tooltipBg,
      borderColor: t.border,
      textStyle: { color: t.text }
    };
  }
  return {
    backgroundColor: 'rgba(5,14,43,.9)',
    borderColor: 'rgba(0,229,255,.4)',
    textStyle: { color: SCR_COLOR.text }
  };
}
export function scrAxisLabel(theme) {
  return { color: theme ? resolveChartTheme(theme).tokens.textDim : SCR_COLOR.textDim };
}
export function scrAxisLine(theme) {
  return { lineStyle: { color: theme ? resolveChartTheme(theme).tokens.border : 'rgba(125,155,201,.4)' } };
}
export function scrSplitLine(theme) {
  return { lineStyle: { color: theme ? resolveChartTheme(theme).tokens.grid : 'rgba(125,155,201,.15)' } };
}
