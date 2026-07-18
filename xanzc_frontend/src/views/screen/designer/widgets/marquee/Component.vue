<template>
  <div class="w-marquee" :style="boxStyle">
    <div ref="innerRef" class="w-marquee-inner" :style="innerStyle">{{ p.text }}</div>
  </div>
</template>
<script setup>
// 跑马灯——横向循环滚动文本(CSS animation 自研零依赖):内容 padding-left:100% 起步于容器右缘,
// keyframes 平移到自身 -100%(容器宽+文本宽的总距离),时长由 anim.js 纯函数按"像素/秒"速度换算。
// 文案/字号变化后重测滚动距离(nextTick 等 DOM 更新),未测得时 anim.js 兜底 800px。
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { marqueeAnimStyle } from './anim';
const props = defineProps({ element: { type: Object, required: true } });
const p = computed(() => props.element.propValue || {});
const innerRef = ref(null);
const distance = ref(0); // 滚动总距离 = 内容 offsetWidth(padding-left:100% 已含容器宽)

function measure() {
  distance.value = innerRef.value ? innerRef.value.offsetWidth : 0;
}
onMounted(measure);
watch(() => [p.value.text, p.value.fontSize], () => nextTick(measure));

const boxStyle = computed(() => ({
  color: p.value.color || '#7d9bc9',
  fontSize: (p.value.fontSize || 18) + 'px'
}));
const innerStyle = computed(() => marqueeAnimStyle(p.value, distance.value || undefined));
</script>
<style scoped>
.w-marquee { width: 100%; height: 100%; overflow: hidden; display: flex; align-items: center; }
.w-marquee-inner {
  display: inline-block; white-space: nowrap; padding-left: 100%;
  will-change: transform; letter-spacing: 2px;
}
</style>
<!-- keyframes 必须放非 scoped 块:animation-name 由 anim.js 以内联样式注入,scoped 编译会
     重写 keyframes 名(加 data-v 后缀)导致内联引用失配;w-marquee-roll 前缀全局唯一,无污染风险 -->
<style>
@keyframes w-marquee-roll {
  from { transform: translateX(0); }
  to { transform: translateX(-100%); }
}
</style>
