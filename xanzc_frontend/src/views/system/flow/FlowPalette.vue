<template>
  <!-- 左侧节点工具盘：拖出节点到画布；START/END 通过下方按钮补齐（保证唯一） -->
  <div class="flow-palette">
    <div class="palette-title">节点</div>

    <div
      v-for="item in dragItems"
      :key="item.type"
      class="palette-item"
      :class="'pi-' + item.type.toLowerCase()"
      draggable="true"
      :title="item.tip"
      @dragstart="onDragStart($event, item.type)"
    >
      <span class="pi-glyph" :class="'glyph-' + item.type.toLowerCase()"></span>
      <span class="pi-label">{{ item.label }}</span>
    </div>

    <div class="palette-divider"></div>

    <div class="palette-title">端点</div>
    <el-button
      class="endpoint-btn"
      size="small"
      :disabled="hasStart"
      @click="$emit('add-endpoint', 'START')"
    >+ 开始节点{{ hasStart ? '（已有）' : '' }}</el-button>
    <el-button
      class="endpoint-btn"
      size="small"
      @click="$emit('add-endpoint', 'END')"
    >+ 结束节点</el-button>

    <div class="palette-hint">
      拖动「审批 / 网关」到右侧画布放置；<br>
      节点悬停底部蓝点可拖出连线。
    </div>
  </div>
</template>

<script setup>
defineProps({
  /** 画布是否已存在 START 节点（限制只加一个开始） */
  hasStart: { type: Boolean, default: false }
});
defineEmits(['add-endpoint']);

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
  padding: 12px;
  background: #fff;
  border: 1px solid $border-2;
  border-radius: 6px;
}
.palette-title { font-size: 13px; font-weight: 600; color: $text-2; margin-bottom: 10px; }

.palette-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  margin-bottom: 8px;
  background: #f8fafc;
  border: 1px solid $border-2;
  border-radius: 6px;
  cursor: grab;
  transition: border-color .15s, box-shadow .15s;
}
.palette-item:hover { border-color: #2563eb; box-shadow: 0 1px 4px rgba(37, 99, 235, .15); }
.palette-item:active { cursor: grabbing; }
.pi-label { font-size: 13px; color: $text-1; }

.pi-glyph {
  width: 20px; height: 20px; flex-shrink: 0;
  border: 1.5px solid #94a3b8;
  background: #fff;
}
.glyph-approval { border-radius: 4px; border-color: #3b82f6; }
.glyph-gateway { transform: rotate(45deg); border-radius: 3px; border-color: #f59e0b; }

.palette-divider { height: 1px; background: $border-2; margin: 14px 0; }

.endpoint-btn { width: 100%; margin: 0 0 8px 0 !important; }

.palette-hint {
  margin-top: 12px;
  font-size: 11px;
  line-height: 1.6;
  color: $text-3;
}
</style>
