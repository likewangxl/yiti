// BorderDecor 边框样式清单——Component.vue(渲染类名)与 Attr.vue(面板选项)共用唯一来源,
// 防选项与类名两处漂移。全部 CSS 自绘(零图片资源/零新依赖),视觉语言对齐 _screen-theme.scss(scr-cyan 系)。
export const BORDER_VARIANTS = [
  { value: 'tech-a', label: '描边发光' },   // 细描边 + 顶部发光条(既有)
  { value: 'tech-b', label: '四角光标' },   // 四角 L 形角标(既有)
  { value: 'tech-c', label: '双线描边' },   // 内外双线(既有)
  { value: 'tech-d', label: '渐变霓虹' },   // 渐变描边 + 内外霓虹光晕
  { value: 'tech-e', label: '斜切角' },     // 八边形斜切角渐变框(clip-path + mask 镂空)
  { value: 'tech-f', label: '点阵角' },     // 四角点阵(radial-gradient 点网格)
  { value: 'tech-g', label: '内发光' }      // 细描边 + 内侧青色弥散光
];

/** 需要渲染四角 span 的变体(模板按此判断,新增角类样式只改本清单) */
export const CORNER_VARIANTS = ['tech-b', 'tech-f'];

/** variant → 渲染类名;非法/缺省回退 tech-a(存量画布 JSON 兼容) */
export function borderDecorClass(variant) {
  return 'decor-' + (BORDER_VARIANTS.some(v => v.value === variant) ? variant : 'tech-a');
}
