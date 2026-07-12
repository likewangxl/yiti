<template>
  <div class="dsn-mark-line">
    <div v-for="(pos, key) in lines" :key="key"
         class="dsn-line" :class="key.startsWith('x') ? 'x' : 'y'"
         :style="key.startsWith('x') ? { top: pos + 'px' } : { left: pos + 'px' }" />
  </div>
</template>

<script setup>
// 吸附对齐线:参照 DataEase MarkLine.vue,但算法抽到 utils/snap.js(纯函数),
// 本组件只做「订阅当前拖拽矩形 → 调 computeSnap → 渲染命中线 + 回写吸附坐标」。
import { ref } from 'vue';
import { computeSnap } from '@/views/screen/designer/utils/snap';
import { useScreenDesignerStore } from '@/stores/screenDesigner';

const store = useScreenDesignerStore();
const lines = ref({}); // 命中线 key→设计态像素

/**
 * 由 Shape.vue 在拖拽 move 中调用:传入当前组件的设计态矩形,返回吸附后的 {top,left}。
 * 其余组件 = componentData 中除当前选中外、且 isShow!==false 的组件。
 */
function snapAndShow(curRect) {
  const others = store.componentData
    .filter(c => c.id !== store.curComponent?.id && c.isShow !== false)
    .map(c => ({ top: c.style.top, left: c.style.left, width: c.style.width, height: c.style.height }));
  const r = computeSnap(curRect, others, 3);
  lines.value = r.lines;
  return { top: r.top, left: r.left };
}
function clear() { lines.value = {}; }

defineExpose({ snapAndShow, clear });
</script>

<style scoped>
.dsn-mark-line { position: absolute; inset: 0; pointer-events: none; z-index: 1000; }
.dsn-line { position: absolute; background: #59c7f9; }
.dsn-line.x { left: 0; width: 100%; height: 1px; }
.dsn-line.y { top: 0; width: 1px; height: 100%; }
</style>
