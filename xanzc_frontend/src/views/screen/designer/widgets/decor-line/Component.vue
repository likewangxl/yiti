<template>
  <div class="w-decor-line" :class="'dir-' + dir">
    <span v-if="p.arrow" class="dl-arrow head" :style="arrowStyle('head')" />
    <span class="dl-line" :style="lineStyle" />
    <span v-if="p.arrow" class="dl-arrow tail" :style="arrowStyle('tail')" />
  </div>
</template>
<script setup>
// 装饰线——横/竖渐变发光线,可选两端角标箭头(CSS border 三角自绘,零图片零依赖)。
// 颜色预设对齐 _screen-theme.scss 变量色值(cyan/blue/gold),纯装饰不接数据。
import { computed } from 'vue';
const props = defineProps({ element: { type: Object, required: true } });
const p = computed(() => props.element.propValue || {});
const dir = computed(() => (p.value.direction === 'v' ? 'v' : 'h'));
const PRESET_COLORS = { cyan: '#00e5ff', blue: '#3d7eff', gold: '#ffd76a' };
const color = computed(() => PRESET_COLORS[p.value.preset] || PRESET_COLORS.cyan);
const thickness = computed(() => Math.max(1, Number(p.value.thickness) || 2));

const lineStyle = computed(() => {
  const c = color.value;
  // 两端渐隐 + 发光,横线沿 90° 竖线沿 180°
  const grad = dir.value === 'h'
    ? `linear-gradient(90deg, transparent, ${c} 18%, ${c} 82%, transparent)`
    : `linear-gradient(180deg, transparent, ${c} 18%, ${c} 82%, transparent)`;
  return {
    flex: 1,
    background: grad,
    boxShadow: `0 0 8px ${c}`,
    ...(dir.value === 'h' ? { height: thickness.value + 'px' } : { width: thickness.value + 'px' })
  };
});
/** 两端箭头:border 三角,头尾各指向外侧 */
function arrowStyle(pos) {
  const c = color.value;
  const size = Math.max(5, thickness.value * 3);
  const t = `${size}px solid transparent`;
  if (dir.value === 'h') {
    return pos === 'head'
      ? { borderTop: t, borderBottom: t, borderRight: `${size}px solid ${c}` }
      : { borderTop: t, borderBottom: t, borderLeft: `${size}px solid ${c}` };
  }
  return pos === 'head'
    ? { borderLeft: t, borderRight: t, borderBottom: `${size}px solid ${c}` }
    : { borderLeft: t, borderRight: t, borderTop: `${size}px solid ${c}` };
}
</script>
<style scoped>
.w-decor-line { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; pointer-events: none; }
.dir-v { flex-direction: column; }
.dl-line { display: block; }
.dl-arrow { display: block; width: 0; height: 0; flex: none; }
</style>
