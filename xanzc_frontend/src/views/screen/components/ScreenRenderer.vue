<template>
  <div class="scr-canvas-render" :style="stageCss">
    <div v-for="c in components" :key="c.id" class="scr-abs"
         :style="absStyle(c)" v-show="c.isShow !== false">
      <!-- ChartWidget:注入 bindSnapshot 后复用 BlockContainer 取数;快照缺失(如草稿预览态
           bindSnapshots 恒空,详见后端 composeDraftPreview)不裸传 null 给 BlockContainer(会
           空指针崩溃),渲染中性占位。 -->
      <BlockContainer v-if="c.component === 'ChartWidget' && blockOf(c)" :block="blockOf(c)" :context="context" />
      <div v-else-if="c.component === 'ChartWidget'" class="scr-abs-empty">暂无预览数据</div>
      <!-- MapCenter 走独立分支:其 props 契约是 mapPoints 数组(来自 ScreenRenderRespDTO.mapPoints，
           PROVINCE 屏实时回填)，与素材类 widgets 的 element/propValue 签名不同，不经 widgetOf 通用注册。 -->
      <MapCenter v-else-if="c.component === 'MapCenter'" :map-points="mapPoints" />
      <component v-else :is="widgetOf(c.component)" :element="c" mode="runtime" />
    </div>
  </div>
</template>
<script setup>
// 运行时渲染——读发布态渲染包(canvasStyle + components + bindSnapshots),组件绝对定位铺在
// 1920×1080 舞台(外层 ScreenView 已 transform: scale 整体缩放，这里只按设计态像素绝对定位)。
// 已删除旧「region/row/block flex 布局」分支(3 屏直接切换，无 fallback)。
import { computed } from 'vue';
import BlockContainer from './BlockContainer.vue';
import MapCenter from './MapCenter.vue';
import { findWidget } from '@/views/screen/designer/widgets';

const props = defineProps({
  renderPackage: { type: Object, default: () => ({ components: [], bindSnapshots: {}, canvasStyle: {} }) },
  mapPoints: { type: Array, default: () => [] },
  context: { type: Object, default: () => ({}) }
});
const components = computed(() => props.renderPackage.components || []);
const stageCss = computed(() => ({
  position: 'relative', width: '1920px', height: '1080px',
  background: props.renderPackage.canvasStyle?.background || 'transparent'
}));
function absStyle(c) {
  return { position: 'absolute', top: c.style.top + 'px', left: c.style.left + 'px',
    width: c.style.width + 'px', height: c.style.height + 'px',
    opacity: c.style.opacity ?? 1 };
}
function widgetOf(component) { return findWidget(component); }
/** 从 bindSnapshots 合成 BlockContainer 需要的 block(bindJson/styleJson/drillJson 字符串);
 *  快照缺失时返回 null，由模板 v-if 隔离，不直接传给 BlockContainer。 */
function blockOf(c) {
  const snap = (props.renderPackage.bindSnapshots || {})[String(c.blockId)];
  if (!snap) return null;
  return {
    id: c.blockId,
    componentType: c.innerType || snap.componentType,
    bindJson: JSON.stringify(snap.bind || {}),
    styleJson: JSON.stringify(snap.styleCfg || {}),
    drillJson: JSON.stringify(snap.drill || {})
  };
}
</script>
<style scoped>
.scr-canvas-render { transform-origin: top left; }
.scr-abs { overflow: hidden; }
.scr-abs-empty {
  width: 100%; height: 100%; display: flex; align-items: center; justify-content: center;
  color: var(--scr-text-dim, #7d9bc9); font-size: 13px;
  border: 1px dashed rgba(96, 148, 214, .38); box-sizing: border-box;
}
</style>
