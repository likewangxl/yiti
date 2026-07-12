<template>
  <div class="w-border-decor" :class="'decor-' + variant">
    <template v-if="variant === 'tech-b'">
      <span class="corner tl" /><span class="corner tr" /><span class="corner bl" /><span class="corner br" />
    </template>
  </div>
</template>
<script setup>
// 边框装饰——复用 _screen-theme.scss 的科技感描边/发光视觉语言(scr-cyan)，提供 3 种变体，纯装饰不接数据
import { computed } from 'vue';
const props = defineProps({ element: { type: Object, required: true } });
const variant = computed(() => (props.element.propValue || {}).variant || 'tech-a');
</script>
<style scoped>
.w-border-decor { width: 100%; height: 100%; box-sizing: border-box; position: relative; pointer-events: none; }

/* tech-a：整框细描边 + 顶部发光条(复用 .scr-block::before 视觉)
   色值改用 CSS 变量 + 字面量 fallback:今天画布根未挂 scr-theme-vars 时按 fallback 渲染(视觉零变化)，
   Task 9/10 在画布根挂 _screen-theme.scss 的 scr-theme-vars 后自动跟随主题覆盖。
   rgba(0,229,255,.25)/#00e5ff 与 _screen-theme.scss 既有 --scr-border/--scr-cyan 定义值完全一致，直接复用；
   .45/.12 两档透明度既有变量表未收录，按同一 --scr-cyan-NN 命名惯例新增，无同名变量时同样退回字面量 fallback。 */
.decor-tech-a { border: 1px solid var(--scr-border, rgba(0, 229, 255, .25)); border-radius: 6px; }
.decor-tech-a::before {
  content: '';
  position: absolute; top: 0; left: 0; right: 0; height: 2px;
  background: linear-gradient(90deg, transparent, var(--scr-cyan, #00e5ff) 20%, var(--scr-cyan, #00e5ff) 80%, transparent);
  opacity: .65;
}

/* tech-b：四角 L 形装饰角标 */
.decor-tech-b .corner { position: absolute; width: 24px; height: 24px; border: 2px solid var(--scr-cyan, #00e5ff); opacity: .85; }
.decor-tech-b .tl { top: 0; left: 0; border-right: none; border-bottom: none; }
.decor-tech-b .tr { top: 0; right: 0; border-left: none; border-bottom: none; }
.decor-tech-b .bl { bottom: 0; left: 0; border-right: none; border-top: none; }
.decor-tech-b .br { bottom: 0; right: 0; border-left: none; border-top: none; }

/* tech-c：内外双线描边框 */
.decor-tech-c {
  border: 1px solid var(--scr-cyan-45, rgba(0, 229, 255, .45));
  border-radius: 4px;
  box-shadow: inset 0 0 0 4px var(--scr-cyan-12, rgba(0, 229, 255, .12));
}
</style>
