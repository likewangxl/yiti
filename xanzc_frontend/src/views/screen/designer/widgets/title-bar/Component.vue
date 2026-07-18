<template>
  <div class="w-titlebar" :class="'preset-' + preset" :style="rootStyle">
    <span class="tb-text" :style="textStyle">{{ p.text }}</span>
  </div>
</template>
<script setup>
// 科技感标题条——3 种预设(CSS 自绘零图片):glow 渐变底+两端发光线 / slash 斜切渐变块 / underline 下划光条。
// 视觉语言对齐 _screen-theme.scss(.scr-title 的白→青文字渐变、--scr-cyan 发光),纯装饰不接数据。
import { computed } from 'vue';
const props = defineProps({ element: { type: Object, required: true } });
const p = computed(() => props.element.propValue || {});
const PRESETS = ['glow', 'slash', 'underline'];
const preset = computed(() => (PRESETS.includes(p.value.preset) ? p.value.preset : 'glow'));
const rootStyle = computed(() => ({
  justifyContent: p.value.align === 'left' ? 'flex-start' : p.value.align === 'right' ? 'flex-end' : 'center'
}));
const textStyle = computed(() => ({ fontSize: (p.value.fontSize || 30) + 'px' }));
</script>
<style scoped>
.w-titlebar {
  width: 100%; height: 100%; position: relative; box-sizing: border-box;
  display: flex; align-items: center; padding: 0 28px; overflow: hidden;
}
.tb-text {
  position: relative; z-index: 1;
  font-weight: 700; letter-spacing: 6px; white-space: nowrap;
  /* 白→青文字渐变,与 .scr-header .scr-title 同款视觉 */
  background: linear-gradient(180deg, #fff 20%, var(--scr-cyan, #00e5ff) 100%);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}

/* glow:中间亮两端暗的渐变底 + 两端发光斜切线 */
.preset-glow {
  background: linear-gradient(90deg, transparent, rgba(0, 229, 255, .14) 28%, rgba(0, 229, 255, .14) 72%, transparent);
}
.preset-glow::before, .preset-glow::after {
  content: ''; position: absolute; top: 50%; height: 2px; width: 18%;
  transform: translateY(-50%) skewX(-32deg);
  background: linear-gradient(90deg, transparent, var(--scr-cyan, #00e5ff));
  box-shadow: 0 0 8px var(--scr-cyan-45, rgba(0, 229, 255, .45));
}
.preset-glow::before { left: 8px; }
.preset-glow::after { right: 8px; background: linear-gradient(90deg, var(--scr-cyan, #00e5ff), transparent); }

/* slash:斜切角渐变色块 + 左侧竖向强调条 */
.preset-slash {
  background: linear-gradient(100deg, rgba(0, 229, 255, .22), rgba(61, 126, 255, .08) 55%, rgba(0, 229, 255, .04));
  clip-path: polygon(0 0, 100% 0, calc(100% - 28px) 100%, 0 100%);
  border-bottom: 1px solid var(--scr-cyan-45, rgba(0, 229, 255, .45));
}
.preset-slash::before {
  content: ''; position: absolute; left: 0; top: 12%; bottom: 12%; width: 4px;
  background: linear-gradient(180deg, var(--scr-cyan, #00e5ff), var(--scr-blue, #3d7eff));
  box-shadow: 0 0 8px var(--scr-cyan-45, rgba(0, 229, 255, .45));
}

/* underline:透明底 + 下划发光光条(中亮两端渐隐) */
.preset-underline::after {
  content: ''; position: absolute; left: 6%; right: 6%; bottom: 2px; height: 3px; border-radius: 2px;
  background: linear-gradient(90deg, transparent, var(--scr-cyan, #00e5ff) 30%, var(--scr-cyan, #00e5ff) 70%, transparent);
  box-shadow: 0 0 10px var(--scr-cyan-45, rgba(0, 229, 255, .45));
}
</style>
