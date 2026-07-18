<template>
  <div ref="wrapRef" class="dsn-canvas-wrap" @mousedown.self="deselect" @drop.prevent="onDrop" @dragover.prevent>
    <div ref="stageRef" class="dsn-stage" :style="stageStyle"
         @mousedown.self="onStageDown" @contextmenu.prevent="onCanvasContextMenu">
      <!-- 组件层:图层顺序=componentData 数组顺序 -->
      <Shape v-for="(c, i) in store.componentData" :key="c.id" :element="c" :index="i"
             @contextmenu="onShapeContextMenu">
        <component :is="widgetOf(c.component)" :element="c" mode="design" />
      </Shape>
      <MarkLine ref="markLineRef" />
      <!-- 框选矩形(设计态坐标,随舞台一起 scale) -->
      <div v-if="marquee" class="dsn-marquee"
           :style="{ top: marquee.top + 'px', left: marquee.left + 'px',
                     width: marquee.width + 'px', height: marquee.height + 'px' }" />
    </div>
    <ContextMenu ref="ctxRef" />
  </div>
</template>

<script setup>
// 画布容器——参照 DataEase CanvasCore.vue 裁剪:保留空白点选取消、drop 落组件、右键入口、网格;
// Tab 移入移出/矩阵仍不做。二期增强:舞台空白左键拖拽 = 框选(半透明矩形与组件求交命中,
// 跳过锁定/隐藏,算法在 utils/marquee.js 纯函数);原地点击空白 = 清空选区。
// 舞台用 transform: scale(store.scale),设计态恒 1920×1080。
import { computed, provide, ref } from 'vue';
import { DESIGN_W, DESIGN_H, screenDeltaToDesign } from '@/views/screen/designer/utils/scale';
import { normalizeRect, hitComponents } from '@/views/screen/designer/utils/marquee';
import { canvasBackgroundStyle } from '@/views/screen/designer/utils/background';
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
  // 背景三选一(纯色/渐变/图片)与运行时 ScreenRenderer 共用同一纯函数;
  // 编辑态网格改由 .dsn-stage::before 覆盖层承载——图片背景会占用 backgroundImage,不能再内联叠网格
  ...canvasBackgroundStyle(store.canvasStyle)
}));

function widgetOf(component) { return findWidget(component); }
function deselect() { store.clearSelection(); ctxRef.value?.hide(); }

// ===== 框选(舞台空白左键拖拽) =====
const marquee = ref(null); // 设计态 {top,left,width,height};null=未框选
function onStageDown(e) {
  if (e.button !== 0) return; // 仅左键
  ctxRef.value?.hide();
  const rect = stageRef.value.getBoundingClientRect();
  // getBoundingClientRect 是缩放后的屏幕矩形,除以 scale 还原设计态坐标
  const toDesign = ev => ({
    x: screenDeltaToDesign(ev.clientX - rect.left, store.scale),
    y: screenDeltaToDesign(ev.clientY - rect.top, store.scale)
  });
  const start = toDesign(e);
  let dragged = false;
  const move = ev => {
    const r = normalizeRect(start, toDesign(ev));
    // 位移超过 2px 才认定是框选(与原地点击取消选择区分)
    if (dragged || r.width > 2 || r.height > 2) { dragged = true; marquee.value = r; }
  };
  const up = () => {
    document.removeEventListener('mousemove', move);
    document.removeEventListener('mouseup', up);
    if (dragged && marquee.value) {
      store.setCurComponents(hitComponents(marquee.value, store.componentData));
    } else {
      store.clearSelection(); // 原地点击空白 = 取消选择(保持原 deselect 行为)
    }
    marquee.value = null;
  };
  document.addEventListener('mousemove', move);
  document.addEventListener('mouseup', up);
}

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
/* 舞台描边微调:细描边 + 一圈弱青色泛光,强化画布边界的科技感(编辑态专属,不进渲染包) */
.dsn-stage { flex: none; box-shadow: 0 0 0 1px rgba(0,229,255,.28), 0 0 24px rgba(0,229,255,.10); }
/* 编辑态网格覆盖层:40px 细网格 + 200px 主网格(略亮),置于 ::before(先于组件绘制,压不住组件);
   pointer-events:none 不挡框选/拖放;从内联样式迁出以兼容图片背景(见 stageStyle 注释) */
.dsn-stage::before {
  content: ''; position: absolute; inset: 0; pointer-events: none;
  background-image:
    linear-gradient(rgba(0,229,255,.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0,229,255,.06) 1px, transparent 1px),
    linear-gradient(rgba(255,255,255,.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,.04) 1px, transparent 1px);
  background-size: 200px 200px, 200px 200px, 40px 40px, 40px 40px;
}
.dsn-marquee { position: absolute; z-index: 1002; pointer-events: none;
  background: rgba(0,229,255,.08); border: 1px dashed rgba(0,229,255,.6); }
</style>
