<template>
  <!-- 左侧节点工具盘：拖出节点到画布；START/END 通过下方按钮补齐（保证唯一） -->
  <div class="flow-palette" role="group" aria-label="流程节点工具箱">
    <div class="palette-title">可添加节点</div>

    <button
      v-for="item in dragItems"
      :key="item.type"
      type="button"
      class="palette-item"
      :class="'pi-' + item.type.toLowerCase()"
      draggable="true"
      :aria-label="`添加${item.label}`"
      :title="`${item.tip}；点击添加或拖到画布`"
      @click="$emit('add-node', item.type)"
      @dragstart="onDragStart($event, item.type)"
    >
      <span class="pi-glyph" :class="'glyph-' + item.type.toLowerCase()" aria-hidden="true"></span>
      <span class="pi-label">{{ item.label }}</span>
    </button>

    <div class="palette-divider"></div>

    <div class="palette-title">流程端点</div>
    <el-button
      class="endpoint-btn"
      size="small"
      :disabled="hasStart"
      @click="$emit('add-endpoint', 'START')"
    >添加开始节点{{ hasStart ? '（已有）' : '' }}</el-button>
    <el-button
      class="endpoint-btn"
      size="small"
      @click="$emit('add-endpoint', 'END')"
    >添加结束节点</el-button>

    <div class="palette-hint">
      可点击或拖动节点到画布；选中节点后，可从底部锚点拖出连线。
    </div>
  </div>
</template>

<script setup>
defineProps({
  /** 画布是否已存在 START 节点（限制只加一个开始） */
  hasStart: { type: Boolean, default: false }
});
defineEmits(['add-endpoint', 'add-node']);

const dragItems = [
  { type: 'APPROVAL', label: '审批节点', tip: '审批环节，可配置审批人与会签/或签' },
  { type: 'GATEWAY', label: '网关节点', tip: '条件分流，出边携带动态条件分支' }
];

/** 拖拽开始：把节点类型写入 dataTransfer，画布 drop 时读取 */
function onDragStart(evt, type) {
  evt.dataTransfer.setData('flow/node-type', type);
  evt.dataTransfer.effectAllowed = 'copy';
}
</script>

<style lang="scss" scoped>
.flow-palette {
  width: 132px;
  flex-shrink: 0;
  padding: var(--space-3);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
}
.palette-title { color: var(--color-text); font-size: 13px; font-weight: 600; margin-bottom: var(--space-2); }

.palette-item {
  appearance: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  margin-bottom: var(--space-2);
  text-align: left;
  width: 100%;
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  cursor: grab;
  transition: border-color 180ms ease-out, box-shadow 180ms ease-out;
}
.palette-item:hover { border-color: var(--color-brand-500); box-shadow: var(--shadow-surface); }
.palette-item:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 2px; }
.palette-item:active { cursor: grabbing; }
.pi-label { color: var(--color-text-strong); font-size: 13px; }

.pi-glyph {
  width: 20px; height: 20px; flex-shrink: 0;
  border: 1.5px solid var(--color-text-muted);
  background: var(--color-surface);
}
.glyph-approval { border-radius: var(--radius-control); border-color: var(--color-brand-500); }
.glyph-gateway { transform: rotate(45deg); border-radius: 3px; border-color: var(--color-warning-fg); }

.palette-divider { height: 1px; background: var(--color-border); margin: var(--space-3) 0; }

.endpoint-btn { width: 100%; margin: 0 0 8px 0 !important; }

.palette-hint {
  margin-top: var(--space-3);
  font-size: 11px;
  line-height: 1.6;
  color: var(--color-text-muted);
}

@media (prefers-reduced-motion: reduce) {
  .palette-item { transition-duration: 0ms; }
}
</style>
