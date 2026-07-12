<template>
  <div class="dsn-shape" :class="{ active, locked: element.isLock }"
       :style="shapeStyle" @mousedown.stop="onBodyDown" @contextmenu.prevent.stop="onContextMenu">
    <slot />
    <!-- 8 个缩放控制点,仅选中且未锁定时显示 -->
    <template v-if="active && !element.isLock">
      <div v-for="p in points" :key="p" class="dsn-point" :class="'p-' + p"
           :style="pointStyle(p)" @mousedown.stop.prevent="onPointDown(p, $event)" />
    </template>
  </div>
</template>

<script setup>
// 单组件可交互外框——参照 DataEase Shape.vue 的 handleMouseDownOnShape(拖拽)与
// handleMouseDownOnPoint(8 点缩放)改写。与原实现差异:
//  ① 去旋转:删除 getCursor/rotate 分支与 calculateComponentPositionAndSize 的旋转参数,
//     8 点缩放退化为「按点位直接调 top/left/width/height」的无旋转版;
//  ② 坐标模型:鼠标位移 (clientX-startX) 先除以 store.scale 换算成设计态位移(screenDeltaToDesign),
//     再叠加到设计态 top/left/width/height(恒 1920×1080 基准),不做 DataEase 的增量 scale 换算;
//  ③ rAF 节流(~30fps)保留;拖拽时调 MarkLine.snapAndShow 吸附;Shift 保持宽高比;
//  ④ clampRect 兜底禁拖出画布/极小尺寸。
import { computed, inject } from 'vue';
import { screenDeltaToDesign, clampRect } from '@/views/screen/designer/utils/scale';
import { useScreenDesignerStore } from '@/stores/screenDesigner';

const props = defineProps({
  element: { type: Object, required: true },
  index: { type: Number, required: true }
});
const store = useScreenDesignerStore();
const markLine = inject('markLineRef');       // CanvasCore provide 的 MarkLine 实例
const points = ['lt', 't', 'rt', 'r', 'rb', 'b', 'lb', 'l'];

const active = computed(() => store.curComponent?.id === props.element.id);
const shapeStyle = computed(() => ({
  position: 'absolute',
  top: props.element.style.top + 'px',
  left: props.element.style.left + 'px',
  width: props.element.style.width + 'px',
  height: props.element.style.height + 'px',
  display: props.element.isShow === false ? 'none' : 'block'
}));

function pointStyle(p) {
  // 8 点位置(百分比锚点);无旋转,直接摆四角四边中点
  const map = {
    lt: { left: '0', top: '0' }, t: { left: '50%', top: '0' }, rt: { left: '100%', top: '0' },
    r: { left: '100%', top: '50%' }, rb: { left: '100%', top: '100%' },
    b: { left: '50%', top: '100%' }, lb: { left: '0', top: '100%' }, l: { left: '0', top: '50%' }
  };
  const cursorMap = { lt: 'nwse-resize', rb: 'nwse-resize', rt: 'nesw-resize', lb: 'nesw-resize',
    t: 'ns-resize', b: 'ns-resize', l: 'ew-resize', r: 'ew-resize' };
  return { ...map[p], transform: 'translate(-50%, -50%)', cursor: cursorMap[p] };
}

function onBodyDown(e) {
  store.selectComponent(props.element.id);
  if (props.element.isLock) return;

  const start = { x: e.clientX, y: e.clientY };
  const origin = { ...props.element.style };
  let moved = false;
  let rafId = null, lastTs = 0;

  const move = ev => {
    const now = Date.now();
    if (now - lastTs < 25 && rafId) return;
    if (rafId) cancelAnimationFrame(rafId);
    rafId = requestAnimationFrame(() => {
      moved = true;
      // 屏幕位移 → 设计态位移(除以 scale)
      const dx = screenDeltaToDesign(ev.clientX - start.x, store.scale);
      const dy = screenDeltaToDesign(ev.clientY - start.y, store.scale);
      let next = { top: origin.top + dy, left: origin.left + dx,
        width: origin.width, height: origin.height };
      // 吸附对齐(命中则贴齐 + 显示对齐线)
      if (markLine?.value) {
        const snapped = markLine.value.snapAndShow({ ...next });
        next.top = snapped.top; next.left = snapped.left;
      }
      next = clampRect(next);
      store.setShapeStyle({ top: next.top, left: next.left });
      lastTs = now; rafId = null;
    });
  };
  const up = () => {
    if (rafId) cancelAnimationFrame(rafId);
    markLine?.value?.clear();
    document.removeEventListener('mousemove', move);
    document.removeEventListener('mouseup', up);
    if (moved) store.recordSnapshot(); // 有位移才记快照
  };
  document.addEventListener('mousemove', move);
  document.addEventListener('mouseup', up);
}

function onPointDown(point, e) {
  store.selectComponent(props.element.id);
  const start = { x: e.clientX, y: e.clientY };
  const origin = { ...props.element.style };
  const ratio = origin.width / origin.height;
  let resized = false;
  let rafId = null, lastTs = 0;

  const move = ev => {
    const now = Date.now();
    if (now - lastTs < 25 && rafId) return;
    if (rafId) cancelAnimationFrame(rafId);
    rafId = requestAnimationFrame(() => {
      resized = true;
      const dx = screenDeltaToDesign(ev.clientX - start.x, store.scale);
      const dy = screenDeltaToDesign(ev.clientY - start.y, store.scale);
      let { top, left, width, height } = origin;
      // 无旋转 8 点缩放:含 l 的点改 left+width,含 r 改 width,含 t 改 top+height,含 b 改 height
      if (point.includes('l')) { left = origin.left + dx; width = origin.width - dx; }
      if (point.includes('r')) { width = origin.width + dx; }
      if (point.includes('t')) { top = origin.top + dy; height = origin.height - dy; }
      if (point.includes('b')) { height = origin.height + dy; }
      // Shift 保持宽高比(以宽为准反推高,四角点生效)
      if (ev.shiftKey && point.length === 2) {
        height = width / ratio;
        if (point.includes('t')) top = origin.top + (origin.height - height);
      }
      const clamped = clampRect({ top, left, width, height });
      store.setShapeStyle(clamped);
      lastTs = now; rafId = null;
    });
  };
  const up = () => {
    if (rafId) cancelAnimationFrame(rafId);
    document.removeEventListener('mousemove', move);
    document.removeEventListener('mouseup', up);
    if (resized) store.recordSnapshot();
  };
  document.addEventListener('mousemove', move);
  document.addEventListener('mouseup', up);
}

const emit = defineEmits(['contextmenu']);
function onContextMenu(e) {
  store.selectComponent(props.element.id);
  emit('contextmenu', { x: e.offsetX, y: e.offsetY, clientX: e.clientX, clientY: e.clientY });
}
</script>

<style scoped>
.dsn-shape { box-sizing: border-box; }
.dsn-shape.active { outline: 1px solid #00e5ff; }
.dsn-shape.locked { cursor: not-allowed; }
.dsn-point { position: absolute; width: 8px; height: 8px; background: #fff;
  border: 1px solid #00e5ff; border-radius: 50%; z-index: 1001; }
</style>
