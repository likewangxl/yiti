<template>
  <div class="w-group">
    <!-- children 相对组左上角绝对定位;组不嵌套(makeGroup 已展开吸收),单层渲染即可 -->
    <div v-for="ch in element.children || []" :key="ch.id" class="w-group-item"
         v-show="ch.isShow !== false" :style="chStyle(ch)">
      <component :is="widgetOf(ch.component)" :element="ch" :mode="mode" />
    </div>
  </div>
</template>
<script setup>
// Group 成组容器(设计态)——多选成组生成,不进组件面板拖拽入口(与 MapCenter 同类:仅登记
// componentsMap 供 findWidget/findAttr 查得到)。children 坐标为相对组左上角(utils/group.js
// makeGroup 换算),组拖动/缩放由外层 Shape.vue 处理(缩放时 scaleGroupChildren 换算 children)。
// ChartWidget 子组件仍按 blockId 从 store.blocks 取数(chart-widget/Component.vue 逻辑不变)。
// 运行时渲染不复用本组件:ScreenRenderer 把 Group 展开为绝对坐标节点走既有分支(见其 components 计算)。
import { findWidget } from '@/views/screen/designer/widgets';
const props = defineProps({
  element: { type: Object, required: true },
  mode: { type: String, default: 'design' }
});
function widgetOf(component) { return findWidget(component); }
function chStyle(ch) {
  const s = ch.style || {};
  return { position: 'absolute', top: (s.top ?? 0) + 'px', left: (s.left ?? 0) + 'px',
    width: (s.width ?? 0) + 'px', height: (s.height ?? 0) + 'px', opacity: s.opacity ?? 1 };
}
</script>
<style scoped>
.w-group { position: relative; width: 100%; height: 100%; }
.w-group-item { position: absolute; overflow: hidden; }
</style>
