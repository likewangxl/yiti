<template>
  <div class="dsn-shape" :class="{ active, locked: element.isLock }"
       :style="shapeStyle" @mousedown.stop="onBodyDown" @contextmenu.prevent.stop="onContextMenu">
    <slot />
    <!-- 8 个缩放控制点,仅单选选中且未锁定时显示(多选态只描边,不出控制点) -->
    <template v-if="soloActive && !element.isLock">
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
// 多选增强:Ctrl+点击增删选择;组件已在多选集合中时按下拖动 = 批量拖动全部选中组件
// (多选拖动不做吸附,逐个 clampRect);Group 节点 8 点缩放时子组件按比例换算
// (以按下时 children 快照为基准一次性换算,避免连续缩放累积误差)。
import { computed, inject } from 'vue';
import { screenDeltaToDesign, clampRect } from '@/views/screen/designer/utils/scale';
import { componentBackgroundStyle } from '@/views/screen/designer/utils/background';
import { scaleGroupChildren } from '@/views/screen/designer/utils/group';
import { useScreenDesignerStore } from '@/stores/screenDesigner';

const props = defineProps({
  element: { type: Object, required: true },
  index: { type: Number, required: true }
});
const store = useScreenDesignerStore();
const markLine = inject('markLineRef');       // CanvasCore provide 的 MarkLine 实例
const points = ['lt', 't', 'rt', 'r', 'rb', 'b', 'lb', 'l'];

// 选中态 = 在多选集合中(单选是长度 1 特例);8 点缩放控制点仅单选时显示(见模板 active && 单选)
const active = computed(() => store.curComponents.some(c => c.id === props.element.id));
const soloActive = computed(() => active.value && store.curComponents.length === 1);
const shapeStyle = computed(() => ({
  position: 'absolute',
  top: props.element.style.top + 'px',
  left: props.element.style.left + 'px',
  width: props.element.style.width + 'px',
  height: props.element.style.height + 'px',
  display: props.element.isShow === false ? 'none' : 'block',
  // 组件级背景(透明/纯色/渐变)——设计态与运行时(ScreenRenderer.absStyle)同一纯函数,WYSIWYG
  ...componentBackgroundStyle(props.element.style)
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
  // Ctrl/Cmd+点击:增删选择,不启动拖拽
  if (e.ctrlKey || e.metaKey) { store.toggleSelect(props.element.id); return; }
  // 已在多选集合中则保持选区(按下即批量拖动入口);否则退化为单选
  if (!store.curComponents.some(c => c.id === props.element.id)) {
    store.selectComponent(props.element.id);
  }
  if (props.element.isLock) return;

  const multi = store.curComponents.length > 1;
  // 拖动目标:多选=全部未锁定选中组件(各自记 origin);单选=自身
  const targets = (multi ? store.curComponents.filter(c => !c.isLock) : [props.element])
    .map(c => ({ c, origin: { ...c.style } }));
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
      if (multi) {
        // 批量拖动:同一位移应用到全部选中组件,逐个 clampRect(不做吸附)
        for (const t of targets) {
          const next = clampRect({ top: t.origin.top + dy, left: t.origin.left + dx,
            width: t.origin.width, height: t.origin.height });
          t.c.style = { ...t.c.style, top: next.top, left: next.left };
        }
      } else {
        let next = { top: origin.top + dy, left: origin.left + dx,
          width: origin.width, height: origin.height };
        // 吸附对齐(命中则贴齐 + 显示对齐线)
        if (markLine?.value) {
          const snapped = markLine.value.snapAndShow({ ...next });
          next.top = snapped.top; next.left = snapped.left;
        }
        next = clampRect(next);
        store.setShapeStyle({ top: next.top, left: next.left });
      }
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
  // Group 缩放:以按下时 children 快照为换算基准(scaleGroupChildren 不 mutate 输入,
  // 每次 move 从同一基准一次性换算到当前尺寸,无累积误差)
  const groupChildrenStart = props.element.component === 'Group' && Array.isArray(props.element.children)
    ? props.element.children : null;
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
      // Group:子组件相对坐标与尺寸按 origin→clamped 比例换算,保持与组当前尺寸自洽
      if (groupChildrenStart) {
        props.element.children = scaleGroupChildren(groupChildrenStart,
          { width: origin.width, height: origin.height },
          { width: clamped.width, height: clamped.height });
      }
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
  // 组件已在多选集合中则保持选区(多选右键弹「成组」等批量操作),否则单选它
  if (!store.curComponents.some(c => c.id === props.element.id)) {
    store.selectComponent(props.element.id);
  }
  emit('contextmenu', { x: e.offsetX, y: e.offsetY, clientX: e.clientX, clientY: e.clientY });
}
</script>

<style scoped>
.dsn-shape { box-sizing: border-box; }
/* 选中态微调:描边外加一圈弱青色泛光,深色底上比单 1px outline 更易辨识(编辑态专属,不进渲染包) */
.dsn-shape.active { outline: 1px solid #00e5ff; box-shadow: 0 0 0 1px rgba(0,229,255,.25), 0 0 10px rgba(0,229,255,.35); }
.dsn-shape.locked { cursor: not-allowed; }
.dsn-point { position: absolute; width: 8px; height: 8px; background: #fff;
  border: 1px solid #00e5ff; border-radius: 50%; z-index: 1001; }
</style>
