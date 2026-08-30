<template>
  <!--
    可视化审批流画布：节点用绝对定位 div 渲染（便于排版徽标），
    连线用底层 SVG <path> + <marker> 箭头绘制，二者共用同一坐标系。
    所有结构性变更（建边/删节点/删边/选中）通过 emit 上抛由父组件 FlowEdit 统一写 graph；
    节点拖拽直接改 node.posX/posY（节点对象是父级 reactive 引用，改即响应）。
  -->
  <div
    ref="canvasRef"
    class="flow-canvas"
    :class="{ 'is-readonly': readonly }"
    role="region"
    tabindex="0"
    aria-label="流程设计画布"
    :aria-describedby="nodes.length ? undefined : 'flow-canvas-empty'"
    @mousedown.self="onBlankMouseDown"
    @keydown.stop="onKeyDown"
    @dragover.prevent
    @drop="onDrop"
  >
    <!-- 连线层（SVG）-->
    <svg class="edge-layer" :width="canvasW" :height="canvasH">
      <defs>
        <!-- 普通箭头 -->
        <marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="3"
                orient="auto" markerUnits="strokeWidth">
          <path d="M0,0 L8,3 L0,6 Z" fill="var(--color-text-muted)" />
        </marker>
        <!-- 选中态箭头 -->
        <marker id="arrow-active" markerWidth="10" markerHeight="10" refX="8" refY="3"
                orient="auto" markerUnits="strokeWidth">
          <path d="M0,0 L8,3 L0,6 Z" fill="var(--color-focus)" />
        </marker>
      </defs>

      <!-- 已有连线 -->
      <g v-for="(e, idx) in edgeGeoms" :key="'edge-' + idx">
        <!-- 透明加宽线提升点击命中区 -->
        <path
          :d="e.d"
          class="edge-hit"
          role="button"
          tabindex="0"
          :aria-label="edgeAriaLabel(idx)"
          @mousedown.stop="selectEdge(idx)"
          @keydown.enter.prevent.stop="selectEdge(idx)"
          @keydown.space.prevent.stop="selectEdge(idx)"
        />
        <path
          :d="e.d"
          class="edge-line"
          :class="{ active: isEdgeSelected(idx) }"
          :marker-end="isEdgeSelected(idx) ? 'url(#arrow-active)' : 'url(#arrow)'"
        />
        <!-- 分支条件 / 默认 徽标 -->
        <foreignObject
          v-if="e.badge"
          :x="e.mx - 60"
          :y="e.my - 13"
          width="120"
          height="26"
          class="edge-badge-fo"
        >
          <div class="edge-badge" :class="{ active: isEdgeSelected(idx) }" :title="e.badge">
            {{ e.badge }}
          </div>
        </foreignObject>
      </g>

      <!-- 建边中临时线 -->
      <path v-if="linking.active" :d="linking.d" class="edge-line linking" marker-end="url(#arrow)" />
    </svg>

    <!-- 节点层（绝对定位 div）-->
    <div
      v-for="node in nodes"
      :key="node.nodeKey"
      class="flow-node"
      :class="[
        'type-' + (node.nodeType || 'APPROVAL').toLowerCase(),
        { selected: isNodeSelected(node.nodeKey) }
      ]"
      :style="nodeStyle(node)"
      role="button"
      tabindex="0"
      :aria-label="nodeAriaLabel(node)"
      :aria-pressed="isNodeSelected(node.nodeKey) ? 'true' : 'false'"
      @mousedown.stop="onNodeMouseDown($event, node)"
      @keydown.enter.prevent.stop="selectNode(node)"
      @keydown.space.prevent.stop="selectNode(node)"
    >
      <!-- 节点主体内容 -->
      <div class="node-inner">
        <span class="node-name">{{ node.name || nodeTypeLabel(node.nodeType) || node.nodeKey }}</span>
        <span v-if="node.nodeType === 'APPROVAL'" class="node-meta">
          <span class="mode-tag">{{ node.approveMode === 'GROUP_ALL' ? '按机构会签' : (node.approveMode === 'ALL' ? '会签' : '或签') }}</span>
          <span class="apv-cnt">{{ node.approveMode === 'GROUP_ALL' ? '审批机构组' : '审批人' }} {{ (node.approvers || []).length }}</span>
        </span>
      </div>

      <!-- 出边锚点（START/APPROVAL/GATEWAY 有出边；END 无）-->
      <button
        v-if="!readonly && node.nodeType !== 'END'"
        type="button"
        class="node-anchor"
        :aria-label="`从${node.name || nodeTypeLabel(node.nodeType)}开始创建连线`"
        title="从此处拖拽到目标节点建立流转连线"
        @mousedown.stop="onAnchorMouseDown($event, node)"
      ></button>
    </div>

    <!-- 空态提示 -->
    <div v-if="!nodes.length" id="flow-canvas-empty" class="canvas-empty" role="status">
      从左侧拖入节点，或点击「+ 添加节点」开始绘制审批流程图
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { ElMessage } from 'element-plus';

const props = defineProps({
  /** 节点数组（父级 graph.nodes 的 reactive 引用），每项含 nodeKey/nodeType/name/approveMode/approvers/posX/posY */
  nodes: { type: Array, default: () => [] },
  /** 连线数组（父级 graph.edges 引用），每项含 fromNodeKey/toNodeKey/isDefault/condition */
  edges: { type: Array, default: () => [] },
  /** 当前选中：{ type:'node'|'edge'|null, key } —— key 为 nodeKey 或 edge 下标 */
  selection: { type: Object, default: () => ({ type: null, key: null }) },
  /** 只读（导入流程）：禁用拖拽/建边/选中编辑 */
  readonly: { type: Boolean, default: false },
  /** 用于在连线徽标上文字化条件的变量白名单 [{field,label,type}] */
  variables: { type: Array, default: () => [] }
});

const emit = defineEmits(['select', 'create-edge', 'delete-node', 'delete-edge', 'drop-node']);

/** 从左侧工具盘拖入节点：在落点位置新增对应类型节点 */
function onDrop(evt) {
  if (props.readonly) return;
  const nodeType = evt.dataTransfer?.getData('flow/node-type');
  if (!nodeType) return;
  const size = NODE_SIZE[nodeType] || NODE_SIZE.APPROVAL;
  // 落点为节点中心，换算成左上角内容坐标（含滚动量）
  const p = relPoint(evt);
  const posX = Math.max(0, Math.round(p.x - size.w / 2));
  const posY = Math.max(0, Math.round(p.y - size.h / 2));
  emit('drop-node', { nodeType, posX, posY });
}

// ---- 节点类型展示 & 尺寸 ----
const NODE_TYPE_LABEL = { START: '开始', APPROVAL: '审批', GATEWAY: '网关', END: '结束' };
function nodeTypeLabel(t) { return NODE_TYPE_LABEL[t] || t; }

// 每种节点的盒子尺寸（与 CSS 中对应类宽高保持一致，用于连线端点几何计算）
const NODE_SIZE = {
  START: { w: 96, h: 44 },
  END: { w: 96, h: 44 },
  APPROVAL: { w: 168, h: 64 },
  GATEWAY: { w: 76, h: 76 }
};
function sizeOf(node) { return NODE_SIZE[node.nodeType] || NODE_SIZE.APPROVAL; }

// ---- 画布尺寸（随节点自适应增长，留白）----
const canvasRef = ref(null);
const canvasW = computed(() => {
  const maxX = props.nodes.reduce((m, n) => Math.max(m, (n.posX || 0) + sizeOf(n).w), 0);
  return Math.max(1000, maxX + 120);
});
const canvasH = computed(() => {
  const maxY = props.nodes.reduce((m, n) => Math.max(m, (n.posY || 0) + sizeOf(n).h), 0);
  return Math.max(560, maxY + 120);
});

function nodeStyle(node) {
  const s = sizeOf(node);
  return {
    left: (node.posX || 0) + 'px',
    top: (node.posY || 0) + 'px',
    width: s.w + 'px',
    height: s.h + 'px'
  };
}

// ---- 入场布局兜底：缺坐标的节点按 sortNo/出现序纵向等距排布 ----
function ensureLayout() {
  const missing = props.nodes.filter(n => n.posX == null || n.posY == null);
  if (!missing.length) return;
  // 已有坐标的不动；缺坐标的按 sortNo（或出现序）纵向排在中间一列
  const ordered = [...props.nodes].sort((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0));
  let i = 0;
  for (const n of ordered) {
    if (n.posX == null || n.posY == null) {
      n.posX = 360;
      n.posY = 40 + i * 110;
    }
    i++;
  }
}
onMounted(ensureLayout);
// 异步加载流程后节点才填充，监听数量变化补跑兜底布局
watch(() => props.nodes.length, ensureLayout);

// ---- 选中判定 ----
function isNodeSelected(key) { return props.selection?.type === 'node' && props.selection?.key === key; }
function isEdgeSelected(idx) { return props.selection?.type === 'edge' && props.selection?.key === idx; }

function nodeAriaLabel(node) {
  const parts = [`${nodeTypeLabel(node.nodeType)}节点`, node.name || node.nodeKey || '未命名'];
  if (node.nodeType === 'APPROVAL') {
    const grouped = node.approveMode === 'GROUP_ALL';
    parts.push(grouped ? '按机构会签' : (node.approveMode === 'ALL' ? '会签' : '或签'),
      `${grouped ? '审批机构组' : '审批人'} ${(node.approvers || []).length} 名`);
  }
  return parts.join('，');
}
function edgeAriaLabel(idx) {
  const edge = props.edges[idx];
  if (!edge) return '选择流程连线';
  const from = nodeByKey.value[edge.fromNodeKey];
  const to = nodeByKey.value[edge.toNodeKey];
  return `选择连线：${from?.name || edge.fromNodeKey || '-'} 到 ${to?.name || edge.toNodeKey || '-'}`;
}

function selectEdge(idx) {
  if (props.readonly) return;
  emit('select', { type: 'edge', key: idx });
}
function selectNode(node) {
  emit('select', { type: 'node', key: node.nodeKey });
}
function onBlankMouseDown() {
  // 点击空白：清空选中
  emit('select', { type: null, key: null });
}

// ---- 几何：求中心连线与节点矩形边框的交点（箭头落在节点边缘而非中心）----
function centerOf(node) {
  const s = sizeOf(node);
  return { x: (node.posX || 0) + s.w / 2, y: (node.posY || 0) + s.h / 2 };
}
/** 从矩形中心 c 沿朝向 t 的方向，求与矩形边框（半宽 hw、半高 hh）的交点 */
function borderPoint(c, t, hw, hh) {
  const dx = t.x - c.x, dy = t.y - c.y;
  if (dx === 0 && dy === 0) return { x: c.x, y: c.y };
  const scale = 1 / Math.max(Math.abs(dx) / hw, Math.abs(dy) / hh);
  return { x: c.x + dx * scale, y: c.y + dy * scale };
}

const nodeByKey = computed(() => {
  const m = {};
  for (const n of props.nodes) m[n.nodeKey] = n;
  return m;
});

// ---- 连线几何 + 徽标 ----
const edgeGeoms = computed(() => {
  return props.edges.map(e => {
    const from = nodeByKey.value[e.fromNodeKey];
    const to = nodeByKey.value[e.toNodeKey];
    if (!from || !to) return { d: '', mx: 0, my: 0, badge: '' };
    const cf = centerOf(from), ct = centerOf(to);
    const sf = sizeOf(from), st = sizeOf(to);
    const p1 = borderPoint(cf, ct, sf.w / 2, sf.h / 2);
    const p2 = borderPoint(ct, cf, st.w / 2, st.h / 2);
    // 三次贝塞尔：控制点沿两端纵向偏移，线条更柔和
    const dyAbs = Math.abs(p2.y - p1.y);
    const cOff = Math.max(30, dyAbs / 2);
    const d = `M ${p1.x},${p1.y} C ${p1.x},${p1.y + cOff} ${p2.x},${p2.y - cOff} ${p2.x},${p2.y}`;
    return {
      d,
      mx: (p1.x + p2.x) / 2,
      my: (p1.y + p2.y) / 2,
      badge: edgeBadge(e)
    };
  });
});

// 条件运算符文字（与 ConditionBuilder 对齐）
const OP_LABEL = {
  EQ: '=', NE: '≠', GT: '>', GE: '≥', LT: '<', LE: '≤',
  IN: '属于', NOT_IN: '不属于', CONTAINS: '含'
};
function edgeBadge(e) {
  if (e.isDefault) return '默认分支';
  const cond = e.condition;
  if (!cond || !Array.isArray(cond.conditions) || !cond.conditions.length) return '';
  const c = cond.conditions[0];
  const label = (props.variables.find(v => v.field === c.field)?.label) || c.field || '?';
  const more = cond.conditions.length > 1 ? ` 等${cond.conditions.length}项` : '';
  return `${label}${OP_LABEL[c.op] || c.op || ''}${c.value ?? ''}${more}`;
}

// ====================================================================== //
//  节点拖拽                                                              //
// ====================================================================== //
const drag = reactive({ active: false, node: null, offX: 0, offY: 0, moved: false });

function relPoint(evt) {
  // 内容坐标 = 视口偏移 + 画布滚动量（画布 overflow:auto，节点用内容坐标定位）
  const el = canvasRef.value;
  const rect = el.getBoundingClientRect();
  return {
    x: evt.clientX - rect.left + el.scrollLeft,
    y: evt.clientY - rect.top + el.scrollTop
  };
}

function onNodeMouseDown(evt, node) {
  if (props.readonly) {
    // 只读也允许选中查看
    emit('select', { type: 'node', key: node.nodeKey });
    return;
  }
  const p = relPoint(evt);
  drag.active = true;
  drag.node = node;
  drag.offX = p.x - (node.posX || 0);
  drag.offY = p.y - (node.posY || 0);
  drag.moved = false;
}

// ====================================================================== //
//  锚点拉线建边                                                          //
// ====================================================================== //
const linking = reactive({ active: false, fromKey: null, d: '', x1: 0, y1: 0 });

function onAnchorMouseDown(evt, node) {
  if (props.readonly) return;
  const c = centerOf(node);
  const s = sizeOf(node);
  linking.active = true;
  linking.fromKey = node.nodeKey;
  // 起点取节点底部中点
  linking.x1 = c.x;
  linking.y1 = c.y + s.h / 2;
  const p = relPoint(evt);
  linking.d = `M ${linking.x1},${linking.y1} L ${p.x},${p.y}`;
}

// ---- 命中测试：光标落在哪个节点上 ----
function hitNode(x, y) {
  // 逆序遍历，命中最上层
  for (let i = props.nodes.length - 1; i >= 0; i--) {
    const n = props.nodes[i];
    const s = sizeOf(n);
    const x0 = n.posX || 0, y0 = n.posY || 0;
    if (x >= x0 && x <= x0 + s.w && y >= y0 && y <= y0 + s.h) return n;
  }
  return null;
}

// ====================================================================== //
//  全局鼠标移动/抬起                                                     //
// ====================================================================== //
function onMouseMove(evt) {
  if (drag.active && drag.node) {
    const p = relPoint(evt);
    const nx = Math.max(0, Math.round(p.x - drag.offX));
    const ny = Math.max(0, Math.round(p.y - drag.offY));
    if (Math.abs(nx - (drag.node.posX || 0)) > 2 || Math.abs(ny - (drag.node.posY || 0)) > 2) {
      drag.moved = true;
    }
    drag.node.posX = nx;
    drag.node.posY = ny;
  } else if (linking.active) {
    const p = relPoint(evt);
    linking.d = `M ${linking.x1},${linking.y1} L ${p.x},${p.y}`;
  }
}

function onMouseUp(evt) {
  if (drag.active) {
    const node = drag.node;
    const moved = drag.moved;
    drag.active = false;
    drag.node = null;
    // 未拖动视为点击 → 选中该节点
    if (!moved && node) emit('select', { type: 'node', key: node.nodeKey });
  } else if (linking.active) {
    const p = relPoint(evt);
    const target = hitNode(p.x, p.y);
    const fromKey = linking.fromKey;
    linking.active = false;
    linking.fromKey = null;
    if (target) tryCreateEdge(fromKey, target);
  }
}

/** 建边即时校验：禁自环 / 禁指向 START / 禁从 END 出 / 禁重复边 */
function tryCreateEdge(fromKey, toNode) {
  const toKey = toNode.nodeKey;
  if (fromKey === toKey) { ElMessage.warning('不能连接到自身'); return; }
  if (toNode.nodeType === 'START') { ElMessage.warning('开始节点不能作为流转目标'); return; }
  const fromNode = nodeByKey.value[fromKey];
  if (fromNode?.nodeType === 'END') { ElMessage.warning('结束节点不能有出边'); return; }
  const dup = props.edges.some(e => e.fromNodeKey === fromKey && e.toNodeKey === toKey);
  if (dup) { ElMessage.warning('该连线已存在'); return; }
  emit('create-edge', { fromNodeKey: fromKey, toNodeKey: toKey });
}

// ---- 键盘删除选中 ----
function onKeyDown(evt) {
  if (props.readonly) return;
  if (evt.key !== 'Delete' && evt.key !== 'Backspace') return;
  // 避免在输入框里误删
  const tag = (evt.target?.tagName || '').toLowerCase();
  if (tag === 'input' || tag === 'textarea' || evt.target?.isContentEditable) return;
  if (props.selection?.type === 'node') emit('delete-node', props.selection.key);
  else if (props.selection?.type === 'edge') emit('delete-edge', props.selection.key);
}

onMounted(() => {
  window.addEventListener('mousemove', onMouseMove);
  window.addEventListener('mouseup', onMouseUp);
  window.addEventListener('keydown', onKeyDown);
});
onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onMouseMove);
  window.removeEventListener('mouseup', onMouseUp);
  window.removeEventListener('keydown', onKeyDown);
});

// 供父组件在外部新增节点后定位用：返回画布可视中心附近的空位坐标
defineExpose({
  nextDropPosition() {
    // 简单错位摆放，避免新节点叠在一起
    const n = props.nodes.length;
    return { posX: 120 + (n % 4) * 60, posY: 60 + n * 24 };
  }
});
</script>

<style lang="scss" scoped>
.flow-canvas {
  position: relative;
  width: 100%;
  height: 100%;        /* 占满父容器（流程图编辑区填满剩余高度）*/
  min-height: 420px;
  overflow: auto;
  background-color: var(--color-surface-soft);
  // 流程图网格底纹
  background-image:
    linear-gradient(var(--color-border) 1px, transparent 1px),
    linear-gradient(90deg, var(--color-border) 1px, transparent 1px);
  background-size: 20px 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  user-select: none;
}
.flow-canvas.is-readonly { cursor: default; }
.flow-canvas:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 2px; }

.edge-layer {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;          // 默认不挡节点；命中线单独开 pointer-events
}
.edge-hit {
  fill: none;
  stroke: transparent;
  stroke-width: 12;
  pointer-events: stroke;
  cursor: pointer;
}
.edge-line {
  fill: none;
  stroke: var(--color-text-muted);
  stroke-width: 2;
  pointer-events: none;
  transition: stroke 180ms ease-out;
}
.edge-line.active { stroke: var(--color-focus); stroke-width: 2.5; }
.edge-line.linking { stroke: var(--color-focus); stroke-dasharray: 5 4; }

.edge-badge-fo { overflow: visible; pointer-events: none; }
.edge-badge {
  display: inline-block;
  max-width: 120px;
  margin: 0 auto;
  padding: 1px 8px;
  font-size: 12px;
  line-height: 18px;
  color: var(--color-text);
  text-align: center;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 9px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.edge-badge.active { color: var(--color-brand-700); border-color: var(--color-focus); }

// ---- 节点 ----
.flow-node {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  padding: 4px 10px;
  appearance: none;
  background: var(--color-surface);
  border: 1.5px solid var(--color-border-strong);
  border-radius: var(--radius-control);
  box-shadow: var(--shadow-surface);
  cursor: move;
  transition: box-shadow 180ms ease-out, border-color 180ms ease-out;
  z-index: 2;
}
.flow-node:hover { border-color: var(--color-brand-500); box-shadow: var(--shadow-popover); }
.flow-node:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 2px; }
.flow-node.selected {
  border-color: var(--color-focus);
  box-shadow: 0 0 0 3px var(--color-brand-100);
  z-index: 3;
}
.node-inner { display: flex; flex-direction: column; align-items: center; gap: 2px; overflow: hidden; }
.node-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-strong);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}
.node-meta { display: flex; align-items: center; gap: 6px; font-size: 11px; }
.mode-tag { background: var(--color-warning-bg); border-radius: var(--radius-control); color: var(--color-warning-fg); padding: 0 5px; }
.apv-cnt { color: var(--color-text-muted); }

// START / END：胶囊
.flow-node.type-start { background: var(--color-success-bg); border-color: var(--color-success-fg); border-radius: 22px; }
.flow-node.type-start .node-name { color: var(--color-success-fg); }
.flow-node.type-end { background: var(--color-danger-bg); border-color: var(--color-danger-fg); border-radius: 22px; }
.flow-node.type-end .node-name { color: var(--color-danger-fg); }

// APPROVAL：矩形卡片（默认样式即矩形）
.flow-node.type-approval { border-color: var(--color-brand-500); }

// GATEWAY：菱形（用旋转盒子，内容反向旋转保持正立）
.flow-node.type-gateway {
  background: var(--color-warning-bg);
  border-color: var(--color-warning-fg);
  transform: rotate(45deg);
  border-radius: 8px;
}
.flow-node.type-gateway .node-inner { transform: rotate(-45deg); }
.flow-node.type-gateway .node-name { color: var(--color-warning-fg); font-size: 12px; }
.flow-node.type-gateway.selected { box-shadow: 0 0 0 3px var(--color-brand-100); }

// 出边锚点
.node-anchor {
  position: absolute;
  bottom: -7px;
  left: 50%;
  width: 12px;
  height: 12px;
  margin-left: -6px;
  appearance: none;
  background: var(--color-focus);
  border: 2px solid var(--color-surface);
  border-radius: 50%;
  cursor: crosshair;
  opacity: 0;
  transition: opacity 180ms ease-out;
  z-index: 4;
}
.flow-node:hover .node-anchor { opacity: 1; }
.flow-node:focus-within .node-anchor { opacity: 1; }
// 网关锚点：盒子被旋转 45°，锚点需补偿回正
.flow-node.type-gateway .node-anchor { transform: rotate(-45deg); bottom: 6px; left: 6px; }

.canvas-empty {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: var(--color-text-muted);
  font-size: 14px;
  pointer-events: none;
}

@media (prefers-reduced-motion: reduce) {
  .edge-line, .flow-node, .node-anchor { transition-duration: 0ms; }
}
</style>
