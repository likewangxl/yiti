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

export function scrTooltipStyle() {
  return {
    backgroundColor: 'rgba(5,14,43,.9)',
    borderColor: 'rgba(0,229,255,.4)',
    textStyle: { color: SCR_COLOR.text }
  };
}
export function scrAxisLabel() {
  return { color: SCR_COLOR.textDim };
}
export function scrAxisLine() {
  return { lineStyle: { color: 'rgba(125,155,201,.4)' } };
}
export function scrSplitLine() {
  return { lineStyle: { color: 'rgba(125,155,201,.15)' } };
}
