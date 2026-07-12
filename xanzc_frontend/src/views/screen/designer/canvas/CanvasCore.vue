<template>
  <div ref="wrapRef" class="dsn-canvas-wrap" @mousedown.self="deselect" @drop.prevent="onDrop" @dragover.prevent>
    <div ref="stageRef" class="dsn-stage" :style="stageStyle"
         @contextmenu.prevent="onCanvasContextMenu">
      <!-- 组件层:图层顺序=componentData 数组顺序 -->
      <Shape v-for="(c, i) in store.componentData" :key="c.id" :element="c" :index="i"
             @contextmenu="onShapeContextMenu">
        <component :is="widgetOf(c.component)" :element="c" mode="design" />
      </Shape>
      <MarkLine ref="markLineRef" />
    </div>
    <ContextMenu ref="ctxRef" />
  </div>
</template>

<script setup>
// 画布容器——参照 DataEase CanvasCore.vue 裁剪:保留空白点选取消、drop 落组件、右键入口、网格;
// 去掉框选套索/Tab 移入移出/矩阵。舞台用 transform: scale(store.scale),设计态恒 1920×1080。
import { computed, provide, ref } from 'vue';
import { DESIGN_W, DESIGN_H, screenDeltaToDesign } from '@/views/screen/designer/utils/scale';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
import { findWidget, newComponentFromMeta } from '@/views/screen/designer/widgets';
import Shape from './Shape.vue';
import MarkLine from './MarkLine.vue';
import ContextMenu from './ContextMenu.vue';

const store = useScreenDesignerStore();
const wrapRef = ref(null);
const stageRef = ref(null);
const markLineRef = ref(null);
const ctxRef = ref(null);
provide('markLineRef', markLineRef); // 供 Shape 拖拽时调吸附

const stageStyle = computed(() => ({
  position: 'relative',
  width: DESIGN_W + 'px',
  height: DESIGN_H + 'px',
  transform: `scale(${store.scale})`,
  transformOrigin: 'top left',
  background: store.canvasStyle.background || '#050e2b',
  // 40px 网格背景(编辑态)
  backgroundImage:
    'linear-gradient(rgba(255,255,255,.04) 1px, transparent 1px),' +
    'linear-gradient(90deg, rgba(255,255,255,.04) 1px, transparent 1px)',
  backgroundSize: '40px 40px'
}));

function widgetOf(component) { return findWidget(component); }
function deselect() { store.selectComponent('__none__'); ctxRef.value?.hide(); }

/** 组件面板拖入:e.dataTransfer 携带 component 类型,落点换算成设计态坐标 */
function onDrop(e) {
  const component = e.dataTransfer.getData('component');
  const innerType = e.dataTransfer.getData('innerType');
  if (!component) return;
  const rect = stageRef.value.getBoundingClientRect();
  // 落点(屏幕) - 舞台左上(屏幕),再除以 scale = 设计态坐标
  const left = screenDeltaToDesign(e.clientX - rect.left, store.scale);
  const top = screenDeltaToDesign(e.clientY - rect.top, store.scale);
  const node = newComponentFromMeta(component, innerType);
  node.style = { ...node.style, top: Math.round(top), left: Math.round(left) };
  store.addComponent(node);
}

/**
 * 屏幕坐标(clientX/clientY)→ ContextMenu 定位坐标。
 * ContextMenu 的 .dsn-ctx 是 position:absolute,定位基准是 .dsn-canvas-wrap(position:relative)
 * 的 padding box,而非视口原点;wrap 又是 overflow:auto,还叠加了滚动偏移。
 * 直接把 clientX/clientY 当 top/left 用,菜单会偏移出右键点位置(真实 bug,修复保持
 * ContextMenu.show(x,y) 接口不变,只在调用侧换算)。
 */
function toWrapPos(clientX, clientY) {
  const wrap = wrapRef.value;
  if (!wrap) return { x: clientX, y: clientY };
  const rect = wrap.getBoundingClientRect();
  return { x: clientX - rect.left + wrap.scrollLeft, y: clientY - rect.top + wrap.scrollTop };
}

function onShapeContextMenu(pos) {
  const { x, y } = toWrapPos(pos.clientX, pos.clientY);
  ctxRef.value?.show(x, y);
}
function onCanvasContextMenu(e) { /* 空白右键暂不弹菜单 */ }
</script>

<style scoped>
.dsn-canvas-wrap { position: relative; flex: 1; overflow: auto; min-width: 0;
  display: flex; align-items: flex-start; justify-content: flex-start;
  padding: 24px; background: #03081c; }
.dsn-stage { flex: none; box-shadow: 0 0 0 1px rgba(0,229,255,.15); }
</style>
